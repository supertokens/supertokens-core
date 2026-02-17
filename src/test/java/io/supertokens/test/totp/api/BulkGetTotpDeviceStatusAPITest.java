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

package io.supertokens.test.totp.api;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.supertokens.ProcessState;
import io.supertokens.featureflag.EE_FEATURES;
import io.supertokens.featureflag.FeatureFlagTestContent;
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

import static org.junit.Assert.*;

public class BulkGetTotpDeviceStatusAPITest {

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

    private JsonObject createTotpDevice(TestingProcessManager.TestingProcess process, String userId, String deviceName)
            throws Exception {
        JsonObject body = new JsonObject();
        body.addProperty("userId", userId);
        body.addProperty("deviceName", deviceName);
        body.addProperty("skew", 0);
        body.addProperty("period", 30);

        return HttpRequestForTesting.sendJsonPOSTRequest(
                process.getProcess(),
                "",
                "http://localhost:3567/recipe/totp/device",
                body,
                1000,
                1000,
                null,
                Utils.getCdiVersionStringLatestForTests(),
                "totp");
    }

    private JsonObject verifyTotpDevice(TestingProcessManager.TestingProcess process, String userId, String deviceName,
            String totp) throws Exception {
        JsonObject body = new JsonObject();
        body.addProperty("userId", userId);
        body.addProperty("deviceName", deviceName);
        body.addProperty("totp", totp);

        return HttpRequestForTesting.sendJsonPOSTRequest(
                process.getProcess(),
                "",
                "http://localhost:3567/recipe/totp/device/verify",
                body,
                1000,
                1000,
                null,
                Utils.getCdiVersionStringLatestForTests(),
                "totp");
    }

    @Test
    public void testEmptyUserIdsArray() throws Exception {
        String[] args = { "../" };

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[] { EE_FEATURES.MFA });

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        JsonObject requestBody = new JsonObject();
        requestBody.add("userIds", new JsonArray());

        JsonObject response = HttpRequestForTesting.sendJsonPOSTRequest(
                process.getProcess(),
                "",
                "http://localhost:3567/recipe/totp/device/status/bulk",
                requestBody,
                1000,
                1000,
                null,
                Utils.getCdiVersionStringLatestForTests(),
                "totp");

        assertEquals("OK", response.get("status").getAsString());
        assertEquals(0, response.getAsJsonArray("users").size());

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testUsersWithNoTotpDevices() throws Exception {
        String[] args = { "../" };

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[] { EE_FEATURES.MFA });

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        JsonObject requestBody = new JsonObject();
        JsonArray userIds = new JsonArray();
        userIds.add("user-without-totp-1");
        userIds.add("user-without-totp-2");
        requestBody.add("userIds", userIds);

        JsonObject response = HttpRequestForTesting.sendJsonPOSTRequest(
                process.getProcess(),
                "",
                "http://localhost:3567/recipe/totp/device/status/bulk",
                requestBody,
                1000,
                1000,
                null,
                Utils.getCdiVersionStringLatestForTests(),
                "totp");

        assertEquals("OK", response.get("status").getAsString());
        JsonArray users = response.getAsJsonArray("users");
        assertEquals(2, users.size());

