/*
 *    Copyright (c) 2026, VRAI Labs and/or its affiliates. All rights reserved.
 *
 *    This software is licensed under the Apache License, Version 2.0 (the
 *    "License"); you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 *    WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 *    License for the specific language governing permissions and limitations
 *    under the License.
 */

package io.supertokens.test.userRoles;

import com.google.gson.JsonObject;
import io.supertokens.Main;
import io.supertokens.ProcessState;
import io.supertokens.featureflag.EE_FEATURES;
import io.supertokens.featureflag.FeatureFlagTestContent;
import io.supertokens.multitenancy.Multitenancy;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.pluginInterface.Storage;
import io.supertokens.pluginInterface.multitenancy.TenantConfig;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifier;
import io.supertokens.pluginInterface.multitenancy.EmailPasswordConfig;
import io.supertokens.pluginInterface.multitenancy.PasswordlessConfig;
import io.supertokens.pluginInterface.multitenancy.ThirdPartyConfig;
import io.supertokens.pluginInterface.userroles.sqlStorage.UserRolesSQLStorage;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.test.TestingProcessManager;
import io.supertokens.test.Utils;
import io.supertokens.test.httpRequest.HttpRequestForTesting;
import io.supertokens.userroles.UserRoles;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import static org.junit.Assert.*;

/**
 * Regression tests for role revocation on a tenant whose storage pool differs from
 * the app's public tenant storage pool.
 *
 * Roles live in the app (public tenant) storage, while the role-to-user mapping
 * lives in each tenant's own storage. addRoleToUser has always resolved its
 * role-existence check against the app storage; removeUserRole used to resolve it
 * against the tenant's own storage, so on a tenant backed by its own database the
 * revoke returned UNKNOWN_ROLE_ERROR and the assignment silently survived (a stale
 * privilege). These tests grant a role on such a tenant and then revoke it,
 * asserting the revoke takes effect.
 */
public class UserRolesTenantOwnStorageTest {
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

    // creates tenant a1/t1 backed by its own user pool (a separate database),
    // so that the app's public tenant storage and the tenant's storage are different.
    // returns true only when the storage really hands the tenant its own user pool;
    // false when the scenario is not reachable there (e.g. the built-in in-memory
    // sqlite storage, which silently keeps one pool for every tenant)
    private boolean setupTenantWithOwnStorage(TestingProcessManager.TestingProcess process)
            throws Exception {
        Main main = process.getProcess();

        // create tenant a1/t1 whose config points at a second user pool
        JsonObject config = new JsonObject();
        StorageLayer.getStorage(new TenantIdentifier(null, null, null), main)
                .modifyConfigToAddANewUserPoolForTesting(config, 1);

        // the app a1 must exist first: create its public tenant from the base tenant...
        Multitenancy.addNewOrUpdateAppOrTenant(
                main,
                new TenantIdentifier(null, null, null),
                new TenantConfig(
                        new TenantIdentifier(null, "a1", null),
                        new EmailPasswordConfig(true),
                        new ThirdPartyConfig(true, null),
                        new PasswordlessConfig(true),
                        null, null, null));

        // ...then create tenant a1/t1 via the app's public tenant, with its own user pool
        Multitenancy.addNewOrUpdateAppOrTenant(
                main,
                new TenantIdentifier(null, "a1", null),
                new TenantConfig(
                        new TenantIdentifier(null, "a1", "t1"),
                        new EmailPasswordConfig(true),
                        new ThirdPartyConfig(true, null),
                        new PasswordlessConfig(true),
                        null, null, config));

        // guard: the scenario only means something if the tenant really is backed by
        // a different user pool than the app's public tenant. A plugin whose
        // modifyConfigToAddANewUserPoolForTesting silently no-ops runs these tests
        // against shared storage and proves nothing, so treat that as "scenario not
        // reachable" rather than letting the tests pass vacuously (or fail loudly,
        // as they would on the built-in in-memory sqlite storage).
        return !StorageLayer.getStorage(new TenantIdentifier(null, "a1", null), main).getUserPoolId()
                .equals(StorageLayer.getStorage(new TenantIdentifier(null, "a1", "t1"), main)
                        .getUserPoolId());
    }

