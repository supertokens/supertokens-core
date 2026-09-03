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

package io.supertokens.test.bulkimport;

import io.supertokens.Main;
import io.supertokens.ProcessState;
import io.supertokens.authRecipe.AuthRecipe;
import io.supertokens.bulkimport.BulkImport;
import io.supertokens.cronjobs.CronTaskTest;
import io.supertokens.cronjobs.Cronjobs;
import io.supertokens.cronjobs.bulkimport.ProcessBulkImportUsers;
import io.supertokens.emailpassword.EmailPassword;
import io.supertokens.featureflag.EE_FEATURES;
import io.supertokens.featureflag.FeatureFlagTestContent;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.pluginInterface.authRecipe.AuthRecipeUserInfo;
import io.supertokens.pluginInterface.bulkimport.BulkImportStorage.BULK_IMPORT_USER_STATUS;
import io.supertokens.pluginInterface.bulkimport.BulkImportUser;
import io.supertokens.pluginInterface.bulkimport.sqlStorage.BulkImportSQLStorage;
import io.supertokens.pluginInterface.multitenancy.AppIdentifier;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifier;
import io.supertokens.pluginInterface.sqlStorage.SQLStorage;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.test.TestingProcessManager;
import io.supertokens.test.TestingProcessManager.TestingProcess;
import io.supertokens.test.Utils;
import io.supertokens.userroles.UserRoles;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static io.supertokens.test.bulkimport.BulkImportTestUtils.generateBulkImportUser;
import static org.junit.Assert.*;

/**
 * Bulk import runs on its own bounded connection pool, never on the live pool that serves API traffic, and
 * keeps the claimed queue rows locked (via savepoints on failure) until they are imported or error-marked.
 */
public class BulkImportDedicatedPoolTest {
    @Rule
    public TestRule watchman = Utils.getOnFailure();

    @Rule
    public TestRule retryFlaky = Utils.retryFlakyTest(3);

    @AfterClass
    public static void afterTesting() {
        Utils.afterTesting();
    }

    @Before
    public void beforeEach() {
        Utils.reset();
    }

    /** Must match {@code BulkImportConnectionPool.APPLICATION_NAME} in the postgresql plugin. */
    private static final String BULK_IMPORT_APPLICATION_NAME = "supertokens-bulk-import";

    private static final AppIdentifier APP = new AppIdentifier(null, null);

    @Test
    public void importCompletesWhenParallelismExceedsTheLivePoolAndApiTrafficKeepsFlowing() throws Exception {
        int parallelism = 6;
        int livePoolSize = 2; // smaller than the parallelism: the old design would have starved here
        Utils.setValueInConfig("postgresql_connection_pool_size", String.valueOf(livePoolSize));
        Utils.setValueInConfig("bulk_migration_parallelism", String.valueOf(parallelism));

        TestingProcess process = startCronProcess();
        if (process == null) {
            return;
        }
        Main main = process.getProcess();
        UserRoles.createNewRoleOrModifyItsPermissions(main, "role1", null);
        UserRoles.createNewRoleOrModifyItsPermissions(main, "role2", null);
        BulkImportTestUtils.createTenants(process);

        BulkImportSQLStorage storage = (BulkImportSQLStorage) StorageLayer.getStorage(main);
        int usersCount = 60;
        BulkImport.addUsers(APP, storage, generateBulkImportUser(usersCount));

        // Live "API traffic" on the live pool for the whole duration of the import
        AtomicBoolean stop = new AtomicBoolean(false);
        AtomicInteger liveRequests = new AtomicInteger();
        List<Throwable> liveFailures = new CopyOnWriteArrayList<>();
        List<Thread> apiThreads = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            Thread t = new Thread(() -> {
                while (!stop.get()) {
                    try {
                        AuthRecipe.getUsers(main, 10, "ASC", null, null, null);
                        liveRequests.incrementAndGet();
                    } catch (Throwable e) {
                        liveFailures.add(e);
                    }
                }
            });
            t.start();
            apiThreads.add(t);
        }

        // Observe how many import backends the server sees while the import runs
        int maxBulkImportBackends = 0;
        long deadline = System.currentTimeMillis() + 120_000;
        while (System.currentTimeMillis() < deadline) {
            maxBulkImportBackends = Math.max(maxBulkImportBackends, countBulkImportBackends(storage));
            if (pendingUsers(storage) == 0) {
                break;
            }
            Thread.sleep(100);
        }
        stop.set(true);
        for (Thread t : apiThreads) {
            t.join();
        }

