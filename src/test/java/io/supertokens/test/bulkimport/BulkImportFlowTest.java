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

package io.supertokens.test.bulkimport;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.supertokens.Main;
import io.supertokens.ProcessState;
import io.supertokens.cronjobs.CronTaskTest;
import io.supertokens.cronjobs.Cronjobs;
import io.supertokens.cronjobs.bulkimport.ProcessBulkImportUsers;
import io.supertokens.featureflag.EE_FEATURES;
import io.supertokens.featureflag.FeatureFlagTestContent;
import io.supertokens.multitenancy.Multitenancy;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.pluginInterface.bulkimport.BulkImportStorage;
import io.supertokens.pluginInterface.multitenancy.*;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.test.TestingProcessManager;
import io.supertokens.test.Utils;
import io.supertokens.test.httpRequest.HttpRequestForTesting;
import io.supertokens.test.httpRequest.HttpResponseException;
import io.supertokens.userroles.UserRoles;
import org.jetbrains.annotations.NotNull;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import static org.junit.Assert.*;

public class BulkImportFlowTest {

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
    public void testWithALotOfUsers() throws Exception {
        Main main = startCronProcess("14");

        int NUMBER_OF_USERS_TO_UPLOAD = 10000;

        if (StorageLayer.getBaseStorage(main).getType() != STORAGE_TYPE.SQL || StorageLayer.isInMemDb(main)) {
            return;
        }

        // Create user roles before inserting bulk users
        {
            UserRoles.createNewRoleOrModifyItsPermissions(main, "role1", null);
            UserRoles.createNewRoleOrModifyItsPermissions(main, "role2", null);
        }

        // upload a bunch of users through the API
        {
            for (int i = 0; i < (NUMBER_OF_USERS_TO_UPLOAD / 1000); i++) {
                JsonObject request = generateUsersJson(1000, i * 1000); // API allows 10k users upload at once
                JsonObject response = uploadBulkImportUsersJson(main, request);
                assertEquals("OK", response.get("status").getAsString());
            }

        }

        long processingStarted = System.currentTimeMillis();

        // wait for the cron job to process them
        // periodically check the remaining unprocessed users
        // Note1: the cronjob starts the processing automatically
        // Note2: the successfully processed users get deleted from the bulk_import_users table
        {
            long count = NUMBER_OF_USERS_TO_UPLOAD;
            while(true) {
                try {
                    JsonObject response = loadBulkImportUsersCountWithStatus(main, null);
                    assertEquals("OK", response.get("status").getAsString());
                    count = response.get("count").getAsLong();
                    int newUsersNumber = loadBulkImportUsersCountWithStatus(main,
                            BulkImportStorage.BULK_IMPORT_USER_STATUS.NEW).get("count").getAsInt();
                    int processingUsersNumber = loadBulkImportUsersCountWithStatus(main,
                            BulkImportStorage.BULK_IMPORT_USER_STATUS.PROCESSING).get("count").getAsInt();
                    int failedUsersNumber = loadBulkImportUsersCountWithStatus(main,
                            BulkImportStorage.BULK_IMPORT_USER_STATUS.FAILED).get("count").getAsInt();
                    count = newUsersNumber + processingUsersNumber;
                    System.out.println("Remaining users: " + count);

                    if (count == 0) {
                        break;
                    }
                } catch (Exception e) {
                    if(e instanceof SocketTimeoutException)  {
                        //ignore
                    } else {
                        throw e;
                    }
                }
                Thread.sleep(1000);
            }
        }

        long processingFinished = System.currentTimeMillis();
        System.out.println("Processed " + NUMBER_OF_USERS_TO_UPLOAD + " users in " + (processingFinished - processingStarted) / 1000
                + " seconds ( or " + (processingFinished - processingStarted) / 60000 + " minutes)");

        // after processing finished, make sure every user got processed correctly
        {
            int failedImportedUsersNumber = loadBulkImportUsersCountWithStatus(main, BulkImportStorage.BULK_IMPORT_USER_STATUS.FAILED).get("count").getAsInt();
            int usersInCore = loadUsersCount(main).get("count").getAsInt();
            assertEquals(NUMBER_OF_USERS_TO_UPLOAD, usersInCore + failedImportedUsersNumber);
            assertEquals(NUMBER_OF_USERS_TO_UPLOAD, usersInCore);
        }

    }


