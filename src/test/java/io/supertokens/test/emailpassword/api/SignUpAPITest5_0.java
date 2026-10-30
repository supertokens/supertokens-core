/*
 *    Copyright (c) 2021, VRAI Labs and/or its affiliates. All rights reserved.
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
import io.supertokens.ActiveUsers;
import io.supertokens.ProcessState;
import io.supertokens.emailpassword.EmailPassword;
import io.supertokens.authRecipe.AuthRecipe;
import io.supertokens.emailverification.EmailVerification;
import io.supertokens.featureflag.EE_FEATURES;
import io.supertokens.featureflag.FeatureFlagTestContent;
import io.supertokens.multitenancy.Multitenancy;
import io.supertokens.pluginInterface.multitenancy.EmailPasswordConfig;
import io.supertokens.pluginInterface.multitenancy.PasswordlessConfig;
import io.supertokens.pluginInterface.multitenancy.TenantConfig;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifier;
import io.supertokens.pluginInterface.multitenancy.ThirdPartyConfig;
import io.supertokens.passwordless.Passwordless;
import io.supertokens.pluginInterface.authRecipe.AuthRecipeUserInfo;
import io.supertokens.thirdparty.ThirdParty;
import io.supertokens.useridmapping.UserIdMapping;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.test.TestingProcessManager;
import io.supertokens.test.Utils;
import io.supertokens.test.httpRequest.HttpRequestForTesting;
import io.supertokens.utils.SemVer;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import java.util.HashMap;

import static org.junit.Assert.*;


public class SignUpAPITest5_0 {

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

    // Check good input works
    @Test
    public void testGoodInput() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        JsonObject responseBody = new JsonObject();
        responseBody.addProperty("email", "random@gmail.com");
        responseBody.addProperty("password", "validPass123");

        Thread.sleep(1); // add a small delay to ensure a unique timestamp
        long beforeSignIn = System.currentTimeMillis();

        JsonObject signInResponse = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/signup", responseBody, 1000, 1000, null, SemVer.v5_0.get(),
                "emailpassword");

        assertEquals(signInResponse.get("status").getAsString(), "OK");
        assertEquals(signInResponse.entrySet().size(), 3);

        JsonObject jsonUser = signInResponse.get("user").getAsJsonObject();
        assertNotNull(jsonUser.get("id"));
        assertNotNull(jsonUser.get("timeJoined"));
        assert (!jsonUser.get("isPrimaryUser").getAsBoolean());
        assert (jsonUser.get("emails").getAsJsonArray().size() == 1);
        assert (jsonUser.get("emails").getAsJsonArray().get(0).getAsString().equals("random@gmail.com"));
        assert (jsonUser.get("phoneNumbers").getAsJsonArray().size() == 0);
        assert (jsonUser.get("thirdParty").getAsJsonArray().size() == 0);
        assert (jsonUser.get("loginMethods").getAsJsonArray().size() == 1);
        JsonObject lM = jsonUser.get("loginMethods").getAsJsonArray().get(0).getAsJsonObject();
        assertFalse(lM.get("verified").getAsBoolean());
        assertNotNull(lM.get("timeJoined"));
        assertNotNull(lM.get("recipeUserId"));
        assertEquals(lM.get("recipeId").getAsString(), "emailpassword");
        assertEquals(lM.get("email").getAsString(), "random@gmail.com");
        assert (lM.entrySet().size() == 6);

        int activeUsers = ActiveUsers.countUsersActiveSince(process.getProcess(), beforeSignIn);
        assert (activeUsers == 1);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testSignUpWithPlaceholderEmailOfUserWithoutEmailMarksTheEmailAsVerified() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        // The placeholder the SDKs generate when a user without any email adds a password factor:
        // <existing user id>@stfakeemail.supertokens.com
        AuthRecipeUserInfo existingUser = createPhoneOnlyUser(process, "+442071838750");
        String placeholderEmail = existingUser.getSupertokensUserId() + "@stfakeemail.supertokens.com";

        JsonObject responseBody = new JsonObject();
        responseBody.addProperty("email", placeholderEmail);
        responseBody.addProperty("password", "validPass123");

        Thread.sleep(1); // add a small delay to ensure a unique timestamp
        long beforeSignIn = System.currentTimeMillis();

        JsonObject signInResponse = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/signup", responseBody, 1000, 1000, null, SemVer.v5_0.get(),
                "emailpassword");

        assertEquals(signInResponse.get("status").getAsString(), "OK");
        assertEquals(signInResponse.entrySet().size(), 3);

        JsonObject jsonUser = signInResponse.get("user").getAsJsonObject();
        assertNotNull(jsonUser.get("id"));
        assertNotNull(jsonUser.get("timeJoined"));
        assert (!jsonUser.get("isPrimaryUser").getAsBoolean());
        assert (jsonUser.get("emails").getAsJsonArray().size() == 1);
        assert (jsonUser.get("emails").getAsJsonArray().get(0).getAsString()
                .equals(placeholderEmail));
        assert (jsonUser.get("phoneNumbers").getAsJsonArray().size() == 0);
        assert (jsonUser.get("thirdParty").getAsJsonArray().size() == 0);
        assert (jsonUser.get("loginMethods").getAsJsonArray().size() == 1);
        JsonObject lM = jsonUser.get("loginMethods").getAsJsonArray().get(0).getAsJsonObject();
        assertTrue(lM.get("verified").getAsBoolean()); // Email must be verified
        assertNotNull(lM.get("timeJoined"));
        assertNotNull(lM.get("recipeUserId"));
        assertEquals(lM.get("recipeId").getAsString(), "emailpassword");
        assertEquals(lM.get("email").getAsString(), placeholderEmail);
        assert (lM.entrySet().size() == 6);

        int activeUsers = ActiveUsers.countUsersActiveSince(process.getProcess(), beforeSignIn);
        assert (activeUsers == 1);

        // double ensure that the email is verified using email verification

        String userId = jsonUser.get("id").getAsString();

        HashMap<String, String> map = new HashMap<>();
        map.put("userId", userId);
        map.put("email", placeholderEmail);

        JsonObject verifyResponse = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/user/email/verify", map, 1000, 1000, null,
                SemVer.v2_7.get(), "emailverification");
        assertEquals(verifyResponse.entrySet().size(), 2);
        assertEquals(verifyResponse.get("status").getAsString(), "OK");
        assertTrue(verifyResponse.get("isVerified").getAsBoolean());

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    private static JsonObject signUpAndGetLoginMethod(TestingProcessManager.TestingProcess process, String email)
            throws Exception {
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("email", email);
        requestBody.addProperty("password", "validPass123");
        JsonObject response = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/signup", requestBody, 1000, 1000, null, SemVer.v5_0.get(),
                "emailpassword");
        assertEquals("OK", response.get("status").getAsString());
        JsonObject user = response.get("user").getAsJsonObject();
        assertEquals(1, user.get("loginMethods").getAsJsonArray().size());
        return user.get("loginMethods").getAsJsonArray().get(0).getAsJsonObject();
    }

    private static void assertSignUpVerified(TestingProcessManager.TestingProcess process, String email,
                                             boolean expectedVerified) throws Exception {
        JsonObject lM = signUpAndGetLoginMethod(process, email);
        String normalisedEmail = io.supertokens.utils.Utils.normaliseEmail(email);
        assertEquals(normalisedEmail, lM.get("email").getAsString());
        assertEquals(email, expectedVerified, lM.get("verified").getAsBoolean());
        // the stored verification state must match what sign-up returned
        assertEquals(email, expectedVerified, EmailVerification.isEmailVerified(process.getProcess(),
                lM.get("recipeUserId").getAsString(), normalisedEmail));
    }

    private static AuthRecipeUserInfo createPhoneOnlyUser(TestingProcessManager.TestingProcess process,
                                                          String phoneNumber) throws Exception {
        Passwordless.CreateCodeResponse code = Passwordless.createCode(process.getProcess(), null, phoneNumber,
                null, null);
        return Passwordless.consumeCode(process.getProcess(), code.deviceId, code.deviceIdHash, code.userInputCode,
                null).user;
    }

    @Test
    public void testIssue107_signUpDoesNotAutoVerifyAttackerChosenFakeEmail() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{
                        EE_FEATURES.ACCOUNT_LINKING, EE_FEATURES.MULTI_TENANCY});
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        // A tenant of this (the test's) app with a custom (non built-in) third-party provider
        TenantIdentifier app = process.getAppForTesting();
        Multitenancy.addNewOrUpdateAppOrTenant(process.getProcess(), app,
                new TenantConfig(new TenantIdentifier(app.getConnectionUriDomain(), app.getAppId(), "t1"),
                        new EmailPasswordConfig(true),
                        new ThirdPartyConfig(true, new ThirdPartyConfig.Provider[]{
                                new ThirdPartyConfig.Provider("corp-idp", "Corp IdP", null, null, null, null, null,
                                        null, null, null, null, null, null, null)}),
                        new PasswordlessConfig(true), null, null, new JsonObject()));

        // A third-party style placeholder whose third-party user does not exist (yet) is not verified
        assertSignUpVerified(process, "victimtwitterid.twitter@stfakeemail.supertokens.com", false);

        // Nor is it verified when the third-party user holding that placeholder exists: core cannot tell who is
        // signing up, and the SDK links it to its owner through the session user's own verified login method.
        ThirdParty.signInUp(process.getProcess(), "google", "tpuser1", "tpuser1.google@stfakeemail.supertokens.com");
        assertSignUpVerified(process, "tpuser1.google@stfakeemail.supertokens.com", false);

        // Addresses on *.fakeemail.com (a domain SuperTokens does not control) are not verified
        assertSignUpVerified(process, "someone@attacker.fakeemail.com", false);
        assertSignUpVerified(process, "victimid@twitter.fakeemail.com", false);

        // A placeholder on the SuperTokens domain whose local part is not a user id of this app is not verified
        assertSignUpVerified(process, java.util.UUID.randomUUID() + "@stfakeemail.supertokens.com", false);
        assertSignUpVerified(process, "@stfakeemail.supertokens.com", false);

        // An external user id the caller could choose does not make the placeholder verified: here an account with
        // an email holds the external id "victimtwitterid2.twitter" (third-party placeholder shape) ...
        AuthRecipeUserInfo userWithChosenId = EmailPassword.signUp(process.getProcess(), "chooser@example.com",
                "validPass123");
        UserIdMapping.createUserIdMapping(process.getProcess(), userWithChosenId.getSupertokensUserId(),
                "victimtwitterid2.twitter", null, false);
        assertSignUpVerified(process, "victimtwitterid2.twitter@stfakeemail.supertokens.com", false);
        // ... and the same id shape is refused even when its user has no email at all
        AuthRecipeUserInfo phoneUserWithChosenId = createPhoneOnlyUser(process, "+442071838751");
        UserIdMapping.createUserIdMapping(process.getProcess(), phoneUserWithChosenId.getSupertokensUserId(),
                "victimtwitterid3.twitter", null, false);
        assertSignUpVerified(process, "victimtwitterid3.twitter@stfakeemail.supertokens.com", false);
        // ... also for other built-in provider ids (matched by prefix, like the SDKs do) and for a provider configured
        // in a tenant of the app
        AuthRecipeUserInfo phoneUserWithGithubShapedId = createPhoneOnlyUser(process, "+442071838756");
        UserIdMapping.createUserIdMapping(process.getProcess(), phoneUserWithGithubShapedId.getSupertokensUserId(),
                "jane.github", null, false);
        assertSignUpVerified(process, "jane.github@stfakeemail.supertokens.com", false);
        AuthRecipeUserInfo phoneUserWithWorkspacesShapedId = createPhoneOnlyUser(process, "+442071838757");
        UserIdMapping.createUserIdMapping(process.getProcess(),
                phoneUserWithWorkspacesShapedId.getSupertokensUserId(), "12345.google-workspaces", null, false);
        assertSignUpVerified(process, "12345.google-workspaces@stfakeemail.supertokens.com", false);
        AuthRecipeUserInfo phoneUserWithCustomProviderShapedId = createPhoneOnlyUser(process, "+442071838758");
        UserIdMapping.createUserIdMapping(process.getProcess(),
                phoneUserWithCustomProviderShapedId.getSupertokensUserId(), "alice.Corp-IdP", null, false);
        assertSignUpVerified(process, "alice.Corp-IdP@stfakeemail.supertokens.com", false);
        // ... and an external id without that shape, whose user has an email, is refused too
        UserIdMapping.createUserIdMapping(process.getProcess(),
                EmailPassword.signUp(process.getProcess(), "handle@example.com", "validPass123")
                        .getSupertokensUserId(), "attackerhandle", null, false);
        assertSignUpVerified(process, "attackerhandle@stfakeemail.supertokens.com", false);

        // The id of an existing user that has an email is not verified (the SDKs only generate the placeholder for
        // users without any email)
        AuthRecipeUserInfo userWithEmail = EmailPassword.signUp(process.getProcess(), "existing@example.com",
                "validPass123");
        assertSignUpVerified(process, userWithEmail.getSupertokensUserId() + "@stfakeemail.supertokens.com", false);

        // ... also when the email is on another login method of the same primary user
        AuthRecipeUserInfo linkedPhoneUser = createPhoneOnlyUser(process, "+442071838752");
        AuthRecipe.createPrimaryUser(process.getProcess(), userWithEmail.getSupertokensUserId());
        AuthRecipe.linkAccounts(process.getProcess(), linkedPhoneUser.getSupertokensUserId(),
                userWithEmail.getSupertokensUserId());
        assertSignUpVerified(process, linkedPhoneUser.getSupertokensUserId() + "@stfakeemail.supertokens.com", false);

        // <id of a user without any email>@stfakeemail.supertokens.com (the SDK's password-factor placeholder) is
        // still verified
        AuthRecipeUserInfo phoneOnlyUser = createPhoneOnlyUser(process, "+442071838753");
        assertSignUpVerified(process, phoneOnlyUser.getSupertokensUserId() + "@stfakeemail.supertokens.com", true);

        // ... also when that user's id is a mapped external user id, including a mixed-case one
        AuthRecipeUserInfo mappedPhoneOnlyUser = createPhoneOnlyUser(process, "+442071838754");
        UserIdMapping.createUserIdMapping(process.getProcess(), mappedPhoneOnlyUser.getSupertokensUserId(),
                "ExtUser-AbC", null, false);
        assertSignUpVerified(process, "ExtUser-AbC@stfakeemail.supertokens.com", true);

        // ... and when that mapped external id contains a '.' but is not a third-party placeholder shape
        AuthRecipeUserInfo dottedPhoneOnlyUser = createPhoneOnlyUser(process, "+442071838759");
        UserIdMapping.createUserIdMapping(process.getProcess(), dottedPhoneOnlyUser.getSupertokensUserId(),
                "john.doe", null, false);
        assertSignUpVerified(process, "john.doe@stfakeemail.supertokens.com", true);

        // ... but not for the same external id with a different case, which is not a user id of this app
        AuthRecipeUserInfo mappedPhoneOnlyUser2 = createPhoneOnlyUser(process, "+442071838755");
        UserIdMapping.createUserIdMapping(process.getProcess(), mappedPhoneOnlyUser2.getSupertokensUserId(),
                "OtherExt", null, false);
        assertSignUpVerified(process, "OTHEREXT@stfakeemail.supertokens.com", false);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }
}
