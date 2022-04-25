package com.oracle.dragonlite;

import com.oracle.bmc.database.DatabaseClient;
import com.oracle.bmc.identity.IdentityClient;
import com.oracle.bmc.limits.LimitsClient;
import com.oracle.dragonlite.configuration.ConfigurationFile;
import com.oracle.dragonlite.configuration.ConfigurationFileAuthenticationDetailsProvider;
import com.oracle.dragonlite.exception.DLException;
import com.oracle.dragonlite.work.Start;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;

public class Main {
	private static final Logger logger = LoggerFactory.getLogger("Dragon Lite");

	static {
		System.setProperty("java.net.useSystemProxies", "true");
	}

	public static void main(String[] args) {

		int status = 0;

		try {
			final Main session = new Main(args);

			session.loadConfiguration();

			session.initializeOCIClients();

			session.work();

/*			session.createDatabase();

			GetResourceAvailabilityRequest getResourceAvailabilityRequest =
					GetResourceAvailabilityRequest.builder()
							.compartmentId(provider.getTenantId())
							.serviceName("database")
							.limitName("adb-free-count")
							.build();
			GetResourceAvailabilityResponse resourceAvailabilityResponse = limitsClient.getResourceAvailability(getResourceAvailabilityRequest);

			if (resourceAvailabilityResponse.getResourceAvailability().getAvailable() <= 0) {
				logger.error("No more free database possible");
			} else {
				logger.error("Now deploying ATPS FREE");
			}
*/
		}
		catch (DLException e) {
			status = e.getErrorCode();
			logger.error("Error", e);
		}

		System.exit(status);
	}

	private void work() {
		logger.info(this.toString());

		switch (action) {
			case "start":
				logger.info("start");
				Start.work(this);
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
	private boolean freeDatabase;
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
			configurationFile = ConfigurationFile.parse(workingDirectory, "adbs.ini", "DEFAULT");
			provider = new ConfigurationFileAuthenticationDetailsProvider(configurationFile.getConfigurationFilePath(),
					configurationFile.getProfile(), configurationFile);
		}
		catch (IOException e) {
			throw new DLException(DLException.CANT_LOAD_CONFIGURATION_FILE, e);
		}
	}

	private void analyzeCommandLineParameters(String[] args) {
		try {
			Class.forName("org.glassfish.jersey.client.JerseyClientBuilder");
		}
		catch (ClassNotFoundException e) {
			e.printStackTrace();
		}

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

				case "-f":
					freeDatabase = true;
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
					throw new DLException( DLException.UNKNOWN_COMMAND_LINE_ARGUMENT );
			}
		}
	}

	private void initializeOCIClients() {
		dbClient = new DatabaseClient(provider);
		dbClient.setRegion(region);

		limitsClient = new LimitsClient(provider);
		limitsClient.setRegion(region);

		identityClient = new IdentityClient(provider);
		identityClient.setRegion(region);
	}

	private void displayUsage() {
	}

	public String getDbName() {
		return dbName;
	}

	public DatabaseClient getDbClient() {
		return dbClient;
	}

	public LimitsClient getLimitsClient() {
		return limitsClient;
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

	public String getWorkloadType() {
		return workloadType;
	}

	public IdentityClient getIdentityClient() {
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