    @Test
    public void testCoreRestartMidImportShouldResultInSuccessfulImport() throws Exception {
        String[] args = { "../" };

        // set processing thread number
        Utils.setValueInConfig("bulk_migration_parallelism", "8");
        Utils.setValueInConfig("bulk_migration_batch_size", "1000");
        Utils.setValueInConfig("log_level", "DEBUG");

        // Start with startProcess=false to avoid race condition with feature flag setup
        TestingProcessManager.TestingProcess process = TestingProcessManager.startIsolatedProcess(args, false);
        Main main = process.getProcess();
        // Set feature flags BEFORE starting the process to avoid race condition
        setFeatureFlags(main, new EE_FEATURES[] {
                EE_FEATURES.ACCOUNT_LINKING, EE_FEATURES.MULTI_TENANCY, EE_FEATURES.MFA});
        // We are setting a non-zero initial wait for tests to avoid race condition with the beforeTest process that deletes data in the storage layer
        CronTaskTest.getInstance(main).setInitialWaitTimeInSeconds(ProcessBulkImportUsers.RESOURCE_KEY, 5);
        CronTaskTest.getInstance(main).setIntervalInSeconds(ProcessBulkImportUsers.RESOURCE_KEY, 60);

        // Now start the process after feature flags are set
        process.startProcess();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        Cronjobs.addCronjob(main, (ProcessBulkImportUsers) main.getResourceDistributor().getResource(new TenantIdentifier(null, null, null), ProcessBulkImportUsers.RESOURCE_KEY));

        int NUMBER_OF_USERS_TO_UPLOAD = 10000;

        if (StorageLayer.getBaseStorage(main).getType() != STORAGE_TYPE.SQL || StorageLayer.isInMemDb(main)) {
            return;
        }

        // Create user roles before inserting bulk users
        {
            UserRoles.createNewRoleOrModifyItsPermissions(main, "role1", null);
            UserRoles.createNewRoleOrModifyItsPermissions(main, "role2", null);
        }

        // upload a bunch of users through the API
        {
            for (int i = 0; i < (NUMBER_OF_USERS_TO_UPLOAD / 1000); i++) {
                JsonObject request = generateUsersJson(1000, i * 1000); // API allows 10k users upload at once
                JsonObject response = uploadBulkImportUsersJson(main, request);
                assertEquals("OK", response.get("status").getAsString());
            }

        }

        long processingStarted = System.currentTimeMillis();
        boolean restartHappened = false;
        // wait for the cron job to process them
        // periodically check the remaining unprocessed users
        // Note1: the cronjob starts the processing automatically
        // Note2: the successfully processed users get deleted from the bulk_import_users table
        {
            long count = NUMBER_OF_USERS_TO_UPLOAD;
            while(true) {
                try {
                    JsonObject response = loadBulkImportUsersCountWithStatus(main, null);
                    assertEquals("OK", response.get("status").getAsString());
                    count = response.get("count").getAsLong();
                    int newUsersNumber = loadBulkImportUsersCountWithStatus(main,
                            BulkImportStorage.BULK_IMPORT_USER_STATUS.NEW).get("count").getAsInt();
                    int processingUsersNumber = loadBulkImportUsersCountWithStatus(main,
                            BulkImportStorage.BULK_IMPORT_USER_STATUS.PROCESSING).get("count").getAsInt();
                    int failedUsersNumber = loadBulkImportUsersCountWithStatus(main,
                            BulkImportStorage.BULK_IMPORT_USER_STATUS.FAILED).get("count").getAsInt();
                    count = newUsersNumber + processingUsersNumber;
                    System.out.println("Remaining users: " + count);

                    if (count == 0) {
                        break;
                    }
                    if((System.currentTimeMillis() - processingStarted > 10000) && !restartHappened) {
                        System.out.println("Killing core");
                        process.kill(false);
                        Utils.setValueInConfig("bulk_migration_parallelism", "14");
                        Utils.setValueInConfig("bulk_migration_batch_size", "4000");
                        System.out.println("Started new core");
                        // Start with startProcess=false to avoid race condition with feature flag setup
                        process = TestingProcessManager.startIsolatedProcess(args, false);
                        main = process.getProcess();
                        // Set feature flags BEFORE starting the process to avoid race condition
                        setFeatureFlags(main, new EE_FEATURES[] {
                                EE_FEATURES.ACCOUNT_LINKING, EE_FEATURES.MULTI_TENANCY, EE_FEATURES.MFA});
                        // We are setting a non-zero initial wait for tests to avoid race condition with the beforeTest process that deletes data in the storage layer
                        CronTaskTest.getInstance(main).setInitialWaitTimeInSeconds(ProcessBulkImportUsers.RESOURCE_KEY, 5);
                        CronTaskTest.getInstance(main).setIntervalInSeconds(ProcessBulkImportUsers.RESOURCE_KEY, 60);

                        // Now start the process after feature flags are set
                        process.startProcess();
                        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

                        Cronjobs.addCronjob(main, (ProcessBulkImportUsers) main.getResourceDistributor().getResource(new TenantIdentifier(null, null, null), ProcessBulkImportUsers.RESOURCE_KEY));
                        restartHappened = true;
                    }
                } catch (Exception e) {
                    if(e instanceof SocketTimeoutException)  {
                        //ignore
                    } else {
                        throw e;
                    }
                }
                Thread.sleep(1000);
            }
        }

        long processingFinished = System.currentTimeMillis();
        System.out.println("Processed " + NUMBER_OF_USERS_TO_UPLOAD + " users in " + (processingFinished - processingStarted) / 1000
                + " seconds ( or " + (processingFinished - processingStarted) / 60000 + " minutes)");

        // after processing finished, make sure every user got processed correctly
        {
            int failedImportedUsersNumber = loadBulkImportUsersCountWithStatus(main, BulkImportStorage.BULK_IMPORT_USER_STATUS.FAILED).get("count").getAsInt();
            int usersInCore = loadUsersCount(main).get("count").getAsInt();
            assertEquals(NUMBER_OF_USERS_TO_UPLOAD, usersInCore + failedImportedUsersNumber);
            assertEquals(NUMBER_OF_USERS_TO_UPLOAD, usersInCore);
        }

    }


