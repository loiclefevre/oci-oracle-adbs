package com.oracle.dragonlite.configuration;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Simple implementation to read OCI configuration files.
 * <p>
 * Note, config files <b>MUST</b> contain a "DEFAULT" profile, else validation
 * will fail. Additional profiles are optional.
 */
public final class ConfigurationFile {
	private static final String DEFAULT_PROFILE_NAME = "DEFAULT";

	public static ConfigFile parse(File workingDirectory, String configurationFilePath, String profile) throws IOException {
		final File configFile = new File(workingDirectory, configurationFilePath);
		final ConfigFile config = parse(configFile.getAbsolutePath(), new FileInputStream(configFile), profile, StandardCharsets.UTF_8);
		config.setWorkingDirectory(workingDirectory,profile);
		config.setConfigFilename(configFile.getCanonicalPath());
		return config;
	}

	private static ConfigFile parse(String configurationFilePath, InputStream configurationStream, String profile, Charset charset) throws IOException {
		final ConfigAccumulator accumulator = new ConfigAccumulator();
		try (final BufferedReader reader = new BufferedReader(new InputStreamReader(configurationStream, charset))) {
			String line;
			while ((line = reader.readLine()) != null) {
				accumulator.accept(line);
			}
		}
		if (!accumulator.foundDefaultProfile) {
		}
		if (profile != null && !accumulator.configurationsByProfile.containsKey(profile)) {
			throw new IllegalArgumentException("No profile named " + profile + " exists in the configuration file");
		}

		return new ConfigFile(accumulator, profile, configurationFilePath);
	}

	private ConfigurationFile() {
	}

	/**
	 * ConfigFile represents a simple lookup mechanism for a OCI config file.
	 */
	public static final class ConfigFile {
		private final ConfigAccumulator accumulator;
		private final String profile;
		private final String configurationFilePath;
		private File workingDirectory;
		private String configFilename;

		public ConfigFile(ConfigAccumulator accumulator, String profile, String configurationFilePath) {
			this.accumulator = accumulator;
			this.profile = profile;
			this.configurationFilePath = configurationFilePath;
		}

		/**
		 * Gets the value associated with a given key. The value returned will
		 * be the one for the selected profile (if available), else the value in
		 * the DEFAULT profile (if specified), else null.
		 *
		 * @param key The key to look up.
		 * @return The value, or null if it didn't exist.
		 */
		public String get(String key) {
			if (profile != null && (accumulator.configurationsByProfile.get(profile).containsKey(key))) {
				if("key_file".equals(key)) {
/*
					String keyFilename = accumulator.configurationsByProfile.get(profile).get(key);

					keyFilename = keyFilename.replace('\\', '/');
					if (keyFilename.startsWith("~/")) {
						keyFilename = System.getProperty("user.home").replace('\\', '/') + keyFilename.substring(1);
					} else if(!keyFilename.startsWith("/") && !(keyFilename.charAt(1) == ':' && keyFilename.charAt(2) == '/')) {
						if(workingDirectory != null) {
							try {
								keyFilename = new File(workingDirectory, keyFilename).getCanonicalPath();
							} catch (IOException ignored) {
							}
						}
					}

					accumulator.configurationsByProfile.get(profile).put("key_file",keyFilename);

					return keyFilename;
*/

					accumulator.configurationsByProfile.get(profile).put("key_file","/opt/oracle/key");

					return "/opt/oracle/key";
				}

				return accumulator.configurationsByProfile.get(profile).get(key);
			}

			return accumulator.foundDefaultProfile
					? accumulator.configurationsByProfile.get(DEFAULT_PROFILE_NAME).get(key)
					: null;
		}

		public Set<String> getAllKeys() {
			if (profile != null) {
				return accumulator.configurationsByProfile.get(profile).keySet();
			}
			return accumulator.foundDefaultProfile
					? accumulator.configurationsByProfile.get(DEFAULT_PROFILE_NAME).keySet()
					: Collections.emptySet();
		}

		public String getConfigurationFilePath() {
			return configurationFilePath;
		}

		public String getProfile() {
			return profile;
		}

		public void setWorkingDirectory(File workingDirectory, String profile) {
			this.workingDirectory = workingDirectory;

			final String key = "key_file";

			if( profile != null && accumulator.configurationsByProfile.get(profile).containsKey(key) ) {
				String keyFilename = accumulator.configurationsByProfile.get(profile).get(key);

				keyFilename = keyFilename.replace('\\', '/');
				if (keyFilename.startsWith("~/")) {
					keyFilename = System.getProperty("user.home").replace('\\', '/') + keyFilename.substring(1);
				} else if(!keyFilename.startsWith("/") && !(keyFilename.charAt(1) == ':' && keyFilename.charAt(2) == '/')) {
					if(workingDirectory != null) {
						try {
							keyFilename = new File(workingDirectory, keyFilename).getCanonicalPath();
						} catch (IOException ignored) {
						}
					}
				}

				accumulator.configurationsByProfile.get(profile).put("key_file",keyFilename);
			}
		}

		public void setConfigFilename(String configFilename) {
			this.configFilename = configFilename.replace('\\', '/');
		}

		public String getConfigFilename() {
			return configFilename;
		}
	}

	private static final class ConfigAccumulator {
		final Map<String, Map<String, String>> configurationsByProfile = new HashMap<>();

		private String currentProfile = null;
		private boolean foundDefaultProfile = false;

		private void accept(String line) {
			final String trimmedLine = line.trim();

			// no blank lines
			if (trimmedLine.isEmpty()) {
				return;
			}

			// skip comments
			if (trimmedLine.charAt(0) == '#') {
				return;
			}

			if (trimmedLine.charAt(0) == '[' && trimmedLine.charAt(trimmedLine.length() - 1) == ']') {
				currentProfile = trimmedLine.substring(1, trimmedLine.length() - 1).trim();
				if (currentProfile.isEmpty()) {
					throw new IllegalStateException("Cannot have empty profile name: " + line);
				}
				if (currentProfile.equals(DEFAULT_PROFILE_NAME)) {
					foundDefaultProfile = true;
				}
				if (!configurationsByProfile.containsKey(currentProfile)) {
					configurationsByProfile.put(currentProfile, new HashMap<>());
				}

				return;
			}

			final int splitIndex = trimmedLine.indexOf('=');
			if (splitIndex == -1) {
				throw new IllegalStateException("Found line with no key-value pair: " + line);
			}

			final String key = trimmedLine.substring(0, splitIndex).trim();
			final String value = trimmedLine.substring(splitIndex + 1).trim();
			if (key.isEmpty()) {
				throw new IllegalStateException("Found line with no key: " + line);
			}

			if (currentProfile == null) {
				throw new IllegalStateException("Config parse error, attempted to read configuration without specifying a profile: " + line);
			}

			configurationsByProfile.get(currentProfile).put(key, value);
		}
	}
}