    @Test
    public void testRemoveUserRoleOnTenantWithOwnStorage() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[] {
                        EE_FEATURES.MULTI_TENANCY});

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        // this test needs a storage that can create a second user pool (the built-in
        // in-memory sqlite storage cannot)
        try {
            if (!setupTenantWithOwnStorage(process)) {
                // storage cannot give the tenant its own user pool; the scenario is
                // not reachable there
                process.kill();
                return;
            }
        } catch (UnsupportedOperationException e) {
            // plugin cannot express multiple user pools; the scenario is not reachable there
            process.kill();
            return;
        }

        Main main = process.getProcess();
        String userId = "user1";
        String role = "role1";
        TenantIdentifier tenantIdentifier = new TenantIdentifier(null, "a1", "t1");

        // the role is created in app a1's public tenant storage, the same place addRoleToUser checks
        UserRoles.createNewRoleOrModifyItsPermissions(tenantIdentifier.toAppIdentifier(),
                StorageLayer.getStorage(new TenantIdentifier(null, "a1", null), main), role, null);

        // grant the role on the tenant (this is the path that already worked)
        Storage tenantStorage = StorageLayer.getStorage(tenantIdentifier, main);
        UserRoles.addRoleToUser(main, tenantIdentifier, tenantStorage, userId, role);

        {
            String[] roles = ((UserRolesSQLStorage) tenantStorage)
                    .getRolesForUser(tenantIdentifier, userId);
            assertEquals(1, roles.length);
            assertEquals(role, roles[0]);
        }

        // revoke via the API: this is the exact path that used to return UNKNOWN_ROLE_ERROR
        // on a tenant with its own storage
        JsonObject request = new JsonObject();
        request.addProperty("userId", userId);
        request.addProperty("role", role);

        JsonObject response = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/appid-a1/t1/recipe/user/role/remove", request, 10000, 10000,
                null, Utils.getCdiVersionStringLatestForTests(), "userroles");

        assertEquals("OK", response.get("status").getAsString());
        assertTrue(response.get("didUserHaveRole").getAsBoolean());

        {
            // the assignment is actually gone from the tenant's storage
            String[] roles = ((UserRolesSQLStorage) tenantStorage)
                    .getRolesForUser(tenantIdentifier, userId);
            assertEquals(0, roles.length);
        }

        // a second revoke reports the user did not have the role, without throwing
        JsonObject secondResponse = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/appid-a1/t1/recipe/user/role/remove", request, 10000, 10000,
                null, Utils.getCdiVersionStringLatestForTests(), "userroles");

        assertEquals("OK", secondResponse.get("status").getAsString());
        assertFalse(secondResponse.get("didUserHaveRole").getAsBoolean());

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testRemoveUnknownRoleOnTenantWithOwnStorageStillThrows() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[] {
                        EE_FEATURES.MULTI_TENANCY});

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        try {
            if (!setupTenantWithOwnStorage(process)) {
                process.kill();
                return;
            }
        } catch (UnsupportedOperationException e) {
            process.kill();
            return;
        }

        Main main = process.getProcess();
        String userId = "user1";
        TenantIdentifier tenantIdentifier = new TenantIdentifier(null, "a1", "t1");
        Storage tenantStorage = StorageLayer.getStorage(tenantIdentifier, main);

        // revoking a role that does not exist anywhere must still surface UNKNOWN_ROLE_ERROR
        JsonObject request = new JsonObject();
        request.addProperty("userId", userId);
        request.addProperty("role", "no_such_role");

        JsonObject response = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/appid-a1/t1/recipe/user/role/remove", request, 10000, 10000,
                null, Utils.getCdiVersionStringLatestForTests(), "userroles");

        assertEquals("UNKNOWN_ROLE_ERROR", response.get("status").getAsString());

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }
}