    @Test
    public void testBatchWithOneUser() throws Exception {
        Main main = startCronProcess("14");

        int NUMBER_OF_USERS_TO_UPLOAD = 1;

        if (StorageLayer.getBaseStorage(main).getType() != STORAGE_TYPE.SQL || StorageLayer.isInMemDb(main)) {
            return;
        }

        // Create user roles before inserting bulk users
        UserRoles.createNewRoleOrModifyItsPermissions(main, "role1", null);
        UserRoles.createNewRoleOrModifyItsPermissions(main, "role2", null);

        // upload a bunch of users through the API
        JsonObject usersJson = generateUsersJson(NUMBER_OF_USERS_TO_UPLOAD, 0);
        JsonObject response = uploadBulkImportUsersJson(main, usersJson);
        assertEquals("OK", response.get("status").getAsString());

        // wait for the cron job to process them
        // periodically check the remaining unprocessed users
        // Note1: the cronjob starts the processing automatically
        // Note2: the successfully processed users get deleted from the bulk_import_users table

        long count = NUMBER_OF_USERS_TO_UPLOAD;
        int failedUsersNumber = 0;
        while (true) {
            response = loadBulkImportUsersCountWithStatus(main, null);
            assertEquals("OK", response.get("status").getAsString());
            count = response.get("count").getAsLong();
            int newUsersNumber = loadBulkImportUsersCountWithStatus(main,
                    BulkImportStorage.BULK_IMPORT_USER_STATUS.NEW).get("count").getAsInt();
            failedUsersNumber = loadBulkImportUsersCountWithStatus(main,
                    BulkImportStorage.BULK_IMPORT_USER_STATUS.FAILED).get("count").getAsInt();
            int processingUsersNumber = loadBulkImportUsersCountWithStatus(main,
                    BulkImportStorage.BULK_IMPORT_USER_STATUS.PROCESSING).get("count").getAsInt();

            count = newUsersNumber + processingUsersNumber;
            if(count == 0) {
                break;
            }
            Thread.sleep(5000); // 5 seconds
        }

        //print failed users
        JsonObject failedUsersLs = loadBulkImportUsersWithStatus(main,
                BulkImportStorage.BULK_IMPORT_USER_STATUS.FAILED);

        // after processing finished, make sure every user got processed correctly
        int failedImportedUsersNumber = loadBulkImportUsersCountWithStatus(main,
                BulkImportStorage.BULK_IMPORT_USER_STATUS.FAILED).get("count").getAsInt();
        int usersInCore = loadUsersCount(main).get("count").getAsInt();
        assertEquals(NUMBER_OF_USERS_TO_UPLOAD , usersInCore + failedImportedUsersNumber);
        assertEquals(0, failedImportedUsersNumber);


    }

    @Test
    public void testBatchWithDuplicate() throws Exception {
        Main main = startCronProcess("14");

        int NUMBER_OF_USERS_TO_UPLOAD = 2;

        if (StorageLayer.getBaseStorage(main).getType() != STORAGE_TYPE.SQL || StorageLayer.isInMemDb(main)) {
            return;
        }

        // Create user roles before inserting bulk users
        UserRoles.createNewRoleOrModifyItsPermissions(main, "role1", null);
        UserRoles.createNewRoleOrModifyItsPermissions(main, "role2", null);

        // upload a bunch of users through the API
        JsonObject usersJson = generateUsersJson(NUMBER_OF_USERS_TO_UPLOAD, 0);

        usersJson.get("users").getAsJsonArray().add(generateUsersJson(1, 0).get("users").getAsJsonArray().get(0).getAsJsonObject());
        usersJson.get("users").getAsJsonArray().add(generateUsersJson(1, 1).get("users").getAsJsonArray().get(0).getAsJsonObject());

        JsonObject response = uploadBulkImportUsersJson(main, usersJson);
        assertEquals("OK", response.get("status").getAsString());

        // wait for the cron job to process them
        // periodically check the remaining unprocessed users
        // Note1: the cronjob starts the processing automatically
        // Note2: the successfully processed users get deleted from the bulk_import_users table

        long count = NUMBER_OF_USERS_TO_UPLOAD;
        int failedUsersNumber = 0;
        while (true) {
            response = loadBulkImportUsersCountWithStatus(main, null);
            assertEquals("OK", response.get("status").getAsString());
            count = response.get("count").getAsLong();
            int newUsersNumber = loadBulkImportUsersCountWithStatus(main,
                    BulkImportStorage.BULK_IMPORT_USER_STATUS.NEW).get("count").getAsInt();
            failedUsersNumber = loadBulkImportUsersCountWithStatus(main,
                    BulkImportStorage.BULK_IMPORT_USER_STATUS.FAILED).get("count").getAsInt();
            int processingUsersNumber = loadBulkImportUsersCountWithStatus(main,
                    BulkImportStorage.BULK_IMPORT_USER_STATUS.PROCESSING).get("count").getAsInt();

            count = newUsersNumber + processingUsersNumber;
            if(count == 0) {
                break;
            }
            Thread.sleep(5000); // 5 seconds
        }

        //print failed users
        JsonObject failedUsersLs = loadBulkImportUsersWithStatus(main,
                BulkImportStorage.BULK_IMPORT_USER_STATUS.FAILED);

        // after processing finished, make sure every user got processed correctly
        int failedImportedUsersNumber = loadBulkImportUsersCountWithStatus(main,
                BulkImportStorage.BULK_IMPORT_USER_STATUS.FAILED).get("count").getAsInt();
        int usersInCore = loadUsersCount(main).get("count").getAsInt();
        assertEquals(NUMBER_OF_USERS_TO_UPLOAD + 2, usersInCore + failedImportedUsersNumber);
        assertEquals(2, failedImportedUsersNumber);


        for(JsonElement userJson : failedUsersLs.get("users").getAsJsonArray()) {
            String errorMessage = userJson.getAsJsonObject().get("errorMessage").getAsString();
            assertTrue(errorMessage.startsWith("E003:"));
        }

    }