        // Both users should have null hasVerifiedDevice (no TOTP devices)
        for (int i = 0; i < users.size(); i++) {
            JsonObject user = users.get(i).getAsJsonObject();
            assertTrue(user.has("userId"));
            assertTrue(user.has("hasVerifiedDevice"));
            assertTrue(user.get("hasVerifiedDevice").isJsonNull());
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testUsersWithUnverifiedDevices() throws Exception {
        String[] args = { "../" };

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[] { EE_FEATURES.MFA });

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        // Create unverified TOTP devices for users
        JsonObject createRes1 = createTotpDevice(process, "user-with-unverified", "device1");
        assertEquals("OK", createRes1.get("status").getAsString());

        JsonObject requestBody = new JsonObject();
        JsonArray userIds = new JsonArray();
        userIds.add("user-with-unverified");
        requestBody.add("userIds", userIds);

        JsonObject response = HttpRequestForTesting.sendJsonPOSTRequest(
                process.getProcess(),
                "",
                "http://localhost:3567/recipe/totp/device/status/bulk",
                requestBody,
                1000,
                1000,
                null,
                Utils.getCdiVersionStringLatestForTests(),
                "totp");

        assertEquals("OK", response.get("status").getAsString());
        JsonArray users = response.getAsJsonArray("users");
        assertEquals(1, users.size());

        // User should have hasVerifiedDevice = false (has unverified device)
        JsonObject user = users.get(0).getAsJsonObject();
        assertEquals("user-with-unverified", user.get("userId").getAsString());
        assertFalse(user.get("hasVerifiedDevice").isJsonNull());
        assertFalse(user.get("hasVerifiedDevice").getAsBoolean());

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testUsersWithVerifiedDevices() throws Exception {
        String[] args = { "../" };

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[] { EE_FEATURES.MFA });

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        // Create and verify TOTP device using import (which creates verified device)
        JsonObject importBody = new JsonObject();
        importBody.addProperty("userId", "user-with-verified");
        importBody.addProperty("deviceName", "verified-device");
        importBody.addProperty("skew", 0);
        importBody.addProperty("period", 30);
        importBody.addProperty("secretKey", "NBSWY3DPO5XXE3DEBFWXA"); // Base32 encoded secret

        JsonObject importRes = HttpRequestForTesting.sendJsonPOSTRequest(
                process.getProcess(),
                "",
                "http://localhost:3567/recipe/totp/device/import",
                importBody,
                1000,
                1000,
                null,
                Utils.getCdiVersionStringLatestForTests(),
                "totp");
        assertEquals("OK", importRes.get("status").getAsString());
        // Note: Import API creates devices as verified, so no need to call markDeviceAsVerified

        JsonObject requestBody = new JsonObject();
        JsonArray userIds = new JsonArray();
        userIds.add("user-with-verified");
        requestBody.add("userIds", userIds);

        JsonObject response = HttpRequestForTesting.sendJsonPOSTRequest(
                process.getProcess(),
                "",
                "http://localhost:3567/recipe/totp/device/status/bulk",
                requestBody,
                1000,
                1000,
                null,
                Utils.getCdiVersionStringLatestForTests(),
                "totp");

        assertEquals("OK", response.get("status").getAsString());
        JsonArray users = response.getAsJsonArray("users");
        assertEquals(1, users.size());

        // User should have hasVerifiedDevice = true
        JsonObject user = users.get(0).getAsJsonObject();
        assertEquals("user-with-verified", user.get("userId").getAsString());
        assertFalse(user.get("hasVerifiedDevice").isJsonNull());
        assertTrue(user.get("hasVerifiedDevice").getAsBoolean());

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testMixedUsers() throws Exception {
        String[] args = { "../" };

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[] { EE_FEATURES.MFA });

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        // User 1: No TOTP devices (will return null)
        // User 2: Has unverified device (will return false)
        createTotpDevice(process, "user-unverified", "device1");

        // User 3: Has verified device (will return true)
        JsonObject importBody = new JsonObject();
        importBody.addProperty("userId", "user-verified");
        importBody.addProperty("deviceName", "verified-device");
        importBody.addProperty("skew", 0);
        importBody.addProperty("period", 30);
        importBody.addProperty("secretKey", "NBSWY3DPO5XXE3DEBFWXA");

        HttpRequestForTesting.sendJsonPOSTRequest(
                process.getProcess(),
                "",
                "http://localhost:3567/recipe/totp/device/import",
                importBody,
                1000,
                1000,
                null,
                Utils.getCdiVersionStringLatestForTests(),
                "totp");
        // Note: Import API creates devices as verified, so no need to call markDeviceAsVerified

        // Query all three users
        JsonObject requestBody = new JsonObject();
        JsonArray userIds = new JsonArray();
        userIds.add("user-no-totp");
        userIds.add("user-unverified");
        userIds.add("user-verified");
        requestBody.add("userIds", userIds);

        JsonObject response = HttpRequestForTesting.sendJsonPOSTRequest(
                process.getProcess(),
                "",
                "http://localhost:3567/recipe/totp/device/status/bulk",
                requestBody,
                1000,
                1000,
                null,
                Utils.getCdiVersionStringLatestForTests(),
                "totp");

        assertEquals("OK", response.get("status").getAsString());
        JsonArray users = response.getAsJsonArray("users");
        assertEquals(3, users.size());

        // User 1: null (no TOTP devices)
        JsonObject user1 = users.get(0).getAsJsonObject();
        assertEquals("user-no-totp", user1.get("userId").getAsString());
        assertTrue(user1.get("hasVerifiedDevice").isJsonNull());

        // User 2: false (has unverified device)
        JsonObject user2 = users.get(1).getAsJsonObject();
        assertEquals("user-unverified", user2.get("userId").getAsString());
        assertFalse(user2.get("hasVerifiedDevice").isJsonNull());
        assertFalse(user2.get("hasVerifiedDevice").getAsBoolean());

        // User 3: true (has verified device)
        JsonObject user3 = users.get(2).getAsJsonObject();
        assertEquals("user-verified", user3.get("userId").getAsString());
        assertFalse(user3.get("hasVerifiedDevice").isJsonNull());
        assertTrue(user3.get("hasVerifiedDevice").getAsBoolean());

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testExceedingMaxUserIds() throws Exception {
        String[] args = { "../" };

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[] { EE_FEATURES.MFA });

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
                    "http://localhost:3567/recipe/totp/device/status/bulk",
                    requestBody,
                    1000,
                    1000,
                    null,
                    Utils.getCdiVersionStringLatestForTests(),
                    "totp");
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
        String[] args = { "../" };

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[] { EE_FEATURES.MFA });

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        JsonObject requestBody = new JsonObject();
        // Missing userIds field

        try {
            HttpRequestForTesting.sendJsonPOSTRequest(
                    process.getProcess(),
                    "",
                    "http://localhost:3567/recipe/totp/device/status/bulk",
                    requestBody,
                    1000,
                    1000,
                    null,
                    Utils.getCdiVersionStringLatestForTests(),
                    "totp");
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
        String[] args = { "../" };

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[] { EE_FEATURES.MFA });

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
                    "http://localhost:3567/recipe/totp/device/status/bulk",
                    requestBody,
                    1000,
                    1000,
                    null,
                    Utils.getCdiVersionStringLatestForTests(),
                    "totp");
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
        String[] args = { "../" };

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[] { EE_FEATURES.MFA });

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        // Create devices for users (in alphabetical order)
        createTotpDevice(process, "user-a", "device1");
        createTotpDevice(process, "user-z", "device1");

        // Query in reverse order
        JsonObject requestBody = new JsonObject();
        JsonArray userIds = new JsonArray();
        userIds.add("user-z");
        userIds.add("user-a");
        requestBody.add("userIds", userIds);

        JsonObject response = HttpRequestForTesting.sendJsonPOSTRequest(
                process.getProcess(),
                "",
                "http://localhost:3567/recipe/totp/device/status/bulk",
                requestBody,
                1000,
                1000,
                null,
                Utils.getCdiVersionStringLatestForTests(),
                "totp");

        assertEquals("OK", response.get("status").getAsString());
        JsonArray users = response.getAsJsonArray("users");

        // Verify order matches request order, not alphabetical
        assertEquals("user-z", users.get(0).getAsJsonObject().get("userId").getAsString());
        assertEquals("user-a", users.get(1).getAsJsonObject().get("userId").getAsString());

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }
}
