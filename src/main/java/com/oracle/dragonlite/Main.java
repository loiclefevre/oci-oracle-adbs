package com.oracle.dragonlite;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oracle.bmc.database.DatabaseClient;
import com.oracle.bmc.database.model.AutonomousDatabaseSummary;
import com.oracle.bmc.database.model.CreateAutonomousDatabaseBase;
import com.oracle.bmc.identity.IdentityClient;
import com.oracle.bmc.limits.LimitsClient;
import com.oracle.bmc.limits.requests.GetResourceAvailabilityRequest;
import com.oracle.bmc.limits.responses.GetResourceAvailabilityResponse;
import com.oracle.dragonlite.configuration.ConfigurationFile;
import com.oracle.dragonlite.configuration.ConfigurationFileAuthenticationDetailsProvider;
import com.oracle.dragonlite.exception.DLException;
import com.oracle.dragonlite.util.ADBConfiguration;
import com.oracle.dragonlite.util.Utils;
import com.oracle.dragonlite.work.Action;
import com.oracle.dragonlite.work.CreateDatabaseUser;
import com.oracle.dragonlite.work.DropDatabaseUser;
import com.oracle.dragonlite.work.Start;
import com.oracle.dragonlite.work.Terminate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;

import static com.oracle.dragonlite.work.Action.*;

/**
 * Use cases:
 * REUSE == FALSE
 * -1- no database: create it and configure it (e.g. create application user)
 * -2- terminate database upon stopping container
 * <p>
 * REUSE == TRUE
 * -1- no database: create it and configure it (e.g. create application user)
 * -2- no termination upon stopping container
 * <p>
 * -1- a database exists, connect to app user (check it exists)
 * -2- no termination upon stopping container
 * <p>
 * REUSE == TRUE
 * MULTITENANT == TRUE
 */
public class Main {
	private static final Logger logger = LoggerFactory.getLogger("Dragon Lite");
	public static final int MAX_TRIES = 30;

	static {
		System.setProperty("java.net.useSystemProxies", "true");
	}

	static boolean stayAlive = true;

	public static void main(String[] args) {
		final long startTime = System.currentTimeMillis();
		int exitStatus = 0;

		final Main session = new Main(args);

		try {
			session.loadConfiguration();

			session.initializeOCIClients();

/*			Runtime.getRuntime().addShutdownHook(new Thread(() -> {
				try {
					// some cleaning up code...
					session.terminate();

					System.out.println("INFO  \uD83D\uDC33 Container - shutting down container.");
				} catch (Exception e) {
					logger.error("Error: "+e.getMessage());
				}
				finally {
					stayAlive = false;
				}
			}));
*/

			switch (session.action) {
				case TerminateDatabase:
					if (!session.reuse) {
						Terminate.work(session, startTime);
					}
					break;

				case CreateUser:
					CreateDatabaseUser.work(session, startTime);
					break;

				case DropUser:
					DropDatabaseUser.work(session, startTime);
					break;

				case StartDatabase:
					try {
						Start.work(session, startTime);
					}
					catch(DLException e) {
						if(e.getErrorCode() == DLException.DATABASE_ALREADY_EXISTS) {
							Utils.sleep(1000L);
							Start.work(session, startTime);
						} else {
							throw e;
						}
					}

					while (stayAlive) {
						Utils.sleep(1000L);
					}
					break;
			}
		}
		catch (DLException e) {
			exitStatus = e.getErrorCode();
			switch (session.action) {
				case TerminateDatabase:
					System.out.printf("DATABASE TERMINATION FAILED (%d)!%nCHECK LOG OUTPUT FOR MORE INFORMATION!%n", exitStatus);
					break;

				case CreateUser:
					System.out.printf("USER CREATION FAILED (%d)!%nCHECK LOG OUTPUT FOR MORE INFORMATION!%n", exitStatus);
					break;

				case DropUser:
					System.out.printf("USER DELETION FAILED (%d)!%nCHECK LOG OUTPUT FOR MORE INFORMATION!%n", exitStatus);
					break;

				case StartDatabase:
					System.out.printf("DATABASE STARTUP FAILED (%d)!%nCHECK LOG OUTPUT FOR MORE INFORMATION!%n", exitStatus);
					break;
			}
			logger.error("Error: " + e.getMessage());
		}

		System.exit(exitStatus);
	}

