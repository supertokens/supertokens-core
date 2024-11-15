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
import io.supertokens.config.Config;
import io.supertokens.featureflag.EE_FEATURES;
import io.supertokens.featureflag.FeatureFlagTestContent;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.pluginInterface.bulkimport.BulkImportStorage;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.test.TestingProcessManager;
import io.supertokens.test.Utils;
import io.supertokens.test.httpRequest.HttpRequestForTesting;
import io.supertokens.test.httpRequest.HttpResponseException;
import io.supertokens.userroles.UserRoles;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import static org.junit.Assert.*;

public class BulkImportFlowTest {

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
    public void testWithOneMillionUsers() throws Exception {
        String[] args = { "../" };

        // set processing thread number
        Utils.setValueInConfig("bulk_migration_parallelism", "14");

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));
        Main main = process.getProcess();

        setFeatureFlags(main, new EE_FEATURES[] {
                        EE_FEATURES.ACCOUNT_LINKING, EE_FEATURES.MULTI_TENANCY, EE_FEATURES.MFA });

        //int NUMBER_OF_USERS_TO_UPLOAD = 1000000; // million
        int NUMBER_OF_USERS_TO_UPLOAD = 10000;
        int parallelism_set_to = Config.getConfig(main).getBulkMigrationParallelism();
        System.out.println("Number of users to be imported with bulk import: " + NUMBER_OF_USERS_TO_UPLOAD);
        System.out.println("Worker threads: " + parallelism_set_to);

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
            for (int i = 0; i < (NUMBER_OF_USERS_TO_UPLOAD / 10000); i++) {
                JsonObject request = generateUsersJson(10000, i * 10000); // API allows 10k users upload at once
                JsonObject response = uploadBulkImportUsersJson(main, request);
                assertEquals("OK", response.get("status").getAsString());
                System.out.println(i + " Uploaded 10k users for bulk import");
            }

        }

        long processingStartedTime = System.currentTimeMillis();

        // Starting the processing cronjob here to be able to measure the runtime
        startBulkImportCronjob(main, 5000);
        System.out.println("CronJob started");

        // wait for the cron job to process them
        // periodically check the remaining unprocessed users
        // Note1: the cronjob starts the processing automatically
        // Note2: the successfully processed users get deleted from the bulk_import_users table
        {
            long count = NUMBER_OF_USERS_TO_UPLOAD;
            while(count != 0) {
                JsonObject response = loadBulkImportUsersCountWithStatus(main, null);
                assertEquals("OK", response.get("status").getAsString());
                count = response.get("count").getAsLong();
                System.out.println("Number of unprocessed users: " + count + "," + response);
                int newUsersNumber = loadBulkImportUsersCountWithStatus(main, BulkImportStorage.BULK_IMPORT_USER_STATUS.NEW).get("count").getAsInt();
                int failedUsersNumber = loadBulkImportUsersCountWithStatus(main, BulkImportStorage.BULK_IMPORT_USER_STATUS.FAILED).get("count").getAsInt();
                int processingUsersNumber = loadBulkImportUsersCountWithStatus(main, BulkImportStorage.BULK_IMPORT_USER_STATUS.PROCESSING).get("count").getAsInt();
                System.out.println("\t stats: ");
                System.out.println("\t\tNEW: \t" + newUsersNumber);
                System.out.println("\t\tFAILED: \t" + failedUsersNumber);
                System.out.println("\t\tPROCESSING: \t" + processingUsersNumber);

                count = newUsersNumber + processingUsersNumber;

                long elapsedSeconds = (System.currentTimeMillis() - processingStartedTime) / 1000;
                System.out.println("Elapsed time: " + elapsedSeconds + " seconds, (" + elapsedSeconds / 3600 + " hours)");
                Thread.sleep(60000); // one minute
            }
        }

        long processingFinishedTime = System.currentTimeMillis();
        System.out.println("Processing took " + (processingFinishedTime - processingStartedTime) / 1000 + " seconds");

        //print failed users
        {
            JsonObject failedUsersLs = loadBulkImportUsersWithStatus(main, BulkImportStorage.BULK_IMPORT_USER_STATUS.FAILED);
            if(failedUsersLs.has("users") ){
                System.out.println(failedUsersLs.get("users"));
            }
        }

        // after processing finished, make sure every user got processed correctly
        {
            int failedImportedUsersNumber = loadBulkImportUsersCountWithStatus(main, BulkImportStorage.BULK_IMPORT_USER_STATUS.FAILED).get("count").getAsInt();
            int usersInCore = loadUsersCount(main).get("count").getAsInt();
            assertEquals(NUMBER_OF_USERS_TO_UPLOAD, usersInCore + failedImportedUsersNumber);
        }

    }

    @Test
    public void testFirstLazyImportAfterBulkImport() throws Exception {
        String[] args = { "../" };

        // set processing thread number
        Utils.setValueInConfig("bulk_migration_parallelism", "14");

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));
        Main main = process.getProcess();

        setFeatureFlags(main, new EE_FEATURES[] {
                EE_FEATURES.ACCOUNT_LINKING, EE_FEATURES.MULTI_TENANCY, EE_FEATURES.MFA });

        int NUMBER_OF_USERS_TO_UPLOAD = 100;
        int parallelism_set_to = Config.getConfig(main).getBulkMigrationParallelism();
        System.out.println("Number of users to be imported with bulk import: " + NUMBER_OF_USERS_TO_UPLOAD);
        System.out.println("Worker threads: " + parallelism_set_to);

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
            System.out.println(i + "th lazy imported");
