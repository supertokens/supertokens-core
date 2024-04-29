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
import io.supertokens.cronjobs.bulkimport.ProcessBulkImportUsers;
import io.supertokens.featureflag.EE_FEATURES;
import io.supertokens.featureflag.FeatureFlagTestContent;
import io.supertokens.featureflag.exceptions.FeatureNotEnabledException;
import io.supertokens.multitenancy.Multitenancy;
import io.supertokens.multitenancy.exception.BadPermissionException;
import io.supertokens.multitenancy.exception.CannotModifyBaseConfigException;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.pluginInterface.Storage;
import io.supertokens.pluginInterface.authRecipe.AuthRecipeUserInfo;
import io.supertokens.pluginInterface.authRecipe.LoginMethod;
import io.supertokens.pluginInterface.bulkimport.BulkImportUser;
import io.supertokens.pluginInterface.bulkimport.BulkImportStorage.BULK_IMPORT_USER_STATUS;
import io.supertokens.pluginInterface.bulkimport.BulkImportUser.TotpDevice;
import io.supertokens.pluginInterface.bulkimport.sqlStorage.BulkImportSQLStorage;
import io.supertokens.pluginInterface.exceptions.InvalidConfigException;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.multitenancy.AppIdentifier;
import io.supertokens.pluginInterface.multitenancy.EmailPasswordConfig;
import io.supertokens.pluginInterface.multitenancy.PasswordlessConfig;
import io.supertokens.pluginInterface.multitenancy.TenantConfig;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifier;
import io.supertokens.pluginInterface.multitenancy.ThirdPartyConfig;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.pluginInterface.totp.TOTPDevice;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.test.TestingProcessManager;
import io.supertokens.test.TestingProcessManager.TestingProcess;
import io.supertokens.test.Utils;
import io.supertokens.thirdparty.InvalidProviderConfigException;
import io.supertokens.totp.Totp;
import io.supertokens.useridmapping.UserIdMapping;
import io.supertokens.usermetadata.UserMetadata;
import io.supertokens.userroles.UserRoles;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import com.google.gson.JsonObject;

