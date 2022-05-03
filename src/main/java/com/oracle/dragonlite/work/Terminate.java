package com.oracle.dragonlite.work;

import com.oracle.bmc.database.DatabaseWaiters;
import com.oracle.bmc.database.model.AutonomousDatabase;
import com.oracle.bmc.database.model.AutonomousDatabaseSummary;
import com.oracle.bmc.database.requests.DeleteAutonomousDatabaseRequest;
import com.oracle.bmc.database.requests.GetAutonomousDatabaseRequest;
import com.oracle.bmc.database.requests.ListAutonomousDatabasesRequest;
import com.oracle.bmc.database.responses.DeleteAutonomousDatabaseResponse;
import com.oracle.bmc.database.responses.GetAutonomousDatabaseResponse;
import com.oracle.bmc.database.responses.ListAutonomousDatabasesResponse;
import com.oracle.bmc.workrequests.WorkRequestClient;
import com.oracle.bmc.workrequests.model.WorkRequestError;
import com.oracle.bmc.workrequests.requests.GetWorkRequestRequest;
import com.oracle.bmc.workrequests.requests.ListWorkRequestErrorsRequest;
import com.oracle.bmc.workrequests.responses.GetWorkRequestResponse;
import com.oracle.bmc.workrequests.responses.ListWorkRequestErrorsResponse;
import com.oracle.dragonlite.Main;
import com.oracle.dragonlite.exception.DLException;
import com.oracle.dragonlite.util.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

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

		if (autonomousDatabaseSummary != null) {
			logger.warn("Deleting database...");

			WorkRequestClient workRequestClient = new WorkRequestClient(session.getProvider());
			workRequestClient.setRegion(session.getProvider().getRegion());
			DeleteAutonomousDatabaseResponse responseTerminate = session.getDbClient().deleteAutonomousDatabase(DeleteAutonomousDatabaseRequest.builder().autonomousDatabaseId(autonomousDatabaseSummary.getId()).build());
			String workRequestId = responseTerminate.getOpcWorkRequestId();

			GetWorkRequestRequest getWorkRequestRequest = GetWorkRequestRequest.builder().workRequestId(workRequestId).build();
			boolean exit = false;
			long startTime = System.currentTimeMillis();
			do {
				GetWorkRequestResponse getWorkRequestResponse = workRequestClient.getWorkRequest(getWorkRequestRequest);
				switch (getWorkRequestResponse.getWorkRequest().getStatus()) {
					case Succeeded:
						exit = true;
						break;
					case Failed:
						final ListWorkRequestErrorsResponse response = workRequestClient.listWorkRequestErrors(ListWorkRequestErrorsRequest.builder().workRequestId(workRequestId).opcRequestId(getWorkRequestResponse.getOpcRequestId()).build());
						final StringBuilder errors = new StringBuilder();
						int i = 0;
						for (WorkRequestError e : response.getItems()) {
							if (i > 0) {
								errors.append("\n");
							}
							errors.append(e.getMessage());
							i++;
						}
						logger.error(errors.toString());
						throw new DLException(DLException.CANT_TERMINATE_ADBS);
					case Accepted:
						logger.debug("Deletion accepted");
						break;
					case InProgress:
						logger.debug("Deletion in progress: " + getWorkRequestResponse.getWorkRequest().getPercentComplete());
						break;
				}

				Utils.sleep(1000L);

			}
			while (!exit);

			DatabaseWaiters waiter = session.getDbClient().getWaiters();
			try {
				final GetAutonomousDatabaseResponse responseGet = waiter.forAutonomousDatabase(GetAutonomousDatabaseRequest.builder().autonomousDatabaseId(autonomousDatabaseSummary.getId()).build(),
						new AutonomousDatabase.LifecycleState[]{AutonomousDatabase.LifecycleState.Terminated}).execute();
			}
			catch (Exception e) {
				throw new DLException(DLException.WAIT_FOR_TERMINATION_FAILURE, e);
			} finally {
				// delete database information (connectionString...)
				new File("database.json").delete();
			}
		}
	}
}
