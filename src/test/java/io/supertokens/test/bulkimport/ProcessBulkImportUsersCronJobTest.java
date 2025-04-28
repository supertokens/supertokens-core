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
 *
 */

package io.supertokens.test.bulkimport;

import io.supertokens.Main;
import io.supertokens.ProcessState;
import io.supertokens.authRecipe.AuthRecipe;
import io.supertokens.authRecipe.UserPaginationContainer;
import io.supertokens.bulkimport.BulkImport;
import io.supertokens.cronjobs.CronTaskTest;
import io.supertokens.cronjobs.Cronjobs;
import io.supertokens.cronjobs.bulkimport.ProcessBulkImportUsers;
import io.supertokens.featureflag.EE_FEATURES;
import io.supertokens.featureflag.FeatureFlagTestContent;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.pluginInterface.Storage;
import io.supertokens.pluginInterface.bulkimport.BulkImportStorage.BULK_IMPORT_USER_STATUS;
import io.supertokens.pluginInterface.bulkimport.BulkImportUser;
import io.supertokens.pluginInterface.bulkimport.sqlStorage.BulkImportSQLStorage;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.multitenancy.AppIdentifier;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifier;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.test.TestingProcessManager;
import io.supertokens.test.TestingProcessManager.TestingProcess;
import io.supertokens.test.Utils;
import io.supertokens.useridmapping.UserIdMapping;
import io.supertokens.userroles.UserRoles;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import java.util.ArrayList;
import java.util.List;

