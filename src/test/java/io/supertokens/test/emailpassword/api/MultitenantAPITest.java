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

package io.supertokens.test.emailpassword.api;

import com.google.gson.JsonObject;
import io.supertokens.ProcessState;
import io.supertokens.emailpassword.ParsedFirebaseSCryptResponse;
import io.supertokens.featureflag.EE_FEATURES;
import io.supertokens.featureflag.FeatureFlagTestContent;
import io.supertokens.featureflag.exceptions.FeatureNotEnabledException;
import io.supertokens.multitenancy.Multitenancy;
import io.supertokens.multitenancy.exception.BadPermissionException;
import io.supertokens.multitenancy.exception.CannotModifyBaseConfigException;
import io.supertokens.pluginInterface.RECIPE_ID;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.pluginInterface.emailpassword.sqlStorage.EmailPasswordSQLStorage;
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

import static org.junit.Assert.*;

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
    }

    private void createTenants()
            throws InvalidProviderConfigException, StorageQueryException,
            FeatureNotEnabledException, TenantOrAppNotFoundException, IOException, InvalidConfigException,
            CannotModifyBaseConfigException, BadPermissionException {
        createTenants(false);
    }

    private void createTenants(Boolean includeHashingKey)
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
            if (includeHashingKey) {
                config.addProperty("firebase_password_hashing_signer_key",
                        "gRhC3eDeQOdyEn4bMd9c6kxguWVmcIVq/SKa0JDPFeM6TcEevkaW56sIWfx88OHbJKnCXdWscZx0l2WbCJ1wbg==");
            }

            Multitenancy.addNewOrUpdateAppOrTenant(
                    process.getProcess(),
                    new TenantIdentifier(null, null, null),
                    new TenantConfig(
                            tenantIdentifier,
                            new EmailPasswordConfig(true),
                            new ThirdPartyConfig(false, null),
                            new PasswordlessConfig(false),
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
            if (includeHashingKey) {
                config.addProperty("firebase_password_hashing_signer_key",
                        "gRhC3eDeQOdyEn4bMd9c6kxguWVmcIVq/SKa0JDPFeM6TcEevkaW56sIWfx88OHbJKnCXdWscZx0l2WbCJ1wbg==");
            }

            Multitenancy.addNewOrUpdateAppOrTenant(
                    process.getProcess(),
                    new TenantIdentifier(null, "a1", null),
                    new TenantConfig(
                            tenantIdentifier,
                            new EmailPasswordConfig(true),
                            new ThirdPartyConfig(false, null),
                            new PasswordlessConfig(false),
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
            if (includeHashingKey) {
                config.addProperty("firebase_password_hashing_signer_key",
                        "gRhC3eDeQOdyEn4bMd9c6kxguWVmcIVq/SKa0JDPFeM6TcEevkaW56sIWfx88OHbJKnCXdWscZx0l2WbCJ1wbg==");
            }

            Multitenancy.addNewOrUpdateAppOrTenant(
                    process.getProcess(),
                    new TenantIdentifier(null, "a1", null),
                    new TenantConfig(
                            tenantIdentifier,
                            new EmailPasswordConfig(true),
                            new ThirdPartyConfig(false, null),
                            new PasswordlessConfig(false),
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

    private JsonObject signUp(TenantIdentifier tenantIdentifier, String email, String password)
            throws HttpResponseException, IOException {
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("email", email);
        requestBody.addProperty("password", password);
        JsonObject signUpResponse = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                HttpRequestForTesting.getMultitenantUrl(tenantIdentifier, "/recipe/signup"),
                requestBody, 1000, 1000, null,
                SemVer.v3_0.get(), "emailpassword");
        assertEquals("OK", signUpResponse.getAsJsonPrimitive("status").getAsString());
        return signUpResponse.getAsJsonObject("user");
    }

    private JsonObject successfulSignIn(TenantIdentifier tenantIdentifier, String email, String password)
            throws HttpResponseException, IOException {
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("email", email);
        requestBody.addProperty("password", password);
        JsonObject signInResponse = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                HttpRequestForTesting.getMultitenantUrl(tenantIdentifier, "/recipe/signin"),
                requestBody, 1000, 1000, null,
                SemVer.v3_0.get(), "emailpassword");
        assertEquals("OK", signInResponse.getAsJsonPrimitive("status").getAsString());
        return signInResponse.getAsJsonObject("user");
    }

    private void wrongCredentialsSignIn(TenantIdentifier tenantIdentifier, String email, String password)
            throws HttpResponseException, IOException {
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("email", email);
        requestBody.addProperty("password", password);
        JsonObject signInResponse = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                HttpRequestForTesting.getMultitenantUrl(tenantIdentifier, "/recipe/signin"),
                requestBody, 1000, 1000, null,
                SemVer.v3_0.get(), "emailpassword");
        assertEquals("WRONG_CREDENTIALS_ERROR", signInResponse.getAsJsonPrimitive("status").getAsString());
    }

    private JsonObject getUserUsingId(TenantIdentifier tenantIdentifier, String userId)
            throws HttpResponseException, IOException {
        HashMap<String, String> map = new HashMap<>();
        map.put("userId", userId);
        JsonObject userResponse = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                HttpRequestForTesting.getMultitenantUrl(tenantIdentifier, "/recipe/user"),
                map, 1000, 1000, null, SemVer.v3_0.get(),
                "emailpassword");
        assertEquals("OK", userResponse.getAsJsonPrimitive("status").getAsString());
        return userResponse.getAsJsonObject("user");
    }

    private void updatePassword(TenantIdentifier tenantIdentifier, String userId, String password)
            throws HttpResponseException, IOException {
        JsonObject body = new JsonObject();
        body.addProperty("userId", userId);
        body.addProperty("password", password);

        JsonObject response = HttpRequestForTesting.sendJsonPUTRequest(process.getProcess(), "",
                HttpRequestForTesting.getMultitenantUrl(tenantIdentifier, "/recipe/user"), body,
                1000, 1000, null, SemVer.v3_0.get(),
                RECIPE_ID.EMAIL_PASSWORD.toString());
        assertEquals("OK", response.getAsJsonPrimitive("status").getAsString());
    }

    private void updateEmail(TenantIdentifier tenantIdentifier, String userId, String email)
            throws HttpResponseException, IOException {
        JsonObject body = new JsonObject();
        body.addProperty("userId", userId);
        body.addProperty("email", email);

        JsonObject response = HttpRequestForTesting.sendJsonPUTRequest(process.getProcess(), "",
                HttpRequestForTesting.getMultitenantUrl(tenantIdentifier, "/recipe/user"), body,
                1000, 1000, null, SemVer.v3_0.get(),
                RECIPE_ID.EMAIL_PASSWORD.toString());
        assertEquals("OK", response.getAsJsonPrimitive("status").getAsString());
    }

    private void updateEmailAndPassword(TenantIdentifier tenantIdentifier, String userId, String email, String password)
            throws HttpResponseException, IOException {
        JsonObject body = new JsonObject();
        body.addProperty("userId", userId);
        body.addProperty("email", email);
        body.addProperty("password", password);

        JsonObject response = HttpRequestForTesting.sendJsonPUTRequest(process.getProcess(), "",
                HttpRequestForTesting.getMultitenantUrl(tenantIdentifier, "/recipe/user"), body,
                1000, 1000, null, SemVer.v3_0.get(),
                RECIPE_ID.EMAIL_PASSWORD.toString());
        assertEquals("OK", response.getAsJsonPrimitive("status").getAsString());
    }

    private JsonObject getUserUsingEmail(TenantIdentifier tenantIdentifier, String email)
            throws HttpResponseException, IOException {
        HashMap<String, String> map = new HashMap<>();
        map.put("email", email);
        JsonObject userResponse = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                HttpRequestForTesting.getMultitenantUrl(tenantIdentifier, "/recipe/user"),
                map, 1000, 1000, null, SemVer.v3_0.get(),
                "emailpassword");
        assertEquals("OK", userResponse.getAsJsonPrimitive("status").getAsString());
        return userResponse.getAsJsonObject("user");
    }


    private String generatePasswordResetToken(TenantIdentifier tenantIdentifier, String userId)
            throws HttpResponseException, IOException {
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("userId", userId);
        JsonObject response = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                HttpRequestForTesting.getMultitenantUrl(tenantIdentifier, "/recipe/user/password/reset/token"),
                requestBody, 1000, 1000, null,
                SemVer.v3_0.get(), "emailpassword");

        assertEquals(response.get("status").getAsString(), "OK");
        assertEquals(response.entrySet().size(), 2);

        return response.get("token").getAsString();
    }

    private void successfulResetPasswordUsingToken(TenantIdentifier tenantIdentifier, String userId, String token,
                                                   String password)
            throws HttpResponseException, IOException {
        JsonObject resetPasswordBody = new JsonObject();
        resetPasswordBody.addProperty("method", "token");
        resetPasswordBody.addProperty("token", token);
        resetPasswordBody.addProperty("newPassword", password);

        JsonObject passwordResetResponse = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                HttpRequestForTesting.getMultitenantUrl(tenantIdentifier, "/recipe/user/password/reset"),
                resetPasswordBody, 1000, 1000, null,
                SemVer.v3_0.get(), "emailpassword");
        assertEquals(passwordResetResponse.get("status").getAsString(), "OK");
        assertEquals(passwordResetResponse.get("userId").getAsString(), userId);
        assertEquals(passwordResetResponse.entrySet().size(), 2);
    }

    private void invalidResetPasswordUsingToken(TenantIdentifier tenantIdentifier, String token, String password)
            throws HttpResponseException, IOException {
        JsonObject resetPasswordBody = new JsonObject();
        resetPasswordBody.addProperty("method", "token");
        resetPasswordBody.addProperty("token", token);
        resetPasswordBody.addProperty("newPassword", password);

        JsonObject passwordResetResponse = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                HttpRequestForTesting.getMultitenantUrl(tenantIdentifier, "/recipe/user/password/reset"),
                resetPasswordBody, 1000, 1000, null,
                SemVer.v3_0.get(), "emailpassword");
        assertEquals(passwordResetResponse.get("status").getAsString(), "RESET_PASSWORD_INVALID_TOKEN_ERROR");
    }

    @Test
    public void testSameEmailWithDifferentPasswordsOnDifferentTenantsWorksCorrectly() throws Exception {
        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        createTenants();

        JsonObject user1 = signUp(t1, "user@example.com", "password1");
        JsonObject user2 = signUp(t2, "user@example.com", "password2");
        JsonObject user3 = signUp(t3, "user@example.com", "password3");

        assertEquals(user1, successfulSignIn(t1, "user@example.com", "password1"));
        assertEquals(user2, successfulSignIn(t2, "user@example.com", "password2"));
        assertEquals(user3, successfulSignIn(t3, "user@example.com", "password3"));
    }

    @Test
    public void testUserWithSameEmailCannotLoginFromDifferentTenantInSharedUserPool() throws Exception {
        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        createTenants();

        // User pool 2 - (null, a1, t1), (null, a1, t2)
        signUp(t2, "user@example.com", "password1");
        signUp(t3, "user@example.com", "password2");

        wrongCredentialsSignIn(t1, "user@example.com", "password1");
        wrongCredentialsSignIn(t3, "user@example.com", "password1");

        wrongCredentialsSignIn(t1, "user@example.com", "password2");
        wrongCredentialsSignIn(t2, "user@example.com", "password2");
    }

    @Test
    public void testGetUserUsingIdReturnsUserFromTheRightTenantWhileQueryingFromAnyTenantInTheSameApp()
            throws Exception {
        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        createTenants();

        JsonObject user1 = signUp(t1, "user@example.com", "password1");
        JsonObject user2 = signUp(t2, "user@example.com", "password2");
        JsonObject user3 = signUp(t3, "user@example.com", "password3");

        for (JsonObject user : new JsonObject[]{user1, user2, user3}) {
            assertEquals(user, getUserUsingId(t1, user.getAsJsonPrimitive("id").getAsString()));
        }
    }

    @Test
    public void testGetUserUsingEmailReturnsTenantSpecificUser() throws Exception {
        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        createTenants();

        JsonObject user1 = signUp(t1, "user@example.com", "password1");
        JsonObject user2 = signUp(t2, "user@example.com", "password2");
        JsonObject user3 = signUp(t3, "user@example.com", "password3");

        assertEquals(user1, getUserUsingEmail(t1, "user@example.com"));
        assertEquals(user2, getUserUsingEmail(t2, "user@example.com"));
        assertEquals(user3, getUserUsingEmail(t3, "user@example.com"));
    }

    @Test
    public void testUpdatePasswordWorksCorrectlyAcrossTenants() throws Exception {
        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        createTenants();

        JsonObject user1 = signUp(t1, "user@example.com", "password1");
        JsonObject user2 = signUp(t2, "user@example.com", "password2");
        JsonObject user3 = signUp(t3, "user@example.com", "password3");

        JsonObject[] users = new JsonObject[]{user1, user2, user3};
        TenantIdentifier[] tenants = new TenantIdentifier[]{t1, t2, t3};

        for (int i = 0; i < users.length; i++) {
            JsonObject user = users[i];
            TenantIdentifier userTenant = tenants[i];

            String newPassword = generateRandomString(16);
            updatePassword(t1, user.getAsJsonPrimitive("id").getAsString(), newPassword);

            for (TenantIdentifier loginTenant : tenants) {
                if (loginTenant.equals(userTenant)) {
                    assertEquals(user, successfulSignIn(loginTenant, "user@example.com", newPassword));
                } else {
                    wrongCredentialsSignIn(loginTenant, "user@example.com", newPassword);
                }
            }
        }
    }

    @Test
    public void testUpdateEmailWorksCorrectlyAcrossTenants() throws Exception {
        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        createTenants();

        JsonObject user1 = signUp(t1, "user@example.com", "password");
        JsonObject user2 = signUp(t2, "user@example.com", "password");
        JsonObject user3 = signUp(t3, "user@example.com", "password");

        JsonObject[] users = new JsonObject[]{user1, user2, user3};
        TenantIdentifier[] tenants = new TenantIdentifier[]{t1, t2, t3};

        for (int i = 0; i < users.length; i++) {
            JsonObject user = users[i];
            TenantIdentifier userTenant = tenants[i];

            String newEmail = (generateRandomString(16) + "@example.com").toLowerCase();
            updateEmail(t1, user.getAsJsonPrimitive("id").getAsString(), newEmail);
            user.remove("email");
            user.addProperty("email", newEmail);

            for (TenantIdentifier loginTenant : tenants) {
                if (loginTenant.equals(userTenant)) {
                    assertEquals(user, successfulSignIn(loginTenant, newEmail, "password"));
                } else {
                    wrongCredentialsSignIn(loginTenant, newEmail, "password");
                }
            }
        }
    }

    @Test
    public void testUpdateEmailAndPasswordWorksCorrectlyAcrossTenants() throws Exception {
        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        createTenants();

        JsonObject user1 = signUp(t1, "user@example.com", "password1");
        JsonObject user2 = signUp(t2, "user@example.com", "password2");
        JsonObject user3 = signUp(t3, "user@example.com", "password3");

        JsonObject[] users = new JsonObject[]{user1, user2, user3};
        TenantIdentifier[] tenants = new TenantIdentifier[]{t1, t2, t3};

        for (int i = 0; i < users.length; i++) {
            JsonObject user = users[i];
            TenantIdentifier userTenant = tenants[i];

            String newPassword = generateRandomString(16);
            String newEmail = (generateRandomString(16) + "@example.com").toLowerCase();
            updateEmailAndPassword(t1, user.getAsJsonPrimitive("id").getAsString(), newEmail, newPassword);

            user.remove("email");
            user.addProperty("email", newEmail);

            for (TenantIdentifier loginTenant : tenants) {
                if (loginTenant.equals(userTenant)) {
                    assertEquals(user, successfulSignIn(loginTenant, newEmail, newPassword));
                } else {
                    wrongCredentialsSignIn(loginTenant, newEmail, newPassword);
                }
            }
        }
    }

    @Test
    public void testResetPasswordWorksCorrectlyAcrossTenants() throws Exception {
        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        createTenants();

        JsonObject user1 = signUp(t1, "user@example.com", "password1");
        JsonObject user2 = signUp(t2, "user@example.com", "password2");
        JsonObject user3 = signUp(t3, "user@example.com", "password3");

        JsonObject[] users = new JsonObject[]{user1, user2, user3};
        TenantIdentifier[] tenants = new TenantIdentifier[]{t1, t2, t3};

        for (int i = 0; i < users.length; i++) {
            JsonObject user = users[i];
            TenantIdentifier userTenant = tenants[i];

            String newPassword = generateRandomString(16);
            String token = generatePasswordResetToken(userTenant, user.getAsJsonPrimitive("id").getAsString());
            successfulResetPasswordUsingToken(userTenant, user.getAsJsonPrimitive("id").getAsString(), token,
                    newPassword);

            for (TenantIdentifier loginTenant : tenants) {
                if (loginTenant.equals(userTenant)) {
                    assertEquals(user, successfulSignIn(loginTenant, "user@example.com", newPassword));
                } else {
                    wrongCredentialsSignIn(loginTenant, "user@example.com", newPassword);
                }
            }
        }
    }

    @Test
    public void testCrossTenantPasswordResetCombinations() throws Exception {
        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        createTenants();

        JsonObject user1 = signUp(t1, "user@example.com", "password1");
        JsonObject user2 = signUp(t2, "user@example.com", "password2");
        JsonObject user3 = signUp(t3, "user@example.com", "password3");

        {
            // Cross-tenant across different user pool is not allowed
            JsonObject[] users = new JsonObject[]{user1, user2};
            TenantIdentifier[] tenants = new TenantIdentifier[]{t1, t2};

            for (int i = 0; i < users.length; i++) {
                JsonObject user = users[i];
                TenantIdentifier userTenant = tenants[i];

                for (TenantIdentifier tenant : tenants) {
                    String newPassword = generateRandomString(16);
                    String token = generatePasswordResetToken(userTenant, user.getAsJsonPrimitive("id").getAsString());

                    if (tenant.equals(userTenant)) {
                        successfulResetPasswordUsingToken(tenant, user.getAsJsonPrimitive("id").getAsString(), token,
                                newPassword);

                        for (TenantIdentifier loginTenant : tenants) {
                            if (loginTenant.equals(userTenant)) {
                                assertEquals(user, successfulSignIn(loginTenant, "user@example.com", newPassword));
                            } else {
                                wrongCredentialsSignIn(loginTenant, "user@example.com", newPassword);
                            }
                        }
                    } else {
                        if (StorageLayer.isInMemDb(process.getProcess())) {
                            // For in memory db, the user is in the same user pool and the password reset will succeed
                            successfulResetPasswordUsingToken(tenant, user.getAsJsonPrimitive("id").getAsString(),
                                    token,
                                    newPassword);
                        } else {
                            invalidResetPasswordUsingToken(tenant, token, newPassword);
                        }
                    }
                }
            }
        }

        {
            // Cross-tenant across different user pool is not allowed
            JsonObject[] users = new JsonObject[]{user1, user3};
            TenantIdentifier[] tenants = new TenantIdentifier[]{t1, t3};

            for (int i = 0; i < users.length; i++) {
                JsonObject user = users[i];
                TenantIdentifier userTenant = tenants[i];

                for (TenantIdentifier tenant : tenants) {
                    String newPassword = generateRandomString(16);
                    String token = generatePasswordResetToken(userTenant, user.getAsJsonPrimitive("id").getAsString());

                    if (tenant.equals(userTenant)) {
                        successfulResetPasswordUsingToken(tenant, user.getAsJsonPrimitive("id").getAsString(), token,
                                newPassword);

                        for (TenantIdentifier loginTenant : tenants) {
                            if (loginTenant.equals(userTenant)) {
                                assertEquals(user, successfulSignIn(loginTenant, "user@example.com", newPassword));
                            } else {
                                wrongCredentialsSignIn(loginTenant, "user@example.com", newPassword);
                            }
                        }
                    } else {
                        if (StorageLayer.isInMemDb(process.getProcess())) {
                            // For in memory db, the user is in the same user pool and the password reset will succeed
                            successfulResetPasswordUsingToken(tenant, user.getAsJsonPrimitive("id").getAsString(),
                                    token,
                                    newPassword);
                        } else {
                            invalidResetPasswordUsingToken(tenant, token, newPassword);
                        }
                    }
                }
            }
        }

        {
            // Cross-tenant within same user pool is allowed
            JsonObject[] users = new JsonObject[]{user2, user3};
            TenantIdentifier[] tenants = new TenantIdentifier[]{t2, t3};

            for (int i = 0; i < users.length; i++) {
                JsonObject user = users[i];
                TenantIdentifier userTenant = tenants[i];

                for (TenantIdentifier tenant : tenants) {
                    String newPassword = generateRandomString(16);
                    String token = generatePasswordResetToken(userTenant, user.getAsJsonPrimitive("id").getAsString());

                    successfulResetPasswordUsingToken(tenant, user.getAsJsonPrimitive("id").getAsString(), token,
                            newPassword);

                    for (TenantIdentifier loginTenant : tenants) {
                        if (loginTenant.equals(userTenant)) {
                            assertEquals(user, successfulSignIn(loginTenant, "user@example.com", newPassword));
                        } else {
                            wrongCredentialsSignIn(loginTenant, "user@example.com", newPassword);
                        }
                    }
                }
            }
        }
    }

    @Test
    public void testImportUsersWorksCorrectlyAcrossTenants() throws Exception {
        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        createTenants(true);

        int firebaseMemCost = 14;
        int firebaseRounds = 8;
        String firebaseSaltSeparator = "Bw==";

        String email = "test@example.com";
        String password = "testPass123";
        String salt = "/cj0jC1br5o4+w==";
        String passwordHash = "qZM035es5AXYqavsKD6/rhtxg7t5PhcyRgv5blc3doYbChX8keMfQLq1ra96O2Pf2TP/eZrR5xtPCYN6mX3ESA" +
                "==";
        String combinedPasswordHash = "$" + ParsedFirebaseSCryptResponse.FIREBASE_SCRYPT_PREFIX + "$" + passwordHash
                + "$" + salt + "$m=" + firebaseMemCost + "$r=" + firebaseRounds + "$s=" + firebaseSaltSeparator;

        long timeJoined = System.currentTimeMillis();

        EmailPasswordSQLStorage storage = (EmailPasswordSQLStorage) StorageLayer.getStorage(t2, process.getProcess());

        storage.signUp(t2, "userId", email, combinedPasswordHash, timeJoined);

        successfulSignIn(t2, email, password);
        wrongCredentialsSignIn(t1, email, password);
        wrongCredentialsSignIn(t3, email, password);
    }

    @Test
    public void testThatTenantIdIsNotAllowedForOlderCDIVersion() throws Exception {
        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        createTenants(false);

        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("email", "test@example.com");
        requestBody.addProperty("password", "password");

        try {
            HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                    HttpRequestForTesting.getMultitenantUrl(t2, "/recipe/signup"),
                    requestBody, 1000, 1000, null,
                    SemVer.v2_21.get(), "emailpassword");
            fail();
        } catch (HttpResponseException e) {
            assertEquals(404, e.statusCode);
        }
    }
}
