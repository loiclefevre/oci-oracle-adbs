package com.oracle.dragonlite.work;

import com.oracle.dragonlite.Main;
import com.oracle.dragonlite.exception.DLException;
import com.oracle.dragonlite.rest.ADBRESTService;
import com.oracle.dragonlite.util.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DropDatabaseUser {
	private static final Logger logger = LoggerFactory.getLogger("Dragon Lite");

	public static void work(Main session, final long processStartTime) {
		dropApplicationUser(session, session.getSqlDevWebURL());

		System.out.printf("DROPPED APPLICATION USER! [%s]%n", Utils.getDurationSince(processStartTime));
	}

	public static void dropApplicationUser(Main session, String sqlDevWebURL) {
		final ADBRESTService adminORDS = new ADBRESTService(sqlDevWebURL,
				"ADMIN", session.getSystemPassword());

		final String dropUserScript = """
				DECLARE
					username varchar2(60) := '%s'; -- filled from calling code
				BEGIN
					ords_metadata.ords_admin.drop_rest_for_schema(p_schema => username);

					-- Drop the user for Autonomous database
					execute immediate 'drop user ' || username || ' cascade';
				END;
					 
				/
				""";

		try {
			logger.info(String.format("Dropping application user %s", session.getUsername()));
			adminORDS.execute(String.format(dropUserScript, session.getUsername()), 1);
		}
		catch (DLException dle) {
			logger.error("Can't drop application user", dle);
			throw dle;
		}
	}

}
