/*
 *    Copyright (c) 2023, VRAI Labs and/or its affiliates. All rights reserved.
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

package io.supertokens.test.multitenant.api;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.supertokens.Main;
import io.supertokens.ProcessState;
import io.supertokens.emailpassword.EmailPassword;
import io.supertokens.emailpassword.exceptions.EmailChangeNotAllowedException;
import io.supertokens.featureflag.EE_FEATURES;
import io.supertokens.featureflag.FeatureFlagTestContent;
import io.supertokens.featureflag.exceptions.FeatureNotEnabledException;
import io.supertokens.multitenancy.Multitenancy;
import io.supertokens.multitenancy.exception.BadPermissionException;
import io.supertokens.multitenancy.exception.CannotModifyBaseConfigException;
import io.supertokens.passwordless.Passwordless;
import io.supertokens.passwordless.exceptions.*;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.pluginInterface.authRecipe.AuthRecipeUserInfo;
import io.supertokens.pluginInterface.emailpassword.exceptions.DuplicateEmailException;
import io.supertokens.pluginInterface.exceptions.InvalidConfigException;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.exceptions.StorageTransactionLogicException;
import io.supertokens.pluginInterface.multitenancy.*;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.pluginInterface.passwordless.exception.DuplicateLinkCodeHashException;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.test.TestingProcessManager;
import io.supertokens.test.Utils;
import io.supertokens.test.httpRequest.HttpRequestForTesting;
import io.supertokens.test.httpRequest.HttpResponseException;
import io.supertokens.thirdparty.InvalidProviderConfigException;
import io.supertokens.thirdparty.ThirdParty;
import io.supertokens.utils.SemVer;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.*;

import static org.junit.Assert.*;

public class TestTenantIdIsNotPresentForOlderCDI {
    TestingProcessManager.TestingProcess process;
    TenantIdentifier t1, t2, t3;

    @AfterClass
    public static void afterTesting() {
        Utils.afterTesting();
    }

    @After
    public void afterEach() throws InterruptedException {
        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Before
    public void beforeEach() throws InterruptedException, InvalidProviderConfigException,
            StorageQueryException, FeatureNotEnabledException, TenantOrAppNotFoundException, IOException,
            InvalidConfigException, CannotModifyBaseConfigException, BadPermissionException {
        Utils.reset();

        String[] args = {"../"};

        this.process = TestingProcessManager.start(args);
        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{EE_FEATURES.MULTI_TENANCY});
        process.startProcess();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        createTenants();
    }

    private void createTenants()
            throws StorageQueryException, TenantOrAppNotFoundException, InvalidProviderConfigException,
            FeatureNotEnabledException, IOException, InvalidConfigException,
            CannotModifyBaseConfigException, BadPermissionException {

        // User pool 1 - (null, a1, null)
        // User pool 2 - (null, a1, t1), (null, a1, t2)

        // NOTE - user pools are not applicable if using in memory database

        { // tenant 1
            JsonObject config = new JsonObject();
            TenantIdentifier tenantIdentifier = new TenantIdentifier(null, "a1", null);

            StorageLayer.getStorage(new TenantIdentifier(null, null, null), process.getProcess())
                    .modifyConfigToAddANewUserPoolForTesting(config, 1);

            Multitenancy.addNewOrUpdateAppOrTenant(
                    process.getProcess(),
                    new TenantIdentifier(null, null, null),
                    new TenantConfig(
                            tenantIdentifier,
                            new EmailPasswordConfig(true),
                            new ThirdPartyConfig(true, null),
                            new PasswordlessConfig(true),
                            config
                    )
            );
        }

        { // tenant 2
            JsonObject config = new JsonObject();
            TenantIdentifier tenantIdentifier = new TenantIdentifier(null, "a2", null);

            StorageLayer.getStorage(new TenantIdentifier(null, null, null), process.getProcess())
                    .modifyConfigToAddANewUserPoolForTesting(config, 2);

            Multitenancy.addNewOrUpdateAppOrTenant(
                    process.getProcess(),
                    new TenantIdentifier(null, null, null),
                    new TenantConfig(
                            tenantIdentifier,
                            new EmailPasswordConfig(true),
                            new ThirdPartyConfig(true, null),
                            new PasswordlessConfig(true),
                            config
                    )
            );
        }

        { // tenant 3
            JsonObject config = new JsonObject();
            TenantIdentifier tenantIdentifier = new TenantIdentifier(null, "a3", null);

            StorageLayer.getStorage(new TenantIdentifier(null, null, null), process.getProcess())
                    .modifyConfigToAddANewUserPoolForTesting(config, 2);

            Multitenancy.addNewOrUpdateAppOrTenant(
                    process.getProcess(),
                    new TenantIdentifier(null, null, null),
                    new TenantConfig(
                            tenantIdentifier,
                            new EmailPasswordConfig(true),
                            new ThirdPartyConfig(true, null),
                            new PasswordlessConfig(true),
                            config
                    )
            );
        }

        t1 = new TenantIdentifier(null, "a1", null);
        t2 = new TenantIdentifier(null, "a2", null);
        t3 = new TenantIdentifier(null, "a3", null);
    }

    private JsonObject epSignUp(TenantIdentifier tenantIdentifier, String email, String password)
            throws HttpResponseException, IOException {
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("email", email);
        requestBody.addProperty("password", password);
        JsonObject signUpResponse = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                HttpRequestForTesting.getMultitenantUrl(tenantIdentifier, "/recipe/signup"),
                requestBody, 1000, 1000, null,
                SemVer.v2_21.get(), "emailpassword");
        assertEquals("OK", signUpResponse.getAsJsonPrimitive("status").getAsString());
        return signUpResponse.getAsJsonObject("user");
    }

    private JsonObject epSignIn(TenantIdentifier tenantIdentifier, String email, String password)
            throws HttpResponseException, IOException {
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("email", email);
        requestBody.addProperty("password", password);
        JsonObject signInResponse = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                HttpRequestForTesting.getMultitenantUrl(tenantIdentifier, "/recipe/signin"),
                requestBody, 1000, 1000, null,
                SemVer.v2_21.get(), "emailpassword");
        assertEquals("OK", signInResponse.getAsJsonPrimitive("status").getAsString());
        return signInResponse.getAsJsonObject("user");
    }

    private JsonObject epGetUserUsingId(TenantIdentifier tenantIdentifier, String userId)
            throws HttpResponseException, IOException {
        HashMap<String, String> map = new HashMap<>();
        map.put("userId", userId);
        JsonObject userResponse = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                HttpRequestForTesting.getMultitenantUrl(tenantIdentifier, "/recipe/user"),
                map, 1000, 1000, null, SemVer.v2_21.get(),
                "emailpassword");
        assertEquals("OK", userResponse.getAsJsonPrimitive("status").getAsString());
        return userResponse.getAsJsonObject("user");
    }

    private JsonObject epGetUserUsingEmail(TenantIdentifier tenantIdentifier, String email)
            throws HttpResponseException, IOException {
        HashMap<String, String> map = new HashMap<>();
        map.put("email", email);
        JsonObject userResponse = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                HttpRequestForTesting.getMultitenantUrl(tenantIdentifier, "/recipe/user"),
                map, 1000, 1000, null, SemVer.v2_21.get(),
                "emailpassword");
        assertEquals("OK", userResponse.getAsJsonPrimitive("status").getAsString());
        return userResponse.getAsJsonObject("user");
    }

    @Test
    public void testEpUsersDontHaveTenantIdsForOlderCDI() throws Exception {
        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        JsonObject user1 = epSignUp(t1, "user@example.com", "password1");
        JsonObject user2 = epSignUp(t2, "user@example.com", "password2");
        JsonObject user3 = epSignUp(t3, "user@example.com", "password3");

        assertFalse(user1.has("tenantIds"));
        assertFalse(user2.has("tenantIds"));
        assertFalse(user3.has("tenantIds"));

        {
            JsonObject nuser1 = epSignIn(t1, "user@example.com", "password1");
            JsonObject nuser2 = epSignIn(t2, "user@example.com", "password2");
            JsonObject nuser3 = epSignIn(t3, "user@example.com", "password3");

            assertFalse(nuser1.has("tenantIds"));
            assertFalse(nuser2.has("tenantIds"));
            assertFalse(nuser3.has("tenantIds"));
        }

        {
            JsonObject nuser1 = epGetUserUsingId(t1, user1.get("id").getAsString());
            JsonObject nuser2 = epGetUserUsingId(t2, user2.get("id").getAsString());
            JsonObject nuser3 = epGetUserUsingId(t3, user3.get("id").getAsString());

            assertFalse(nuser1.has("tenantIds"));
            assertFalse(nuser2.has("tenantIds"));
            assertFalse(nuser3.has("tenantIds"));
        }

        {
            JsonObject nuser1 = epGetUserUsingEmail(t1, "user@example.com");
            JsonObject nuser2 = epGetUserUsingEmail(t2, "user@example.com");
            JsonObject nuser3 = epGetUserUsingEmail(t3, "user@example.com");

            assertFalse(nuser1.has("tenantIds"));
            assertFalse(nuser2.has("tenantIds"));
            assertFalse(nuser3.has("tenantIds"));
        }

    }

    private void createUsers(TenantIdentifier tenantIdentifier, int numUsers, String prefix)
            throws TenantOrAppNotFoundException, DuplicateEmailException, StorageQueryException,
            BadPermissionException, DuplicateLinkCodeHashException, NoSuchAlgorithmException, IOException,
            RestartFlowException, InvalidKeyException, Base64EncodingException, DeviceIdHashMismatchException,
            StorageTransactionLogicException, IncorrectUserInputCodeException, ExpiredUserInputCodeException,
            EmailChangeNotAllowedException {

        HashMap<TenantIdentifier, ArrayList<String>> tenantToUsers = new HashMap<>();
        HashMap<String, ArrayList<String>> recipeToUsers = new HashMap<>();

        if (tenantToUsers.get(tenantIdentifier) == null) {
            tenantToUsers.put(tenantIdentifier, new ArrayList<>());
        }

        TenantIdentifierWithStorage tenantIdentifierWithStorage = tenantIdentifier.withStorage(
                StorageLayer.getStorage(tenantIdentifier, process.getProcess()));
        for (int i = 0; i < numUsers; i++) {
            {
                AuthRecipeUserInfo user = EmailPassword.signUp(
                        tenantIdentifierWithStorage, process.getProcess(),
                        prefix + "epuser" + i + "@example.com", "password" + i);
                tenantToUsers.get(tenantIdentifier).add(user.getSupertokensUserId());
                if (!recipeToUsers.containsKey("emailpassword")) {
                    recipeToUsers.put("emailpassword", new ArrayList<>());
                }
                recipeToUsers.get("emailpassword").add(user.getSupertokensUserId());
            }
            {
                Passwordless.CreateCodeResponse codeResponse = Passwordless.createCode(
                        tenantIdentifierWithStorage,
                        process.getProcess(),
                        prefix + "pluser" + i + "@example.com",
                        null, null,
                        "abcd"
                );
                Passwordless.ConsumeCodeResponse response = Passwordless.consumeCode(tenantIdentifierWithStorage,
                        process.getProcess(), codeResponse.deviceId,
                        codeResponse.deviceIdHash, "abcd", null);
                tenantToUsers.get(tenantIdentifier).add(response.user.getSupertokensUserId());

                if (!recipeToUsers.containsKey("passwordless")) {
                    recipeToUsers.put("passwordless", new ArrayList<>());
                }
                recipeToUsers.get("passwordless").add(response.user.getSupertokensUserId());
            }
            {
                ThirdParty.SignInUpResponse user1 = ThirdParty.signInUp(tenantIdentifierWithStorage,
                        process.getProcess(), "google", "googleid" + i, prefix + "tpuser" + i + "@example.com");
                tenantToUsers.get(tenantIdentifier).add(user1.user.getSupertokensUserId());
                ThirdParty.SignInUpResponse user2 = ThirdParty.signInUp(tenantIdentifierWithStorage,
                        process.getProcess(), "facebook", "fbid" + i, prefix + "tpuser" + i + "@example.com");
                tenantToUsers.get(tenantIdentifier).add(user2.user.getSupertokensUserId());


                if (!recipeToUsers.containsKey("thirdparty")) {
                    recipeToUsers.put("thirdparty", new ArrayList<>());
                }
                recipeToUsers.get("thirdparty").add(user1.user.getSupertokensUserId());
                recipeToUsers.get("thirdparty").add(user2.user.getSupertokensUserId());
            }
        }
    }

    public static JsonObject listUsers(TenantIdentifier sourceTenant, String paginationToken, String limit,
                                       String includeRecipeIds, Main main)
            throws HttpResponseException, IOException {
        Map<String, String> params = new HashMap<>();
        if (paginationToken != null) {
            params.put("paginationToken", paginationToken);
        }
        if (limit != null) {
            params.put("limit", limit);
        }
        if (includeRecipeIds != null) {
            params.put("includeRecipeIds", includeRecipeIds);
        }
        JsonObject response = HttpRequestForTesting.sendGETRequest(main, "",
                HttpRequestForTesting.getMultitenantUrl(sourceTenant, "/users"),
                params, 1000, 1000, null,
                SemVer.v2_21.get(), null);

        assertEquals("OK", response.getAsJsonPrimitive("status").getAsString());
        return response;
    }

    @Test
    public void testUserPaginationUserObjectsDontHaveTenantIdsInOlderCDIVersion() throws Exception {
        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        createUsers(t1, 50, "t1");
        createUsers(t2, 50, "t2");
        createUsers(t3, 50, "t3");

        for (TenantIdentifier tenantIdentifier : new TenantIdentifier[]{t1, t2, t3}) {
            { // All recipes
                Set<String> userIdSet = new HashSet<>();

                JsonObject userList = listUsers(tenantIdentifier, null, "10", null, process.getProcess());
                String paginationToken = userList.get("nextPaginationToken").getAsString();

                JsonArray users = userList.get("users").getAsJsonArray();
                for (JsonElement user : users) {
                    String userId = user.getAsJsonObject().get("user").getAsJsonObject().get("id").getAsString();
                    assertFalse(userIdSet.contains(userId));
                    userIdSet.add(userId);

                    assertFalse(user.getAsJsonObject().get("user").getAsJsonObject().has("tenantIds"));
                }

                while (paginationToken != null) {
                    userList = listUsers(tenantIdentifier, paginationToken, "10", null, process.getProcess());
                    users = userList.get("users").getAsJsonArray();

                    for (JsonElement user : users) {
                        String userId = user.getAsJsonObject().get("user").getAsJsonObject().get("id").getAsString();
                        assertFalse(userIdSet.contains(userId));
                        userIdSet.add(userId);

                        assertFalse(user.getAsJsonObject().get("user").getAsJsonObject().has("tenantIds"));
                    }

                    paginationToken = null;
                    if (userList.has("nextPaginationToken")) {
                        paginationToken = userList.get("nextPaginationToken").getAsString();
                    }
                }

                assertEquals(200, userIdSet.size());
            }

            { // recipe combinations
                String[] combinations = new String[]{"emailpassword", "passwordless", "thirdparty",
                        "emailpassword,passwordless", "emailpassword,thirdparty", "passwordless,thirdparty"};
                int[] userCounts = new int[]{50, 50, 100, 100, 150, 150};

                for (int i = 0; i < combinations.length; i++) {
                    String includeRecipeIds = combinations[i];
                    int userCount = userCounts[i];

                    Set<String> userIdSet = new HashSet<>();

                    JsonObject userList = listUsers(tenantIdentifier, null, "10", includeRecipeIds,
                            process.getProcess());
                    String paginationToken = userList.get("nextPaginationToken").getAsString();

                    JsonArray users = userList.get("users").getAsJsonArray();
                    for (JsonElement user : users) {
                        String userId = user.getAsJsonObject().get("user").getAsJsonObject().get("id").getAsString();
                        String recipeId = user.getAsJsonObject().get("recipeId").getAsString();
                        assertFalse(userIdSet.contains(userId));
                        userIdSet.add(userId);

                        assertFalse(user.getAsJsonObject().get("user").getAsJsonObject().has("tenantIds"));
                    }

                    while (paginationToken != null) {
                        userList = listUsers(tenantIdentifier, paginationToken, "10", includeRecipeIds,
                                process.getProcess());
                        users = userList.get("users").getAsJsonArray();

                        for (JsonElement user : users) {
                            String userId = user.getAsJsonObject().get("user").getAsJsonObject().get("id")
                                    .getAsString();
                            String recipeId = user.getAsJsonObject().get("recipeId").getAsString();
                            assertFalse(userIdSet.contains(userId));
                            userIdSet.add(userId);

                            assertFalse(user.getAsJsonObject().get("user").getAsJsonObject().has("tenantIds"));
                        }

                        paginationToken = null;
                        if (userList.has("nextPaginationToken")) {
                            paginationToken = userList.get("nextPaginationToken").getAsString();
                        }
                    }

                    assertEquals(userCount, userIdSet.size());
                }
            }
        }
    }

    private static String generateRandomString(int length) {
        StringBuilder sb = new StringBuilder(length);
        final String ALPHABET = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
        final Random RANDOM = new Random();
        for (int i = 0; i < length; i++) {
            int randomIndex = RANDOM.nextInt(ALPHABET.length());
            char randomChar = ALPHABET.charAt(randomIndex);
            sb.append(randomChar);
        }
        return sb.toString();
    }

    private JsonObject createCodeWithEmail(TenantIdentifier tenantIdentifier, String email)
            throws HttpResponseException, IOException {
        String exampleCode = generateRandomString(6);
        JsonObject createCodeRequestBody = new JsonObject();
        createCodeRequestBody.addProperty("email", email);
        createCodeRequestBody.addProperty("userInputCode", exampleCode);

        JsonObject response = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                HttpRequestForTesting.getMultitenantUrl(tenantIdentifier, "/recipe/signinup/code"),
                createCodeRequestBody, 1000, 1000, null,
                SemVer.v2_21.get(), "passwordless");

        assertEquals("OK", response.get("status").getAsString());
        assertEquals(8, response.entrySet().size());

        return response;
    }

    private JsonObject createCodeWithNumber(TenantIdentifier tenantIdentifier, String phoneNumber)
            throws HttpResponseException, IOException {
        JsonObject createCodeRequestBody = new JsonObject();
        createCodeRequestBody.addProperty("phoneNumber", phoneNumber);

        JsonObject response = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                HttpRequestForTesting.getMultitenantUrl(tenantIdentifier, "/recipe/signinup/code"),
                createCodeRequestBody, 1000, 1000, null,
                SemVer.v2_21.get(), "passwordless");

        assertEquals("OK", response.get("status").getAsString());
        assertEquals(8, response.entrySet().size());

        return response;
    }

    private JsonObject consumeCode(TenantIdentifier tenantIdentifier, String preAuthSessionId, String linkCode)
            throws HttpResponseException, IOException {
        JsonObject consumeCodeRequestBody = new JsonObject();
        consumeCodeRequestBody.addProperty("preAuthSessionId", preAuthSessionId);
        consumeCodeRequestBody.addProperty("linkCode", linkCode);

        JsonObject response = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                HttpRequestForTesting.getMultitenantUrl(tenantIdentifier, "/recipe/signinup/code/consume"),
                consumeCodeRequestBody, 1000, 1000, null,
                SemVer.v2_21.get(), "passwordless");

        assertEquals("OK", response.get("status").getAsString());
        return response.get("user").getAsJsonObject();
    }

    private JsonObject consumeCode(TenantIdentifier tenantIdentifier, String deviceId, String preAuthSessionId,
                                   String userInputCode)
            throws HttpResponseException, IOException {
        JsonObject consumeCodeRequestBody = new JsonObject();
        consumeCodeRequestBody.addProperty("deviceId", deviceId);
        consumeCodeRequestBody.addProperty("preAuthSessionId", preAuthSessionId);
        consumeCodeRequestBody.addProperty("userInputCode", userInputCode);

        JsonObject response = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                HttpRequestForTesting.getMultitenantUrl(tenantIdentifier, "/recipe/signinup/code/consume"),
                consumeCodeRequestBody, 1000, 1000, null,
                SemVer.v2_21.get(), "passwordless");
        assertEquals("OK", response.get("status").getAsString());
        return response.get("user").getAsJsonObject();
    }

    private JsonObject signInUpEmailUsingLinkCode(TenantIdentifier tenantIdentifier, String email)
            throws HttpResponseException, IOException {
        JsonObject code = createCodeWithEmail(tenantIdentifier, email);
        return consumeCode(tenantIdentifier, code.get("preAuthSessionId").getAsString(),
                code.get("linkCode").getAsString());
    }

    private JsonObject signInUpEmailUsingUserInputCode(TenantIdentifier tenantIdentifier, String email)
            throws HttpResponseException, IOException {
        JsonObject code = createCodeWithEmail(tenantIdentifier, email);
        return consumeCode(tenantIdentifier, code.get("deviceId").getAsString(),
                code.get("preAuthSessionId").getAsString(), code.get("userInputCode").getAsString());
    }

    private JsonObject signInUpNumberUsingLinkCode(TenantIdentifier tenantIdentifier, String phoneNumber)
            throws HttpResponseException, IOException {
        JsonObject code = createCodeWithNumber(tenantIdentifier, phoneNumber);
        return consumeCode(tenantIdentifier, code.get("preAuthSessionId").getAsString(),
                code.get("linkCode").getAsString());
    }

    private JsonObject signInUpNumberUsingUserInputCode(TenantIdentifier tenantIdentifier, String phoneNumber)
            throws HttpResponseException, IOException {
        JsonObject code = createCodeWithNumber(tenantIdentifier, phoneNumber);
        return consumeCode(tenantIdentifier, code.get("deviceId").getAsString(),
                code.get("preAuthSessionId").getAsString(), code.get("userInputCode").getAsString());
    }

    private JsonObject plessGetUserUsingId(TenantIdentifier tenantIdentifier, String userId)
            throws HttpResponseException, IOException {
        HashMap<String, String> map = new HashMap<>();
        map.put("userId", userId);
        JsonObject userResponse = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                HttpRequestForTesting.getMultitenantUrl(tenantIdentifier, "/recipe/user"),
                map, 1000, 1000, null, SemVer.v2_21.get(),
                "passwordless");
        assertEquals("OK", userResponse.getAsJsonPrimitive("status").getAsString());
        return userResponse.getAsJsonObject("user");
    }

    private JsonObject plessGetUserUsingEmail(TenantIdentifier tenantIdentifier, String email)
            throws HttpResponseException, IOException {
        HashMap<String, String> map = new HashMap<>();
        map.put("email", email);
        JsonObject userResponse = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                HttpRequestForTesting.getMultitenantUrl(tenantIdentifier, "/recipe/user"),
                map, 1000, 1000, null, SemVer.v2_21.get(),
                "passwordless");
        assertEquals("OK", userResponse.getAsJsonPrimitive("status").getAsString());
        return userResponse.getAsJsonObject("user");
    }

    private JsonObject plessGetUserUsingNumber(TenantIdentifier tenantIdentifier, String phoneNumber)
            throws HttpResponseException, IOException {
        HashMap<String, String> map = new HashMap<>();
        map.put("phoneNumber", phoneNumber);
        JsonObject userResponse = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                HttpRequestForTesting.getMultitenantUrl(tenantIdentifier, "/recipe/user"),
                map, 1000, 1000, null, SemVer.v2_21.get(),
                "passwordless");
        assertEquals("OK", userResponse.getAsJsonPrimitive("status").getAsString());
        return userResponse.getAsJsonObject("user");
    }

    @Test
    public void testPlessUsersDontHaveTenantIdsInOlderCDI() throws Exception {
        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        for (TenantIdentifier t : new TenantIdentifier[]{t1, t2, t3}) {
            JsonObject user1 = signInUpEmailUsingUserInputCode(t, "user1@example.com");
            JsonObject user2 = signInUpEmailUsingLinkCode(t, "user2@example.com");
            JsonObject user3 = signInUpNumberUsingLinkCode(t, "+919876543210");
            JsonObject user4 = signInUpNumberUsingUserInputCode(t, "+919876543212");

            assertFalse(user1.has("tenantIds"));
            assertFalse(user2.has("tenantIds"));
            assertFalse(user3.has("tenantIds"));
            assertFalse(user4.has("tenantIds"));

            {
                JsonObject nuser1 = plessGetUserUsingId(t, user1.get("id").getAsString());
                JsonObject nuser2 = plessGetUserUsingId(t, user2.get("id").getAsString());
                JsonObject nuser3 = plessGetUserUsingId(t, user3.get("id").getAsString());
                JsonObject nuser4 = plessGetUserUsingId(t, user4.get("id").getAsString());

                assertFalse(nuser1.has("tenantIds"));
                assertFalse(nuser2.has("tenantIds"));
                assertFalse(nuser3.has("tenantIds"));
                assertFalse(nuser4.has("tenantIds"));
            }

            {
                JsonObject nuser1 = plessGetUserUsingEmail(t, "user1@example.com");
                JsonObject nuser2 = plessGetUserUsingEmail(t, "user2@example.com");
                JsonObject nuser3 = plessGetUserUsingNumber(t, "+919876543210");
                JsonObject nuser4 = plessGetUserUsingNumber(t, "+919876543212");

                assertFalse(nuser1.has("tenantIds"));
                assertFalse(nuser2.has("tenantIds"));
                assertFalse(nuser3.has("tenantIds"));
                assertFalse(nuser4.has("tenantIds"));
            }
        }
    }

    public JsonObject signInUp(TenantIdentifier tenantIdentifier, String thirdPartyId, String thirdPartyUserId,
                               String email)
            throws HttpResponseException, IOException {
        JsonObject emailObject = new JsonObject();
        emailObject.addProperty("id", email);

        JsonObject signUpRequestBody = new JsonObject();
        signUpRequestBody.addProperty("thirdPartyId", thirdPartyId);
        signUpRequestBody.addProperty("thirdPartyUserId", thirdPartyUserId);
        signUpRequestBody.add("email", emailObject);

        JsonObject response = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                HttpRequestForTesting.getMultitenantUrl(tenantIdentifier, "/recipe/signinup"), signUpRequestBody,
                1000, 1000, null,
                SemVer.v2_21.get(), "thirdparty");
        assertEquals("OK", response.get("status").getAsString());
        assertEquals(3, response.entrySet().size());

        return response.get("user").getAsJsonObject();
    }

    private JsonObject tpGetUserUsingId(TenantIdentifier tenantIdentifier, String userId)
            throws HttpResponseException, IOException {
        HashMap<String, String> map = new HashMap<>();
        map.put("userId", userId);
        JsonObject userResponse = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                HttpRequestForTesting.getMultitenantUrl(tenantIdentifier, "/recipe/user"),
                map, 1000, 1000, null, SemVer.v2_21.get(),
                "thirdparty");
        assertEquals("OK", userResponse.getAsJsonPrimitive("status").getAsString());
        return userResponse.getAsJsonObject("user");
    }

    private JsonObject getUserUsingThirdPartyUserId(TenantIdentifier tenantIdentifier, String thirdPartyId,
                                                    String thirdPartyUserId)
            throws HttpResponseException, IOException {
        HashMap<String, String> map = new HashMap<>();
        map.put("thirdPartyId", thirdPartyId);
        map.put("thirdPartyUserId", thirdPartyUserId);
        JsonObject userResponse = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                HttpRequestForTesting.getMultitenantUrl(tenantIdentifier, "/recipe/user"),
                map, 1000, 1000, null, SemVer.v2_21.get(),
                "thirdparty");
        assertEquals("OK", userResponse.getAsJsonPrimitive("status").getAsString());
        return userResponse.getAsJsonObject("user");
    }

    private JsonObject[] getUsersUsingEmail(TenantIdentifier tenantIdentifier, String email)
            throws HttpResponseException, IOException {
        HashMap<String, String> map = new HashMap<>();
        map.put("email", email);
        JsonObject userResponse = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                HttpRequestForTesting.getMultitenantUrl(tenantIdentifier, "/recipe/users/by-email"),
                map, 1000, 1000, null, SemVer.v2_21.get(),
                "thirdparty");
        assertEquals("OK", userResponse.getAsJsonPrimitive("status").getAsString());

        JsonArray userObjects = userResponse.getAsJsonArray("users");
        JsonObject[] users = new JsonObject[userObjects.size()];
        for (int i = 0; i < userObjects.size(); i++) {
            JsonElement jsonElement = userObjects.get(i);
            if (jsonElement.isJsonObject()) {
                users[i] = jsonElement.getAsJsonObject();
            }
        }
        return users;
    }

    @Test
    public void testTpUsersDontHaveTenantIdsForOlderCDI() throws Exception {
        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        for (TenantIdentifier t : new TenantIdentifier[]{t1, t2, t3}) {
            JsonObject user1 = signInUp(t, "google", "google-user-id", "user@gmail.com");
            JsonObject user2 = signInUp(t, "facebook", "fb-user-id", "user@gmail.com");

            assertFalse(user1.has("tenantIds"));
            assertFalse(user2.has("tenantIds"));

            {
                JsonObject nuser1 = tpGetUserUsingId(t, user1.get("id").getAsString());
                JsonObject nuser2 = tpGetUserUsingId(t, user2.get("id").getAsString());

                assertFalse(nuser1.has("tenantIds"));
                assertFalse(nuser2.has("tenantIds"));
            }

            {
                JsonObject nuser1 = getUserUsingThirdPartyUserId(t, "google", "google-user-id");
                JsonObject nuser2 = getUserUsingThirdPartyUserId(t, "facebook", "fb-user-id");

                assertFalse(nuser1.has("tenantIds"));
                assertFalse(nuser2.has("tenantIds"));
            }

            {
                JsonObject[] users = getUsersUsingEmail(t, "user@gmail.com");
                assertEquals(2, users.length);
                assertFalse(users[0].has("tenantIds"));
                assertFalse(users[1].has("tenantIds"));
            }
        }
    }
}
