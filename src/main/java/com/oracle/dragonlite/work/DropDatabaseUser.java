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
		final ADBRESTService adminORDS = new ADBRESTService(sqlDevWebURL, "ADMIN", session.getSystemPassword());

		final String dropUserScript = """
				DECLARE
					l_username varchar2(128) := '%s';
				BEGIN
					for c in (select 'alter system kill session '''||sid||','||serial#||',@'||inst_id||'''' as kill_command from gv$session where username = upper(l_username))
					loop
						execute immediate c.kill_command;
					end loop;
				
				
					ords_metadata.ords_admin.drop_rest_for_schema(p_schema => l_username);
				
					-- Drop the user for Autonomous database
					for i in 1 .. 10
					loop
						begin
							execute immediate 'drop user ' || l_username || ' cascade';
						exception when others then
							begin
							    -- if username doesn't exist then exit
							    if SQLCODE = -1918 then
							        exit;
							    else
									if i = 10 then
										raise;
									end if;
				                    sys.dbms_session.sleep(0.5);
								end if;
							end;	
						end;
					end loop;
				END;
				
				/""";

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
