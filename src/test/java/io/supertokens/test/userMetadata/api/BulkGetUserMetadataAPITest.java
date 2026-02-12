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

package io.supertokens.test.userMetadata.api;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.supertokens.ProcessState;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.test.TestingProcessManager;
import io.supertokens.test.Utils;
import io.supertokens.test.httpRequest.HttpRequestForTesting;
import io.supertokens.test.httpRequest.HttpResponseException;
import io.supertokens.usermetadata.UserMetadata;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import static org.junit.Assert.*;

public class BulkGetUserMetadataAPITest {
    @Rule
    public TestRule watchman = Utils.getOnFailure();

    @Rule
    public TestRule retryFlaky = Utils.retryFlakyTest();

    @AfterClass
    public static void afterTesting() {
        Utils.afterTesting();
    }

    @Before
    public void beforeEach() {
        Utils.reset();
    }

    @Test
    public void testEmptyUserIdsArray() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        JsonObject requestBody = new JsonObject();
        requestBody.add("userIds", new JsonArray());

        JsonObject response = HttpRequestForTesting.sendJsonPOSTRequest(
                process.getProcess(),
                "",
                "http://localhost:3567/recipe/user/metadata/bulk",
                requestBody,
                1000,
                1000,
                null,
                Utils.getCdiVersionStringLatestForTests(),
                "usermetadata");

        assertEquals("OK", response.get("status").getAsString());
        assertEquals(0, response.getAsJsonArray("users").size());

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testNonExistingUsers() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        JsonObject requestBody = new JsonObject();
        JsonArray userIds = new JsonArray();
        userIds.add("nonexistent-user-1");
        userIds.add("nonexistent-user-2");
        requestBody.add("userIds", userIds);

        JsonObject response = HttpRequestForTesting.sendJsonPOSTRequest(
                process.getProcess(),
                "",
                "http://localhost:3567/recipe/user/metadata/bulk",
                requestBody,
                1000,
                1000,
                null,
                Utils.getCdiVersionStringLatestForTests(),
                "usermetadata");

        assertEquals("OK", response.get("status").getAsString());
        JsonArray users = response.getAsJsonArray("users");
        assertEquals(2, users.size());

