/*
 *    Copyright (c) 2026, VRAI Labs and/or its affiliates. All rights reserved.
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

package io.supertokens.test.userroles.api;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
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

import java.util.HashMap;

import static org.junit.Assert.*;

/**
 * Regression tests for the "ghost tenant" management-API bypass.
 *
 * App-specific management APIs (here PUT /recipe/role) must only run in the public
 * tenant context and, when API_KEYS is configured, must require a valid key. Prepending
 * an unknown tenant segment (e.g. /ghosttenant/recipe/role) used to bypass BOTH boundaries
 * on CDI versions in [3.0, 5.0): the api-key / IP pre-checks failed open when tenant
 * resolution threw TenantOrAppNotFoundException, and the public-tenant guard was only
 * enforced for CDI >= 5.0. An unauthenticated caller could therefore create app-level
 * roles. These tests assert the secure behavior and fail on the vulnerable code.
 */
public class GhostTenantBypassTest {
    @Rule
    public TestRule watchman = Utils.getOnFailure();

    @Rule
    public TestRule retryFlaky = Utils.retryFlakyTest();

    // long enough / valid characters to pass api-key validation
    private static final String API_KEY = "someRandomApiKeyForTesting1234567890";

    // CDI versions that support a tenant path segment but predate the >= 5.0 public-tenant guard
    private static final String[] BYPASS_WINDOW_CDI = {"3.0", "3.1", "4.0"};

    @AfterClass
    public static void afterTesting() {
        Utils.afterTesting();
    }

    @Before
    public void beforeEach() {
        Utils.reset();
    }

    // Control: with API_KEYS set, an unauthenticated create on the normal public path is 401.
    @Test
    public void testUnauthenticatedPublicPathIsRejected() throws Exception {
        String[] args = {"../"};
        Utils.setValueInConfig("api_keys", API_KEY);
        TestingProcessManager.TestingProcess process = TestingProcessManager.startIsolatedProcess(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));
        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        assertRoleCreateRejected(process, "/recipe/role", "5.4", "ldvr-control-public", 401);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    // Control: with API_KEYS set, an unauthenticated ghost-tenant create on a modern CDI is rejected.
    @Test
    public void testGhostTenantOnModernCdiIsRejected() throws Exception {
        String[] args = {"../"};
        Utils.setValueInConfig("api_keys", API_KEY);
        TestingProcessManager.TestingProcess process = TestingProcessManager.startIsolatedProcess(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));
        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        assertRoleCreateRejected(process, "/ghosttenant/recipe/role", "5.4", "ldvr-control-ghost", 401, 403);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    // THE BUG (API_KEYS configured): an unauthenticated ghost-tenant create on CDI 3.0/3.1/4.0
    // must be rejected. On the vulnerable code it returns 200 createdNewRole:true.
    @Test
    public void testGhostTenantOnOldCdiWithApiKeysIsRejected() throws Exception {
        String[] args = {"../"};
        Utils.setValueInConfig("api_keys", API_KEY);
        TestingProcessManager.TestingProcess process = TestingProcessManager.startIsolatedProcess(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));
        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        for (String cdiVersion : BYPASS_WINDOW_CDI) {
            assertRoleCreateRejected(process, "/ghosttenant/recipe/role", cdiVersion,
                    "ldvr-ghost-key-" + cdiVersion.replace(".", "_"), 401, 403);
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    // THE BUG (no API_KEYS - network-isolated deployment): the public-tenant guard is the only
    // boundary. A ghost-tenant create on CDI 3.0/3.1/4.0 must be rejected and must not create a
    // role in the app's public storage.
    @Test
    public void testGhostTenantOnOldCdiWithoutApiKeysIsRejected() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.startIsolatedProcess(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));
        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        for (String cdiVersion : BYPASS_WINDOW_CDI) {
            String roleName = "ldvr-ghost-nokey-" + cdiVersion.replace(".", "_");
            assertRoleCreateRejected(process, "/ghosttenant/recipe/role", cdiVersion, roleName, 403);

            // and it must not have leaked into the app's public storage
            assertFalse("Role '" + roleName + "' must not exist after a rejected ghost-tenant create on CDI "
                            + cdiVersion, publicRolesContain(process, cdiVersion, roleName));
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    // Ghost APP (as opposed to ghost tenant): an unknown appId has no storage behind it, so an
    // app-specific create must be rejected and cannot mutate the default app. Unlike a ghost
    // tenant (which reuses the real default app's storage), there is no data to write to here.
    // The api-key check resolves against the (nonexistent) app's public tenant, which throws
    // TenantOrAppNotFoundException and now fails closed -> 401, before the handler's own 400.
    @Test
    public void testGhostAppOnOldCdiIsRejected() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.startIsolatedProcess(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));
        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        for (String cdiVersion : BYPASS_WINDOW_CDI) {
            String roleName = "ldvr-ghostapp-" + cdiVersion.replace(".", "_");
            assertRoleCreateRejected(process, "/appid-ghostapp/recipe/role", cdiVersion, roleName, 400, 401, 403);

            // it must not have leaked into the default app's public storage either
            assertFalse("Role '" + roleName + "' must not exist in the default app after a rejected ghost-app "
                            + "create on CDI " + cdiVersion, publicRolesContain(process, cdiVersion, roleName));
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    // Asserts the unauthenticated create is rejected (never 200/createdNewRole). acceptableStatuses lists the
    // rejection codes that are correct for the scenario: 401 (missing api key), 403 (non-public tenant), or
    // 400 (unknown app — no storage exists to write to).
    private void assertRoleCreateRejected(TestingProcessManager.TestingProcess process, String path,
                                          String cdiVersion, String roleName, int... acceptableStatuses)
            throws Exception {
        JsonObject body = new JsonObject();
        body.addProperty("role", roleName);
        JsonArray permissions = new JsonArray();
        permissions.add("read");
        body.add("permissions", permissions);

        try {
            JsonObject resp = HttpRequestForTesting.sendJsonPUTRequest(process.getProcess(), "",
                    "http://localhost:3567" + path, body, 10000, 10000, null, cdiVersion, "userroles");
            fail("Unauthenticated create at " + path + " on CDI " + cdiVersion
                    + " must be rejected, but it succeeded with: " + resp);
        } catch (HttpResponseException e) {
            boolean matched = false;
            for (int status : acceptableStatuses) {
                matched = matched || e.statusCode == status;
            }
            assertTrue("Expected one of " + java.util.Arrays.toString(acceptableStatuses) + " for " + path
                    + " on CDI " + cdiVersion + ", got " + e.statusCode + ": " + e.getMessage(), matched);
        }
    }

    private boolean publicRolesContain(TestingProcessManager.TestingProcess process, String cdiVersion,
                                       String roleName) throws Exception {
        JsonObject resp = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/roles", new HashMap<>(), 10000, 10000, null, cdiVersion, "userroles");
        assertEquals("OK", resp.get("status").getAsString());
        for (var element : resp.getAsJsonArray("roles")) {
            if (element.getAsString().equals(roleName)) {
                return true;
            }
        }
        return false;
    }
}