    @Test
    public void testBatchWithDuplicateUserIdMappingWithInputValidation() throws Exception {
        Main main = startCronProcess("14");

        int NUMBER_OF_USERS_TO_UPLOAD = 20;

        if (StorageLayer.getBaseStorage(main).getType() != STORAGE_TYPE.SQL || StorageLayer.isInMemDb(main)) {
            return;
        }

        // Create user roles before inserting bulk users
        UserRoles.createNewRoleOrModifyItsPermissions(main, "role1", null);
        UserRoles.createNewRoleOrModifyItsPermissions(main, "role2", null);

        // upload a bunch of users through the API
        JsonObject usersJson = generateUsersJson(NUMBER_OF_USERS_TO_UPLOAD, 0);

        //set the first and last users' externalId to the same value
        usersJson.get("users").getAsJsonArray().get(0).getAsJsonObject().addProperty("externalUserId",
                "some-text-external-id");
        usersJson.get("users").getAsJsonArray().get(19).getAsJsonObject().addProperty("externalUserId",
                "some-text-external-id");

        try {
            JsonObject response = uploadBulkImportUsersJson(main, usersJson);
        } catch (HttpResponseException expected) {
            assertEquals(400, expected.statusCode);
            assertEquals("Http error. Status Code: 400. Message: {\"error\":\"Data has missing or invalid fields. Please check the users field for more details.\",\"users\":[{\"index\":19,\"errors\":[\"externalUserId some-text-external-id is not unique. It is already used by another user.\"]}]}",
                    expected.getMessage());
        }
    }

    @Test
    public void testBatchWithInvalidInput() throws Exception {
        Main main = startCronProcess("14");

        int NUMBER_OF_USERS_TO_UPLOAD = 2;

        if (StorageLayer.getBaseStorage(main).getType() != STORAGE_TYPE.SQL || StorageLayer.isInMemDb(main)) {
            return;
        }

        // Create user roles before inserting bulk users
        UserRoles.createNewRoleOrModifyItsPermissions(main, "role1", null);
        UserRoles.createNewRoleOrModifyItsPermissions(main, "role2", null);

        // upload a bunch of users through the API
        JsonObject usersJson = generateUsersJson(NUMBER_OF_USERS_TO_UPLOAD, 0);

        usersJson.get("users").getAsJsonArray().get(0).getAsJsonObject().addProperty("externalUserId",
                Boolean.FALSE); // invalid, should be string
        try {
            JsonObject response = uploadBulkImportUsersJson(main, usersJson);
        } catch (HttpResponseException exception) {
            assertEquals(400, exception.statusCode);
            assertEquals("Http error. Status Code: 400. Message: {\"error\":\"Data has missing or invalid " +
                    "fields. Please check the users field for more details.\",\"users\":[{\"index\":0,\"errors\":" +
                    "[\"externalUserId should be of type string.\"]}]}", exception.getMessage());
        }
    }

    @Test
    public void testBatchWithMissingRole() throws Exception {
        Main main = startCronProcess("14");

        int NUMBER_OF_USERS_TO_UPLOAD = 2;

        if (StorageLayer.getBaseStorage(main).getType() != STORAGE_TYPE.SQL || StorageLayer.isInMemDb(main)) {
            return;
        }

        // Creating only one user role before inserting bulk users
        UserRoles.createNewRoleOrModifyItsPermissions(main, "role1", null);

        // upload a bunch of users through the API
        JsonObject usersJson = generateUsersJson(NUMBER_OF_USERS_TO_UPLOAD, 0);

        try {
            JsonObject response = uploadBulkImportUsersJson(main, usersJson);
        } catch (HttpResponseException exception) {
            assertEquals(400, exception.statusCode);
            assertEquals(400, exception.statusCode);
            assertEquals("Http error. Status Code: 400. Message: {\"error\":\"Data has missing or " +
                    "invalid fields. Please check the users field for more details.\",\"users\":[{\"index\":0,\"errors\"" +
                    ":[\"Role role2 does not exist.\"]},{\"index\":1,\"errors\":[\"Role role2 does not exist.\"]}]}",
                    exception.getMessage());
        }
    }

