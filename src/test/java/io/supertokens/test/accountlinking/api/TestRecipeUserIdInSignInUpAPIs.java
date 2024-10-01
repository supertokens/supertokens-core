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

package io.supertokens.test.accountlinking.api;

import com.google.gson.JsonObject;
import io.supertokens.Main;
import io.supertokens.ProcessState;
import io.supertokens.authRecipe.AuthRecipe;
import io.supertokens.emailpassword.EmailPassword;
import io.supertokens.emailpassword.exceptions.EmailChangeNotAllowedException;
import io.supertokens.featureflag.EE_FEATURES;
import io.supertokens.featureflag.FeatureFlagTestContent;
import io.supertokens.passwordless.Passwordless;
import io.supertokens.passwordless.exceptions.*;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.pluginInterface.authRecipe.AuthRecipeUserInfo;
import io.supertokens.pluginInterface.emailpassword.exceptions.DuplicateEmailException;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.exceptions.StorageTransactionLogicException;
import io.supertokens.pluginInterface.passwordless.exception.DuplicateLinkCodeHashException;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.test.TestingProcessManager;
import io.supertokens.test.Utils;
import io.supertokens.test.httpRequest.HttpRequestForTesting;
import io.supertokens.thirdparty.ThirdParty;
import io.supertokens.useridmapping.UserIdMapping;
import io.supertokens.utils.SemVer;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class TestRecipeUserIdInSignInUpAPIs {
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


    AuthRecipeUserInfo createEmailPasswordUser(Main main, String email, String password)
            throws DuplicateEmailException, StorageQueryException {
        return EmailPassword.signUp(main, email, password);
    }

    AuthRecipeUserInfo createThirdPartyUser(Main main, String thirdPartyId, String thirdPartyUserId, String email)
            throws EmailChangeNotAllowedException, StorageQueryException {
        return ThirdParty.signInUp(main, thirdPartyId, thirdPartyUserId, email).user;
    }

    AuthRecipeUserInfo createPasswordlessUserWithEmail(Main main, String email)
            throws DuplicateLinkCodeHashException, StorageQueryException, NoSuchAlgorithmException, IOException,
            RestartFlowException, InvalidKeyException, Base64EncodingException, DeviceIdHashMismatchException,
            StorageTransactionLogicException, IncorrectUserInputCodeException, ExpiredUserInputCodeException {
        Passwordless.CreateCodeResponse code = Passwordless.createCode(main, email, null,
                null, "123456");
        return Passwordless.consumeCode(main, code.deviceId, code.deviceIdHash,
                code.userInputCode, null).user;
    }

    AuthRecipeUserInfo createPasswordlessUserWithPhone(Main main, String phone)
            throws DuplicateLinkCodeHashException, StorageQueryException, NoSuchAlgorithmException, IOException,
            RestartFlowException, InvalidKeyException, Base64EncodingException, DeviceIdHashMismatchException,
            StorageTransactionLogicException, IncorrectUserInputCodeException, ExpiredUserInputCodeException {
        Passwordless.CreateCodeResponse code = Passwordless.createCode(main, null, phone,
                null, "123456");
        return Passwordless.consumeCode(main, code.deviceId, code.deviceIdHash,
                code.userInputCode, null).user;
    }

    @Test
    public void testEmailPasswordSignUp() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args, false);
        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{
                        EE_FEATURES.ACCOUNT_LINKING, EE_FEATURES.MULTI_TENANCY});
        process.startProcess();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        JsonObject responseBody = new JsonObject();
        responseBody.addProperty("email", "random@gmail.com");
        responseBody.addProperty("password", "validPass123");

        JsonObject signUpResponse = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/signup", responseBody, 1000, 1000, null, SemVer.v4_0.get(),
                "emailpassword");

        assertEquals(signUpResponse.get("status").getAsString(), "OK");
        assertEquals(signUpResponse.entrySet().size(), 3);
        assertEquals(signUpResponse.get("recipeUserId"), signUpResponse.get("user").getAsJsonObject().get("id"));

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testEmailPasswordSignIn() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args, false);
        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{
                        EE_FEATURES.ACCOUNT_LINKING, EE_FEATURES.MULTI_TENANCY});
        process.startProcess();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        AuthRecipeUserInfo user = createEmailPasswordUser(process.getProcess(),
                "test@example.com", "password");

        {
            // Before account linking
            JsonObject responseBody = new JsonObject();
            responseBody.addProperty("email", "test@example.com");
            responseBody.addProperty("password", "password");

            JsonObject signInResponse = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/signin", responseBody, 1000, 1000, null, SemVer.v4_0.get(),
                    "emailpassword");

            assertEquals(signInResponse.get("status").getAsString(), "OK");
            assertEquals(signInResponse.entrySet().size(), 3);
            assertEquals(signInResponse.get("recipeUserId").getAsString(), user.getSupertokensUserId());
        }

        AuthRecipeUserInfo user2 = createPasswordlessUserWithEmail(process.getProcess(),
                "test@example.com");

        AuthRecipeUserInfo primaryUser = AuthRecipe.createPrimaryUser(process.getProcess(),
                user2.getSupertokensUserId()).user;
        AuthRecipe.linkAccounts(process.getProcess(), user.getSupertokensUserId(), primaryUser.getSupertokensUserId());

        {
            // After account linking
            JsonObject responseBody = new JsonObject();
            responseBody.addProperty("email", "test@example.com");
            responseBody.addProperty("password", "password");

            JsonObject signInResponse = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/signin", responseBody, 1000, 1000, null, SemVer.v4_0.get(),
                    "emailpassword");

            assertEquals(signInResponse.get("status").getAsString(), "OK");
            assertEquals(signInResponse.entrySet().size(), 3);
            assertEquals(signInResponse.get("recipeUserId").getAsString(), user.getSupertokensUserId());
        }

        // With another email password user
        AuthRecipeUserInfo user3 = createEmailPasswordUser(process.getProcess(),
                "test2@example.com", "password");
        AuthRecipe.linkAccounts(process.getProcess(), user3.getSupertokensUserId(), primaryUser.getSupertokensUserId());

        {
            // After account linking
            JsonObject responseBody = new JsonObject();
            responseBody.addProperty("email", "test@example.com");
            responseBody.addProperty("password", "password");

            JsonObject signInResponse = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/signin", responseBody, 1000, 1000, null, SemVer.v4_0.get(),
                    "emailpassword");

            assertEquals(signInResponse.get("status").getAsString(), "OK");
            assertEquals(signInResponse.entrySet().size(), 3);
            assertEquals(signInResponse.get("recipeUserId").getAsString(), user.getSupertokensUserId());
        }
        {
            // After account linking
            JsonObject responseBody = new JsonObject();
            responseBody.addProperty("email", "test2@example.com");
            responseBody.addProperty("password", "password");

            JsonObject signInResponse = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/signin", responseBody, 1000, 1000, null, SemVer.v4_0.get(),
                    "emailpassword");

            assertEquals(signInResponse.get("status").getAsString(), "OK");
            assertEquals(signInResponse.entrySet().size(), 3);
            assertEquals(signInResponse.get("recipeUserId").getAsString(), user3.getSupertokensUserId());
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testThirdPartySignInUp() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args, false);
        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{
                        EE_FEATURES.ACCOUNT_LINKING, EE_FEATURES.MULTI_TENANCY});
        process.startProcess();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        String userId = null;
        {
            JsonObject emailObject = new JsonObject();
            emailObject.addProperty("id", "test@example.com");
            emailObject.addProperty("isVerified", false);

            JsonObject signUpRequestBody = new JsonObject();
            signUpRequestBody.addProperty("thirdPartyId", "google");
            signUpRequestBody.addProperty("thirdPartyUserId", "google-user");
            signUpRequestBody.add("email", emailObject);

            JsonObject response = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/signinup", signUpRequestBody, 1000, 1000, null,
                    SemVer.v4_0.get(), "thirdparty");

            assertEquals(4, response.entrySet().size());
            assertEquals(response.get("recipeUserId"), response.get("user").getAsJsonObject().get("id"));
            userId = response.get("recipeUserId").getAsString();
        }

        {
            // Without account linking
            JsonObject emailObject = new JsonObject();
            emailObject.addProperty("id", "test@example.com");
            emailObject.addProperty("isVerified", false);

            JsonObject signUpRequestBody = new JsonObject();
            signUpRequestBody.addProperty("thirdPartyId", "google");
            signUpRequestBody.addProperty("thirdPartyUserId", "google-user");
            signUpRequestBody.add("email", emailObject);

            JsonObject response = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/signinup", signUpRequestBody, 1000, 1000, null,
                    SemVer.v4_0.get(), "thirdparty");

            assertEquals(4, response.entrySet().size());
            assertEquals(userId, response.get("recipeUserId").getAsString());
        }

        AuthRecipeUserInfo user2 = createEmailPasswordUser(process.getProcess(),
                "test@example.com", "password");

        AuthRecipeUserInfo primaryUser = AuthRecipe.createPrimaryUser(process.getProcess(),
                user2.getSupertokensUserId()).user;
        AuthRecipe.linkAccounts(process.getProcess(), userId, primaryUser.getSupertokensUserId());

        {
            // After account linking
            JsonObject emailObject = new JsonObject();
            emailObject.addProperty("id", "test@example.com");
            emailObject.addProperty("isVerified", false);

            JsonObject signUpRequestBody = new JsonObject();
            signUpRequestBody.addProperty("thirdPartyId", "google");
            signUpRequestBody.addProperty("thirdPartyUserId", "google-user");
            signUpRequestBody.add("email", emailObject);

            JsonObject response = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/signinup", signUpRequestBody, 1000, 1000, null,
                    SemVer.v4_0.get(), "thirdparty");

            assertEquals(4, response.entrySet().size());
            assertEquals(userId, response.get("recipeUserId").getAsString());
        }

        AuthRecipeUserInfo user3 = createThirdPartyUser(process.getProcess(), "facebook", "fb-user",
                "test@example.com");
        AuthRecipe.linkAccounts(process.getProcess(), user3.getSupertokensUserId(), primaryUser.getSupertokensUserId());

        {
            // After account linking
            JsonObject emailObject = new JsonObject();
            emailObject.addProperty("id", "test@example.com");
            emailObject.addProperty("isVerified", false);

            JsonObject signUpRequestBody = new JsonObject();
            signUpRequestBody.addProperty("thirdPartyId", "google");
            signUpRequestBody.addProperty("thirdPartyUserId", "google-user");
            signUpRequestBody.add("email", emailObject);

            JsonObject response = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/signinup", signUpRequestBody, 1000, 1000, null,
                    SemVer.v4_0.get(), "thirdparty");

            assertEquals(4, response.entrySet().size());
            assertEquals(userId, response.get("recipeUserId").getAsString());
        }
        {
            // After account linking
            JsonObject emailObject = new JsonObject();
            emailObject.addProperty("id", "test@example.com");
            emailObject.addProperty("isVerified", false);

            JsonObject signUpRequestBody = new JsonObject();
            signUpRequestBody.addProperty("thirdPartyId", "facebook");
            signUpRequestBody.addProperty("thirdPartyUserId", "fb-user");
            signUpRequestBody.add("email", emailObject);

            JsonObject response = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/signinup", signUpRequestBody, 1000, 1000, null,
                    SemVer.v4_0.get(), "thirdparty");

            assertEquals(4, response.entrySet().size());
            assertEquals(user3.getSupertokensUserId(), response.get("recipeUserId").getAsString());
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testPasswordlessConsumeCode() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args, false);
        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{
                        EE_FEATURES.ACCOUNT_LINKING, EE_FEATURES.MULTI_TENANCY});
        process.startProcess();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        String userId = null;
        {
            Passwordless.CreateCodeResponse createResp = Passwordless.createCode(process.getProcess(),
                    "test@example.com", null, null, null);

            JsonObject consumeCodeRequestBody = new JsonObject();
            consumeCodeRequestBody.addProperty("preAuthSessionId", createResp.deviceIdHash);
            consumeCodeRequestBody.addProperty("linkCode", createResp.linkCode);

            JsonObject response = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/signinup/code/consume", consumeCodeRequestBody, 1000, 1000, null,
                    SemVer.v4_0.get(), "passwordless");

            assertEquals(4, response.entrySet().size());
            assertEquals(response.get("recipeUserId"), response.get("user").getAsJsonObject().get("id"));
            userId = response.get("recipeUserId").getAsString();
        }

        { // Without account linking
            Passwordless.CreateCodeResponse createResp = Passwordless.createCode(process.getProcess(),
                    "test@example.com", null, null, null);

            JsonObject consumeCodeRequestBody = new JsonObject();
            consumeCodeRequestBody.addProperty("preAuthSessionId", createResp.deviceIdHash);
            consumeCodeRequestBody.addProperty("linkCode", createResp.linkCode);

            JsonObject response = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/signinup/code/consume", consumeCodeRequestBody, 1000, 1000, null,
                    SemVer.v4_0.get(), "passwordless");

            assertEquals(4, response.entrySet().size());
            assertEquals(userId, response.get("recipeUserId").getAsString());
        }

        AuthRecipeUserInfo user2 = createThirdPartyUser(process.getProcess(), "google", "google-user",
                "test@example.com");

        AuthRecipeUserInfo primaryUser = AuthRecipe.createPrimaryUser(process.getProcess(),
                user2.getSupertokensUserId()).user;
        AuthRecipe.linkAccounts(process.getProcess(), userId, primaryUser.getSupertokensUserId());

        { // after account linking
            Passwordless.CreateCodeResponse createResp = Passwordless.createCode(process.getProcess(),
                    "test@example.com", null, null, null);

            JsonObject consumeCodeRequestBody = new JsonObject();
            consumeCodeRequestBody.addProperty("preAuthSessionId", createResp.deviceIdHash);
            consumeCodeRequestBody.addProperty("linkCode", createResp.linkCode);

            JsonObject response = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/signinup/code/consume", consumeCodeRequestBody, 1000, 1000, null,
                    SemVer.v4_0.get(), "passwordless");

            assertEquals(4, response.entrySet().size());
            assertEquals(userId, response.get("recipeUserId").getAsString());
        }

        AuthRecipeUserInfo user3 = createPasswordlessUserWithEmail(process.getProcess(), "test2@example.com");
        AuthRecipe.linkAccounts(process.getProcess(), user3.getSupertokensUserId(), primaryUser.getSupertokensUserId());

        { // after account linking
            Passwordless.CreateCodeResponse createResp = Passwordless.createCode(process.getProcess(),
                    "test@example.com", null, null, null);

            JsonObject consumeCodeRequestBody = new JsonObject();
            consumeCodeRequestBody.addProperty("preAuthSessionId", createResp.deviceIdHash);
            consumeCodeRequestBody.addProperty("linkCode", createResp.linkCode);

            JsonObject response = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/signinup/code/consume", consumeCodeRequestBody, 1000, 1000, null,
                    SemVer.v4_0.get(), "passwordless");

            assertEquals(4, response.entrySet().size());
            assertEquals(userId, response.get("recipeUserId").getAsString());
        }
        { // after account linking
            Passwordless.CreateCodeResponse createResp = Passwordless.createCode(process.getProcess(),
                    "test2@example.com", null, null, null);

            JsonObject consumeCodeRequestBody = new JsonObject();
            consumeCodeRequestBody.addProperty("preAuthSessionId", createResp.deviceIdHash);
            consumeCodeRequestBody.addProperty("linkCode", createResp.linkCode);

            JsonObject response = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/signinup/code/consume", consumeCodeRequestBody, 1000, 1000, null,
                    SemVer.v4_0.get(), "passwordless");

            assertEquals(4, response.entrySet().size());
            assertEquals(user3.getSupertokensUserId(), response.get("recipeUserId").getAsString());
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testPasswordlessConsumeCodeForPhone() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args, false);
        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{
                        EE_FEATURES.ACCOUNT_LINKING, EE_FEATURES.MULTI_TENANCY});
        process.startProcess();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        String userId = null;
        {
            Passwordless.CreateCodeResponse createResp = Passwordless.createCode(process.getProcess(), null,
                    "+919876543210", null, null);

            JsonObject consumeCodeRequestBody = new JsonObject();
            consumeCodeRequestBody.addProperty("preAuthSessionId", createResp.deviceIdHash);
            consumeCodeRequestBody.addProperty("linkCode", createResp.linkCode);

            JsonObject response = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/signinup/code/consume", consumeCodeRequestBody, 1000, 1000, null,
                    SemVer.v4_0.get(), "passwordless");

            assertEquals(4, response.entrySet().size());
            assertEquals(response.get("recipeUserId"), response.get("user").getAsJsonObject().get("id"));
            userId = response.get("recipeUserId").getAsString();
        }

        { // Without account linking
            Passwordless.CreateCodeResponse createResp = Passwordless.createCode(process.getProcess(), null,
                    "+919876543210", null, null);

            JsonObject consumeCodeRequestBody = new JsonObject();
            consumeCodeRequestBody.addProperty("preAuthSessionId", createResp.deviceIdHash);
            consumeCodeRequestBody.addProperty("linkCode", createResp.linkCode);

            JsonObject response = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/signinup/code/consume", consumeCodeRequestBody, 1000, 1000, null,
                    SemVer.v4_0.get(), "passwordless");

            assertEquals(4, response.entrySet().size());
            assertEquals(userId, response.get("recipeUserId").getAsString());
        }

        AuthRecipeUserInfo user2 = createThirdPartyUser(process.getProcess(), "google", "google-user",
                "test@example.com");

        AuthRecipeUserInfo primaryUser = AuthRecipe.createPrimaryUser(process.getProcess(),
                user2.getSupertokensUserId()).user;
        AuthRecipe.linkAccounts(process.getProcess(), userId, primaryUser.getSupertokensUserId());

        { // after account linking
            Passwordless.CreateCodeResponse createResp = Passwordless.createCode(process.getProcess(), null,
                    "+919876543210", null, null);

            JsonObject consumeCodeRequestBody = new JsonObject();
            consumeCodeRequestBody.addProperty("preAuthSessionId", createResp.deviceIdHash);
            consumeCodeRequestBody.addProperty("linkCode", createResp.linkCode);

            JsonObject response = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/signinup/code/consume", consumeCodeRequestBody, 1000, 1000, null,
                    SemVer.v4_0.get(), "passwordless");

            assertEquals(4, response.entrySet().size());
            assertEquals(userId, response.get("recipeUserId").getAsString());
        }

        AuthRecipeUserInfo user3 = createPasswordlessUserWithPhone(process.getProcess(), "+919876543211");
        AuthRecipe.linkAccounts(process.getProcess(), user3.getSupertokensUserId(), primaryUser.getSupertokensUserId());

        { // after account linking
            Passwordless.CreateCodeResponse createResp = Passwordless.createCode(process.getProcess(), null,
                    "+919876543210", null, null);

            JsonObject consumeCodeRequestBody = new JsonObject();
            consumeCodeRequestBody.addProperty("preAuthSessionId", createResp.deviceIdHash);
            consumeCodeRequestBody.addProperty("linkCode", createResp.linkCode);

            JsonObject response = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/signinup/code/consume", consumeCodeRequestBody, 1000, 1000, null,
                    SemVer.v4_0.get(), "passwordless");

            assertEquals(4, response.entrySet().size());
            assertEquals(userId, response.get("recipeUserId").getAsString());
        }
        { // after account linking
            Passwordless.CreateCodeResponse createResp = Passwordless.createCode(process.getProcess(), null,
                    "+919876543211", null, null);

            JsonObject consumeCodeRequestBody = new JsonObject();
            consumeCodeRequestBody.addProperty("preAuthSessionId", createResp.deviceIdHash);
            consumeCodeRequestBody.addProperty("linkCode", createResp.linkCode);

            JsonObject response = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/signinup/code/consume", consumeCodeRequestBody, 1000, 1000, null,
                    SemVer.v4_0.get(), "passwordless");

            assertEquals(4, response.entrySet().size());
            assertEquals(user3.getSupertokensUserId(), response.get("recipeUserId").getAsString());
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testPasswordlessConsumeCodeForPhoneAndEmail() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args, false);
        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{
                        EE_FEATURES.ACCOUNT_LINKING, EE_FEATURES.MULTI_TENANCY});
        process.startProcess();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        String userId = null;
        {
            Passwordless.CreateCodeResponse createResp = Passwordless.createCode(process.getProcess(), null,
                    "+919876543210", null, null);

            JsonObject consumeCodeRequestBody = new JsonObject();
            consumeCodeRequestBody.addProperty("preAuthSessionId", createResp.deviceIdHash);
            consumeCodeRequestBody.addProperty("linkCode", createResp.linkCode);

            JsonObject response = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/signinup/code/consume", consumeCodeRequestBody, 1000, 1000, null,
                    SemVer.v4_0.get(), "passwordless");

            assertEquals(4, response.entrySet().size());
            assertEquals(response.get("recipeUserId"), response.get("user").getAsJsonObject().get("id"));
            userId = response.get("recipeUserId").getAsString();
        }

        Passwordless.updateUser(process.getProcess(), userId, new Passwordless.FieldUpdate("test@example.com"), null);

        { // Without account linking - phone
            Passwordless.CreateCodeResponse createResp = Passwordless.createCode(process.getProcess(), null,
                    "+919876543210", null, null);

            JsonObject consumeCodeRequestBody = new JsonObject();
            consumeCodeRequestBody.addProperty("preAuthSessionId", createResp.deviceIdHash);
            consumeCodeRequestBody.addProperty("linkCode", createResp.linkCode);

            JsonObject response = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/signinup/code/consume", consumeCodeRequestBody, 1000, 1000, null,
                    SemVer.v4_0.get(), "passwordless");

            assertEquals(4, response.entrySet().size());
            assertEquals(userId, response.get("recipeUserId").getAsString());
        }
        { // Without account linking - email
            Passwordless.CreateCodeResponse createResp = Passwordless.createCode(process.getProcess(),
                    "test@example.com", null, null, null);

            JsonObject consumeCodeRequestBody = new JsonObject();
            consumeCodeRequestBody.addProperty("preAuthSessionId", createResp.deviceIdHash);
            consumeCodeRequestBody.addProperty("linkCode", createResp.linkCode);

            JsonObject response = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/signinup/code/consume", consumeCodeRequestBody, 1000, 1000, null,
                    SemVer.v4_0.get(), "passwordless");

            assertEquals(4, response.entrySet().size());
            assertEquals(userId, response.get("recipeUserId").getAsString());
        }

        AuthRecipeUserInfo user2 = createThirdPartyUser(process.getProcess(), "google", "google-user",
                "test@example.com");

        AuthRecipeUserInfo primaryUser = AuthRecipe.createPrimaryUser(process.getProcess(),
                user2.getSupertokensUserId()).user;
        AuthRecipe.linkAccounts(process.getProcess(), userId, primaryUser.getSupertokensUserId());

        { // after account linking - phone
            Passwordless.CreateCodeResponse createResp = Passwordless.createCode(process.getProcess(), null,
                    "+919876543210", null, null);

            JsonObject consumeCodeRequestBody = new JsonObject();
            consumeCodeRequestBody.addProperty("preAuthSessionId", createResp.deviceIdHash);
            consumeCodeRequestBody.addProperty("linkCode", createResp.linkCode);

            JsonObject response = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/signinup/code/consume", consumeCodeRequestBody, 1000, 1000, null,
                    SemVer.v4_0.get(), "passwordless");

            assertEquals(4, response.entrySet().size());
            assertEquals(userId, response.get("recipeUserId").getAsString());
        }
        { // after account linking - email
            Passwordless.CreateCodeResponse createResp = Passwordless.createCode(process.getProcess(),
                    "test@example.com", null, null, null);

            JsonObject consumeCodeRequestBody = new JsonObject();
            consumeCodeRequestBody.addProperty("preAuthSessionId", createResp.deviceIdHash);
            consumeCodeRequestBody.addProperty("linkCode", createResp.linkCode);

            JsonObject response = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/signinup/code/consume", consumeCodeRequestBody, 1000, 1000, null,
                    SemVer.v4_0.get(), "passwordless");

            assertEquals(4, response.entrySet().size());
            assertEquals(userId, response.get("recipeUserId").getAsString());
        }

        AuthRecipeUserInfo user3 = createPasswordlessUserWithPhone(process.getProcess(), "+919876543211");
        AuthRecipe.linkAccounts(process.getProcess(), user3.getSupertokensUserId(), primaryUser.getSupertokensUserId());

        { // after account linking - phone
            Passwordless.CreateCodeResponse createResp = Passwordless.createCode(process.getProcess(), null,
                    "+919876543210", null, null);

            JsonObject consumeCodeRequestBody = new JsonObject();
            consumeCodeRequestBody.addProperty("preAuthSessionId", createResp.deviceIdHash);
            consumeCodeRequestBody.addProperty("linkCode", createResp.linkCode);

            JsonObject response = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/signinup/code/consume", consumeCodeRequestBody, 1000, 1000, null,
                    SemVer.v4_0.get(), "passwordless");

            assertEquals(4, response.entrySet().size());
            assertEquals(userId, response.get("recipeUserId").getAsString());
        }
        { // after account linking - email
            Passwordless.CreateCodeResponse createResp = Passwordless.createCode(process.getProcess(),
                    "test@example.com", null, null, null);

            JsonObject consumeCodeRequestBody = new JsonObject();
            consumeCodeRequestBody.addProperty("preAuthSessionId", createResp.deviceIdHash);
            consumeCodeRequestBody.addProperty("linkCode", createResp.linkCode);

            JsonObject response = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/signinup/code/consume", consumeCodeRequestBody, 1000, 1000, null,
                    SemVer.v4_0.get(), "passwordless");

            assertEquals(4, response.entrySet().size());
            assertEquals(userId, response.get("recipeUserId").getAsString());
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testWithEmailPasswordUserWithUserIdMapping() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args, false);
        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{
                        EE_FEATURES.ACCOUNT_LINKING, EE_FEATURES.MULTI_TENANCY});
        process.startProcess();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        AuthRecipeUserInfo user = createEmailPasswordUser(process.getProcess(),
                "test@example.com", "password");
        UserIdMapping.createUserIdMapping(process.getProcess(), user.getSupertokensUserId(), "extuserid", "", false);

        {
            // Before account linking
            JsonObject responseBody = new JsonObject();
            responseBody.addProperty("email", "test@example.com");
            responseBody.addProperty("password", "password");

            JsonObject signInResponse = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/signin", responseBody, 1000, 1000, null, SemVer.v4_0.get(),
                    "emailpassword");

            assertEquals(signInResponse.get("status").getAsString(), "OK");
            assertEquals(signInResponse.entrySet().size(), 3);
            assertEquals(signInResponse.get("recipeUserId").getAsString(), "extuserid");
        }

        AuthRecipeUserInfo user2 = createPasswordlessUserWithEmail(process.getProcess(),
                "test@example.com");

        AuthRecipeUserInfo primaryUser = AuthRecipe.createPrimaryUser(process.getProcess(),
                user2.getSupertokensUserId()).user;
        AuthRecipe.linkAccounts(process.getProcess(), user.getSupertokensUserId(), primaryUser.getSupertokensUserId());

        {
            // After account linking
            JsonObject responseBody = new JsonObject();
            responseBody.addProperty("email", "test@example.com");
            responseBody.addProperty("password", "password");

            JsonObject signInResponse = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/signin", responseBody, 1000, 1000, null, SemVer.v4_0.get(),
                    "emailpassword");

            assertEquals(signInResponse.get("status").getAsString(), "OK");
            assertEquals(signInResponse.entrySet().size(), 3);
            assertEquals(signInResponse.get("recipeUserId").getAsString(), "extuserid");
        }

        // With another email password user
        AuthRecipeUserInfo user3 = createEmailPasswordUser(process.getProcess(),
                "test2@example.com", "password");
        AuthRecipe.linkAccounts(process.getProcess(), user3.getSupertokensUserId(), primaryUser.getSupertokensUserId());

        {
            // After account linking
            JsonObject responseBody = new JsonObject();
            responseBody.addProperty("email", "test@example.com");
            responseBody.addProperty("password", "password");

            JsonObject signInResponse = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/signin", responseBody, 1000, 1000, null, SemVer.v4_0.get(),
                    "emailpassword");

            assertEquals(signInResponse.get("status").getAsString(), "OK");
            assertEquals(signInResponse.entrySet().size(), 3);
            assertEquals(signInResponse.get("recipeUserId").getAsString(), "extuserid");
        }
        {
            // After account linking
            JsonObject responseBody = new JsonObject();
            responseBody.addProperty("email", "test2@example.com");
            responseBody.addProperty("password", "password");

            JsonObject signInResponse = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/signin", responseBody, 1000, 1000, null, SemVer.v4_0.get(),
                    "emailpassword");

            assertEquals(signInResponse.get("status").getAsString(), "OK");
            assertEquals(signInResponse.entrySet().size(), 3);
            assertEquals(signInResponse.get("recipeUserId").getAsString(), user3.getSupertokensUserId());
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }
}