//            System.out.println("\tOriginal user: " + userToImportLazy);
//            System.out.println("\tResponse user: " + lazyImportResponse.get("user"));
        }

        // bulk import all of the users
        {
            JsonObject bulkUploadResponse = uploadBulkImportUsersJson(main, allUsersJson);
            assertEquals("OK", bulkUploadResponse.get("status").getAsString());
            System.out.println("Bulk uploaded all of the users");
        }

        long processingStartedTime = System.currentTimeMillis();


        // Starting the processing cronjob here to be able to measure the runtime
        startBulkImportCronjob(main, 10000);
        System.out.println("CronJob started");

        // wait for the cron job to process them
        // periodically check the remaining unprocessed users
        // Note1: the cronjob starts the processing automatically
        // Note2: the successfully processed users get deleted from the bulk_import_users table
        {
            long count = NUMBER_OF_USERS_TO_UPLOAD;
            while(count != 0) {
                JsonObject response = loadBulkImportUsersCountWithStatus(main, null);
                assertEquals("OK", response.get("status").getAsString());
                count = response.get("count").getAsLong();
                System.out.println("Number of unprocessed users: " + count + "," + response);
                int newUsersNumber = loadBulkImportUsersCountWithStatus(main, BulkImportStorage.BULK_IMPORT_USER_STATUS.NEW).get("count").getAsInt();
                int failedUsersNumber = loadBulkImportUsersCountWithStatus(main, BulkImportStorage.BULK_IMPORT_USER_STATUS.FAILED).get("count").getAsInt();
                int processingUsersNumber = loadBulkImportUsersCountWithStatus(main, BulkImportStorage.BULK_IMPORT_USER_STATUS.PROCESSING).get("count").getAsInt();
                System.out.println("\t stats: ");
                System.out.println("\t\tNEW: \t" + newUsersNumber);
                System.out.println("\t\tFAILED: \t" + failedUsersNumber);
                System.out.println("\t\tPROCESSING: \t" + processingUsersNumber);

                count = newUsersNumber + processingUsersNumber; // + processingUsersNumber;

                Thread.sleep(60000); // one minute
            }
        }

        long processingFinishedTime = System.currentTimeMillis();
        System.out.println("Processing took " + (processingFinishedTime - processingStartedTime) / 1000 + " seconds");

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
            System.out.println(errorMessage);
        }

        stopBulkImportCronjob(main);
    }

    private static JsonObject lazyImportUser(Main main, JsonObject user)
            throws HttpResponseException, IOException {
        return HttpRequestForTesting.sendJsonPOSTRequest(main, "",
                "http://localhost:3567/bulk-import/import",
                user, 1000, 1000, null, Utils.getCdiVersionStringLatestForTests(), null);
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

            user.addProperty("externalUserId", UUID.randomUUID().toString());
            user.add("userMetadata", parser.parse("{\"key1\":\"value1\",\"key2\":{\"key3\":\"value3\"}}"));
            user.add("userRoles", parser.parse(
                    "[{\"role\":\"role1\", \"tenantIds\": [\"public\"]},{\"role\":\"role2\", \"tenantIds\": [\"public\"]}]"));
            user.add("totpDevices", parser.parse("[{\"secretKey\":\"secretKey\",\"deviceName\":\"deviceName\"}]"));

            JsonArray tenanatIds = parser.parse("[\"public\"]").getAsJsonArray();
            String email = " johndoe+" + (i + startIndex) + "@gmail.com ";

            Random random = new Random();

            JsonArray loginMethodsArray = new JsonArray();
            if(random.nextInt(2) == 0){
                loginMethodsArray.add(createEmailLoginMethod(email, tenanatIds));
            }
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

    private static void startBulkImportCronjob(Main main, int batchSize) throws HttpResponseException, IOException {
        JsonObject request = new JsonObject();
        request.addProperty("batchSize", batchSize);
        request.addProperty("command", "START");
        JsonObject response = HttpRequestForTesting.sendJsonPOSTRequest(main, "",
                "http://localhost:3567/bulk-import/backgroundjob",
                request, 1000, 10000, null, Utils.getCdiVersionStringLatestForTests(), null);
        System.out.println(response);
        assertEquals("ACTIVE", response.get("jobStatus").getAsString());
    }

    private static void stopBulkImportCronjob(Main main) throws HttpResponseException, IOException {
        JsonObject request = new JsonObject();
        request.addProperty("command", "STOP");
        JsonObject response = HttpRequestForTesting.sendJsonPOSTRequest(main, "",
                "http://localhost:3567/bulk-import/backgroundjob",
                request, 1000, 10000, null, Utils.getCdiVersionStringLatestForTests(), null);
        System.out.println(response);
        assertEquals("INACTIVE", response.get("jobStatus").getAsString());
    }

    private static JsonObject uploadBulkImportUsersJson(Main main, JsonObject request) throws IOException, HttpResponseException {
        return HttpRequestForTesting.sendJsonPOSTRequest(main, "",
                "http://localhost:3567/bulk-import/users",
                request, 1000, 10000, null, Utils.getCdiVersionStringLatestForTests(), null);
    }

    @Test
    public void writeUsersToFile() throws Exception {
        String[] args = { "../" };

        // set processing thread number
        Utils.setValueInConfig("bulk_migration_parallelism", "14");

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));
        Main main = process.getProcess();

        setFeatureFlags(main, new EE_FEATURES[] {
                EE_FEATURES.ACCOUNT_LINKING, EE_FEATURES.MULTI_TENANCY, EE_FEATURES.MFA });

        int NUMBER_OF_USERS_TO_UPLOAD = 1000000;
        int parallelism_set_to = Config.getConfig(main).getBulkMigrationParallelism();
        System.out.println("Number of users to be imported with bulk import: " + NUMBER_OF_USERS_TO_UPLOAD);
        System.out.println("Worker threads: " + parallelism_set_to);

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
            for (int i = 0; i < (NUMBER_OF_USERS_TO_UPLOAD / 10000); i++) {
                JsonObject request = generateUsersJson(10000, i * 10000); // API allows 10k users upload at once
                FileWriter fileWriter = new FileWriter(new File("/home/prophet/Projects/bulkimport-users-" + i + ".json"));
                fileWriter.write(String.valueOf(request));
                fileWriter.flush();
                fileWriter.close();
            }

        }

        System.out.println("setup done, waiting");
        while(true){
            Thread.sleep(10000);
        }
    }


}
