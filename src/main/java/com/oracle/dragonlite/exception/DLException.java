package com.oracle.dragonlite.exception;

public class DLException extends RuntimeException {
	public static final int UNKNOWN_COMMAND_LINE_ARGUMENT = 1;
	public static final int CANT_LOAD_CONFIGURATION_FILE = 2;
	public static final int FREE_TIERS_DATABASE_RESOURCE_EXHAUSTED = 3;
	public static final int EXISTING_DB_DOESNT_MATCH_VERSION = 4;
	public static final int EXISTING_DB_DOESNT_MATCH_WORKLOAD_TYPE = 5;
	public static final int ORDS_ERROR = 6;
	public static final int UNPARSABLE_ORDS_RESPONSE = 7;
	public static final int CANT_CHECK_ADMIN_CONNECTION = 8;
	public static final int CANT_START_ADBS = 9;
	public static final int WAIT_FOR_START_FAILURE = 10;
	public static final int UNKNOWN_WORKLOAD_TYPE = 11;
	public static final int DATABASE_RESOURCE_LIMIT_REACHED = 12;
	public static final int DATABASE_ALREADY_EXISTS = 13;
	public static final int DATABASE_CREATION_FATAL_ERROR = 14;
	public static final int WAIT_FOR_CREATION_FAILURE = 15;
	public static final int CANT_STOP_ADBS = 16;
	public static final int WAIT_FOR_STOP_FAILURE = 17;
	public static final int UNKNOWN_CURRENT_IP_ADDRESS = 18;
	public static final int CANT_WRITE_DATABASE_CONFIGURATION = 19;


	private final int errorCode;

	public DLException(int errorCode, Throwable cause) {
		super(String.valueOf(errorCode), cause);
		this.errorCode = errorCode;
	}

	public DLException(int errorCode) {
		this(errorCode, null);
	}

	public int getErrorCode() {
		return errorCode;
	}
}