        // Both users should have null metadata
        for (int i = 0; i < users.size(); i++) {
            JsonObject user = users.get(i).getAsJsonObject();
            assertTrue(user.has("userId"));
            assertTrue(user.has("metadata"));
            assertTrue(user.get("metadata").isJsonNull());
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testExistingUsersWithMetadata() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        // Create metadata for users
        JsonObject metadata1 = new JsonObject();
        metadata1.addProperty("mfa", "enabled");
        metadata1.addProperty("preference", "dark");
        UserMetadata.updateUserMetadata(process.getProcess(), "user1", metadata1);

        JsonObject metadata2 = new JsonObject();
        metadata2.addProperty("mfa", "disabled");
        UserMetadata.updateUserMetadata(process.getProcess(), "user2", metadata2);

        // Query bulk metadata
        JsonObject requestBody = new JsonObject();
        JsonArray userIds = new JsonArray();
        userIds.add("user1");
        userIds.add("user2");
        requestBody.add("userIds", userIds);

        JsonObject response = HttpRequestForTesting.sendJsonPOSTRequest(
                process.getProcess(),
                "",
                "http://localhost:3567/recipe/user/metadata/bulk",
                requestBody,
                1000,
                1000,
                null,
                Utils.getCdiVersionStringLatestForTests(),
                "usermetadata");

        assertEquals("OK", response.get("status").getAsString());
        JsonArray users = response.getAsJsonArray("users");
        assertEquals(2, users.size());

        // Verify user1 metadata
        JsonObject user1Response = users.get(0).getAsJsonObject();
        assertEquals("user1", user1Response.get("userId").getAsString());
        assertFalse(user1Response.get("metadata").isJsonNull());
        JsonObject user1Metadata = user1Response.getAsJsonObject("metadata");
        assertEquals("enabled", user1Metadata.get("mfa").getAsString());
        assertEquals("dark", user1Metadata.get("preference").getAsString());

        // Verify user2 metadata
        JsonObject user2Response = users.get(1).getAsJsonObject();
        assertEquals("user2", user2Response.get("userId").getAsString());
        assertFalse(user2Response.get("metadata").isJsonNull());
        JsonObject user2Metadata = user2Response.getAsJsonObject("metadata");
        assertEquals("disabled", user2Metadata.get("mfa").getAsString());

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testMixedExistingAndNonExistingUsers() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        // Create metadata for only one user
        JsonObject metadata = new JsonObject();
        metadata.addProperty("mfa", "enabled");
        UserMetadata.updateUserMetadata(process.getProcess(), "existing-user", metadata);

        // Query bulk metadata for both existing and non-existing users
        JsonObject requestBody = new JsonObject();
        JsonArray userIds = new JsonArray();
        userIds.add("existing-user");
        userIds.add("nonexistent-user");
        requestBody.add("userIds", userIds);

        JsonObject response = HttpRequestForTesting.sendJsonPOSTRequest(
                process.getProcess(),
                "",
                "http://localhost:3567/recipe/user/metadata/bulk",
                requestBody,
                1000,
                1000,
                null,
                Utils.getCdiVersionStringLatestForTests(),
                "usermetadata");

        assertEquals("OK", response.get("status").getAsString());
        JsonArray users = response.getAsJsonArray("users");
        assertEquals(2, users.size());

        // Verify existing user has metadata
        JsonObject existingUserResponse = users.get(0).getAsJsonObject();
        assertEquals("existing-user", existingUserResponse.get("userId").getAsString());
        assertFalse(existingUserResponse.get("metadata").isJsonNull());
        assertEquals("enabled", existingUserResponse.getAsJsonObject("metadata").get("mfa").getAsString());

        // Verify non-existing user has null metadata
        JsonObject nonExistingUserResponse = users.get(1).getAsJsonObject();
        assertEquals("nonexistent-user", nonExistingUserResponse.get("userId").getAsString());
        assertTrue(nonExistingUserResponse.get("metadata").isJsonNull());

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testExceedingMaxUserIds() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        // Create request with 501 user IDs (exceeds max of 500)
        JsonObject requestBody = new JsonObject();
        JsonArray userIds = new JsonArray();
        for (int i = 0; i < 501; i++) {
            userIds.add("user-" + i);
        }
        requestBody.add("userIds", userIds);

        try {
            HttpRequestForTesting.sendJsonPOSTRequest(
                    process.getProcess(),
                    "",
                    "http://localhost:3567/recipe/user/metadata/bulk",
                    requestBody,
                    1000,
                    1000,
                    null,
                    Utils.getCdiVersionStringLatestForTests(),
                    "usermetadata");
            fail("Expected HttpResponseException");
        } catch (HttpResponseException e) {
            assertEquals(400, e.statusCode);
            assertTrue(e.getMessage().contains("500"));
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testMissingUserIdsField() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        JsonObject requestBody = new JsonObject();
        // Missing userIds field

        try {
            HttpRequestForTesting.sendJsonPOSTRequest(
                    process.getProcess(),
                    "",
                    "http://localhost:3567/recipe/user/metadata/bulk",
                    requestBody,
                    1000,
                    1000,
                    null,
                    Utils.getCdiVersionStringLatestForTests(),
                    "usermetadata");
            fail("Expected HttpResponseException");
        } catch (HttpResponseException e) {
            assertEquals(400, e.statusCode);
            assertTrue(e.getMessage().contains("userIds"));
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testInvalidUserIdsFormat() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        // Test with non-string values in array
        JsonObject requestBody = new JsonObject();
        JsonArray userIds = new JsonArray();
        userIds.add("valid-user");
        userIds.add(123); // Invalid: number instead of string
        requestBody.add("userIds", userIds);

        try {
            HttpRequestForTesting.sendJsonPOSTRequest(
                    process.getProcess(),
                    "",
                    "http://localhost:3567/recipe/user/metadata/bulk",
                    requestBody,
                    1000,
                    1000,
                    null,
                    Utils.getCdiVersionStringLatestForTests(),
                    "usermetadata");
            fail("Expected HttpResponseException");
        } catch (HttpResponseException e) {
            assertEquals(400, e.statusCode);
            assertTrue(e.getMessage().contains("userIds") || e.getMessage().contains("string"));
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testResponseOrderMatchesRequestOrder() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        // Create metadata for users
        JsonObject metadata1 = new JsonObject();
        metadata1.addProperty("order", "first");
        UserMetadata.updateUserMetadata(process.getProcess(), "user-z", metadata1);

        JsonObject metadata2 = new JsonObject();
        metadata2.addProperty("order", "second");
        UserMetadata.updateUserMetadata(process.getProcess(), "user-a", metadata2);

        // Query in specific order
        JsonObject requestBody = new JsonObject();
        JsonArray userIds = new JsonArray();
        userIds.add("user-z");
        userIds.add("user-a");
        requestBody.add("userIds", userIds);

        JsonObject response = HttpRequestForTesting.sendJsonPOSTRequest(
                process.getProcess(),
                "",
                "http://localhost:3567/recipe/user/metadata/bulk",
                requestBody,
                1000,
                1000,
                null,
                Utils.getCdiVersionStringLatestForTests(),
                "usermetadata");

        assertEquals("OK", response.get("status").getAsString());
        JsonArray users = response.getAsJsonArray("users");

        // Verify order matches request order, not alphabetical
        assertEquals("user-z", users.get(0).getAsJsonObject().get("userId").getAsString());
        assertEquals("user-a", users.get(1).getAsJsonObject().get("userId").getAsString());

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }
}
