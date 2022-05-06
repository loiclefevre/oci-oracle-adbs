package com.oracle.dragonlite.util;

public final class ADBConfiguration {
	private String connectionString;
	private String sqlDevWebUrl;

	public ADBConfiguration() {
	}

	public String getConnectionString() {
		return connectionString;
	}

	public void setConnectionString(String connectionString) {
		this.connectionString = connectionString;
	}

	public String getSqlDevWebUrl() {
		return sqlDevWebUrl;
	}

	public void setSqlDevWebUrl(String sqlDevWebUrl) {
		this.sqlDevWebUrl = sqlDevWebUrl;
	}
}