    @Test
    public void testBatchWithOnlyOneWithDuplicate() throws Exception {
        Main main = startCronProcess("8", 10);

        int NUMBER_OF_USERS_TO_UPLOAD = 9;

        if (StorageLayer.getBaseStorage(main).getType() != STORAGE_TYPE.SQL || StorageLayer.isInMemDb(main)) {
            return;
        }

        //create tenant t1
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

        // Create user roles before inserting bulk users
        UserRoles.createNewRoleOrModifyItsPermissions(main, "role1", null);
        UserRoles.createNewRoleOrModifyItsPermissions(main, "role2", null);

        // upload a bunch of users through the API
        JsonObject usersJson = generateUsersJson(NUMBER_OF_USERS_TO_UPLOAD, 0);

        usersJson.get("users").getAsJsonArray().add(generateUsersJson(1, 0).get("users").getAsJsonArray().get(0).getAsJsonObject());

        JsonObject response = uploadBulkImportUsersJson(main, usersJson);
        assertEquals("OK", response.get("status").getAsString());

        // wait for the cron job to process them
        // periodically check the remaining unprocessed users
        // Note1: the cronjob starts the processing automatically
        // Note2: the successfully processed users get deleted from the bulk_import_users table

        long count = NUMBER_OF_USERS_TO_UPLOAD;
        int failedUsersNumber = 0;
        while (true) {
            response = loadBulkImportUsersCountWithStatus(main, null);
            assertEquals("OK", response.get("status").getAsString());
            int newUsersNumber = loadBulkImportUsersCountWithStatus(main,
                    BulkImportStorage.BULK_IMPORT_USER_STATUS.NEW).get("count").getAsInt();
            int processingUsersNumber = loadBulkImportUsersCountWithStatus(main,
                    BulkImportStorage.BULK_IMPORT_USER_STATUS.PROCESSING).get("count").getAsInt();

            count = newUsersNumber + processingUsersNumber;
            if(count == 0) {
                break;
            }
            Thread.sleep(5000);
        }

        //print failed users
        JsonObject failedUsersLs = loadBulkImportUsersWithStatus(main,
                BulkImportStorage.BULK_IMPORT_USER_STATUS.FAILED);

        // after processing finished, make sure every user got processed correctly
        int failedImportedUsersNumber = loadBulkImportUsersCountWithStatus(main,
                BulkImportStorage.BULK_IMPORT_USER_STATUS.FAILED).get("count").getAsInt();
        int usersInCore = loadUsersCount(main).get("count").getAsInt();
        assertEquals(NUMBER_OF_USERS_TO_UPLOAD + 1, usersInCore + failedImportedUsersNumber);
        assertEquals(1, failedImportedUsersNumber);


        for(JsonElement userJson : failedUsersLs.get("users").getAsJsonArray()) {
            String errorMessage = userJson.getAsJsonObject().get("errorMessage").getAsString();
            assertTrue(errorMessage.startsWith("E003:"));
        }

    }

    @Test
    public void testBatchWithOneThreadWorks() throws Exception {
        Main main = startCronProcess("1");

        int NUMBER_OF_USERS_TO_UPLOAD = 5;

        if (StorageLayer.getBaseStorage(main).getType() != STORAGE_TYPE.SQL || StorageLayer.isInMemDb(main)) {
            return;
        }

        // Create user roles before inserting bulk users
        UserRoles.createNewRoleOrModifyItsPermissions(main, "role1", null);
        UserRoles.createNewRoleOrModifyItsPermissions(main, "role2", null);

        // upload a bunch of users through the API
        JsonObject usersJson = generateUsersJson(NUMBER_OF_USERS_TO_UPLOAD, 0);

        JsonObject response = uploadBulkImportUsersJson(main, usersJson);
        assertEquals("OK", response.get("status").getAsString());

        // wait for the cron job to process them
        // periodically check the remaining unprocessed users
        // Note1: the cronjob starts the processing automatically
        // Note2: the successfully processed users get deleted from the bulk_import_users table

        long count = NUMBER_OF_USERS_TO_UPLOAD;
        while (true) {
            response = loadBulkImportUsersCountWithStatus(main, null);
            assertEquals("OK", response.get("status").getAsString());
            int newUsersNumber = loadBulkImportUsersCountWithStatus(main,
                    BulkImportStorage.BULK_IMPORT_USER_STATUS.NEW).get("count").getAsInt();
            int processingUsersNumber = loadBulkImportUsersCountWithStatus(main,
                    BulkImportStorage.BULK_IMPORT_USER_STATUS.PROCESSING).get("count").getAsInt();

            count = newUsersNumber + processingUsersNumber;
            if(count == 0) {
                break;
            }
            Thread.sleep(5000); // 5 seconds
        }

        // after processing finished, make sure every user got processed correctly
        int failedImportedUsersNumber = loadBulkImportUsersCountWithStatus(main,
                BulkImportStorage.BULK_IMPORT_USER_STATUS.FAILED).get("count").getAsInt();
        int usersInCore = loadUsersCount(main).get("count").getAsInt();
        assertEquals(NUMBER_OF_USERS_TO_UPLOAD, usersInCore);
        assertEquals(0, failedImportedUsersNumber);
    }