import static io.supertokens.test.bulkimport.BulkImportTestUtils.generateBulkImportUser;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.List;

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
        Main main = process.getProcess();

        if (StorageLayer.getBaseStorage(main).getType() != STORAGE_TYPE.SQL || StorageLayer.isInMemDb(main)) {
            return;
        }

        // Create user roles before inserting bulk users
        {
            UserRoles.createNewRoleOrModifyItsPermissions(main, "role1", null);
            UserRoles.createNewRoleOrModifyItsPermissions(main, "role2", null);
        }

        createTenants(main);

        BulkImportSQLStorage storage = (BulkImportSQLStorage) StorageLayer.getStorage(main);
        AppIdentifier appIdentifier = new AppIdentifier(null, null);

        int usersCount = 1;
        List<BulkImportUser> users = generateBulkImportUser(usersCount);
        BulkImport.addUsers(appIdentifier, storage, users);

        BulkImportUser bulkImportUser = users.get(0);

        Thread.sleep(6000);

        List<BulkImportUser> usersAfterProcessing = storage.getBulkImportUsers(appIdentifier, 100, null,
                null, null);

        assertEquals(0, usersAfterProcessing.size());

        UserPaginationContainer container = AuthRecipe.getUsers(main, 100, "ASC", null, null, null);
        assertEquals(usersCount, container.users.length);

        UserIdMapping.populateExternalUserIdForUsers(appIdentifier, storage, container.users);

        TenantIdentifier publicTenant = new TenantIdentifier(null, null, "public");

        assertBulkImportUserAndAuthRecipeUserAreEqual(appIdentifier, publicTenant, storage, bulkImportUser,
                container.users[0]);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void shouldProcessBulkImportUsersInMultipleTenantsWithDifferentStorages() throws Exception {
        TestingProcess process = startCronProcess();
        Main main = process.getProcess();

        if (StorageLayer.getBaseStorage(main).getType() != STORAGE_TYPE.SQL || StorageLayer.isInMemDb(main)) {
            return;
        }

        // Create user roles before inserting bulk users
        {
            UserRoles.createNewRoleOrModifyItsPermissions(main, "role1", null);
            UserRoles.createNewRoleOrModifyItsPermissions(main, "role2", null);
        }

        createTenants(main);

        TenantIdentifier t1 = new TenantIdentifier(null, null, "t1");
        TenantIdentifier t2 = new TenantIdentifier(null, null, "t2");

        BulkImportSQLStorage storage = (BulkImportSQLStorage) StorageLayer.getStorage(main);
        AppIdentifier appIdentifier = new AppIdentifier(null, null);

        List<BulkImportUser> usersT1 = generateBulkImportUser(1, List.of(t1.getTenantId()), 0);
        List<BulkImportUser> usersT2 = generateBulkImportUser(1, List.of(t2.getTenantId()), 1);

        BulkImportUser bulkImportUserT1 = usersT1.get(0);
        BulkImportUser bulkImportUserT2 = usersT2.get(0);

        BulkImport.addUsers(appIdentifier, storage, List.of(bulkImportUserT1, bulkImportUserT2));

        Thread.sleep(6000);

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

        assertBulkImportUserAndAuthRecipeUserAreEqual(appIdentifier, t1, storageT1, bulkImportUserT1,
                containerT1.users[0]);
        assertBulkImportUserAndAuthRecipeUserAreEqual(appIdentifier, t2, storageT2, bulkImportUserT2,
                containerT2.users[0]);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void shouldDeleteEverythingFromtheDBIfAnythingFails() throws Exception {
        // Creating a non-existing user role will result in an error.
        // Since, user role creation happens at the last step of the bulk import process, everything should be deleted from the DB.

        // NOTE: We will also need to disable the bulk import user validation in the cron job for this test to work.
        Main.isTesting_skipBulkImportUserValidationInCronJob = true;

        TestingProcess process = startCronProcess();
        Main main = process.getProcess();

        if (StorageLayer.getBaseStorage(main).getType() != STORAGE_TYPE.SQL || StorageLayer.isInMemDb(main)) {
            return;
        }

        createTenants(main);

        BulkImportSQLStorage storage = (BulkImportSQLStorage) StorageLayer.getStorage(main);
        AppIdentifier appIdentifier = new AppIdentifier(null, null);

        List<BulkImportUser> users = generateBulkImportUser(1);
        BulkImport.addUsers(appIdentifier, storage, users);

        Thread.sleep(6000);

        List<BulkImportUser> usersAfterProcessing = storage.getBulkImportUsers(appIdentifier, 100, null,
                null, null);

        assertEquals(1, usersAfterProcessing.size());

        assertEquals(BULK_IMPORT_USER_STATUS.FAILED, usersAfterProcessing.get(0).status);
        assertEquals("Role role1 does not exist! You need pre-create the role before assigning it to the user.",
                usersAfterProcessing.get(0).errorMessage);

        UserPaginationContainer container = AuthRecipe.getUsers(main, 100, "ASC", null, null, null);
        assertEquals(0, container.users.length);
    }

    @Test
    public void shouldThrowTenantDoesNotExistError() throws Exception {
        TestingProcess process = startCronProcess();
        Main main = process.getProcess();

        if (StorageLayer.getBaseStorage(main).getType() != STORAGE_TYPE.SQL || StorageLayer.isInMemDb(main)) {
            return;
        }

        BulkImportSQLStorage storage = (BulkImportSQLStorage) StorageLayer.getStorage(main);
        AppIdentifier appIdentifier = new AppIdentifier(null, null);

        // Create user roles before inserting bulk users
        {
            UserRoles.createNewRoleOrModifyItsPermissions(main, "role1", null);
            UserRoles.createNewRoleOrModifyItsPermissions(main, "role2", null);
        }

        List<BulkImportUser> users = generateBulkImportUser(1);
        BulkImport.addUsers(appIdentifier, storage, users);

        Thread.sleep(6000);

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
        Main main = process.getProcess();

        if (StorageLayer.getBaseStorage(main).getType() != STORAGE_TYPE.SQL || StorageLayer.isInMemDb(main)) {
            return;
        }

        BulkImportSQLStorage storage = (BulkImportSQLStorage) StorageLayer.getStorage(main);
        AppIdentifier appIdentifier = new AppIdentifier(null, null);

        // Create user roles before inserting bulk users
        {
            UserRoles.createNewRoleOrModifyItsPermissions(main, "role1", null);
            UserRoles.createNewRoleOrModifyItsPermissions(main, "role2", null);
        }
        createTenants(main);


        List<BulkImportUser> users = generateBulkImportUser(1, List.of("t1", "t2"), 0);
        BulkImport.addUsers(appIdentifier, storage, users);

        Thread.sleep(6000);

        List<BulkImportUser> usersAfterProcessing = storage.getBulkImportUsers(appIdentifier, 100, null,
                null, null);

        assertEquals(1, usersAfterProcessing.size());
        assertEquals(BULK_IMPORT_USER_STATUS.FAILED, usersAfterProcessing.get(0).status);
        assertEquals(
                "[All tenants for a user must share the same database for emailpassword recipe., All tenants for a user must share the same database for thirdparty recipe., All tenants for a user must share the same database for passwordless recipe.]",
                usersAfterProcessing.get(0).errorMessage);
    }

    private TestingProcess startCronProcess() throws InterruptedException {
        String[] args = { "../" };

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args, false);

        Main main = process.getProcess();

        FeatureFlagTestContent.getInstance(main)
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[] {
                        EE_FEATURES.ACCOUNT_LINKING, EE_FEATURES.MULTI_TENANCY, EE_FEATURES.MFA });

        // We are setting a non-zero initial wait for tests to avoid race condition with the beforeTest process that deletes data in the storage layer
        CronTaskTest.getInstance(main).setInitialWaitTimeInSeconds(ProcessBulkImportUsers.RESOURCE_KEY, 5);
        CronTaskTest.getInstance(main).setIntervalInSeconds(ProcessBulkImportUsers.RESOURCE_KEY, 100000);
        process.startProcess();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(main).getType() != STORAGE_TYPE.SQL) {
            return null;
        }

        return process;
    }

    private void assertBulkImportUserAndAuthRecipeUserAreEqual(AppIdentifier appIdentifier,
            TenantIdentifier tenantIdentifier, Storage storage, BulkImportUser bulkImportUser,
            AuthRecipeUserInfo authRecipeUser) throws StorageQueryException, TenantOrAppNotFoundException {
        for (LoginMethod lm1 : authRecipeUser.loginMethods) {
            bulkImportUser.loginMethods.forEach(lm2 -> {
                if (lm2.recipeId.equals(lm1.recipeId.toString())) {
                    assertLoginMethodEquals(lm1, lm2);
                }
            });
        }
        assertEquals(bulkImportUser.externalUserId, authRecipeUser.getSupertokensOrExternalUserId());
        assertEquals(bulkImportUser.userMetadata,
                UserMetadata.getUserMetadata(appIdentifier, storage, authRecipeUser.getSupertokensOrExternalUserId()));

        String[] createdUserRoles = UserRoles.getRolesForUser(tenantIdentifier, storage,
                authRecipeUser.getSupertokensOrExternalUserId());
        String[] bulkImportUserRoles = bulkImportUser.userRoles.stream().map(r -> r.role).toArray(String[]::new);
        assertArrayEquals(bulkImportUserRoles, createdUserRoles);

        TOTPDevice[] createdTotpDevices = Totp.getDevices(appIdentifier, storage,
                authRecipeUser.getSupertokensOrExternalUserId());
        assertTotpDevicesEquals(createdTotpDevices, bulkImportUser.totpDevices.toArray(new TotpDevice[0]));
    }

    private void assertLoginMethodEquals(LoginMethod lm1,
            io.supertokens.pluginInterface.bulkimport.BulkImportUser.LoginMethod lm2) {
        assertEquals(lm1.email, lm2.email);
        assertEquals(lm1.verified, lm2.isVerified);
        assertTrue(lm2.tenantIds.containsAll(lm1.tenantIds) && lm1.tenantIds.containsAll(lm2.tenantIds));

        switch (lm2.recipeId) {
            case "emailpassword":
                assertEquals(lm1.passwordHash, lm2.passwordHash);
                break;
            case "thirdparty":
                assertEquals(lm1.thirdParty.id, lm2.thirdPartyId);
                assertEquals(lm1.thirdParty.userId, lm2.thirdPartyUserId);
                break;
            case "passwordless":
                assertEquals(lm1.phoneNumber, lm2.phoneNumber);
                break;
            default:
                break;
        }
    }

    private void assertTotpDevicesEquals(TOTPDevice[] createdTotpDevices, TotpDevice[] bulkImportTotpDevices) {
        assertEquals(createdTotpDevices.length, bulkImportTotpDevices.length);
        for (int i = 0; i < createdTotpDevices.length; i++) {
            assertEquals(createdTotpDevices[i].deviceName, bulkImportTotpDevices[i].deviceName);
            assertEquals(createdTotpDevices[i].period, bulkImportTotpDevices[i].period);
            assertEquals(createdTotpDevices[i].secretKey, bulkImportTotpDevices[i].secretKey);
            assertEquals(createdTotpDevices[i].skew, bulkImportTotpDevices[i].skew);
        }
    }

    private void createTenants(Main main)
            throws StorageQueryException, TenantOrAppNotFoundException, InvalidProviderConfigException,
            FeatureNotEnabledException, IOException, InvalidConfigException,
            CannotModifyBaseConfigException, BadPermissionException {
        { // tenant 1 (t1 in the same storage as public tenant)
            TenantIdentifier tenantIdentifier = new TenantIdentifier(null, null, "t1");

            Multitenancy.addNewOrUpdateAppOrTenant(
                    main,
                    new TenantIdentifier(null, null, null),
                    new TenantConfig(
                            tenantIdentifier,
                            new EmailPasswordConfig(true),
                            new ThirdPartyConfig(true, null),
                            new PasswordlessConfig(true),
                            null, null, new JsonObject()));
        }
        { // tenant 2 (t2 in the different storage than public tenant)
            TenantIdentifier tenantIdentifier = new TenantIdentifier(null, null, "t2");

            JsonObject config = new JsonObject();

            StorageLayer.getStorage(new TenantIdentifier(null, null, null), main)
                    .modifyConfigToAddANewUserPoolForTesting(config, 1);
            Multitenancy.addNewOrUpdateAppOrTenant(
                    main,
                    new TenantIdentifier(null, null, null),
                    new TenantConfig(
                            tenantIdentifier,
                            new EmailPasswordConfig(true),
                            new ThirdPartyConfig(true, null),
                            new PasswordlessConfig(true),
                            null, null, config));
        }
    }
}
