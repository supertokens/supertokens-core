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

package io.supertokens.test.bulkimport.apis;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.util.HashMap;
import java.util.UUID;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import io.supertokens.ProcessState;
import io.supertokens.featureflag.EE_FEATURES;
import io.supertokens.featureflag.FeatureFlagTestContent;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.test.TestingProcessManager;
import io.supertokens.test.Utils;
import io.supertokens.test.httpRequest.HttpRequestForTesting;

public class AddBulkImportUsersTest {
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

    public String getResponseMessageFromError(String response) {
        return response.substring(response.indexOf("Message: ") + "Message: ".length());
    }

    @Test
    public void shouldThrow400Error() throws Exception {
        String[] args = { "../" };

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));
        
        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        String genericErrMsg = "Data has missing or invalid fields. Please check the users field for more details.";

        // users is required in the json body
        {
            // CASE 1: users field is not present
            try {
                JsonObject request = new JsonObject();
                HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                        "http://localhost:3567/bulk-import/add-users",
                        request, 1000, 1000, null, Utils.getCdiVersionStringLatestForTests(), null);
            } catch (io.supertokens.test.httpRequest.HttpResponseException e) {
                String responseString = getResponseMessageFromError(e.getMessage());
                assertEquals(400, e.statusCode);
                assertEquals(responseString, "Field name 'users' is invalid in JSON input");
            }
            // CASE 2: users field type in incorrect
            try {
                JsonObject request = new JsonParser().parse("{\"users\": \"string\"}").getAsJsonObject();
                HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                        "http://localhost:3567/bulk-import/add-users",
                        request, 1000, 1000, null, Utils.getCdiVersionStringLatestForTests(), null);
            } catch (io.supertokens.test.httpRequest.HttpResponseException e) {
                String responseString = getResponseMessageFromError(e.getMessage());
                assertEquals(400, e.statusCode);
                assertEquals(responseString, "Field name 'users' is invalid in JSON input");
            }
        }
        // loginMethod array is required in the user object
        {
            // CASE 1: loginMethods field is not present
            try {
                JsonObject request = new JsonParser().parse("{\"users\":[{}]}").getAsJsonObject();
                HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                        "http://localhost:3567/bulk-import/add-users",
                        request, 1000, 1000, null, Utils.getCdiVersionStringLatestForTests(), null);
            } catch (io.supertokens.test.httpRequest.HttpResponseException e) {
                String responseString = getResponseMessageFromError(e.getMessage());
                assertEquals(400, e.statusCode);
                assertEquals(responseString, "{\"error\":\"" + genericErrMsg +  "\",\"users\":[{\"index\":0,\"errors\":[\"loginMethods is required.\"]}]}");
            }
            // CASE 2: loginMethods field type in incorrect
            try {
                JsonObject request = new JsonParser().parse("{\"users\":[{\"loginMethods\": \"string\"}]}")
                        .getAsJsonObject();
                HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                        "http://localhost:3567/bulk-import/add-users",
                        request, 1000, 1000, null, Utils.getCdiVersionStringLatestForTests(), null);
            } catch (io.supertokens.test.httpRequest.HttpResponseException e) {
                String responseString = getResponseMessageFromError(e.getMessage());
                assertEquals(400, e.statusCode);
                assertEquals(responseString,
                        "{\"error\":\"" + genericErrMsg +  "\",\"users\":[{\"index\":0,\"errors\":[\"loginMethods should be of type array of object.\"]}]}");
            }
            // CASE 3: loginMethods array is empty
            try {
                JsonObject request = new JsonParser().parse("{\"users\":[{\"loginMethods\": []}]}").getAsJsonObject();
                HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                        "http://localhost:3567/bulk-import/add-users",
                        request, 1000, 1000, null, Utils.getCdiVersionStringLatestForTests(), null);
            } catch (io.supertokens.test.httpRequest.HttpResponseException e) {
                String responseString = getResponseMessageFromError(e.getMessage());
                assertEquals(400, e.statusCode);
                assertEquals(responseString, "{\"error\":\"" + genericErrMsg +  "\",\"users\":[{\"index\":0,\"errors\":[\"At least one loginMethod is required.\"]}]}");
            }
        }
        // Invalid field type of non required fields outside loginMethod
        {
            try {
                JsonObject request = new JsonParser()
                        .parse("{\"users\":[{\"externalUserId\":[],\"userMetaData\":[],\"roles\":{},\"totp\":{}}]}")
                        .getAsJsonObject();
                HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                        "http://localhost:3567/bulk-import/add-users",
                        request, 1000, 1000, null, Utils.getCdiVersionStringLatestForTests(), null);
            } catch (io.supertokens.test.httpRequest.HttpResponseException e) {
                String responseString = getResponseMessageFromError(e.getMessage());
                assertEquals(400, e.statusCode);
                assertEquals(responseString,
                        "{\"error\":\"" + genericErrMsg +  "\",\"users\":[{\"index\":0,\"errors\":[\"externalUserId should be of type string.\",\"roles should be of type array of string.\",\"totp should be of type array of object.\",\"loginMethods is required.\"]}]}");
            }
        }
        // Invalid field type of non required fields inside loginMethod
        {
            try {
                JsonObject request = new JsonParser().parse(
                        "{\"users\":[{\"loginMethods\":[{\"recipeId\":[],\"tenantId\":[],\"isPrimary\":[],\"isVerified\":[],\"timeJoinedInMSSinceEpoch\":[]}]}]}")
                        .getAsJsonObject();
                HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                        "http://localhost:3567/bulk-import/add-users",
                        request, 1000, 1000, null, Utils.getCdiVersionStringLatestForTests(), null);
            } catch (io.supertokens.test.httpRequest.HttpResponseException e) {
                String responseString = getResponseMessageFromError(e.getMessage());
                assertEquals(400, e.statusCode);
                assertEquals(responseString,
                        "{\"error\":\"" + genericErrMsg +  "\",\"users\":[{\"index\":0,\"errors\":[\"recipeId should be of type string for a loginMethod.\",\"tenantId should be of type string for a loginMethod.\",\"isVerified should be of type boolean for a loginMethod.\",\"isPrimary should be of type boolean for a loginMethod.\",\"timeJoinedInMSSinceEpoch should be of type number for a loginMethod\"]}]}");
            }
        }
        // Invalid recipeId
        {
            try {
                JsonObject request = new JsonParser()
                        .parse("{\"users\":[{\"loginMethods\":[{\"recipeId\":\"invalid_recipe_id\"}]}]}")
                        .getAsJsonObject();
                HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                        "http://localhost:3567/bulk-import/add-users",
                        request, 1000, 1000, null, Utils.getCdiVersionStringLatestForTests(), null);
            } catch (io.supertokens.test.httpRequest.HttpResponseException e) {
                String responseString = getResponseMessageFromError(e.getMessage());
                assertEquals(400, e.statusCode);
                assertEquals(responseString,
                        "{\"error\":\"" + genericErrMsg +  "\",\"users\":[{\"index\":0,\"errors\":[\"Invalid recipeId for loginMethod. Pass one of emailpassword, thirdparty or, passwordless!\"]}]}");
            }
        }
        // Invalid field type in emailpassword recipe
        {
            // CASE 1: email, passwordHash and hashingAlgorithm are not present
            try {
                JsonObject request = new JsonParser()
                        .parse("{\"users\":[{\"loginMethods\":[{\"recipeId\":\"emailpassword\"}]}]}").getAsJsonObject();
                HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                        "http://localhost:3567/bulk-import/add-users",
                        request, 1000, 1000, null, Utils.getCdiVersionStringLatestForTests(), null);
            } catch (io.supertokens.test.httpRequest.HttpResponseException e) {
                String responseString = getResponseMessageFromError(e.getMessage());
                assertEquals(400, e.statusCode);
                assertEquals(responseString,
                        "{\"error\":\"" + genericErrMsg +  "\",\"users\":[{\"index\":0,\"errors\":[\"email is required for an emailpassword recipe.\",\"passwordHash is required for an emailpassword recipe.\",\"hashingAlgorithm is required for an emailpassword recipe.\"]}]}");
            }
            // CASE 2: email, passwordHash and hashingAlgorithm field type is incorrect
            try {
                JsonObject request = new JsonParser().parse(
                        "{\"users\":[{\"loginMethods\":[{\"recipeId\":\"emailpassword\",\"email\":[],\"passwordHash\":[],\"hashingAlgorithm\":[]}]}]}")
                        .getAsJsonObject();
                HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                        "http://localhost:3567/bulk-import/add-users",
                        request, 1000, 1000, null, Utils.getCdiVersionStringLatestForTests(), null);
            } catch (io.supertokens.test.httpRequest.HttpResponseException e) {
                String responseString = getResponseMessageFromError(e.getMessage());
                assertEquals(400, e.statusCode);
                assertEquals(responseString,
                        "{\"error\":\"" + genericErrMsg +  "\",\"users\":[{\"index\":0,\"errors\":[\"email should be of type string for an emailpassword recipe.\",\"passwordHash should be of type string for an emailpassword recipe.\",\"hashingAlgorithm should be of type string for an emailpassword recipe.\"]}]}");
            }
            // CASE 3: hashingAlgorithm is not one of bcrypt, argon2, firebase_scrypt
            try {
                JsonObject request = new JsonParser().parse(
                        "{\"users\":[{\"loginMethods\":[{\"recipeId\":\"emailpassword\",\"email\":\"johndoe@gmail.com\",\"passwordHash\":\"somehash\",\"hashingAlgorithm\":\"invalid_algorithm\"}]}]}")
                        .getAsJsonObject();
                HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                        "http://localhost:3567/bulk-import/add-users",
                        request, 1000, 1000, null, Utils.getCdiVersionStringLatestForTests(), null);
            } catch (io.supertokens.test.httpRequest.HttpResponseException e) {
                String responseString = getResponseMessageFromError(e.getMessage());
                assertEquals(400, e.statusCode);
                assertEquals(responseString,
                        "{\"error\":\"" + genericErrMsg +  "\",\"users\":[{\"index\":0,\"errors\":[\"Invalid hashingAlgorithm for emailpassword recipe. Pass one of bcrypt, argon2 or, firebase_scrypt!\"]}]}");
            }
        }
        // Invalid field type in thirdparty recipe
        {
            // CASE 1: email, thirdPartyId and thirdPartyUserId are not present
            try {
                JsonObject request = new JsonParser()
                        .parse("{\"users\":[{\"loginMethods\":[{\"recipeId\":\"thirdparty\"}]}]}").getAsJsonObject();
                HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                        "http://localhost:3567/bulk-import/add-users",
                        request, 1000, 1000, null, Utils.getCdiVersionStringLatestForTests(), null);
            } catch (io.supertokens.test.httpRequest.HttpResponseException e) {
                String responseString = getResponseMessageFromError(e.getMessage());
                assertEquals(400, e.statusCode);
                assertEquals(responseString,
                        "{\"error\":\"" + genericErrMsg +  "\",\"users\":[{\"index\":0,\"errors\":[\"email is required for a thirdparty recipe.\",\"thirdPartyId is required for a thirdparty recipe.\",\"thirdPartyUserId is required for a thirdparty recipe.\"]}]}");
            }
            // CASE 2: email, passwordHash and thirdPartyUserId field type is incorrect
            try {
                JsonObject request = new JsonParser().parse(
                        "{\"users\":[{\"loginMethods\":[{\"recipeId\":\"thirdparty\",\"email\":[],\"thirdPartyId\":[],\"thirdPartyUserId\":[]}]}]}")
                        .getAsJsonObject();
                HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                        "http://localhost:3567/bulk-import/add-users",
                        request, 1000, 1000, null, Utils.getCdiVersionStringLatestForTests(), null);
            } catch (io.supertokens.test.httpRequest.HttpResponseException e) {
                String responseString = getResponseMessageFromError(e.getMessage());
                assertEquals(400, e.statusCode);
                assertEquals(responseString,
                        "{\"error\":\"" + genericErrMsg +  "\",\"users\":[{\"index\":0,\"errors\":[\"email should be of type string for a thirdparty recipe.\",\"thirdPartyId should be of type string for a thirdparty recipe.\",\"thirdPartyUserId should be of type string for a thirdparty recipe.\"]}]}");
            }
        }
        // Invalid field type in passwordless recipe
        {
            // CASE 1: email and phoneNumber are not present
            try {
                JsonObject request = new JsonParser()
                        .parse("{\"users\":[{\"loginMethods\":[{\"recipeId\":\"passwordless\"}]}]}").getAsJsonObject();
                HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                        "http://localhost:3567/bulk-import/add-users",
                        request, 1000, 1000, null, Utils.getCdiVersionStringLatestForTests(), null);
            } catch (io.supertokens.test.httpRequest.HttpResponseException e) {
                String responseString = getResponseMessageFromError(e.getMessage());
                assertEquals(400, e.statusCode);
                assertEquals(responseString,
                        "{\"error\":\"" + genericErrMsg +  "\",\"users\":[{\"index\":0,\"errors\":[\"Either email or phoneNumber is required for a passwordless recipe.\"]}]}");
            }
            // CASE 2: email and phoneNumber field type is incorrect
            try {
                JsonObject request = new JsonParser().parse(
                        "{\"users\":[{\"loginMethods\":[{\"recipeId\":\"passwordless\",\"email\":[],\"phoneNumber\":[]}]}]}")
                        .getAsJsonObject();
                HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                        "http://localhost:3567/bulk-import/add-users",
                        request, 1000, 1000, null, Utils.getCdiVersionStringLatestForTests(), null);
            } catch (io.supertokens.test.httpRequest.HttpResponseException e) {
                String responseString = getResponseMessageFromError(e.getMessage());
                assertEquals(400, e.statusCode);
                assertEquals(responseString,
                        "{\"error\":\"" + genericErrMsg +  "\",\"users\":[{\"index\":0,\"errors\":[\"email should be of type string for a passwordless recipe.\",\"phoneNumber should be of type string for a passwordless recipe.\"]}]}");
            }
        }
        // Validate tenantId
        {
            // CASE 1: Different tenantId when multitenancy is not enabled
            try {
                JsonObject request = new JsonParser().parse(
                        "{\"users\":[{\"loginMethods\":[{\"tenantId\":\"invalid\",\"recipeId\":\"passwordless\",\"email\":\"johndoe@gmail.com\"}]}]}")
                        .getAsJsonObject();
                HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                        "http://localhost:3567/bulk-import/add-users",
                        request, 1000, 1000, null, Utils.getCdiVersionStringLatestForTests(), null);
            } catch (io.supertokens.test.httpRequest.HttpResponseException e) {
                String responseString = getResponseMessageFromError(e.getMessage());
                assertEquals(400, e.statusCode);
                assertEquals(responseString,
                        "{\"error\":\"" + genericErrMsg + "\",\"users\":[{\"index\":0,\"errors\":[\"Multitenancy must be enabled before importing users to a different tenant.\"]}]}");
            }
            // CASE 2: Different tenantId when multitenancy is enabled
            try {
                FeatureFlagTestContent.getInstance(process.getProcess())
                    .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{EE_FEATURES.MULTI_TENANCY});

                JsonObject request = new JsonParser().parse(
                        "{\"users\":[{\"loginMethods\":[{\"tenantId\":\"invalid\",\"recipeId\":\"passwordless\",\"email\":\"johndoe@gmail.com\"}]}]}")
                        .getAsJsonObject();
                HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                        "http://localhost:3567/bulk-import/add-users",
                        request, 1000, 1000, null, Utils.getCdiVersionStringLatestForTests(), null);
            } catch (io.supertokens.test.httpRequest.HttpResponseException e) {
                String responseString = getResponseMessageFromError(e.getMessage());
                assertEquals(400, e.statusCode);
                assertEquals(responseString,
                        "{\"error\":\"" + genericErrMsg + "\",\"users\":[{\"index\":0,\"errors\":[\"Invalid tenantId: invalid for passwordless recipe.\"]}]}");
            }
        }
        // No two loginMethods can have isPrimary as true
        {
            // CASE 1: email, passwordHash and hashingAlgorithm are not present
            try {
                JsonObject request = new JsonParser()
                        .parse("{\"users\":[{\"loginMethods\":[{\"recipeId\":\"emailpassword\",\"email\":\"johndoe@gmail.com\",\"passwordHash\":\"somehash\",\"hashingAlgorithm\":\"bcrypt\",\"isPrimary\":true},{\"recipeId\":\"passwordless\",\"email\":\"johndoe@gmail.com\",\"isPrimary\":true}]}]}").getAsJsonObject();
                HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                        "http://localhost:3567/bulk-import/add-users",
                        request, 1000, 1000, null, Utils.getCdiVersionStringLatestForTests(), null);
            } catch (io.supertokens.test.httpRequest.HttpResponseException e) {
                String responseString = getResponseMessageFromError(e.getMessage());
                assertEquals(400, e.statusCode);
                assertEquals(responseString,
                        "{\"error\":\"" + genericErrMsg +  "\",\"users\":[{\"index\":0,\"errors\":[\"No two loginMethods can have isPrimary as true.\"]}]}");
            }
        }
        // Can't import less than 1 user at a time
        {
            try {
                JsonObject request = generateUsersJson(0);
            HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
            "http://localhost:3567/bulk-import/add-users",
            request, 1000, 1000, null, Utils.getCdiVersionStringLatestForTests(), null);
            } catch (io.supertokens.test.httpRequest.HttpResponseException e) {
                String responseString = getResponseMessageFromError(e.getMessage());
                assertEquals(400, e.statusCode);
                assertEquals(responseString, "{\"error\":\"You need to add at least one user.\"}");
            }
        }
        // Can't import more than 10000 users at a time
        {
            try {
                JsonObject request = generateUsersJson(10001);
            HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
            "http://localhost:3567/bulk-import/add-users",
            request, 1000, 1000, null, Utils.getCdiVersionStringLatestForTests(), null);
            } catch (io.supertokens.test.httpRequest.HttpResponseException e) {
                String responseString = getResponseMessageFromError(e.getMessage());
                assertEquals(400, e.statusCode);
                assertEquals(responseString, "{\"error\":\"You can only add 10000 users at a time.\"}");
            }
        }

        process.kill();
        Assert.assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void shouldReturn200Response() throws Exception {
        String[] args = { "../" };

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        JsonObject request = generateUsersJson(10000);
        JsonObject response = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
        "http://localhost:3567/bulk-import/add-users",
        request, 1000, 10000, null, Utils.getCdiVersionStringLatestForTests(), null);
        assertEquals("OK", response.get("status").getAsString());

        process.kill();
        Assert.assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void shouldNormaliseFields() throws Exception {
        String[] args = { "../" };

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        JsonObject request = generateUsersJson(1);
        JsonObject response = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
        "http://localhost:3567/bulk-import/add-users",
        request, 1000, 1000, null, Utils.getCdiVersionStringLatestForTests(), null);
        assertEquals("OK", response.get("status").getAsString());

        JsonObject getResponse = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                "http://localhost:3567/bulk-import/users",
                new HashMap<>(), 1000, 1000, null, Utils.getCdiVersionStringLatestForTests(), null);

        assertEquals("OK", getResponse.get("status").getAsString());
        JsonArray bulkImportUsers = getResponse.get("users").getAsJsonArray();
        assertEquals(1, bulkImportUsers.size());

        JsonParser parser = new JsonParser();
        JsonObject bulkImportUserJson = bulkImportUsers.get(0).getAsJsonObject();
        JsonArray loginMethods = parser.parse(bulkImportUserJson.get("rawData").getAsString()).getAsJsonObject().getAsJsonArray("loginMethods");

        for (int i = 0; i < loginMethods.size(); i++) {
            JsonObject loginMethod = loginMethods.get(i).getAsJsonObject();
            if (loginMethod.has("email")) {
                assertEquals("johndoe+1@gmail.com", loginMethod.get("hashingAlgorithm").getAsString());
            }
            if (loginMethod.has("phoneNumber")) {
                assertEquals("919999999999", loginMethod.get("phoneNumber").getAsString());
            }
            if (loginMethod.has("hashingAlgorithm")) {
                assertEquals("ARGON2", loginMethod.get("hashingAlgorithm").getAsString());
            }
        }

        process.kill();
        Assert.assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    public static JsonObject generateUsersJson(int numberOfUsers) {
        JsonObject userJsonObject = new JsonObject();
        JsonParser parser = new JsonParser();

        JsonArray usersArray = new JsonArray();
        for (int i = 0; i < numberOfUsers; i++) {
            JsonObject user = new JsonObject();

            user.addProperty("externalUserId", UUID.randomUUID().toString());
            user.add("userMetadata", parser.parse("{\"key1\":\"value1\",\"key2\":{\"key3\":\"value3\"}}"));
            user.add("roles", parser.parse("[\"role1\", \"role2\"]"));
            user.add("totp", parser.parse("[{\"secretKey\":\"secretKey\",\"period\": 30,\"skew\":1,\"deviceName\":\"deviceName\"}]"));

            String email = " johndoe+" + i + "@gmail.com ";

            JsonArray loginMethodsArray = new JsonArray();
            loginMethodsArray.add(createEmailLoginMethod(email));
            loginMethodsArray.add(createThirdPartyLoginMethod(email));
            loginMethodsArray.add(createPasswordlessLoginMethod(email));
            user.add("loginMethods", loginMethodsArray);

            usersArray.add(user);
        }

        userJsonObject.add("users", usersArray);
        return userJsonObject;
    }

    private static JsonObject createEmailLoginMethod(String email) {
        JsonObject loginMethod = new JsonObject();
        loginMethod.addProperty("tenantId", "public");
        loginMethod.addProperty("email", email);
        loginMethod.addProperty("recipeId", "emailpassword");
        loginMethod.addProperty("passwordHash", "$argon2d$v=19$m=12,t=3,p=1$aGI4enNvMmd0Zm0wMDAwMA$r6p7qbr6HD+8CD7sBi4HVw");
        loginMethod.addProperty("hashingAlgorithm", "argon2");
        loginMethod.addProperty("isVerified", true);
        loginMethod.addProperty("isPrimary", true);
        loginMethod.addProperty("timeJoinedInMSSinceEpoch",  0);
        return loginMethod;
    }

    private static JsonObject createThirdPartyLoginMethod(String email) {
        JsonObject loginMethod = new JsonObject();
        loginMethod.addProperty("tenantId", "public");
        loginMethod.addProperty("recipeId", "thirdparty");
        loginMethod.addProperty("email", email);
        loginMethod.addProperty("thirdPartyId", "google");
        loginMethod.addProperty("thirdPartyUserId", "112618388912586834161");
        loginMethod.addProperty("isVerified", true);
        loginMethod.addProperty("isPrimary", false);
        loginMethod.addProperty("timeJoinedInMSSinceEpoch", 0);
        return loginMethod;
    }

    private static JsonObject createPasswordlessLoginMethod(String email) {
        JsonObject loginMethod = new JsonObject();
        loginMethod.addProperty("tenantId", "public");
        loginMethod.addProperty("email", email);
        loginMethod.addProperty("recipeId", "passwordless");
        loginMethod.addProperty("phoneNumber", "+91-9999999999");
        loginMethod.addProperty("isVerified", true);
        loginMethod.addProperty("isPrimary", false);
        loginMethod.addProperty("timeJoinedInMSSinceEpoch", 0);
        return loginMethod;
    }
}
