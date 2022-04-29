package com.oracle.dragonlite.configuration;

import com.google.common.base.Preconditions;
import com.google.common.base.Supplier;
import com.oracle.bmc.auth.BasicAuthenticationDetailsProvider;
import com.oracle.bmc.auth.BasicConfigFileAuthenticationProvider;
import com.oracle.bmc.auth.SimpleAuthenticationDetailsProvider;
import com.oracle.bmc.auth.SimplePrivateKeySupplier;
import com.oracle.bmc.http.ClientConfigurator;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class ConfigurationFileAuthenticationDetailsProvider implements BasicAuthenticationDetailsProvider {
	private BasicConfigFileAuthenticationProvider delegate;

	public ConfigurationFileAuthenticationDetailsProvider(ConfigurationFile.ConfigFile configFile) throws IOException {
		this.delegate = new ConfigurationFileSimpleAuthenticationDetailsProvider(configFile);
	}

	public String getFingerprint() {
		return this.delegate.getFingerprint();
	}

	public String getTenantId() {
		return this.delegate.getTenantId();
	}

	public String getUserId() {
		return this.delegate.getUserId();
	}

	public List<ClientConfigurator> getClientConfigurators() {
		return this.delegate.getClientConfigurators();
	}

	/**
	 * @deprecated
	 */
	@Deprecated
	public String getPassPhrase() {
		return this.delegate.getPassPhrase();
	}

	public char[] getPassphraseCharacters() {
		return this.delegate.getPassphraseCharacters();
	}

	public InputStream getPrivateKey() {
		return this.delegate.getPrivateKey();
	}

	public String getKeyId() {
		return this.delegate.getKeyId();
	}

	public String getPemFilePath() {
		return this.delegate.getPemFilePath();
	}

	public String toString() {
		return "ConfigurationFileAuthenticationDetailsProvider(delegate=" + this.delegate + ")";
	}

	private static class ConfigurationFileSimpleAuthenticationDetailsProvider implements BasicConfigFileAuthenticationProvider {
		private final SimpleAuthenticationDetailsProvider delegate;
		private final String pemFilePath;
		private final List<ClientConfigurator> clientConfigurators;

		private ConfigurationFileSimpleAuthenticationDetailsProvider(ConfigurationFile.ConfigFile configFile) {
			String fingerprint = Preconditions.checkNotNull(configFile.get("fingerprint"), "missing fingerprint in config");
			String tenantId = Preconditions.checkNotNull(configFile.get("tenancy"), "missing tenancy in config");
			String userId = Preconditions.checkNotNull(configFile.get("user"), "missing user in config");
			String pemFilePath = Preconditions.checkNotNull(configFile.get("key_file"), "missing key_file in config");
			String passPhrase = configFile.get("pass_phrase");
			Supplier<InputStream> privateKeySupplier = new SimplePrivateKeySupplier(pemFilePath);
			SimpleAuthenticationDetailsProvider.SimpleAuthenticationDetailsProviderBuilder builder = SimpleAuthenticationDetailsProvider.builder().
					privateKeySupplier(privateKeySupplier).fingerprint(fingerprint).userId(userId).tenantId(tenantId);
			if (passPhrase != null) {
				builder = builder.passphraseCharacters(passPhrase.toCharArray());
			}

			this.delegate = builder.build();
			this.pemFilePath = pemFilePath;
			this.clientConfigurators = new ArrayList();
		}

		public String getFingerprint() {
			return this.delegate.getFingerprint();
		}

		public String getTenantId() {
			return this.delegate.getTenantId();
		}

		public String getUserId() {
			return this.delegate.getUserId();
		}

		/**
		 * @deprecated
		 */
		@Deprecated
		public String getPassPhrase() {
			return this.delegate.getPassPhrase();
		}

		/**
		 * @deprecated
		 */
		@Deprecated
		public char[] getPassphraseCharacters() {
			return this.delegate.getPassphraseCharacters();
		}

		public InputStream getPrivateKey() {
			return this.delegate.getPrivateKey();
		}

		public String getKeyId() {
			return this.delegate.getKeyId();
		}

		public String getPemFilePath() {
			return this.pemFilePath;
		}

		public List<ClientConfigurator> getClientConfigurators() {
			return this.clientConfigurators;
		}
	}

}
