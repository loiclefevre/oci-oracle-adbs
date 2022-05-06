package com.oracle.dragonlite.work;

import com.oracle.bmc.database.DatabaseWaiters;
import com.oracle.bmc.database.model.AutonomousDatabase;
import com.oracle.bmc.database.model.AutonomousDatabaseConnectionStrings;
import com.oracle.bmc.database.model.AutonomousDatabaseSummary;
import com.oracle.bmc.database.model.CreateAutonomousDatabaseBase;
import com.oracle.bmc.database.model.CreateAutonomousDatabaseDetails;
import com.oracle.bmc.database.model.CustomerContact;
import com.oracle.bmc.database.model.DatabaseConnectionStringProfile;
import com.oracle.bmc.database.requests.CreateAutonomousDatabaseRequest;
import com.oracle.bmc.database.requests.GetAutonomousDatabaseRequest;
import com.oracle.bmc.database.requests.ListAutonomousDatabasesRequest;
import com.oracle.bmc.database.requests.StartAutonomousDatabaseRequest;
import com.oracle.bmc.database.requests.StopAutonomousDatabaseRequest;
import com.oracle.bmc.database.responses.CreateAutonomousDatabaseResponse;
import com.oracle.bmc.database.responses.GetAutonomousDatabaseResponse;
import com.oracle.bmc.database.responses.ListAutonomousDatabasesResponse;
import com.oracle.bmc.database.responses.StartAutonomousDatabaseResponse;
import com.oracle.bmc.database.responses.StopAutonomousDatabaseResponse;
import com.oracle.bmc.identity.requests.GetUserRequest;
import com.oracle.bmc.identity.responses.GetUserResponse;
import com.oracle.bmc.model.BmcException;
import com.oracle.bmc.workrequests.WorkRequestClient;
import com.oracle.bmc.workrequests.model.WorkRequestError;
import com.oracle.bmc.workrequests.requests.GetWorkRequestRequest;
import com.oracle.bmc.workrequests.requests.ListWorkRequestErrorsRequest;
import com.oracle.bmc.workrequests.responses.GetWorkRequestResponse;
import com.oracle.bmc.workrequests.responses.ListWorkRequestErrorsResponse;
import com.oracle.dragonlite.Main;
import com.oracle.dragonlite.exception.DLException;
import com.oracle.dragonlite.rest.ADBRESTService;
import com.oracle.dragonlite.util.PublicIPv4Retriever;
import com.oracle.dragonlite.util.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Start {
	private static final Logger logger = LoggerFactory.getLogger("Dragon Lite");

	public static void work(Main session, final long processStartTime) {
		// -2- validate the database with the wanted name doesn't exist already inside the given compartment
		final ListAutonomousDatabasesRequest listADB = ListAutonomousDatabasesRequest.builder()
				.displayName(session.getDbName()+"_Database")
				.dbVersion(session.getVersion())
				.dbWorkload(session.getWorkloadTypeSummary())
				.compartmentId(session.getConfigFile().get("compartment_id"))
				.build();
		final ListAutonomousDatabasesResponse listADBResponse = session.getDbClient().listAutonomousDatabases(listADB);
		boolean dbNameAlreadyExists = false;

		AutonomousDatabaseSummary alreadyExistADB = null;

		for (AutonomousDatabaseSummary adb : listADBResponse.getItems()) {
			if (adb.getLifecycleState() != AutonomousDatabaseSummary.LifecycleState.Terminated) {
				if (adb.getDbName().equals(session.getDbName())) {
					dbNameAlreadyExists = true;
					alreadyExistADB = adb;
					break;
				}
			}
		}

		String infoPanel = null;

		if (dbNameAlreadyExists) {
			logger.warn("database already exists");

			// if it exists already, validate this matches the one wanted!
			if (!alreadyExistADB.getDbVersion().equals(session.getVersion())) {
				if (session.isFreeTiersDatabaseResourceExhausted()) {
					logger.error("FREE_TIERS_DATABASE_RESOURCE_EXHAUSTED");
					throw new DLException(DLException.FREE_TIERS_DATABASE_RESOURCE_EXHAUSTED);
				}

				throw new DLException(DLException.EXISTING_DB_DOESNT_MATCH_VERSION);
			}

			if (!alreadyExistADB.getDbWorkload().toString().equals(session.getWorkloadType().toString())) {
				if (session.isFreeTiersDatabaseResourceExhausted()) {
					logger.error("FREE_TIERS_DATABASE_RESOURCE_EXHAUSTED");
					throw new DLException(DLException.FREE_TIERS_DATABASE_RESOURCE_EXHAUSTED);
				}

				throw new DLException(DLException.EXISTING_DB_DOESNT_MATCH_WORKLOAD_TYPE);
			}

			if (alreadyExistADB.getLifecycleState() != AutonomousDatabaseSummary.LifecycleState.Available) {
				// Start!
				WorkRequestClient workRequestClient = new WorkRequestClient(session.getProvider());
				workRequestClient.setRegion(session.getProvider().getRegion());
				StartAutonomousDatabaseResponse responseTerminate = session.getDbClient().startAutonomousDatabase(StartAutonomousDatabaseRequest.builder().autonomousDatabaseId(alreadyExistADB.getId()).build());
				String workRequestId = responseTerminate.getOpcWorkRequestId();

				GetWorkRequestRequest getWorkRequestRequest = GetWorkRequestRequest.builder().workRequestId(workRequestId).build();
				boolean exit = false;
				long startTime = System.currentTimeMillis();
				float pendingProgressMove = 0f;
				do {
					GetWorkRequestResponse getWorkRequestResponse = workRequestClient.getWorkRequest(getWorkRequestRequest);
					switch (getWorkRequestResponse.getWorkRequest().getStatus()) {
						case Succeeded:
							exit = true;
							break;
						case Failed:
							final ListWorkRequestErrorsResponse response = workRequestClient.listWorkRequestErrors(ListWorkRequestErrorsRequest.builder().workRequestId(workRequestId).opcRequestId(getWorkRequestResponse.getOpcRequestId()).build());
							final StringBuilder errors = new StringBuilder();
							int i = 0;
							for (WorkRequestError e : response.getItems()) {
								if (i > 0) {
									errors.append("\n");
								}
								errors.append(e.getMessage());
								i++;
							}
							logger.error(errors.toString());
							throw new DLException(DLException.CANT_START_ADBS);
						case Accepted:
							logger.debug("Start accepted");
							break;
						case InProgress:
							logger.debug("Start in progress: " + getWorkRequestResponse.getWorkRequest().getPercentComplete());
							break;
					}

					Utils.sleep(1000L);

				}
				while (!exit);

				DatabaseWaiters waiter = session.getDbClient().getWaiters();
				try {
					final GetAutonomousDatabaseResponse responseGet = waiter.forAutonomousDatabase(GetAutonomousDatabaseRequest.builder().autonomousDatabaseId(alreadyExistADB.getId()).build(),
							new AutonomousDatabase.LifecycleState[]{AutonomousDatabase.LifecycleState.Available}).execute();
				}
				catch (Exception e) {
					throw new DLException(DLException.WAIT_FOR_START_FAILURE, e);
				}
			}

			// database restarted!

			final ADBRESTService adminORDS = new ADBRESTService(alreadyExistADB.getConnectionUrls().getSqlDevWebUrl(),
					"ADMIN", session.getSystemPassword());

			try {
				adminORDS.execute("SELECT 1 FROM DUAL", 30);
			}
			catch (DLException dle) {
				// Stop, wrong database!
				WorkRequestClient workRequestClient = new WorkRequestClient(session.getProvider());
				workRequestClient.setRegion(session.getProvider().getRegion());
				StopAutonomousDatabaseResponse responseTerminate = session.getDbClient().stopAutonomousDatabase(StopAutonomousDatabaseRequest.builder().autonomousDatabaseId(alreadyExistADB.getId()).build());
				String workRequestId = responseTerminate.getOpcWorkRequestId();

				GetWorkRequestRequest getWorkRequestRequest = GetWorkRequestRequest.builder().workRequestId(workRequestId).build();
				boolean exit = false;
				long startTime = System.currentTimeMillis();
				float pendingProgressMove = 0f;
				do {
					GetWorkRequestResponse getWorkRequestResponse = workRequestClient.getWorkRequest(getWorkRequestRequest);
					switch (getWorkRequestResponse.getWorkRequest().getStatus()) {
						case Succeeded:
							exit = true;
							break;
						case Failed:
							final ListWorkRequestErrorsResponse response = workRequestClient.listWorkRequestErrors(ListWorkRequestErrorsRequest.builder().workRequestId(workRequestId).opcRequestId(getWorkRequestResponse.getOpcRequestId()).build());
							final StringBuilder errors = new StringBuilder();
							int i = 0;
							for (WorkRequestError e : response.getItems()) {
								if (i > 0) {
									errors.append("\n");
								}
								errors.append(e.getMessage());
								i++;
							}
							logger.error(errors.toString());
							throw new DLException(DLException.CANT_STOP_ADBS);
						case Accepted:
							logger.debug("Stop accepted");
							break;
						case InProgress:
							logger.debug("Stop in progress: " + getWorkRequestResponse.getWorkRequest().getPercentComplete());
							break;
					}

					Utils.sleep(1000L);

				}
				while (!exit);

				DatabaseWaiters waiter = session.getDbClient().getWaiters();
				try {
					final GetAutonomousDatabaseResponse responseGet = waiter.forAutonomousDatabase(GetAutonomousDatabaseRequest.builder().autonomousDatabaseId(alreadyExistADB.getId()).build(),
							new AutonomousDatabase.LifecycleState[]{AutonomousDatabase.LifecycleState.Available}).execute();
				}
				catch (Exception e) {
					throw new DLException(DLException.WAIT_FOR_STOP_FAILURE, e);
				}

				if (session.isFreeTiersDatabaseResourceExhausted()) {
					logger.error("FREE_TIERS_DATABASE_RESOURCE_EXHAUSTED");
					throw new DLException(DLException.FREE_TIERS_DATABASE_RESOURCE_EXHAUSTED);
				}

				throw new DLException(DLException.CANT_CHECK_ADMIN_CONNECTION, dle);
			}

			// ALL good: same
			// - tenant
			// - compartment
			// - db name
			// - type
			// - version
			// - ADMIN password

			// Test application user exists
			final ADBRESTService userORDS = new ADBRESTService(alreadyExistADB.getConnectionUrls().getSqlDevWebUrl(),
					session.getUsername().toUpperCase(), session.getUserPassword());

			try {
				userORDS.execute("SELECT 1 FROM DUAL", 1);
			}
			catch (DLException dle) {
				CreateDatabaseUser.createApplicationUser(session, alreadyExistADB.getConnectionUrls().getSqlDevWebUrl());
			}

			infoPanel = generateInfoPanel(alreadyExistADB);

			generateDatabaseConfiguration(alreadyExistADB.getConnectionStrings(),alreadyExistADB.getConnectionUrls().getSqlDevWebUrl());
		}
		//--------------------------------------------------------------------------------------------------------------
		// Database does not exist:
		else {
			if (session.isFreeTiersDatabaseResourceExhausted()) {
				logger.error("FREE_TIERS_DATABASE_RESOURCE_EXHAUSTED");
				throw new DLException(DLException.FREE_TIERS_DATABASE_RESOURCE_EXHAUSTED);
			}

			logger.info("create new database!");

			GetUserResponse userResponse = session.getIdentityClient().getUser(GetUserRequest.builder().userId(session.getConfigFile().get("user")).build());

			final List<CustomerContact> customerContacts = new ArrayList<>();
			if (userResponse.getUser().getEmail() != null) {
				customerContacts.add(CustomerContact.builder().email(userResponse.getUser().getEmail()).build());
			}

			CreateAutonomousDatabaseBase.DbWorkload databaseType = session.getWorkloadType();

			CreateAutonomousDatabaseDetails createFreeRequest = CreateAutonomousDatabaseDetails.builder()
					.dbVersion(session.getVersion())
					.cpuCoreCount(1)
					.dataStorageSizeInTBs(1)
					.displayName(session.getDbName() + "_Database")
					.adminPassword(session.getUserPassword())
					.dbName(session.getDbName())
					.compartmentId(session.getConfigFile().get("compartment_id"))
					.dbWorkload(databaseType)
					.isAutoScalingEnabled(!session.isFreeDatabase() && (databaseType == CreateAutonomousDatabaseBase.DbWorkload.Oltp ||
							databaseType == CreateAutonomousDatabaseBase.DbWorkload.Ajd || databaseType == CreateAutonomousDatabaseBase.DbWorkload.Dw))
					.licenseModel(session.isFreeDatabase() || databaseType == CreateAutonomousDatabaseBase.DbWorkload.Ajd ? CreateAutonomousDatabaseBase.LicenseModel.LicenseIncluded :
							(session.isByol() ? CreateAutonomousDatabaseBase.LicenseModel.BringYourOwnLicense : CreateAutonomousDatabaseBase.LicenseModel.LicenseIncluded))
					.isPreviewVersionWithServiceTermsAccepted(Boolean.FALSE)
					.isFreeTier(session.isFreeDatabase() ? Boolean.TRUE : Boolean.FALSE)
					.customerContacts(customerContacts)
					// ACLs
					.arePrimaryWhitelistedIpsUsed(true)
					.whitelistedIps(Arrays.stream((session.getInvokerIPAddress() + "," + retrieveCurrentIPAddress()).split(",")).toList())
					// no wallets
					.isMtlsConnectionRequired(false)
					.autonomousMaintenanceScheduleType(CreateAutonomousDatabaseBase.AutonomousMaintenanceScheduleType.Regular)
					.build();

			String workRequestId = null;
			AutonomousDatabase autonomousDatabase = null;
			WorkRequestClient workRequestClient = new WorkRequestClient(session.getProvider());
			workRequestClient.setRegion(session.getProvider().getRegion());

			BmcException creationException = null;

			try {
				CreateAutonomousDatabaseResponse responseCreate = session.getDbClient().createAutonomousDatabase(CreateAutonomousDatabaseRequest.builder().createAutonomousDatabaseDetails(createFreeRequest).build());
				autonomousDatabase = responseCreate.getAutonomousDatabase();
				workRequestId = responseCreate.getOpcWorkRequestId();
			}
			catch (BmcException e) {
				//e.printStackTrace();
				if (e.getStatusCode() == 400 && e.getServiceCode().equals("LimitExceeded")) {
					if (e.getMessage().startsWith("Tenancy has reached maximum limit for Free Tier Autonomous Database")) {
						throw new DLException(DLException.FREE_TIERS_DATABASE_RESOURCE_EXHAUSTED, e);
					}
					else {
						throw new DLException(DLException.DATABASE_RESOURCE_LIMIT_REACHED, e);
					}
				}
				else if (e.getStatusCode() == 400 && e.getServiceCode().equals("InvalidParameter") &&
						e.getMessage().contains(session.getDbName()) && e.getMessage().contains("already exists")) {
					throw new DLException(DLException.DATABASE_ALREADY_EXISTS);
				}

				creationException = e;
			}

			if (autonomousDatabase == null) {
				throw new DLException(DLException.DATABASE_CREATION_FATAL_ERROR, creationException);
			}

			GetWorkRequestRequest getWorkRequestRequest = GetWorkRequestRequest.builder().workRequestId(workRequestId).build();
			boolean exit = false;
			long startTime = System.currentTimeMillis();
			float pendingProgressMove = 0f;
			boolean probe = true;
			GetWorkRequestResponse getWorkRequestResponse = null;
			do {
				if (probe) {
					getWorkRequestResponse = workRequestClient.getWorkRequest(getWorkRequestRequest);
				}
				switch (getWorkRequestResponse.getWorkRequest().getStatus()) {
					case Succeeded:
						exit = true;
						break;
					case Failed:
						final ListWorkRequestErrorsResponse response = workRequestClient.listWorkRequestErrors(ListWorkRequestErrorsRequest.builder().workRequestId(workRequestId).opcRequestId(getWorkRequestResponse.getOpcRequestId()).build());
						final StringBuilder errors = new StringBuilder();
						int i = 0;
						for (WorkRequestError e : response.getItems()) {
							if (i > 0) {
								errors.append("\n");
							}
							errors.append(e.getMessage());
							i++;
						}

						logger.error(errors.toString());
						throw new DLException(DLException.DATABASE_CREATION_FATAL_ERROR);
					case Accepted:
						logger.debug("Database creation accepted");
						break;
					case InProgress:
						logger.debug("Database creation in progress: " + getWorkRequestResponse.getWorkRequest().getPercentComplete());
						break;
				}

				Utils.sleep(1000L);

				probe = !probe;
			}
			while (!exit);

			DatabaseWaiters waiter = session.getDbClient().getWaiters();
			try {
				GetAutonomousDatabaseResponse responseGet = waiter.forAutonomousDatabase(GetAutonomousDatabaseRequest.builder().autonomousDatabaseId(autonomousDatabase.getId()).build(),
						new AutonomousDatabase.LifecycleState[]{AutonomousDatabase.LifecycleState.Available}).execute();
				autonomousDatabase = responseGet.getAutonomousDatabase();
			}
			catch (Exception e) {
				throw new DLException(DLException.WAIT_FOR_CREATION_FAILURE, e);
			}

			CreateDatabaseUser.createApplicationUser(session, autonomousDatabase.getConnectionUrls().getSqlDevWebUrl());

			infoPanel = generateInfoPanel(autonomousDatabase);

			generateDatabaseConfiguration(autonomousDatabase.getConnectionStrings(), autonomousDatabase.getConnectionUrls().getSqlDevWebUrl());
		}

		System.out.printf("DATABASE IS READY TO USE! [%s]%n", Utils.getDurationSince(processStartTime));

		if(infoPanel != null) {
			System.out.println(infoPanel);
		}
	}

	private static String generateInfoPanel(AutonomousDatabaseSummary adb) {
		final StringBuilder sb = new StringBuilder("\nDatabase: ").append(adb.getDbName()).append("\n")
				.append("| \\_ version: ").append(adb.getDbVersion()).append("\n").append("| \\_ type: Autonomous ").append(getWorkloadType(adb.getDbWorkload()))
				.append(adb.getIsFreeTier() ? " (FREE TIERS)" : "").append("\n")
				.append("| \\_ consumer groups: LOW, MEDIUM, HIGH").append(adb.getDbWorkload() != AutonomousDatabaseSummary.DbWorkload.Dw ? ", TP, TPURGENT" :"").append("\n")
				.append("| \\_ auto-scaling CPU: ").append(adb.getIsAutoScalingEnabled()?"on":"off").append(", Storage: ").append(adb.getIsAutoScalingForStorageEnabled()?"on":"off").append("\n|\n")
				.append("| \\_ Service console\n")
				.append("|  \\_ ").append(adb.getServiceConsoleUrl()).append("\n")
				.append("| \\_ SQL Dev web (ORDS v").append(adb.getApexDetails().getOrdsVersion()).append(")\n")
				.append("|  \\_ ").append(adb.getConnectionUrls().getSqlDevWebUrl()).append("\n")
				.append("| \\_ Application Express (APEX v").append(adb.getApexDetails().getApexVersion()).append(")\n")
				.append("|  \\_ ").append(adb.getConnectionUrls().getApexUrl()).append("\n")
				.append("| \\_ Machine Learning users management\n")
				.append("|  \\_ ").append(adb.getConnectionUrls().getMachineLearningUserManagementUrl()).append("\n")
				.append("| \\_ Graph Studio\n")
				.append("   \\_ ").append(adb.getConnectionUrls().getGraphStudioUrl())
				;

		return sb.toString();
	}

	private static String getWorkloadType(AutonomousDatabaseSummary.DbWorkload dbWorkload) {
		return switch(dbWorkload) {
			case Ajd -> "JSON";
			case Apex -> "APEX";
			case Oltp -> "Transaction Processing";
			case Dw -> "Data Warehouse";
			default -> throw new IllegalStateException("Unexpected value: " + dbWorkload);
		};
	}

	private static String generateInfoPanel(AutonomousDatabase adb) {
		final StringBuilder sb = new StringBuilder("\nDatabase: ").append(adb.getDbName()).append("\n")
				.append("| \\_ version: ").append(adb.getDbVersion()).append("\n").append("| \\_ type: Autonomous ").append(getWorkloadType(adb.getDbWorkload()))
				.append(adb.getIsFreeTier() ? " (FREE TIERS)" : "").append("\n")
				.append("| \\_ consumer groups: LOW, MEDIUM, HIGH").append(adb.getDbWorkload() != AutonomousDatabase.DbWorkload.Dw ? ", TP, TPURGENT" :"").append("\n")
				.append("| \\_ auto-scaling CPU: ").append(adb.getIsAutoScalingEnabled()?"on":"off").append(", Storage: ").append(adb.getIsAutoScalingForStorageEnabled()?"on":"off").append("\n|\n")
				.append("| \\_ Service console\n")
				.append("|  \\_ ").append(adb.getServiceConsoleUrl()).append("\n")
				.append("| \\_ SQL Dev web (ORDS v").append(adb.getApexDetails().getOrdsVersion()).append(")\n")
				.append("|  \\_ ").append(adb.getConnectionUrls().getSqlDevWebUrl()).append("\n")
				.append("| \\_ Application Express (APEX v").append(adb.getApexDetails().getApexVersion()).append(")\n")
				.append("|  \\_ ").append(adb.getConnectionUrls().getApexUrl()).append("\n")
				.append("| \\_ Machine Learning users management\n")
				.append("|  \\_ ").append(adb.getConnectionUrls().getMachineLearningUserManagementUrl()).append("\n")
				.append("| \\_ Graph Studio\n")
				.append("   \\_ ").append(adb.getConnectionUrls().getGraphStudioUrl())
				;

		return sb.toString();
	}

	private static String getWorkloadType(AutonomousDatabase.DbWorkload dbWorkload) {
		return switch(dbWorkload) {
			case Ajd -> "JSON";
			case Apex -> "APEX";
			case Oltp -> "Transaction Processing";
			case Dw -> "Data Warehouse";
			default -> throw new IllegalStateException("Unexpected value: " + dbWorkload);
		};
	}

	private static void generateDatabaseConfiguration(AutonomousDatabaseConnectionStrings connectionStrings, String SqlDevWebUrl) {
		for (DatabaseConnectionStringProfile dcsp : connectionStrings.getProfiles()) {
			if (dcsp.getDisplayName().toLowerCase().endsWith("_low") && dcsp.getTlsAuthentication() == DatabaseConnectionStringProfile.TlsAuthentication.Server) {
				try (PrintWriter out = new PrintWriter("database.json")) {
					out.printf("{\"connectionString\": \"%s\", \"sqlDevWebUrl\": \"%s\"}", dcsp.getValue().replaceAll("\"", "\\\\\""), SqlDevWebUrl);
				}
				catch (IOException ioe) {
					throw new DLException(DLException.CANT_WRITE_DATABASE_CONFIGURATION);
				}
				break;
			}
		}
	}

	private static String retrieveCurrentIPAddress() {
		return PublicIPv4Retriever.get();

//		try {
//			final HttpRequest request = HttpRequest.newBuilder()
//					.uri(new URI("http://checkip.dyndns.org/"))
//					.headers("Accept", "*/*")
//					.headers("Keep-Alive", "timeout=5, max=100")
//					.GET()
//					.build();
//
//			HttpResponse<String> response;
//
//			int tries = 0;
//
//			do {
//				response = HttpClient
//						.newBuilder()
////					.connectTimeout(Duration.ofSeconds(20))
//						.version(HttpClient.Version.HTTP_1_1)
//						.proxy(ProxySelector.getDefault())
//						.build()
//						.send(request, HttpResponse.BodyHandlers.ofString());
//
//				if (response.statusCode() == 200) {
//					break;
//				}
//
//				//System.out.println("retrieveCurrentIPAddress: "+response.statusCode());
//				// sleep 1 second
//				Utils.sleep(1000L);
//
//				tries++;
//
//				logger.debug("Get current IP address, try #" + (tries + 1));
//
//			}
//			while (tries < MAX_TRIES);
//
//			if (tries >= MAX_TRIES && response.statusCode() != 200) {
//				throw new RuntimeException("Request for self IP address was not successful (" + response.statusCode() + ")");
//			}
//			final String responseBody = response.body();
//
//			return responseBody.substring(76, responseBody.indexOf("</body>"));
//		}
//		catch (Exception e) {
//			throw new DLException(DLException.UNKNOWN_CURRENT_IP_ADDRESS, e);
//		}
	}
}
