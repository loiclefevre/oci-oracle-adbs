package com.oracle.dragonlite;

import com.oracle.bmc.database.DatabaseClient;
import com.oracle.bmc.database.model.CreateAutonomousDatabaseBase;
import com.oracle.bmc.identity.IdentityClient;
import com.oracle.bmc.limits.LimitsClient;
import com.oracle.bmc.limits.requests.GetResourceAvailabilityRequest;
import com.oracle.bmc.limits.responses.GetResourceAvailabilityResponse;
import com.oracle.dragonlite.configuration.ConfigurationFile;
import com.oracle.dragonlite.configuration.ConfigurationFileAuthenticationDetailsProvider;
import com.oracle.dragonlite.exception.DLException;
import com.oracle.dragonlite.work.Start;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;

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

	static {
		System.setProperty("java.net.useSystemProxies", "true");
	}

	public static void main(String[] args) {
		int exitStatus = 0;

		try {
			final Main session = new Main(args);

			session.loadConfiguration();

			session.initializeOCIClients();

			session.work();
		}
		catch (DLException e) {
			exitStatus = e.getErrorCode();
			logger.error("Error", e);
		}

		System.exit(exitStatus);
	}

	private void work() {
		// logger.info(this.toString());

		switch (action) {
			case "start":
				logger.info("start");
				Start.work(this);
				break;

			case "stop":
				logger.info("stop");
				//Stop.work(this);
				break;
		}
	}


	private final File workingDirectory = new File(".");
	private ConfigurationFile.ConfigFile configurationFile;
	private ConfigurationFileAuthenticationDetailsProvider provider;

	private String action;

	private String dbName;
	private String profileName;
	private String region;
	private String userPassword;
	private String systemPassword;
	private String version;
	private String workloadType;
	private String username;
	private boolean freeDatabase = true;
	private boolean byol;
	private String invokerIPAddress;

	private DatabaseClient dbClient;
	private LimitsClient limitsClient;
	private IdentityClient identityClient;

	public Main(String[] args) {
		analyzeCommandLineParameters(args);
	}

	private void loadConfiguration() {
		try {
			configurationFile = ConfigurationFile.parse(workingDirectory, "config", "DEFAULT");
			provider = new ConfigurationFileAuthenticationDetailsProvider(configurationFile);
		}
		catch (IOException e) {
			throw new DLException(DLException.CANT_LOAD_CONFIGURATION_FILE, e);
		}
	}

	private void displayUsage() {
		System.out.println("Usage: dragonlite -a <start|stop> -p <OCI configuration profile> -r <OCI region> -d <database name> -u <user name>" +
				" -p <password> -sp <ADMIN password> -v <19c|21c> -w <json|oltp|dw|apex> -i <IPv4[,IPv4]*> [-b] [-nf]");
	}

	private void analyzeCommandLineParameters(String[] args) {
		for (int i = 0; i < args.length; i++) {
			String arg = args[i].toLowerCase();

			switch (arg) {
				case "-a":
					if (i + 1 < args.length) {
						action = args[++i];
					}
					break;

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

				case "-r":
					if (i + 1 < args.length) {
						region = args[++i];
					}
					break;

				case "-sp":
					if (i + 1 < args.length) {
						systemPassword = args[++i];
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

				default:
					displayUsage();
					throw new DLException(DLException.UNKNOWN_COMMAND_LINE_ARGUMENT);
			}
		}
	}

	private void initializeOCIClients() {
		dbClient = new DatabaseClient(provider);
		dbClient.setRegion(region);
	}

	private Boolean freeTiersDatabaseResourceExhausted = null;

	public boolean isFreeTiersDatabaseResourceExhausted() {
		if (freeTiersDatabaseResourceExhausted == null) {
			// lazy initialization of limitsClient
			limitsClient = new LimitsClient(provider);
			limitsClient.setRegion(region);

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

	public String getSystemPassword() {
		return systemPassword;
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

	public IdentityClient getIdentityClient() {
		// initialize only in the case of database creation
		if (identityClient == null) {
			identityClient = new IdentityClient(provider);
			identityClient.setRegion(region);
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

	@Override
	public String toString() {
		return "Main{" +
				"workingDirectory=" + workingDirectory +
				", action='" + action + '\'' +
				", dbName='" + dbName + '\'' +
				", profileName='" + profileName + '\'' +
				", region='" + region + '\'' +
				", userPassword='" + userPassword + '\'' +
				", systemPassword='" + systemPassword + '\'' +
				", version='" + version + '\'' +
				", workloadType='" + workloadType + '\'' +
				", username='" + username + '\'' +
				", freeDatabase=" + freeDatabase +
				", byol=" + byol +
				", invokerIPAddress='" + invokerIPAddress + '\'' +
				'}';
	}
}
