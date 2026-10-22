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
import io.supertokens.authRecipe.StoredUsersCount;
import io.supertokens.emailpassword.EmailPassword;
import io.supertokens.featureflag.EE_FEATURES;
import io.supertokens.featureflag.FeatureFlagTestContent;
import io.supertokens.multitenancy.Multitenancy;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.pluginInterface.Storage;
import io.supertokens.pluginInterface.authRecipe.UsersCountCacheStorage;
import io.supertokens.pluginInterface.authRecipe.UsersCountSnapshot;
import io.supertokens.pluginInterface.authRecipe.sqlStorage.AuthRecipeSQLStorage;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.multitenancy.*;
import io.supertokens.storageLayer.StorageLayer;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import java.lang.reflect.Proxy;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

public class StoredUsersCountTest {
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

    // The plugin stores the app count on the app's public tenant, which exists only in the user pool that holds that
    // tenant. Another user pool of the app (holding only some of its other tenants) can't store it - the write fails
    // its foreign key to the tenants table - so that pool is counted directly, without the stored count.
    @Test
    public void appCountIsStoredOnlyInThePublicTenantsUserPool() throws Exception {
        AppIdentifier app = new AppIdentifier(null, "a1");
        List<String> storedCountCalls = new ArrayList<>();
        Storage publicPool = fakeStorage("pool-public", 5, 7, storedCountCalls);
        Storage otherPool = fakeStorage("pool-other", 3, 4, storedCountCalls);

        assertEquals(5 + 4, StoredUsersCount.getAppUsersCount(app, new Storage[]{otherPool, publicPool},
                publicPool));
        assertEquals(List.of("pool-public"), storedCountCalls);
    }

    // The same against a real storage: an app with a tenant in a second user pool (a separate database on
    // Postgres) gets its app-wide count, the same as AuthRecipe.getUsersCountAcrossAllTenants.
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
                StoredUsersCount.getAppUsersCount(app, storages, publicStorage));
        assertEquals(3, StoredUsersCount.getAppUsersCount(app, storages, publicStorage));
        if (publicStorage instanceof UsersCountCacheStorage) {
            // the public tenant's pool stored its part of the count (the other pool's two users are counted directly)
            assertEquals("1", publicStorage.getKeyValue(a1Public, "APP_USERS_COUNT").value);
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    // A storage in user pool poolId whose stored app count is storedCount and whose direct app count is directCount.
    // Storing the app count fails unless it is the app's public tenant's pool, as the plugin's foreign key does.
    private static Storage fakeStorage(String poolId, long storedCount, long directCount,
                                       List<String> storedCountCalls) {
        return (Storage) Proxy.newProxyInstance(StoredUsersCountTest.class.getClassLoader(),
                new Class<?>[]{UsersCountCacheStorage.class, AuthRecipeSQLStorage.class}, (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "getType":
                            return STORAGE_TYPE.SQL;
                        case "getUserPoolId":
                            return poolId;
                        case "getOrComputeAppUsersCount":
                            storedCountCalls.add(poolId);
                            if (!poolId.equals("pool-public")) {
                                throw new StorageQueryException(new SQLException(
                                        "insert or update on table \"key_value\" violates foreign key constraint"));
                            }
                            return new UsersCountSnapshot(storedCount, System.currentTimeMillis(), true);
                        case "getUsersCount":
                            if (args[0] instanceof AppIdentifier) {
                                return directCount;
                            }
                            break;
                        case "hashCode":
                            return System.identityHashCode(proxy);
                        case "equals":
                            return proxy == args[0];
                        case "toString":
                            return "fakeStorage(" + poolId + ")";
                        default:
                            break;
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
    }
}