    @Test
    public void testFirstLazyImportAfterBulkImport() throws Exception {
        Main main = startCronProcess("14", 10);


        int NUMBER_OF_USERS_TO_UPLOAD = 100;

        if (StorageLayer.getBaseStorage(main).getType() != STORAGE_TYPE.SQL || StorageLayer.isInMemDb(main)) {
            return;
        }

        // Create user roles before inserting bulk users
        {
            UserRoles.createNewRoleOrModifyItsPermissions(main, "role1", null);
            UserRoles.createNewRoleOrModifyItsPermissions(main, "role2", null);
        }

        // create users
        JsonObject allUsersJson = generateUsersJson(NUMBER_OF_USERS_TO_UPLOAD, 0);

        // lazy import most of the users
        int successfully_lazy_imported = 0;
        for (int i = 0; i < allUsersJson.get("users").getAsJsonArray().size() / 10 * 9; i++) {
            JsonObject userToImportLazy = allUsersJson.get("users").getAsJsonArray().get(i).getAsJsonObject();
            JsonObject lazyImportResponse = lazyImportUser(main, userToImportLazy);
            assertEquals("OK", lazyImportResponse.get("status").getAsString());
            assertNotNull(lazyImportResponse.get("user"));
            successfully_lazy_imported++;
        }

        // bulk import all of the users
        {
            JsonObject bulkUploadResponse = uploadBulkImportUsersJson(main, allUsersJson);
            assertEquals("OK", bulkUploadResponse.get("status").getAsString());
        }

        // wait for the cron job to process them
        // periodically check the remaining unprocessed users
        // Note1: the cronjob starts the processing automatically
        // Note2: the successfully processed users get deleted from the bulk_import_users table
        {
            long count = NUMBER_OF_USERS_TO_UPLOAD;
            while(count != 0) {
                JsonObject response = loadBulkImportUsersCountWithStatus(main, null);
                assertEquals("OK", response.get("status").getAsString());
                int newUsersNumber = loadBulkImportUsersCountWithStatus(main, BulkImportStorage.BULK_IMPORT_USER_STATUS.NEW).get("count").getAsInt();
                int processingUsersNumber = loadBulkImportUsersCountWithStatus(main, BulkImportStorage.BULK_IMPORT_USER_STATUS.PROCESSING).get("count").getAsInt();

                count = newUsersNumber + processingUsersNumber;

                Thread.sleep(60000); // one minute
            }
        }


        // expect: lazy imported users are already there, duplicate.. errors
        // expect: not lazy imported users are imported successfully
        {
            int failedImportedUsersNumber = loadBulkImportUsersCountWithStatus(main, BulkImportStorage.BULK_IMPORT_USER_STATUS.FAILED).get("count").getAsInt();
            assertEquals(successfully_lazy_imported, failedImportedUsersNumber);
            int usersInCore = loadUsersCount(main).get("count").getAsInt();
            assertEquals(NUMBER_OF_USERS_TO_UPLOAD, usersInCore); // lazy + bulk = all users
        }

        JsonObject failedUsers = loadBulkImportUsersWithStatus(main, BulkImportStorage.BULK_IMPORT_USER_STATUS.FAILED);
        JsonArray faileds = failedUsers.getAsJsonArray("users");
        for (JsonElement failedUser : faileds) {
            String errorMessage = failedUser.getAsJsonObject().get("errorMessage").getAsString();
            assertTrue(errorMessage.startsWith("E003:") || errorMessage.startsWith("E005:")
                    || errorMessage.startsWith("E006:") || errorMessage.startsWith("E007:")); // duplicate email, phone, etc errors
        }

    }

    @Test
    public void testLazyImport() throws Exception {
        Main main = startCronProcess("1");

        int NUMBER_OF_USERS_TO_UPLOAD = 100;

        if (StorageLayer.getBaseStorage(main).getType() != STORAGE_TYPE.SQL || StorageLayer.isInMemDb(main)) {
            return;
        }

        // Create user roles before inserting bulk users
        {
            UserRoles.createNewRoleOrModifyItsPermissions(main, "role1", null);
            UserRoles.createNewRoleOrModifyItsPermissions(main, "role2", null);
        }

        // create users
        JsonObject allUsersJson = generateUsersJson(NUMBER_OF_USERS_TO_UPLOAD, 0);

        // lazy import most of the users
        int successfully_lazy_imported = 0;
        for (int i = 0; i < allUsersJson.get("users").getAsJsonArray().size(); i++) {
            JsonObject userToImportLazy = allUsersJson.get("users").getAsJsonArray().get(i).getAsJsonObject();
            JsonObject lazyImportResponse = lazyImportUser(main, userToImportLazy);
            assertEquals("OK", lazyImportResponse.get("status").getAsString());
            assertNotNull(lazyImportResponse.get("user"));
            successfully_lazy_imported++;
        }

        // expect: lazy imported users are already there, duplicate.. errors
        // expect: not lazy imported users are imported successfully
        {
            assertEquals(NUMBER_OF_USERS_TO_UPLOAD, successfully_lazy_imported );
            int usersInCore = loadUsersCount(main).get("count").getAsInt();
            assertEquals(NUMBER_OF_USERS_TO_UPLOAD, usersInCore);
        }
    }

    @Test
    public void testLazyImportUnknownRecipeLoginMethod() throws Exception {
        Main main = startCronProcess("1");

        if (StorageLayer.getBaseStorage(main).getType() != STORAGE_TYPE.SQL || StorageLayer.isInMemDb(main)) {
            return;
        }

        // Create user roles before inserting bulk users
        {
            UserRoles.createNewRoleOrModifyItsPermissions(main, "role1", null);
            UserRoles.createNewRoleOrModifyItsPermissions(main, "role2", null);
        }

        // create users
        JsonObject allUsersJson = generateUsersJson(1, 0);
        allUsersJson.get("users").getAsJsonArray().get(0).getAsJsonObject().get("loginMethods")
                .getAsJsonArray().get(0).getAsJsonObject().addProperty("recipeId", "not-existing-recipe");

        JsonObject userToImportLazy = allUsersJson.get("users").getAsJsonArray().get(0).getAsJsonObject();
        try {
            JsonObject lazyImportResponse = lazyImportUser(main, userToImportLazy);
        } catch (HttpResponseException expected) {
            assertEquals(400, expected.statusCode);
            assertNotNull(expected.getMessage());
            assertEquals("Http error. Status Code: 400. Message: {\"errors\":[\"Invalid recipeId for loginMethod. Pass one of emailpassword, thirdparty or, passwordless!\"]}",
                    expected.getMessage());
        }
    }