import static io.supertokens.test.bulkimport.BulkImportTestUtils.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class ProcessBulkImportUsersCronJobTest {
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
    public void shouldProcessBulkImportUsersInTheSameTenant() throws Exception {
        TestingProcess process = startCronProcess();
        if(process == null) {
            return;
        }

        Main main = process.getProcess();

        // Create user roles before inserting bulk users
        {
            UserRoles.createNewRoleOrModifyItsPermissions(main, "role1", null);
            UserRoles.createNewRoleOrModifyItsPermissions(main, "role2", null);
        }

        BulkImportTestUtils.createTenants(main);

        BulkImportSQLStorage storage = (BulkImportSQLStorage) StorageLayer.getStorage(main);
        AppIdentifier appIdentifier = new AppIdentifier(null, null);

        int usersCount = 1;
        List<BulkImportUser> users = generateBulkImportUser(usersCount);
        BulkImport.addUsers(appIdentifier, storage, users);

        BulkImportUser bulkImportUser = users.get(0);

        waitForProcessingWithTimeout(appIdentifier, storage, 30);

        List<BulkImportUser> usersAfterProcessing = storage.getBulkImportUsers(appIdentifier, 100, null,
                null, null);

        assertEquals(0, usersAfterProcessing.size());

        UserPaginationContainer container = AuthRecipe.getUsers(main, 100, "ASC", null, null, null);
        assertEquals(usersCount, container.users.length);

        UserIdMapping.populateExternalUserIdForUsers(appIdentifier, storage, container.users);

        TenantIdentifier publicTenant = new TenantIdentifier(null, null, "public");

        BulkImportTestUtils.assertBulkImportUserAndAuthRecipeUserAreEqual(main, appIdentifier, publicTenant, storage,
                bulkImportUser,
                container.users[0]);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void shouldProcessBulkImportUsersInTheSameTenantWithoutExternalIdWithRoles() throws Exception {
        TestingProcess process = startCronProcess();
        if(process == null) {
            return;
        }

        Main main = process.getProcess();

        // Create user roles before inserting bulk users
        {
            UserRoles.createNewRoleOrModifyItsPermissions(main, "role1", null);
            UserRoles.createNewRoleOrModifyItsPermissions(main, "role2", null);
        }

        BulkImportTestUtils.createTenants(main);

        BulkImportSQLStorage storage = (BulkImportSQLStorage) StorageLayer.getStorage(main);
        AppIdentifier appIdentifier = new AppIdentifier(null, null);

        int usersCount = 1;
        List<BulkImportUser> users = generateBulkImportUserWithNoExternalIdWithRoles(usersCount, List.of("public", "t1"), 0, List.of("role1", "role2"));
        BulkImport.addUsers(appIdentifier, storage, users);

        BulkImportUser bulkImportUser = users.get(0);

        waitForProcessingWithTimeout(appIdentifier, storage, 30);

        List<BulkImportUser> usersAfterProcessing = storage.getBulkImportUsers(appIdentifier, 100, null,
                null, null);

        assertEquals(0, usersAfterProcessing.size());

        UserPaginationContainer container = AuthRecipe.getUsers(main, 100, "ASC", null, null, null);
        assertEquals(usersCount, container.users.length);

        UserIdMapping.populateExternalUserIdForUsers(appIdentifier, storage, container.users);

        TenantIdentifier publicTenant = new TenantIdentifier(null, null, "public");

        BulkImportTestUtils.assertBulkImportUserAndAuthRecipeUserAreEqual(main, appIdentifier, publicTenant, storage,
                bulkImportUser,
                container.users[0]);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void shouldProcessPlainTextPasswordBulkImportUsersInTheSameTenant() throws Exception {
        TestingProcess process = startCronProcess();
        if(process == null) {
            return;
        }

        Main main = process.getProcess();

        // Create user roles before inserting bulk users
        {
            UserRoles.createNewRoleOrModifyItsPermissions(main, "role1", null);
            UserRoles.createNewRoleOrModifyItsPermissions(main, "role2", null);
        }

        BulkImportTestUtils.createTenants(main);

        BulkImportSQLStorage storage = (BulkImportSQLStorage) StorageLayer.getStorage(main);
        AppIdentifier appIdentifier = new AppIdentifier(null, null);

        int usersCount = 1;
        List<BulkImportUser> users = generateBulkImportUserPlainTextPasswordAndRoles(usersCount, List.of("public", "t1"), 0, List.of("role1", "role2"));
        BulkImport.addUsers(appIdentifier, storage, users);

        BulkImportUser bulkImportUser = users.get(0);

        waitForProcessingWithTimeout(appIdentifier, storage, 30);

        List<BulkImportUser> usersAfterProcessing = storage.getBulkImportUsers(appIdentifier, 100, null,
                null, null);

        assertEquals(0, usersAfterProcessing.size());

        UserPaginationContainer container = AuthRecipe.getUsers(main, 100, "ASC", null, null, null);
        assertEquals(usersCount, container.users.length);

        UserIdMapping.populateExternalUserIdForUsers(appIdentifier, storage, container.users);

        TenantIdentifier publicTenant = new TenantIdentifier(null, null, "public");

        BulkImportTestUtils.assertBulkImportUserAndAuthRecipeUserAreEqual(main, appIdentifier, publicTenant, storage,
                bulkImportUser,
                container.users[0]);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void shouldProcessBulkImportUsersInNotSoLargeNumbersInTheSameTenant() throws Exception {
        Utils.setValueInConfig("bulk_migration_parallelism", "5");
        TestingProcess process = startCronProcess();
        if(process == null) {
            return;
        }
        Main main = process.getProcess();

        // Create user roles before inserting bulk users
        {
            UserRoles.createNewRoleOrModifyItsPermissions(main, "role1", null);
            UserRoles.createNewRoleOrModifyItsPermissions(main, "role2", null);
        }

        BulkImportTestUtils.createTenants(main);

        BulkImportSQLStorage storage = (BulkImportSQLStorage) StorageLayer.getStorage(main);
        AppIdentifier appIdentifier = new AppIdentifier(null, null);

        int usersCount = 15;
        List<BulkImportUser> users = generateBulkImportUser(usersCount);
        BulkImport.addUsers(appIdentifier, storage, users);

        waitForProcessingWithTimeout(appIdentifier, storage, 60);

        List<BulkImportUser> usersAfterProcessing = storage.getBulkImportUsers(appIdentifier, 1000, null,
                null, null);

        assertEquals(0, usersAfterProcessing.size());

        UserPaginationContainer container = AuthRecipe.getUsers(main, 1000, "ASC", null, null, null);
        assertEquals(usersCount, container.users.length);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void shouldProcessBulkImportUsersInLargeNumbersInTheSameTenant() throws Exception {
        TestingProcess process = startCronProcess();
        if(process == null) {
            return;
        }
        Main main = process.getProcess();

        // Create user roles before inserting bulk users
        {
            UserRoles.createNewRoleOrModifyItsPermissions(main, "role1", null);
            UserRoles.createNewRoleOrModifyItsPermissions(main, "role2", null);
        }

        BulkImportTestUtils.createTenants(main);

        BulkImportSQLStorage storage = (BulkImportSQLStorage) StorageLayer.getStorage(main);
        AppIdentifier appIdentifier = new AppIdentifier(null, null);

        int usersCount = 1000;
        List<BulkImportUser> users = generateBulkImportUser(usersCount);
        BulkImport.addUsers(appIdentifier, storage, users);

        waitForProcessingWithTimeout(appIdentifier, storage, 300); // 5 minutes

        List<BulkImportUser> usersAfterProcessing = storage.getBulkImportUsers(appIdentifier, 1000, null,
                null, null);

        assertEquals(0, usersAfterProcessing.size());

        UserPaginationContainer container = AuthRecipe.getUsers(main, 1000, "ASC", null, null, null);
        assertEquals(usersCount, container.users.length);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void shouldProcessBulkImportUsersInMultipleTenantsWithDifferentStoragesOnMultipleThreads() throws Exception {
        Utils.setValueInConfig("bulk_migration_parallelism", "3");

        TestingProcess process = startCronProcess();
        if(process == null) {
            return;
        }
        Main main = process.getProcess();


        // Create user roles before inserting bulk users
        {
            UserRoles.createNewRoleOrModifyItsPermissions(main, "role1", null);
            UserRoles.createNewRoleOrModifyItsPermissions(main, "role2", null);
        }

        BulkImportTestUtils.createTenants(main);

        TenantIdentifier t1 = new TenantIdentifier(null, null, "t1");
        TenantIdentifier t2 = new TenantIdentifier(null, null, "t2");

        BulkImportSQLStorage storage = (BulkImportSQLStorage) StorageLayer.getStorage(main);
        AppIdentifier appIdentifier = new AppIdentifier(null, null);

        List<BulkImportUser> usersT1 = generateBulkImportUser(1, List.of(t1.getTenantId()), 0);
        List<BulkImportUser> usersT2 = generateBulkImportUser(1, List.of(t2.getTenantId()), 1);

        BulkImportUser bulkImportUserT1 = usersT1.get(0);
        BulkImportUser bulkImportUserT2 = usersT2.get(0);

        BulkImport.addUsers(appIdentifier, storage, usersT1);
        BulkImport.addUsers(appIdentifier, storage, usersT2);

        waitForProcessingWithTimeout(appIdentifier, storage, 30);

        List<BulkImportUser> usersAfterProcessing = storage.getBulkImportUsers(appIdentifier, 100, null,
                null, null);

        assertEquals(0, usersAfterProcessing.size());

        Storage storageT1 = StorageLayer.getStorage(t1, main);
        Storage storageT2 = StorageLayer.getStorage(t2, main);

        UserPaginationContainer containerT1 = AuthRecipe.getUsers(t1, storageT1, 100, "ASC", null, null, null);
        UserPaginationContainer containerT2 = AuthRecipe.getUsers(t2, storageT2, 100, "ASC", null, null, null);

        assertEquals(usersT1.size() + usersT2.size(), containerT1.users.length + containerT2.users.length);

        UserIdMapping.populateExternalUserIdForUsers(appIdentifier, storageT1, containerT1.users);
        UserIdMapping.populateExternalUserIdForUsers(appIdentifier, storageT2, containerT2.users);

        BulkImportTestUtils.assertBulkImportUserAndAuthRecipeUserAreEqual(main, appIdentifier, t1, storageT1,
                bulkImportUserT1,
                containerT1.users[0]);
        BulkImportTestUtils.assertBulkImportUserAndAuthRecipeUserAreEqual(main, appIdentifier, t2, storageT2,
                bulkImportUserT2,
                containerT2.users[0]);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void shouldProcessBulkImportUsersInMultipleTenantsWithDifferentStoragesOnOneThreads() throws Exception {
        Utils.setValueInConfig("bulk_migration_parallelism", "1");

        TestingProcess process = startCronProcess();
        if(process == null) {
            return;
        }
        Main main = process.getProcess();

        // Create user roles before inserting bulk users
        {
            UserRoles.createNewRoleOrModifyItsPermissions(main, "role1", null);
            UserRoles.createNewRoleOrModifyItsPermissions(main, "role2", null);
        }

        BulkImportTestUtils.createTenants(main);

        TenantIdentifier t1 = new TenantIdentifier(null, null, "t1");
        TenantIdentifier t2 = new TenantIdentifier(null, null, "t2");

        BulkImportSQLStorage storage = (BulkImportSQLStorage) StorageLayer.getStorage(main);
        AppIdentifier appIdentifier = new AppIdentifier(null, null);

        List<BulkImportUser> usersT1 = generateBulkImportUser(1, List.of(t1.getTenantId()), 0);
        List<BulkImportUser> usersT2 = generateBulkImportUser(1, List.of(t2.getTenantId()), 1);

        BulkImportUser bulkImportUserT1 = usersT1.get(0);
        BulkImportUser bulkImportUserT2 = usersT2.get(0);

        BulkImport.addUsers(appIdentifier, storage, usersT1);
        BulkImport.addUsers(appIdentifier, storage, usersT2);

        waitForProcessingWithTimeout(appIdentifier, storage, 30);

        List<BulkImportUser> usersAfterProcessing = storage.getBulkImportUsers(appIdentifier, 100, null,
                null, null);

        assertEquals(0, usersAfterProcessing.size());

        Storage storageT1 = StorageLayer.getStorage(t1, main);
        Storage storageT2 = StorageLayer.getStorage(t2, main);

        UserPaginationContainer containerT1 = AuthRecipe.getUsers(t1, storageT1, 100, "ASC", null, null, null);
        UserPaginationContainer containerT2 = AuthRecipe.getUsers(t2, storageT2, 100, "ASC", null, null, null);

        assertEquals(usersT1.size() + usersT2.size(), containerT1.users.length + containerT2.users.length);

        UserIdMapping.populateExternalUserIdForUsers(appIdentifier, storageT1, containerT1.users);
        UserIdMapping.populateExternalUserIdForUsers(appIdentifier, storageT2, containerT2.users);

        BulkImportTestUtils.assertBulkImportUserAndAuthRecipeUserAreEqual(main, appIdentifier, t1, storageT1,
                bulkImportUserT1,
                containerT1.users[0]);
        BulkImportTestUtils.assertBulkImportUserAndAuthRecipeUserAreEqual(main, appIdentifier, t2, storageT2,
                bulkImportUserT2,
                containerT2.users[0]);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void shouldProcessBulkImportUsersInLargeNumberInMultipleTenantsWithDifferentStorages() throws Exception {
        Utils.setValueInConfig("bulk_migration_parallelism", "12");

        TestingProcess process = startCronProcess();
        if(process == null) {
            return;
        }
        Main main = process.getProcess();

        // Create user roles before inserting bulk users
        {
            UserRoles.createNewRoleOrModifyItsPermissions(main, "role1", null);
            UserRoles.createNewRoleOrModifyItsPermissions(main, "role2", null);
        }

        BulkImportTestUtils.createTenants(main);

        TenantIdentifier t1 = new TenantIdentifier(null, null, "t1");
        TenantIdentifier t2 = new TenantIdentifier(null, null, "t2");

        BulkImportSQLStorage storage = (BulkImportSQLStorage) StorageLayer.getStorage(main);
        AppIdentifier appIdentifier = new AppIdentifier(null, null);

        List<BulkImportUser> usersT1 = generateBulkImportUser(500, List.of(t1.getTenantId()), 0);
        List<BulkImportUser> usersT2 = generateBulkImportUser(500, List.of(t2.getTenantId()), 500);

        List<BulkImportUser> allUsers = new ArrayList<>();
        allUsers.addAll(usersT1);
        allUsers.addAll(usersT2);

        BulkImport.addUsers(appIdentifier, storage, allUsers);

        waitForProcessingWithTimeout(appIdentifier, storage, 120);

        List<BulkImportUser> usersAfterProcessing = storage.getBulkImportUsers(appIdentifier, 1000, null,
                null, null);

        assertEquals(0, usersAfterProcessing.size());

        Storage storageT1 = StorageLayer.getStorage(t1, main);
        Storage storageT2 = StorageLayer.getStorage(t2, main);

        UserPaginationContainer containerT1 = AuthRecipe.getUsers(t1, storageT1, 500, "ASC", null, null, null);
        UserPaginationContainer containerT2 = AuthRecipe.getUsers(t2, storageT2, 500, "ASC", null, null, null);

        assertEquals(usersT1.size() + usersT2.size(), containerT1.users.length + containerT2.users.length);

        UserIdMapping.populateExternalUserIdForUsers(appIdentifier, storageT1, containerT1.users);
        UserIdMapping.populateExternalUserIdForUsers(appIdentifier, storageT2, containerT2.users);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void shouldProcessBulkImportUsersInLargeNumberInMultipleTenantsWithDifferentStoragesOnOneThread() throws Exception {
        Utils.setValueInConfig("bulk_migration_parallelism", "1");

        TestingProcess process = startCronProcess();
        if(process == null) {
            return;
        }
        Main main = process.getProcess();

        // Create user roles before inserting bulk users
        {
            UserRoles.createNewRoleOrModifyItsPermissions(main, "role1", null);
            UserRoles.createNewRoleOrModifyItsPermissions(main, "role2", null);
        }

        BulkImportTestUtils.createTenants(main);

        TenantIdentifier t1 = new TenantIdentifier(null, null, "t1");
        TenantIdentifier t2 = new TenantIdentifier(null, null, "t2");

        BulkImportSQLStorage storage = (BulkImportSQLStorage) StorageLayer.getStorage(main);
        AppIdentifier appIdentifier = new AppIdentifier(null, null);

        List<BulkImportUser> usersT1 = generateBulkImportUser(50, List.of(t1.getTenantId()), 0);
        List<BulkImportUser> usersT2 = generateBulkImportUser(50, List.of(t2.getTenantId()), 50);

        List<BulkImportUser> allUsers = new ArrayList<>();
        allUsers.addAll(usersT1);
        allUsers.addAll(usersT2);

        BulkImport.addUsers(appIdentifier, storage, allUsers);

        waitForProcessingWithTimeout(appIdentifier, storage, 120);

        List<BulkImportUser> usersAfterProcessing = storage.getBulkImportUsers(appIdentifier, 1000, null,
                null, null);

        assertEquals(0, usersAfterProcessing.size());

        Storage storageT1 = StorageLayer.getStorage(t1, main);
        Storage storageT2 = StorageLayer.getStorage(t2, main);

        UserPaginationContainer containerT1 = AuthRecipe.getUsers(t1, storageT1, 500, "ASC", null, null, null);
        UserPaginationContainer containerT2 = AuthRecipe.getUsers(t2, storageT2, 500, "ASC", null, null, null);

        assertEquals(usersT1.size() + usersT2.size(), containerT1.users.length + containerT2.users.length);

        UserIdMapping.populateExternalUserIdForUsers(appIdentifier, storageT1, containerT1.users);
        UserIdMapping.populateExternalUserIdForUsers(appIdentifier, storageT2, containerT2.users);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void shouldDeleteEverythingFromTheDBIfAnythingFails() throws Exception {
        // Creating a non-existing user role will result in an error.
        // Since, user role creation happens at the last step of the bulk import process, everything should be deleted from the DB.

        // NOTE: We will also need to disable the bulk import user validation in the cron job for this test to work.
        Main.isTesting_skipBulkImportUserValidationInCronJob = true;

        TestingProcess process = startCronProcess();
        if(process == null) {
            return;
        }
        Main main = process.getProcess();

        BulkImportTestUtils.createTenants(main);

        BulkImportSQLStorage storage = (BulkImportSQLStorage) StorageLayer.getStorage(main);
        AppIdentifier appIdentifier = new AppIdentifier(null, null);

        // note the missing role creation here!

        List<BulkImportUser> users = generateBulkImportUser(1);
        BulkImport.addUsers(appIdentifier, storage, users);

        waitForProcessingWithTimeout(appIdentifier, storage, 30);

        List<BulkImportUser> usersAfterProcessing = storage.getBulkImportUsers(appIdentifier, 100, null,
                null, null);

        assertEquals(1, usersAfterProcessing.size());

        assertEquals(BULK_IMPORT_USER_STATUS.FAILED, usersAfterProcessing.get(0).status);
        assertEquals("E034: Role does not exist! You need to pre-create the role before assigning it to the user.",
                usersAfterProcessing.get(0).errorMessage);

        UserPaginationContainer container = AuthRecipe.getUsers(main, 100, "ASC", null, null, null);
        assertEquals(0, container.users.length);
    }


    @Test
    public void shouldDeleteEverythingFromTheDBIfAnythingFailsOnMultipleThreads() throws Exception {
        Utils.setValueInConfig("bulk_migration_parallelism", "8");
        // Creating a non-existing user role will result in an error.
        // Since, user role creation happens at the last step of the bulk import process, everything should be deleted from the DB.

        // NOTE: We will also need to disable the bulk import user validation in the cron job for this test to work.
        Main.isTesting_skipBulkImportUserValidationInCronJob = true;

        TestingProcess process = startCronProcess();
        if(process == null) {
            return;
        }
        Main main = process.getProcess();

        BulkImportTestUtils.createTenants(main);
        Thread.sleep(1000);

        BulkImportSQLStorage storage = (BulkImportSQLStorage) StorageLayer.getStorage(main);
        AppIdentifier appIdentifier = new AppIdentifier(null, null);

        // note the missing role creation here!

        List<BulkImportUser> users = generateBulkImportUser(100);
        BulkImport.addUsers(appIdentifier, storage, users);

        waitForProcessingWithTimeout(appIdentifier, storage, 120);

        List<BulkImportUser> usersAfterProcessing = storage.getBulkImportUsers(appIdentifier, 100, null,
                null, null);

        assertEquals(100, usersAfterProcessing.size());

        for(BulkImportUser userAfterProcessing: usersAfterProcessing){
            assertEquals(BULK_IMPORT_USER_STATUS.FAILED, userAfterProcessing.status); // should process every user and every one of them should fail because of the missing role
            assertEquals("E034: Role does not exist! You need to pre-create the role before assigning it to the user.",
                    userAfterProcessing.errorMessage);
        }

        UserPaginationContainer container = AuthRecipe.getUsers(main, 100, "ASC", null, null, null);
        assertEquals(0, container.users.length);
    }

    @Test
    public void shouldDeleteOnlyFailedFromTheDBIfAnythingFailsOnMultipleThreads() throws Exception {
        Utils.setValueInConfig("bulk_migration_parallelism", "8");
        // Creating a non-existing user role will result in an error.
        // Since, user role creation happens at the last step of the bulk import process, everything should be deleted from the DB.

        // NOTE: We will also need to disable the bulk import user validation in the cron job for this test to work.
        Main.isTesting_skipBulkImportUserValidationInCronJob = true;

        TestingProcess process = startCronProcess();
        if(process == null) {
            return;
        }
        Main main = process.getProcess();

        BulkImportTestUtils.createTenants(main);

        BulkImportSQLStorage storage = (BulkImportSQLStorage) StorageLayer.getStorage(main);
        AppIdentifier appIdentifier = new AppIdentifier(null, null);

        // Create one user role before inserting bulk users
        {
            UserRoles.createNewRoleOrModifyItsPermissions(main, "role1", null);
        }

        List<BulkImportUser> users = generateBulkImportUserWithRoles(99, List.of("public", "t1"), 0, List.of("role1"));
        users.addAll(generateBulkImportUserWithRoles(1, List.of("public", "t1"), 99, List.of("notExistingRole")));

        BulkImport.addUsers(appIdentifier, storage, users);

        waitForProcessingWithTimeout(appIdentifier, storage, 60);

        List<BulkImportUser> usersAfterProcessing = storage.getBulkImportUsers(appIdentifier, 100, null,
                null, null);

        assertEquals(1, usersAfterProcessing.size());

        int numberOfFailed = 0;
        for(int i = 0; i < usersAfterProcessing.size(); i++){
            if(usersAfterProcessing.get(i).status == BULK_IMPORT_USER_STATUS.FAILED) {
                assertEquals(
                        "E034: Role does not exist! You need to pre-create the role before assigning it to the user.",
                        usersAfterProcessing.get(i).errorMessage);
                numberOfFailed++;
            }
        }

        UserPaginationContainer container = AuthRecipe.getUsers(main, 100, "ASC", null, null, null);
        assertEquals(99, container.users.length);
        assertEquals(1, numberOfFailed);
    }


    @Test
    public void shouldThrowTenantDoesNotExistError() throws Exception {
        TestingProcess process = startCronProcess();
        if(process == null) {
            return;
        }
        Main main = process.getProcess();

        BulkImportSQLStorage storage = (BulkImportSQLStorage) StorageLayer.getStorage(main);
        AppIdentifier appIdentifier = new AppIdentifier(null, null);

        // Create user roles before inserting bulk users
        {
            UserRoles.createNewRoleOrModifyItsPermissions(main, "role1", null);
            UserRoles.createNewRoleOrModifyItsPermissions(main, "role2", null);
        }

        List<BulkImportUser> users = generateBulkImportUser(1);
        BulkImport.addUsers(appIdentifier, storage, users);

        waitForProcessingWithTimeout(appIdentifier, storage, 30);

        List<BulkImportUser> usersAfterProcessing = storage.getBulkImportUsers(appIdentifier, 100, null,
                null, null);

        assertEquals(1, usersAfterProcessing.size());
        assertEquals(BULK_IMPORT_USER_STATUS.FAILED, usersAfterProcessing.get(0).status);
        assertEquals(
                "[Invalid tenantId: t1 for a user role., Invalid tenantId: t1 for a user role., Invalid tenantId: t1 for emailpassword recipe., Invalid tenantId: t1 for thirdparty recipe., Invalid tenantId: t1 for passwordless recipe.]",
                usersAfterProcessing.get(0).errorMessage);
    }

    @Test
    public void shouldThrowTenantHaveDifferentStoragesError() throws Exception {
        TestingProcess process = startCronProcess();
        if(process == null) {
            return;
        }
        Main main = process.getProcess();

        BulkImportSQLStorage storage = (BulkImportSQLStorage) StorageLayer.getStorage(main);
        AppIdentifier appIdentifier = new AppIdentifier(null, null);

        // Create user roles before inserting bulk users
        {
            UserRoles.createNewRoleOrModifyItsPermissions(main, "role1", null);
            UserRoles.createNewRoleOrModifyItsPermissions(main, "role2", null);
        }
        BulkImportTestUtils.createTenants(main);

        List<BulkImportUser> users = generateBulkImportUser(1, List.of("t1", "t2"), 0);
        BulkImport.addUsers(appIdentifier, storage, users);

        waitForProcessingWithTimeout(appIdentifier, storage, 30);

        List<BulkImportUser> usersAfterProcessing = storage.getBulkImportUsers(appIdentifier, 100, null,
                null, null);

        assertEquals(1, usersAfterProcessing.size());
        assertEquals(BULK_IMPORT_USER_STATUS.FAILED, usersAfterProcessing.get(0).status);
        assertEquals(
                "[All tenants for a user must share the same database for emailpassword recipe., All tenants for a user must share the same database for thirdparty recipe., All tenants for a user must share the same database for passwordless recipe.]",
                usersAfterProcessing.get(0).errorMessage);
    }

    private TestingProcess startCronProcess() throws InterruptedException, TenantOrAppNotFoundException {
        String[] args = { "../" };

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args, false);

        Main main = process.getProcess();


        FeatureFlagTestContent.getInstance(main)
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[] {
                        EE_FEATURES.ACCOUNT_LINKING, EE_FEATURES.MULTI_TENANCY, EE_FEATURES.MFA });

        // We are setting a non-zero initial wait for tests to avoid race condition with the beforeTest process that deletes data in the storage layer
        CronTaskTest.getInstance(main).setInitialWaitTimeInSeconds(ProcessBulkImportUsers.RESOURCE_KEY, 10);
        CronTaskTest.getInstance(main).setIntervalInSeconds(ProcessBulkImportUsers.RESOURCE_KEY, 10);

        process.startProcess();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(main).getType() != STORAGE_TYPE.SQL || StorageLayer.isInMemDb(main)) {
            return null;
        }

        Cronjobs.addCronjob(main, (ProcessBulkImportUsers) main.getResourceDistributor().getResource(new TenantIdentifier(null, null, null), ProcessBulkImportUsers.RESOURCE_KEY));

        return process;
    }

    //will not wait after timeout was reached
    private void waitForProcessingWithTimeout(AppIdentifier appIdentifier, BulkImportSQLStorage storage, int timeoutSeconds)
            throws StorageQueryException, InterruptedException {
        long numberOfUnprocessedUsers = 1;
        long startTime = System.currentTimeMillis();
        long currentTime = 0;
        while(numberOfUnprocessedUsers > 0){
            numberOfUnprocessedUsers = (storage.getBulkImportUsersCount(appIdentifier, BULK_IMPORT_USER_STATUS.NEW)
                    + storage.getBulkImportUsersCount(appIdentifier, BULK_IMPORT_USER_STATUS.PROCESSING));
            Thread.sleep(1000);
            currentTime = System.currentTimeMillis();
            if((currentTime - startTime) > (timeoutSeconds * 1000L)){
                System.out.println("Timeout of " + timeoutSeconds + " seconds reached.");
                break;
            }
        }
    }
}