	private final File workingDirectory = new File(".");
	private ConfigurationFile.ConfigFile configurationFile;
	private ConfigurationFileAuthenticationDetailsProvider provider;

	private String dbName;
	private String profileName;
	private String userPassword;
	private String adminPassword;
	private String version;
	private String workloadType;
	private String username;
	private boolean freeDatabase = true;
	private boolean byol;
	private String invokerIPAddress;
	private boolean reuse;
	private Action action = StartDatabase;
	private String sqlDevWebURL;

	private DatabaseClient dbClient;
	private LimitsClient limitsClient;
	private IdentityClient identityClient;

	public Main(String[] args) {
		analyzeCommandLineParameters(args);
	}

	private void loadConfiguration() {
		try {
			configurationFile = ConfigurationFile.parse(workingDirectory, "config", profileName);
			provider = new ConfigurationFileAuthenticationDetailsProvider(configurationFile);
		}
		catch (IOException e) {
			throw new DLException(DLException.CANT_LOAD_CONFIGURATION_FILE, e);
		}

		try {
			final File existingDatabaseConfiguration = new File("database.json");
			if (existingDatabaseConfiguration.exists() && existingDatabaseConfiguration.isFile()) {
				ADBConfiguration adbConfiguration = new ObjectMapper().readValue(existingDatabaseConfiguration, ADBConfiguration.class);
				if (adbConfiguration.getSqlDevWebUrl() != null) {
					sqlDevWebURL = adbConfiguration.getSqlDevWebUrl();
				}
			}
		}
		catch (IOException e) {
			throw new DLException(DLException.INVALID_DATABASE_CONFIGURATION);
		}
	}

	private void displayUsage() {
		System.out.println("Usage: dragonlite -p <OCI configuration profile> -r [true|false*] -d <database name> -u <user name>" +
				" -up <password> -ap <ADMIN password> -v <19c|21c> -w <json|oltp|dw> -i <IPv4[,IPv4]*> [-b] [-nf] [-t] [-cu] [-du]\n\n" +
				"-p    Oracle Cloud Infrastructure configuration profile\n" +
				"-r    reuse database instance, do not create a new one\n" +
				"-d    database name\n" +
				"-v    database version\n" +
				"-w    database workload type\n" +
				"-u    user name\n" +
				"-up   user password\n" +
				"-ap   ADMIN user password\n" +
				"-i    comma separated list of IPv4 address to permit (no space)\n" +
				"-b    bring your own license mode\n" +
				"-nf   non Always Free Tiers deployment\n" +
				"-t    ask to terminate the database\n" +
				"-cu   create user only\n" +
				"-du   drop user only");
	}

	private void analyzeCommandLineParameters(String[] args) {
		for (int i = 0; i < args.length; i++) {
			String arg = args[i].toLowerCase();

			switch (arg) {
				case "-d":
					if (i + 1 < args.length) {
						dbName = args[++i];
					}
					break;

				case "-p":
					if (i + 1 < args.length) {
						profileName = args[++i];
					}
					break;

				case "-ap":
					if (i + 1 < args.length) {
						adminPassword = args[++i];
					}
					break;

				case "-up":
					if (i + 1 < args.length) {
						userPassword = args[++i];
					}
					break;

				case "-nf":
					freeDatabase = false;
					break;

				case "-v":
					if (i + 1 < args.length) {
						version = args[++i];
					}
					break;

				case "-w":
					if (i + 1 < args.length) {
						workloadType = args[++i];
					}
					break;

				case "-u":
					if (i + 1 < args.length) {
						username = args[++i];
					}
					break;

				case "-b":
					byol = true;
					break;

				case "-i":
					if (i + 1 < args.length) {
						invokerIPAddress = args[++i];
					}
					break;

				case "-r":
					if (i + 1 < args.length) {
						reuse = args[++i].equalsIgnoreCase("true");
					}
					break;

				case "-t":
					if (action == StartDatabase) {
						action = TerminateDatabase;
					}
					else {
						displayUsage();
						throw new DLException(DLException.MULTIPLE_ACTION_REQUESTED);
					}
					break;

				case "-cu":
					if (action == StartDatabase) {
						action = CreateUser;
					}
					else {
						displayUsage();
						throw new DLException(DLException.MULTIPLE_ACTION_REQUESTED);
					}
					break;

				case "-du":
					if (action == StartDatabase) {
						action = DropUser;
					}
					else {
						displayUsage();
						throw new DLException(DLException.MULTIPLE_ACTION_REQUESTED);
					}
					break;

				default:
					displayUsage();
					throw new DLException(DLException.UNKNOWN_COMMAND_LINE_ARGUMENT);
			}
		}
	}