    @Test
    public void testLazyImportDuplicatesFail() throws Exception {
        Main main = startCronProcess("1");

        if (StorageLayer.getBaseStorage(main).getType() != STORAGE_TYPE.SQL || StorageLayer.isInMemDb(main)) {
            return;
        }

        // Create user roles before inserting bulk users
        {
            UserRoles.createNewRoleOrModifyItsPermissions(main, "role1", null);
            UserRoles.createNewRoleOrModifyItsPermissions(main, "role2", null);
        }

        // create users
        JsonObject allUsersJson = generateUsersJson(1, 0);

        JsonObject userToImportLazy = allUsersJson.get("users").getAsJsonArray().get(0).getAsJsonObject();
        JsonObject lazyImportResponse = lazyImportUser(main, userToImportLazy);
        assertEquals("OK", lazyImportResponse.get("status").getAsString());
        assertNotNull(lazyImportResponse.get("user"));

        int usersInCore = loadUsersCount(main).get("count").getAsInt();
        assertEquals(1, usersInCore);

        JsonObject userToImportLazyAgain = allUsersJson.get("users").getAsJsonArray().get(0).getAsJsonObject();
        try {
            JsonObject lazyImportResponseTwo = lazyImportUser(main, userToImportLazyAgain);
        } catch (HttpResponseException expected) {
            assertEquals(400, expected.statusCode);
        }
    }

    private static JsonObject lazyImportUser(Main main, JsonObject user)
            throws HttpResponseException, IOException {
        return HttpRequestForTesting.sendJsonPOSTRequest(main, "",
                "http://localhost:3567/bulk-import/import",
                user, 100000, 100000, null, Utils.getCdiVersionStringLatestForTests(), null);
    }

    private static JsonObject loadBulkImportUsersCountWithStatus(Main main, BulkImportStorage.BULK_IMPORT_USER_STATUS status)
            throws HttpResponseException, IOException {
        Map<String, String> params = new HashMap<>();
        if(status!= null) {
            params.put("status", status.name());
        }
        return HttpRequestForTesting.sendGETRequest(main, "",
                "http://localhost:3567/bulk-import/users/count",
                params, 10000, 10000, null, Utils.getCdiVersionStringLatestForTests(), null);
    }

    private static JsonObject loadBulkImportUsersWithStatus(Main main, BulkImportStorage.BULK_IMPORT_USER_STATUS status)
            throws HttpResponseException, IOException {
        Map<String, String> params = new HashMap<>();
        if(status!= null) {
            params.put("status", status.name());
        }
        return HttpRequestForTesting.sendGETRequest(main, "",
                "http://localhost:3567/bulk-import/users",
                params, 10000, 10000, null, Utils.getCdiVersionStringLatestForTests(), null);
    }

    private static JsonObject loadUsersCount(Main main) throws HttpResponseException, IOException {
        Map<String, String> params = new HashMap<>();

        return HttpRequestForTesting.sendGETRequest(main, "",
                "http://localhost:3567/users/count",
                params, 10000, 10000, null, Utils.getCdiVersionStringLatestForTests(), null);
    }

    private static JsonObject generateUsersJson(int numberOfUsers, int startIndex) {
        JsonObject userJsonObject = new JsonObject();
        JsonParser parser = new JsonParser();

        JsonArray usersArray = new JsonArray();
        for (int i = 0; i < numberOfUsers; i++) {
            JsonObject user = new JsonObject();

            user.addProperty("externalUserId", "external_" + UUID.randomUUID().toString());
            user.add("userMetadata", parser.parse("{\"key1\":"+ UUID.randomUUID().toString() + ",\"key2\":{\"key3\":\"value3\"}}"));
            user.add("userRoles", parser.parse(
                    "[{\"role\":\"role1\", \"tenantIds\": [\"public\"]},{\"role\":\"role2\", \"tenantIds\": [\"public\"]}]"));
            user.add("totpDevices", parser.parse("[{\"secretKey\":\"secretKey\",\"deviceName\":\"deviceName\"}]"));

            //JsonArray tenanatIds = parser.parse("[\"public\", \"t1\"]").getAsJsonArray();
            JsonArray tenanatIds = parser.parse("[\"public\"]").getAsJsonArray();
            String email = " johndoe+" + (i + startIndex) + "@gmail.com ";

            Random random = new Random();

            JsonArray loginMethodsArray = new JsonArray();
            //if(random.nextInt(2) == 0){
                loginMethodsArray.add(createEmailLoginMethod(email, tenanatIds));
            //}
            if(random.nextInt(2) == 0){
                loginMethodsArray.add(createThirdPartyLoginMethod(email, tenanatIds));
            }
            if(random.nextInt(2) == 0){
                loginMethodsArray.add(createPasswordlessLoginMethod(email, tenanatIds, "+910000" + (startIndex + i)));
            }
            if(loginMethodsArray.size() == 0) {
                int methodNumber = random.nextInt(3);
                switch (methodNumber) {
                    case 0:
                        loginMethodsArray.add(createEmailLoginMethod(email, tenanatIds));
                        break;
                    case 1:
                        loginMethodsArray.add(createThirdPartyLoginMethod(email, tenanatIds));
                        break;
                    case 2:
                        loginMethodsArray.add(createPasswordlessLoginMethod(email, tenanatIds, "+911000" + (startIndex + i)));
                        break;
                }
            }
            user.add("loginMethods", loginMethodsArray);

            usersArray.add(user);
        }

        userJsonObject.add("users", usersArray);
        return userJsonObject;
    }

