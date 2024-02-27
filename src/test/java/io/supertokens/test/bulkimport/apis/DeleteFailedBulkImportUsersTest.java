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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.util.List;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

import io.supertokens.ProcessState;
import io.supertokens.bulkimport.BulkImport;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.pluginInterface.bulkimport.BulkImportStorage;
import io.supertokens.pluginInterface.bulkimport.BulkImportUser;
import io.supertokens.pluginInterface.multitenancy.AppIdentifierWithStorage;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.test.TestingProcessManager;
import io.supertokens.test.Utils;
import io.supertokens.test.httpRequest.HttpRequestForTesting;

import static io.supertokens.test.bulkimport.BulkImportTestUtils.generateBulkImportUser;

public class DeleteFailedBulkImportUsersTest {
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
    public void shouldReturn400Error() throws Exception {
        String[] args = { "../" };

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        {
            try {
                JsonObject request = new JsonObject();
                HttpRequestForTesting.sendJsonDELETERequest(process.getProcess(), "",
                        "http://localhost:3567/bulk-import/users",
                        request, 1000, 1000, null, Utils.getCdiVersionStringLatestForTests(), null);
            } catch (io.supertokens.test.httpRequest.HttpResponseException e) {
                assertEquals(400, e.statusCode);
                assertEquals("Http error. Status Code: 400. Message: Field name 'ids' is invalid in JSON input", e.getMessage());
            }
        }
        {
            try {
                JsonObject request = new JsonParser().parse("{\"ids\":[]}").getAsJsonObject();
                HttpRequestForTesting.sendJsonDELETERequest(process.getProcess(), "",
                        "http://localhost:3567/bulk-import/users",
                        request, 1000, 1000, null, Utils.getCdiVersionStringLatestForTests(), null);
            } catch (io.supertokens.test.httpRequest.HttpResponseException e) {
                assertEquals(400, e.statusCode);
                assertEquals("Http error. Status Code: 400. Message: Field name 'ids' cannot be an empty array", e.getMessage());
            }
        }
        {
            try {
                JsonObject request = new JsonParser().parse("{\"ids\":[\"\"]}").getAsJsonObject();
                HttpRequestForTesting.sendJsonDELETERequest(process.getProcess(), "",
                        "http://localhost:3567/bulk-import/users",
                        request, 1000, 1000, null, Utils.getCdiVersionStringLatestForTests(), null);
            } catch (io.supertokens.test.httpRequest.HttpResponseException e) {
                assertEquals(400, e.statusCode);
                assertEquals("Http error. Status Code: 400. Message: Field name 'ids' cannot contain an empty string", e.getMessage());
            }
        }
        {
            try {
                // Create a string array of 500 uuids
                JsonObject request = new JsonObject();
                JsonArray ids = new JsonArray();
                for (int i = 0; i < 501; i++) {
                    ids.add(new JsonPrimitive(io.supertokens.utils.Utils.getUUID()));
                }
                request.add("ids", ids);

                HttpRequestForTesting.sendJsonDELETERequest(process.getProcess(), "",
                        "http://localhost:3567/bulk-import/users",
                        request, 1000, 1000, null, Utils.getCdiVersionStringLatestForTests(), null);
            } catch (io.supertokens.test.httpRequest.HttpResponseException e) {
                assertEquals(400, e.statusCode);
                assertEquals("Http error. Status Code: 400. Message: Field name 'ids' cannot contain more than 500 elements", e.getMessage());
            }
        }

        process.kill();
        Assert.assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void shouldReturn200Response() throws Exception {
        String[] args = { "../" };

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        BulkImportStorage storage = (BulkImportStorage) StorageLayer.getStorage(process.main);
        AppIdentifierWithStorage appIdentifierWithStorage = new AppIdentifierWithStorage(null, null, storage);

        // Insert users
        List<BulkImportUser> users = generateBulkImportUser(5);
        BulkImport.addUsers(appIdentifierWithStorage, users);

        JsonObject request = new JsonObject();
        JsonArray ids = new JsonArray();
        for (BulkImportUser user : users) {
            ids.add(new JsonPrimitive(user.id));
        }
        request.add("ids", ids);

        JsonObject response = HttpRequestForTesting.sendJsonDELETERequest(process.getProcess(), "",
        "http://localhost:3567/bulk-import/users",
        request, 1000, 10000, null, Utils.getCdiVersionStringLatestForTests(), null);
        assertEquals("OK", response.get("status").getAsString());

        process.kill();
        Assert.assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

}