	private void initializeOCIClients() {
		dbClient = new DatabaseClient(provider);
		dbClient.setRegion(provider.getRegion());
	}

	private Boolean freeTiersDatabaseResourceExhausted = null;

	public boolean isFreeTiersDatabaseResourceExhausted() {
		if (freeTiersDatabaseResourceExhausted == null) {
			// lazy initialization of limitsClient
			limitsClient = new LimitsClient(provider);
			limitsClient.setRegion(provider.getRegion());

			GetResourceAvailabilityRequest getResourceAvailabilityRequest =
					GetResourceAvailabilityRequest.builder()
							.compartmentId(getProvider().getTenantId())
							.serviceName("database")
							.limitName("adb-free-count")
							.build();
			GetResourceAvailabilityResponse resourceAvailabilityResponse = limitsClient.getResourceAvailability(getResourceAvailabilityRequest);

			return freeTiersDatabaseResourceExhausted = resourceAvailabilityResponse.getResourceAvailability().getAvailable() <= 0;
		}
		else {
			return freeTiersDatabaseResourceExhausted;
		}
	}

	public String getDbName() {
		return dbName;
	}

	public DatabaseClient getDbClient() {
		return dbClient;
	}

	public ConfigurationFileAuthenticationDetailsProvider getProvider() {
		return provider;
	}

	public ConfigurationFile.ConfigFile getConfigFile() {
		return configurationFile;
	}

	public String getAdminPassword() {
		return adminPassword;
	}

	public String getVersion() {
		return version;
	}

	public CreateAutonomousDatabaseBase.DbWorkload getWorkloadType() {
		switch (workloadType.toLowerCase()) {
			case "ajd":
			case "json":
				return CreateAutonomousDatabaseBase.DbWorkload.Ajd;
			case "oltp":
				return CreateAutonomousDatabaseBase.DbWorkload.Oltp;
			case "dw":
				return CreateAutonomousDatabaseBase.DbWorkload.Dw;
			case "apex":
				return CreateAutonomousDatabaseBase.DbWorkload.Apex;
			default:
				throw new DLException(DLException.UNKNOWN_WORKLOAD_TYPE);
		}
	}

	public AutonomousDatabaseSummary.DbWorkload getWorkloadTypeSummary() {
		switch (workloadType.toLowerCase()) {
			case "ajd":
			case "json":
				return AutonomousDatabaseSummary.DbWorkload.Ajd;
			case "oltp":
				return AutonomousDatabaseSummary.DbWorkload.Oltp;
			case "dw":
				return AutonomousDatabaseSummary.DbWorkload.Dw;
			case "apex":
				return AutonomousDatabaseSummary.DbWorkload.Apex;
			default:
				throw new DLException(DLException.UNKNOWN_WORKLOAD_TYPE);
		}
	}

	public IdentityClient getIdentityClient() {
		// initialize only in the case of database creation
		if (identityClient == null) {
			identityClient = new IdentityClient(provider);
			identityClient.setRegion(provider.getRegion());
		}

		return identityClient;
	}

	public String getUsername() {
		return username;
	}

	public String getUserPassword() {
		return userPassword;
	}

	public boolean isFreeDatabase() {
		return freeDatabase;
	}

	public boolean isByol() {
		return byol;
	}

	public String getInvokerIPAddress() {
		return invokerIPAddress;
	}

	public String getSqlDevWebURL() {
		return sqlDevWebURL;
	}

	@Override
	public String toString() {
		return "Main{" +
				"workingDirectory=" + workingDirectory +
				", dbName='" + dbName + '\'' +
				", profileName='" + profileName + '\'' +
				", region='" + provider.getRegion() + '\'' +
				", userPassword='" + userPassword + '\'' +
				", adminPassword='" + adminPassword + '\'' +
				", version='" + version + '\'' +
				", workloadType='" + workloadType + '\'' +
				", username='" + username + '\'' +
				", freeDatabase=" + freeDatabase +
				", byol=" + byol +
				", invokerIPAddress='" + invokerIPAddress + '\'' +
				'}';
	}
}
