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

package io.supertokens.test;

import com.google.gson.JsonObject;
import io.supertokens.ProcessState;
import io.supertokens.authRecipe.AuthRecipe;
import io.supertokens.authRecipe.ApproximateUserCount;
import io.supertokens.emailpassword.EmailPassword;
import io.supertokens.featureflag.EE_FEATURES;
import io.supertokens.featureflag.FeatureFlagTestContent;
import io.supertokens.multitenancy.Multitenancy;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.pluginInterface.Storage;
import io.supertokens.pluginInterface.authRecipe.UsersCountCacheStorage;
import io.supertokens.pluginInterface.multitenancy.*;
import io.supertokens.storageLayer.StorageLayer;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;


import static org.junit.Assert.*;

public class AppUsersCountAcrossUserPoolsTest {
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

    // An app with a tenant in a second user pool (a separate database on Postgres) gets its app-wide count, the same
    // as AuthRecipe.getUsersCountAcrossAllTenants. Each pool stores its part of the count on a tenant of the app that
    // exists in that database: the public tenant in the first, t1 in the second (which has no public tenant of a1).
    @Test
    public void appCountWithATenantInAnotherUserPool() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.startIsolatedProcess(args, false);
        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{EE_FEATURES.MULTI_TENANCY});
        process.startProcess();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getBaseStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            process.kill();
            assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
            return;
        }

        TenantIdentifier a1Public = new TenantIdentifier(null, "a1", null);
        TenantIdentifier a1t1 = new TenantIdentifier(null, "a1", "t1");
        JsonObject ownPool = new JsonObject();
        StorageLayer.getBaseStorage(process.getProcess()).modifyConfigToAddANewUserPoolForTesting(ownPool, 1);
        Multitenancy.addNewOrUpdateAppOrTenant(process.getProcess(), TenantIdentifier.BASE_TENANT,
                new TenantConfig(a1Public, new EmailPasswordConfig(true), new ThirdPartyConfig(true, null),
                        new PasswordlessConfig(true), null, null, new JsonObject()));
        Multitenancy.addNewOrUpdateAppOrTenant(process.getProcess(), a1Public,
                new TenantConfig(a1t1, new EmailPasswordConfig(true), new ThirdPartyConfig(true, null),
                        new PasswordlessConfig(true), null, null, ownPool));

        Storage publicStorage = StorageLayer.getStorage(a1Public, process.getProcess());
        Storage t1Storage = StorageLayer.getStorage(a1t1, process.getProcess());
        if (publicStorage.getUserPoolId().equals(t1Storage.getUserPoolId())) {
            // the storage keeps one user pool for every tenant (the in-memory db), so the scenario isn't reachable
            process.kill();
            assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
            return;
        }

        EmailPassword.signUp(a1Public, publicStorage, process.getProcess(), "public@example.com", "password123");
        EmailPassword.signUp(a1t1, t1Storage, process.getProcess(), "t1a@example.com", "password123");
        EmailPassword.signUp(a1t1, t1Storage, process.getProcess(), "t1b@example.com", "password123");

        AppIdentifier app = a1Public.toAppIdentifier();
        Storage[] storages = StorageLayer.getStoragesForApp(process.getProcess(), app);
        assertEquals(2, storages.length);
        assertEquals(AuthRecipe.getUsersCountAcrossAllTenants(app, storages, null),
                ApproximateUserCount.getInstance(process.getProcess(), app)
                        .serveApp(process.getProcess()).count);
        assertEquals(3, ApproximateUserCount.getInstance(process.getProcess(), app)
                .serveApp(process.getProcess()).count);
        if (publicStorage instanceof UsersCountCacheStorage) {
            assertEquals("1", publicStorage.getKeyValue(a1Public, "APP_USERS_COUNT").value);
            assertEquals("2", t1Storage.getKeyValue(a1t1, "APP_USERS_COUNT").value);
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }
}