        assertEquals(0, pendingUsers(storage));
        assertEquals(0, storage.getBulkImportUsersCount(APP, BULK_IMPORT_USER_STATUS.FAILED));
        assertEquals(usersCount, AuthRecipe.getUsersCount(main, null));
        assertTrue("live API calls should have run during the import", liveRequests.get() > 0);
        assertTrue("live API calls must not fail while bulk import runs: " + liveFailures, liveFailures.isEmpty());
        assertTrue("expected to observe the dedicated import pool in use", maxBulkImportBackends > 0);
        // one pool per user pool of the app (createTenants makes two), each capped at the parallelism
        assertTrue("import backends " + maxBulkImportBackends + " must stay within 2 pools x parallelism",
                maxBulkImportBackends <= 2 * parallelism);
        waitForBulkImportBackends(storage, 0);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void failedUserIsMarkedWhileTheRestOfTheChunkStillGetsImported() throws Exception {
        Utils.setValueInConfig("bulk_migration_parallelism", "1");
        TestingProcess process = startCronProcess();
        if (process == null) {
            return;
        }
        Main main = process.getProcess();
        UserRoles.createNewRoleOrModifyItsPermissions(main, "role1", null);
        UserRoles.createNewRoleOrModifyItsPermissions(main, "role2", null);
        BulkImportTestUtils.createTenants(process);

        BulkImportSQLStorage storage = (BulkImportSQLStorage) StorageLayer.getStorage(main);

        // user3 already exists: its import fails at insert time, i.e. with a real database error in the
        // middle of the chunk's transaction. Without the savepoint, the worker could neither keep the claim
        // nor write the error status on that connection ("current transaction is aborted").
        AuthRecipeUserInfo existing = EmailPassword.signUp(main, "user3@example.com", "password");
        List<BulkImportUser> users = generateBulkImportUser(6);
        BulkImport.addUsers(APP, storage, users);

        long deadline = System.currentTimeMillis() + 60_000;
        while (System.currentTimeMillis() < deadline && pendingUsers(storage) > 0) {
            Thread.sleep(500);
        }

        assertEquals(0, pendingUsers(storage));
        List<BulkImportUser> failed = storage.getBulkImportUsers(APP, 10, BULK_IMPORT_USER_STATUS.FAILED, null, null);
        assertEquals(1, failed.size());
        BulkImportUser failedUser = users.stream().filter(u -> u.id.equals(failed.get(0).id)).findFirst().orElseThrow();
        assertEquals("user3@example.com", failedUser.loginMethods.get(0).email);
        assertNotNull(failed.get(0).errorMessage);
        // 5 imported + the pre-existing one
        assertEquals(6, AuthRecipe.getUsersCount(main, null));
        assertNotNull(AuthRecipe.getUserById(main, existing.getSupertokensUserId()));
        waitForBulkImportBackends(storage, 0);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void synchronousImportUserReleasesItsPool() throws Exception {
        String[] args = {"../"};
        TestingProcess process = TestingProcessManager.startIsolatedProcess(args, false);
        Main main = process.getProcess();
        FeatureFlagTestContent.getInstance(main)
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{
                        EE_FEATURES.ACCOUNT_LINKING, EE_FEATURES.MULTI_TENANCY, EE_FEATURES.MFA});
        process.startProcess();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));
        if (StorageLayer.getStorage(main).getType() != STORAGE_TYPE.SQL || StorageLayer.isInMemDb(main)) {
            return;
        }
        UserRoles.createNewRoleOrModifyItsPermissions(main, "role1", null);
        UserRoles.createNewRoleOrModifyItsPermissions(main, "role2", null);
        BulkImportTestUtils.createTenants(process);
        BulkImportSQLStorage storage = (BulkImportSQLStorage) StorageLayer.getStorage(main);

        BulkImportUser user = generateBulkImportUser(1).get(0);
        AuthRecipeUserInfo imported = BulkImport.importUser(main, APP, user);
        assertNotNull(imported);
        assertEquals(1, AuthRecipe.getUsersCount(main, null));
        waitForBulkImportBackends(storage, 0);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    private TestingProcess startCronProcess() throws Exception {
        String[] args = {"../"};
        TestingProcess process = TestingProcessManager.startIsolatedProcess(args, false);
        Main main = process.getProcess();
        FeatureFlagTestContent.getInstance(main)
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{
                        EE_FEATURES.ACCOUNT_LINKING, EE_FEATURES.MULTI_TENANCY, EE_FEATURES.MFA});
        CronTaskTest.getInstance(main).setInitialWaitTimeInSeconds(ProcessBulkImportUsers.RESOURCE_KEY, 5);
        CronTaskTest.getInstance(main).setIntervalInSeconds(ProcessBulkImportUsers.RESOURCE_KEY, 5);
        process.startProcess();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));
        if (StorageLayer.getStorage(main).getType() != STORAGE_TYPE.SQL || StorageLayer.isInMemDb(main)) {
            return null;
        }
        Cronjobs.addCronjob(main, (ProcessBulkImportUsers) main.getResourceDistributor()
                .getResource(new TenantIdentifier(null, null, null), ProcessBulkImportUsers.RESOURCE_KEY));
        return process;
    }

    private static long pendingUsers(BulkImportSQLStorage storage) throws Exception {
        return storage.getBulkImportUsersCount(APP, BULK_IMPORT_USER_STATUS.NEW)
                + storage.getBulkImportUsersCount(APP, BULK_IMPORT_USER_STATUS.PROCESSING);
    }

    private static int countBulkImportBackends(SQLStorage storage) throws Exception {
        return storage.startTransaction(con -> {
            Connection sqlCon = (Connection) con.getConnection();
            try (PreparedStatement pst = sqlCon.prepareStatement(
                    "SELECT COUNT(*) FROM pg_stat_activity WHERE application_name = ?")) {
                pst.setString(1, BULK_IMPORT_APPLICATION_NAME);
                try (ResultSet rs = pst.executeQuery()) {
                    rs.next();
                    return rs.getInt(1);
                }
            }
        });
    }

    private static void waitForBulkImportBackends(SQLStorage storage, int expected) throws Exception {
        long deadline = System.currentTimeMillis() + 15_000;
        int seen = -1;
        while (System.currentTimeMillis() < deadline) {
            seen = countBulkImportBackends(storage);
            if (seen == expected) {
                return;
            }
            Thread.sleep(200);
        }
        assertEquals("bulk import backends in pg_stat_activity", expected, seen);
    }
}
