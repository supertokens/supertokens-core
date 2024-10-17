/*
 *    Copyright (c) 2024, VRAI Labs and/or its affiliates. All rights reserved.
 *
 *    This software is licensed under the Apache License, Version 2.0 (the
 *    "License") as published by the Apache Software Foundation.
 *
 *    You may not use this file except in compliance with the License. You may
 *    obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 *    WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 *    License for the specific language governing permissions and limitations
 *    under the License.
 */

package io.supertokens.test.bulkimport.apis;

import com.google.gson.JsonObject;
import io.supertokens.Main;
import io.supertokens.ProcessState;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.test.TestingProcessManager;
import io.supertokens.test.Utils;
import io.supertokens.test.httpRequest.HttpRequestForTesting;
import io.supertokens.test.httpRequest.HttpResponseException;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class BulkImportBackgroundJobManagerAPITest {

    public static final String BACKGROUNDJOB_MANAGER_ENDPOINT = "http://localhost:3567/bulk-import/backgroundjob";
    @Rule
    public TestRule watchman = Utils.getOnFailure();

    @AfterClass
    public static void afterTesting() {
        Utils.afterTesting();
    }

    @Before
    public void beforeEach() {
        Utils.reset();
    }

    @Test
    public void testByDefaultNotActive() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));
        Main main = process.getProcess();

        if (StorageLayer.getBaseStorage(main).getType() != STORAGE_TYPE.SQL || StorageLayer.isInMemDb(main)) {
            return;
        }

        //by default inactive
        {
            JsonObject currentStatus = loadBulkImportCronjobStatus(main);

            assertEquals("OK", currentStatus.get("status").getAsString());
            assertEquals("INACTIVE", currentStatus.get("jobStatus").getAsString());
        }

        //starting it makes it active
        {
            JsonObject startResponse = startBulkImportCronjob(main, 100);
            assertEquals("OK", startResponse.get("status").getAsString());
            assertEquals("ACTIVE", startResponse.get("jobStatus").getAsString());

            JsonObject currentStatus = loadBulkImportCronjobStatus(main);
            assertEquals("OK", currentStatus.get("status").getAsString());
            assertEquals("ACTIVE", currentStatus.get("jobStatus").getAsString());
        }


        //stopping it makes it inactive
        {
            JsonObject startResponse = stopBulkImportCronjob(main);
            assertEquals("OK", startResponse.get("status").getAsString());
            assertEquals("INACTIVE", startResponse.get("jobStatus").getAsString());

            JsonObject currentStatus = loadBulkImportCronjobStatus(main);
            assertEquals("OK", currentStatus.get("status").getAsString());
            assertEquals("INACTIVE", currentStatus.get("jobStatus").getAsString());
        }
    }

    private static JsonObject startBulkImportCronjob(Main main, int batchSize) throws HttpResponseException, IOException {
        return sendCommandForBulkImportCronjob(main, "START", batchSize);
    }

    private static JsonObject stopBulkImportCronjob(Main main) throws HttpResponseException, IOException {
        return sendCommandForBulkImportCronjob(main, "STOP", null);
    }

    private static JsonObject sendCommandForBulkImportCronjob(Main main, String command, Integer batchSize) throws HttpResponseException, IOException {
        JsonObject request = new JsonObject();
        request.addProperty("command", command);
        if (batchSize != null) {
            request.addProperty("batchSize", batchSize);
        }
        return HttpRequestForTesting.sendJsonPOSTRequest(main, "",
                BACKGROUNDJOB_MANAGER_ENDPOINT,
                request, 1000, 10000, null, Utils.getCdiVersionStringLatestForTests(), null);
    }

    private static JsonObject loadBulkImportCronjobStatus(Main main) throws HttpResponseException, IOException {
        return HttpRequestForTesting.sendGETRequest(main, "", BACKGROUNDJOB_MANAGER_ENDPOINT, null,
                1000, 1000, null, Utils.getCdiVersionStringLatestForTests(), null);
    }



}
