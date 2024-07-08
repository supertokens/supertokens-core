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

package io.supertokens.test.passwordless.api;

import com.google.gson.JsonObject;
import io.supertokens.ProcessState;
import io.supertokens.featureflag.EE_FEATURES;
import io.supertokens.featureflag.FeatureFlagTestContent;
import io.supertokens.featureflag.exceptions.FeatureNotEnabledException;
import io.supertokens.multitenancy.Multitenancy;
import io.supertokens.multitenancy.exception.BadPermissionException;
import io.supertokens.multitenancy.exception.CannotModifyBaseConfigException;
import io.supertokens.pluginInterface.RECIPE_ID;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.pluginInterface.exceptions.InvalidConfigException;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.multitenancy.*;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.test.TestingProcessManager;
import io.supertokens.test.Utils;
import io.supertokens.test.httpRequest.HttpRequestForTesting;
import io.supertokens.test.httpRequest.HttpResponseException;
import io.supertokens.thirdparty.InvalidProviderConfigException;
import io.supertokens.utils.SemVer;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.HashMap;
import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class MultitenantAPITest {
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
                            new EmailPasswordConfig(false),
                            new ThirdPartyConfig(false, null),
                            new PasswordlessConfig(true),
                            null, null,
                            config
                    )
            );
        }

        { // tenant 2
            JsonObject config = new JsonObject();
            TenantIdentifier tenantIdentifier = new TenantIdentifier(null, "a1", "t1");

            StorageLayer.getStorage(new TenantIdentifier(null, null, null), process.getProcess())
                    .modifyConfigToAddANewUserPoolForTesting(config, 2);

            Multitenancy.addNewOrUpdateAppOrTenant(
                    process.getProcess(),
                    new TenantIdentifier(null, "a1", null),
                    new TenantConfig(
                            tenantIdentifier,
                            new EmailPasswordConfig(false),
                            new ThirdPartyConfig(false, null),
                            new PasswordlessConfig(true),
                            null, null,
                            config
                    )
            );
        }

        { // tenant 3
            JsonObject config = new JsonObject();
            TenantIdentifier tenantIdentifier = new TenantIdentifier(null, "a1", "t2");

            StorageLayer.getStorage(new TenantIdentifier(null, null, null), process.getProcess())
                    .modifyConfigToAddANewUserPoolForTesting(config, 2);

            Multitenancy.addNewOrUpdateAppOrTenant(
                    process.getProcess(),
                    new TenantIdentifier(null, "a1", null),
                    new TenantConfig(
                            tenantIdentifier,
                            new EmailPasswordConfig(false),
                            new ThirdPartyConfig(false, null),
                            new PasswordlessConfig(true),
                            null, null,
                            config
                    )
            );
        }

        t1 = new TenantIdentifier(null, "a1", null);
        t2 = new TenantIdentifier(null, "a1", "t1");
        t3 = new TenantIdentifier(null, "a1", "t2");
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

    private static String generateRandomNumber(int length) {
        StringBuilder sb = new StringBuilder(length);
        final String ALPHABET = "1234567890";
        final Random RANDOM = new Random();
        for (int i = 0; i < length; i++) {
            int randomIndex = RANDOM.nextInt(ALPHABET.length());
            char randomChar = ALPHABET.charAt(randomIndex);
            sb.append(randomChar);
        }
        return "+44" + sb.toString();
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
                SemVer.v3_0.get(), "passwordless");

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
                SemVer.v3_0.get(), "passwordless");

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
                SemVer.v3_0.get(), "passwordless");

        assertEquals("OK", response.get("status").getAsString());
        return response.get("user").getAsJsonObject();
    }

    private void unsuccessfulConsumeCode(TenantIdentifier tenantIdentifier, String preAuthSessionId, String linkCode)
            throws HttpResponseException, IOException {
        JsonObject consumeCodeRequestBody = new JsonObject();
        consumeCodeRequestBody.addProperty("preAuthSessionId", preAuthSessionId);
        consumeCodeRequestBody.addProperty("linkCode", linkCode);

        JsonObject response = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                HttpRequestForTesting.getMultitenantUrl(tenantIdentifier, "/recipe/signinup/code/consume"),
                consumeCodeRequestBody, 1000, 1000, null,
                SemVer.v3_0.get(), "passwordless");

        assertEquals("RESTART_FLOW_ERROR", response.get("status").getAsString());
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
                SemVer.v3_0.get(), "passwordless");
        assertEquals("OK", response.get("status").getAsString());
        return response.get("user").getAsJsonObject();
    }

    private void unsuccessfulConsumeCode(TenantIdentifier tenantIdentifier, String deviceId, String preAuthSessionId,
                                         String userInputCode)
            throws HttpResponseException, IOException {
        JsonObject consumeCodeRequestBody = new JsonObject();
        consumeCodeRequestBody.addProperty("deviceId", deviceId);
        consumeCodeRequestBody.addProperty("preAuthSessionId", preAuthSessionId);
        consumeCodeRequestBody.addProperty("userInputCode", userInputCode);

        JsonObject response = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                HttpRequestForTesting.getMultitenantUrl(tenantIdentifier, "/recipe/signinup/code/consume"),
                consumeCodeRequestBody, 1000, 1000, null,
                SemVer.v3_0.get(), "passwordless");
        assertEquals("RESTART_FLOW_ERROR", response.get("status").getAsString());
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

    private JsonObject getUserUsingId(TenantIdentifier tenantIdentifier, String userId)
            throws HttpResponseException, IOException {
        HashMap<String, String> map = new HashMap<>();
        map.put("userId", userId);
        JsonObject userResponse = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                HttpRequestForTesting.getMultitenantUrl(tenantIdentifier, "/recipe/user"),
                map, 1000, 1000, null, SemVer.v3_0.get(),
                "passwordless");
        assertEquals("OK", userResponse.getAsJsonPrimitive("status").getAsString());
        return userResponse.getAsJsonObject("user");
    }

    private JsonObject getUserUsingEmail(TenantIdentifier tenantIdentifier, String email)
            throws HttpResponseException, IOException {
        HashMap<String, String> map = new HashMap<>();
        map.put("email", email);
        JsonObject userResponse = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                HttpRequestForTesting.getMultitenantUrl(tenantIdentifier, "/recipe/user"),
                map, 1000, 1000, null, SemVer.v3_0.get(),
                "passwordless");
        assertEquals("OK", userResponse.getAsJsonPrimitive("status").getAsString());
        return userResponse.getAsJsonObject("user");
    }

    private JsonObject getUserUsingNumber(TenantIdentifier tenantIdentifier, String phoneNumber)
            throws HttpResponseException, IOException {
        HashMap<String, String> map = new HashMap<>();
        map.put("phoneNumber", phoneNumber);
        JsonObject userResponse = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                HttpRequestForTesting.getMultitenantUrl(tenantIdentifier, "/recipe/user"),
                map, 1000, 1000, null, SemVer.v3_0.get(),
                "passwordless");
        assertEquals("OK", userResponse.getAsJsonPrimitive("status").getAsString());
        return userResponse.getAsJsonObject("user");
    }

    private void updateEmail(TenantIdentifier tenantIdentifier, String userId, String email)
            throws HttpResponseException, IOException {
        JsonObject body = new JsonObject();
        body.addProperty("userId", userId);
        body.addProperty("email", email);

        JsonObject response = HttpRequestForTesting.sendJsonPUTRequest(process.getProcess(), "",
                HttpRequestForTesting.getMultitenantUrl(tenantIdentifier, "/recipe/user"), body,
                1000, 1000, null, SemVer.v3_0.get(),
                RECIPE_ID.PASSWORDLESS.toString());
        assertEquals("OK", response.getAsJsonPrimitive("status").getAsString());
    }

    private void updatePhoneNumber(TenantIdentifier tenantIdentifier, String userId, String phoneNumber)
            throws HttpResponseException, IOException {
        JsonObject body = new JsonObject();
        body.addProperty("userId", userId);
        body.addProperty("phoneNumber", phoneNumber);

        JsonObject response = HttpRequestForTesting.sendJsonPUTRequest(process.getProcess(), "",
                HttpRequestForTesting.getMultitenantUrl(tenantIdentifier, "/recipe/user"), body,
                1000, 1000, null, SemVer.v3_0.get(),
                RECIPE_ID.PASSWORDLESS.toString());
        assertEquals("OK", response.getAsJsonPrimitive("status").getAsString());
    }

    @Test
    public void testUserWithSameEmailCanLoginAcrossTenants() throws Exception {
        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        {
            JsonObject user1 = signInUpEmailUsingLinkCode(t1, "user1@example.com");
            JsonObject user2 = signInUpEmailUsingLinkCode(t2, "user1@example.com");
            JsonObject user3 = signInUpEmailUsingLinkCode(t3, "user1@example.com");

            assertEquals("user1@example.com", user1.get("email").getAsString());
            assertEquals("user1@example.com", user2.get("email").getAsString());
            assertEquals("user1@example.com", user3.get("email").getAsString());
        }
        {
            JsonObject user1 = signInUpEmailUsingUserInputCode(t1, "user2@example.com");
            JsonObject user2 = signInUpEmailUsingUserInputCode(t2, "user2@example.com");
            JsonObject user3 = signInUpEmailUsingUserInputCode(t3, "user2@example.com");

            assertEquals("user2@example.com", user1.get("email").getAsString());
            assertEquals("user2@example.com", user2.get("email").getAsString());
            assertEquals("user2@example.com", user3.get("email").getAsString());
        }
    }

    @Test
    public void testUserWithSameNumberCanLoginAcrossTenants() throws Exception {
        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        {
            JsonObject user1 = signInUpNumberUsingLinkCode(t1, "+442071838750");
            JsonObject user2 = signInUpNumberUsingLinkCode(t2, "+442071838750");
            JsonObject user3 = signInUpNumberUsingLinkCode(t3, "+442071838750");

            assertEquals("+442071838750", user1.get("phoneNumber").getAsString());
            assertEquals("+442071838750", user2.get("phoneNumber").getAsString());
            assertEquals("+442071838750", user3.get("phoneNumber").getAsString());
        }
        {
            JsonObject user1 = signInUpNumberUsingUserInputCode(t1, "+442071838751");
            JsonObject user2 = signInUpNumberUsingUserInputCode(t2, "+442071838751");
            JsonObject user3 = signInUpNumberUsingUserInputCode(t3, "+442071838751");

            assertEquals("+442071838751", user1.get("phoneNumber").getAsString());
            assertEquals("+442071838751", user2.get("phoneNumber").getAsString());
            assertEquals("+442071838751", user3.get("phoneNumber").getAsString());
        }
    }

    @Test
    public void testCreateCodeOnOneTenantAndConsumeOnAnotherDoesNotWork() throws Exception {
        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        TenantIdentifier[] tenants = new TenantIdentifier[]{t1, t2, t3};
        for (TenantIdentifier createTenant : tenants) {
            for (TenantIdentifier consumeTenant : tenants) {
                { // email with link code
                    JsonObject code = createCodeWithEmail(createTenant, "user@example.com");

                    if (createTenant.equals(consumeTenant)) {
                        consumeCode(consumeTenant, code.get("preAuthSessionId").getAsString(),
                                code.get("linkCode").getAsString());
                    } else {
                        unsuccessfulConsumeCode(consumeTenant, code.get("preAuthSessionId").getAsString(),
                                code.get("linkCode").getAsString());
                    }
                }
                { // email with user input code
                    JsonObject code = createCodeWithEmail(createTenant, "user@example.com");

                    if (createTenant.equals(consumeTenant)) {
                        consumeCode(consumeTenant, code.get("deviceId").getAsString(),
                                code.get("preAuthSessionId").getAsString(), code.get("userInputCode").getAsString());
                    } else {
                        unsuccessfulConsumeCode(consumeTenant, code.get("deviceId").getAsString(),
                                code.get("preAuthSessionId").getAsString(), code.get("userInputCode").getAsString());
                    }
                }
                { // phoneNumber with link code
                    JsonObject code = createCodeWithNumber(createTenant, "+442071838750");

                    if (createTenant.equals(consumeTenant)) {
                        consumeCode(consumeTenant, code.get("preAuthSessionId").getAsString(),
                                code.get("linkCode").getAsString());
                    } else {
                        unsuccessfulConsumeCode(consumeTenant, code.get("preAuthSessionId").getAsString(),
                                code.get("linkCode").getAsString());
                    }
                }
                { // phoneNumber with user input code
                    JsonObject code = createCodeWithNumber(createTenant, "+442071838750");

                    if (createTenant.equals(consumeTenant)) {
                        consumeCode(consumeTenant, code.get("deviceId").getAsString(),
                                code.get("preAuthSessionId").getAsString(), code.get("userInputCode").getAsString());
                    } else {
                        unsuccessfulConsumeCode(consumeTenant, code.get("deviceId").getAsString(),
                                code.get("preAuthSessionId").getAsString(), code.get("userInputCode").getAsString());
                    }
                }
            }
        }
    }

    @Test
    public void testGetUserUsingIdReturnsUserFromTheRightTenantWhileQueryingFromAnyTenantInTheSameApp()
            throws Exception {
        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        {
            JsonObject user1 = signInUpEmailUsingLinkCode(t1, "user1@example.com");
            JsonObject user2 = signInUpEmailUsingLinkCode(t2, "user1@example.com");
            JsonObject user3 = signInUpEmailUsingLinkCode(t3, "user1@example.com");

            assertEquals(user1, getUserUsingId(t1, user1.get("id").getAsString()));
            assertEquals(user2, getUserUsingId(t1, user2.get("id").getAsString()));
            assertEquals(user3, getUserUsingId(t1, user3.get("id").getAsString()));
        }

        {
            JsonObject user1 = signInUpNumberUsingUserInputCode(t1, "+442071838750");
            JsonObject user2 = signInUpNumberUsingUserInputCode(t2, "+442071838750");
            JsonObject user3 = signInUpNumberUsingUserInputCode(t3, "+442071838750");

            assertEquals(user1, getUserUsingId(t1, user1.get("id").getAsString()));
            assertEquals(user2, getUserUsingId(t1, user2.get("id").getAsString()));
            assertEquals(user3, getUserUsingId(t1, user3.get("id").getAsString()));
        }
    }

    @Test
    public void testGetUserByEmailReturnsTenantSpecificUser() throws Exception {
        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        JsonObject user1 = signInUpEmailUsingLinkCode(t1, "user1@example.com");
        JsonObject user2 = signInUpEmailUsingLinkCode(t2, "user1@example.com");
        JsonObject user3 = signInUpEmailUsingLinkCode(t3, "user1@example.com");

        assertEquals(user1, getUserUsingEmail(t1, user1.get("email").getAsString()));
        assertEquals(user2, getUserUsingEmail(t2, user2.get("email").getAsString()));
        assertEquals(user3, getUserUsingEmail(t3, user3.get("email").getAsString()));
    }

    @Test
    public void testGetUserByNumberReturnsTenantSpecificUser() throws Exception {
        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        JsonObject user1 = signInUpNumberUsingUserInputCode(t1, "+442071838750");
        JsonObject user2 = signInUpNumberUsingUserInputCode(t2, "+442071838750");
        JsonObject user3 = signInUpNumberUsingUserInputCode(t3, "+442071838750");

        assertEquals(user1, getUserUsingNumber(t1, user1.get("phoneNumber").getAsString()));
        assertEquals(user2, getUserUsingNumber(t2, user2.get("phoneNumber").getAsString()));
        assertEquals(user3, getUserUsingNumber(t3, user3.get("phoneNumber").getAsString()));
    }

    @Test
    public void testUpdateEmail() throws Exception {
        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        JsonObject user1 = signInUpEmailUsingLinkCode(t1, "user1@example.com");
        JsonObject user2 = signInUpEmailUsingLinkCode(t2, "user1@example.com");
        JsonObject user3 = signInUpEmailUsingLinkCode(t3, "user1@example.com");

        JsonObject[] users = new JsonObject[]{user1, user2, user3};
        TenantIdentifier[] tenants = new TenantIdentifier[]{t1, t2, t3};

        for (int i = 0; i < users.length; i++) {
            JsonObject user = users[i];
            TenantIdentifier userTenant = tenants[i];

            String newEmail = (generateRandomString(16) + "@example.com").toLowerCase();
            updateEmail(t1, user.getAsJsonPrimitive("id").getAsString(), newEmail);
            user.remove("email");
            user.addProperty("email", newEmail);

            assertEquals(user, signInUpEmailUsingLinkCode(userTenant, newEmail));
        }
    }

    @Test
    public void testUpdateNumber() throws Exception {
        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        JsonObject user1 = signInUpNumberUsingUserInputCode(t1, "+442071838750");
        JsonObject user2 = signInUpNumberUsingUserInputCode(t2, "+442071838750");
        JsonObject user3 = signInUpNumberUsingUserInputCode(t3, "+442071838750");

        JsonObject[] users = new JsonObject[]{user1, user2, user3};
        TenantIdentifier[] tenants = new TenantIdentifier[]{t1, t2, t3};

        for (int i = 0; i < users.length; i++) {
            JsonObject user = users[i];
            TenantIdentifier userTenant = tenants[i];

            String newPhoneNumber = generateRandomNumber(8);
            updatePhoneNumber(t1, user.getAsJsonPrimitive("id").getAsString(), newPhoneNumber);
            user.remove("phoneNumber");
            // We need to normalize the phone number before adding it to the user object, as the update API performs
            // normalization.
            user.addProperty("phoneNumber", io.supertokens.utils.Utils.normalizeIfPhoneNumber(newPhoneNumber));

            assertEquals(user, signInUpNumberUsingUserInputCode(userTenant, newPhoneNumber));
        }
    }
}
