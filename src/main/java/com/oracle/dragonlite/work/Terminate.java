package com.oracle.dragonlite.work;

import com.oracle.bmc.database.model.AutonomousDatabaseSummary;
import com.oracle.bmc.database.requests.ListAutonomousDatabasesRequest;
import com.oracle.bmc.database.responses.ListAutonomousDatabasesResponse;
import com.oracle.dragonlite.Main;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Terminate {
	private static final Logger logger = LoggerFactory.getLogger("Dragon Lite");

	public static void work(Main session) {
		final ListAutonomousDatabasesRequest listADB = ListAutonomousDatabasesRequest.builder().compartmentId(session.getConfigFile().get("compartment_id")).build();
		final ListAutonomousDatabasesResponse listADBResponse = session.getDbClient().listAutonomousDatabases(listADB);

		AutonomousDatabaseSummary autonomousDatabaseSummary = null;

		for (AutonomousDatabaseSummary adb : listADBResponse.getItems()) {
			if (adb.getLifecycleState() != AutonomousDatabaseSummary.LifecycleState.Terminated) {
				if (adb.getDbName().equals(session.getDbName())) {
					autonomousDatabaseSummary = adb;
					break;
				}
			}
		}

		if(autonomousDatabaseSummary != null) {

		}
	}
}
