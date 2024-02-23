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

import java.util.HashMap;
import java.util.Map;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import io.supertokens.ProcessState;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.test.TestingProcessManager;
import io.supertokens.test.Utils;
import io.supertokens.test.httpRequest.HttpRequestForTesting;

public class GetBulkImportUsersTest {
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

        try {
            Map<String, String> params = new HashMap<>();
            params.put("status", "INVALID_STATUS");
            HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                    "http://localhost:3567/bulk-import/users",
                    params, 1000, 1000, null, Utils.getCdiVersionStringLatestForTests(), null);

        } catch (io.supertokens.test.httpRequest.HttpResponseException e) {
            assertEquals(400, e.statusCode);
            assertEquals(
                    "Http error. Status Code: 400. Message: Invalid value for status. Pass one of NEW, PROCESSING, or FAILED!",
                    e.getMessage());
        }

        try {
            Map<String, String> params = new HashMap<>();
            params.put("limit", "0");
            HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                    "http://localhost:3567/bulk-import/users",
                    params, 1000, 1000, null, Utils.getCdiVersionStringLatestForTests(), null);

        } catch (io.supertokens.test.httpRequest.HttpResponseException e) {
            assertEquals(400, e.statusCode);
            assertEquals("Http error. Status Code: 400. Message: limit must a positive integer with min value 1",
                    e.getMessage());
        }

        try {
            Map<String, String> params = new HashMap<>();
            params.put("limit", "501");
            HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                    "http://localhost:3567/bulk-import/users",
                    params, 1000, 1000, null, Utils.getCdiVersionStringLatestForTests(), null);

        } catch (io.supertokens.test.httpRequest.HttpResponseException e) {
            assertEquals(400, e.statusCode);
            assertEquals("Http error. Status Code: 400. Message: Max limit allowed is 500", e.getMessage());
        }

        try {
            Map<String, String> params = new HashMap<>();
            params.put("paginationToken", "invalid_token");
            HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                    "http://localhost:3567/bulk-import/users",
                    params, 1000, 1000, null, Utils.getCdiVersionStringLatestForTests(), null);

        } catch (io.supertokens.test.httpRequest.HttpResponseException e) {
            assertEquals(400, e.statusCode);
            assertEquals("Http error. Status Code: 400. Message: invalid pagination token", e.getMessage());
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

        // Create a bulk import user to test the GET API
        String rawData = "{\"users\":[{\"loginMethods\":[{\"recipeId\":\"passwordless\",\"email\":\"johndoe@gmail.com\"}]}]}";
        {
            JsonObject request = new JsonParser().parse(rawData).getAsJsonObject();
            JsonObject res = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                    "http://localhost:3567/bulk-import/users",
                    request, 1000, 1000, null, Utils.getCdiVersionStringLatestForTests(), null);
            assert res.get("status").getAsString().equals("OK");
        }

        Map<String, String> params = new HashMap<>();
        JsonObject response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                "http://localhost:3567/bulk-import/users",
                params, 1000, 1000, null, Utils.getCdiVersionStringLatestForTests(), null);
        assertEquals("OK", response.get("status").getAsString());
        JsonArray bulkImportUsers = response.get("users").getAsJsonArray();
        assertEquals(1, bulkImportUsers.size());
        JsonObject bulkImportUserJson = bulkImportUsers.get(0).getAsJsonObject();
        bulkImportUserJson.get("status").getAsString().equals("NEW");
        bulkImportUserJson.get("rawData").getAsString().equals(rawData);
    
        process.kill();
        Assert.assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }
}