    private static JsonObject createEmailLoginMethod(String email, JsonArray tenantIds) {
        JsonObject loginMethod = new JsonObject();
        loginMethod.add("tenantIds", tenantIds);
        loginMethod.addProperty("email", email);
        loginMethod.addProperty("recipeId", "emailpassword");
        loginMethod.addProperty("passwordHash",
                "$argon2d$v=19$m=12,t=3,p=1$aGI4enNvMmd0Zm0wMDAwMA$r6p7qbr6HD+8CD7sBi4HVw");
        loginMethod.addProperty("hashingAlgorithm", "argon2");
        loginMethod.addProperty("isVerified", true);
        loginMethod.addProperty("isPrimary", true);
        loginMethod.addProperty("timeJoinedInMSSinceEpoch", 0);
        return loginMethod;
    }

    private static JsonObject createThirdPartyLoginMethod(String email, JsonArray tenantIds) {
        JsonObject loginMethod = new JsonObject();
        loginMethod.add("tenantIds", tenantIds);
        loginMethod.addProperty("recipeId", "thirdparty");
        loginMethod.addProperty("email", email);
        loginMethod.addProperty("thirdPartyId", "google");
        loginMethod.addProperty("thirdPartyUserId", String.valueOf(email.hashCode()));
        loginMethod.addProperty("isVerified", true);
        loginMethod.addProperty("isPrimary", false);
        loginMethod.addProperty("timeJoinedInMSSinceEpoch", 0);
        return loginMethod;
    }

    private static JsonObject createPasswordlessLoginMethod(String email, JsonArray tenantIds, String phoneNumber) {
        JsonObject loginMethod = new JsonObject();
        loginMethod.add("tenantIds", tenantIds);
        loginMethod.addProperty("email", email);
        loginMethod.addProperty("recipeId", "passwordless");
        loginMethod.addProperty("phoneNumber", phoneNumber);
        loginMethod.addProperty("isVerified", true);
        loginMethod.addProperty("isPrimary", false);
        loginMethod.addProperty("timeJoinedInMSSinceEpoch", 0);
        return loginMethod;
    }

    private void setFeatureFlags(Main main, EE_FEATURES[] features) {
        FeatureFlagTestContent.getInstance(main).setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, features);
    }


    private static JsonObject uploadBulkImportUsersJson(Main main, JsonObject request) throws IOException, HttpResponseException {
        return HttpRequestForTesting.sendJsonPOSTRequest(main, "",
                "http://localhost:3567/bulk-import/users",
                request, 1000, 10000, null, Utils.getCdiVersionStringLatestForTests(), null);
    }

    @NotNull
    private Main startCronProcess(String parallelism) throws IOException, InterruptedException, TenantOrAppNotFoundException {
        return startCronProcess(parallelism, 5 * 60);
    }


    @NotNull
    private Main startCronProcess(String parallelism, int intervalInSeconds) throws IOException, InterruptedException, TenantOrAppNotFoundException {
        String[] args = { "../" };

        // set processing thread number
        Utils.setValueInConfig("bulk_migration_parallelism", parallelism);
        //Utils.setValueInConfig("bulk_migration_batch_size", "1000");
        Utils.setValueInConfig("log_level", "DEBUG");

        // Start with startProcess=false to avoid race condition with feature flag setup
        TestingProcessManager.TestingProcess process = TestingProcessManager.startIsolatedProcess(args, false);
        Main main = process.getProcess();
        // Set feature flags BEFORE starting the process to avoid race condition
        setFeatureFlags(main, new EE_FEATURES[] {
                EE_FEATURES.ACCOUNT_LINKING, EE_FEATURES.MULTI_TENANCY, EE_FEATURES.MFA});
        // We are setting a non-zero initial wait for tests to avoid race condition with the beforeTest process that deletes data in the storage layer
        CronTaskTest.getInstance(main).setInitialWaitTimeInSeconds(ProcessBulkImportUsers.RESOURCE_KEY, 5);
        CronTaskTest.getInstance(main).setIntervalInSeconds(ProcessBulkImportUsers.RESOURCE_KEY, intervalInSeconds);

        // Now start the process after feature flags are set
        process.startProcess();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        Cronjobs.addCronjob(main, (ProcessBulkImportUsers) main.getResourceDistributor().getResource(new TenantIdentifier(null, null, null), ProcessBulkImportUsers.RESOURCE_KEY));
        return main;
    }

}
