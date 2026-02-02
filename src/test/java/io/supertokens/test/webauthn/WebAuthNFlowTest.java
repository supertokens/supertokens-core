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

package io.supertokens.test.webauthn;

import com.google.gson.JsonObject;
import io.supertokens.ProcessState;
import io.supertokens.featureflag.EE_FEATURES;
import io.supertokens.featureflag.FeatureFlagTestContent;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.pluginInterface.authRecipe.AuthRecipeUserInfo;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.test.TestingProcessManager;
import io.supertokens.test.httpRequest.HttpRequestForTesting;
import io.supertokens.utils.SemVer;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class WebAuthNFlowTest {

    @Rule
    public TestRule watchman = io.supertokens.test.Utils.getOnFailure();

    @AfterClass
    public static void afterTesting() {
        io.supertokens.test.Utils.afterTesting();
    }

    @Before
    public void beforeEach() {
        io.supertokens.test.Utils.reset();
    }

    @Test
    public void registerWebAuthNAndEmailPasswordUsersWithUIDMappingAndAccountLinkingAndSignInTest() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{
                        EE_FEATURES.ACCOUNT_LINKING, EE_FEATURES.MULTI_TENANCY});
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        int numberOfUsers = 1;  //10k users

        List<JsonObject> users = io.supertokens.test.webauthn.Utils.registerUsers(process.getProcess(), numberOfUsers);
        List<AuthRecipeUserInfo> epUsers = Utils.createEmailPasswordUsers(process, numberOfUsers, true);

        int w = 0;
        for (JsonObject user : users) {
            String userId = user.getAsJsonObject("user").get("id").getAsString();
            Utils.createUserIdMapping(process.getProcess(), userId, "waexternal_" + userId);
            Utils.verifyEmailFor(process.getProcess(), userId, "user" + w++ + "@example.com");
        }


        int i = 0;
        for (AuthRecipeUserInfo user : epUsers) {
            Utils.createUserIdMapping(process.getProcess(), user.getSupertokensUserId(), "external_" + user.getSupertokensUserId());
            Utils.verifyEmailFor(process.getProcess(), user.getSupertokensUserId(), "user" + i++ + "@example.com");
        }

        Utils.linkAccounts(process.getProcess(), epUsers.stream().map(AuthRecipeUserInfo::getSupertokensUserId).collect(
                Collectors.toList()), users.stream().map(u -> u.getAsJsonObject("user").get("id").getAsString()).collect(Collectors.toList()));

        JsonObject signInResponse = io.supertokens.test.webauthn.Utils.signInWithUser(process.getProcess(), users.get(0));

        JsonObject signInEPRequest = new JsonObject();
        signInEPRequest.addProperty("email", "user0@example.com");
        signInEPRequest.addProperty("password", "password");

        Thread.sleep(1);
        JsonObject ePSignInResponse = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/signin", signInEPRequest, 1000, 1000, null, SemVer.v5_3.get(),
                "emailpassword");
        assertEquals("OK", ePSignInResponse.get("status").getAsString());

        ePSignInResponse.remove("recipeUserId");
        signInResponse.remove("recipeUserId");
        assertEquals(signInResponse, ePSignInResponse);
    }

    @Test
    public void registerEPAndWebauthNUserRemoveCredentialRegisterCredential() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{
                        EE_FEATURES.ACCOUNT_LINKING, EE_FEATURES.MULTI_TENANCY});
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        int numberOfUsers = 1;

        List<AuthRecipeUserInfo> epUsers = Utils.createEmailPasswordUsers(process, numberOfUsers, true);
        List<JsonObject> users = io.supertokens.test.webauthn.Utils.registerUsers(process.getProcess(), numberOfUsers);

        int w = 0;
        for (JsonObject user : users) {
            String userId = user.getAsJsonObject("user").get("id").getAsString();
            Utils.verifyEmailFor(process.getProcess(), userId, "user" + w++ + "@example.com");
        }


        int i = 0;
        for (AuthRecipeUserInfo user : epUsers) {
            Utils.verifyEmailFor(process.getProcess(), user.getSupertokensUserId(), "user" + i++ + "@example.com");
        }


        //link accounts
        Utils.linkAccounts(process.getProcess(), epUsers.stream().map(AuthRecipeUserInfo::getSupertokensUserId).collect(
                Collectors.toList()), users.stream().map(u -> u.getAsJsonObject("user").get("id").getAsString())
                .collect(Collectors.toList()));

        //sign in with WA user
        JsonObject signInResponse = io.supertokens.test.webauthn.Utils.signInWithUser(process.getProcess(),
                users.get(0));
//        System.out.println(signInResponse);
        String webauthnRecipeUserId = signInResponse.get("recipeUserId").getAsString();
//        System.out.println(signInResponse.get("user").getAsJsonObject().get("webauthn").getAsJsonObject()
//                .get("credentialIds"));
        String credentialId = signInResponse.get("user").getAsJsonObject().get("webauthn").getAsJsonObject()
                .get("credentialIds").getAsJsonArray().get(0).getAsString();

        // sign in with EP user
        JsonObject signInEPRequest = new JsonObject();
        signInEPRequest.addProperty("email", "user0@example.com");
        signInEPRequest.addProperty("password", "password");

        Thread.sleep(1);
        JsonObject ePSignInResponse = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/signin", signInEPRequest, 1000, 1000, null, SemVer.v5_3.get(),
                "emailpassword");
        assertEquals("OK", ePSignInResponse.get("status").getAsString());

        //the two responses are the same apart from the recipeUserId
        ePSignInResponse.remove("recipeUserId");
        signInResponse.remove("recipeUserId");
        assertEquals(signInResponse, ePSignInResponse);

        //remove credential for WA user
        JsonObject removeCredentialResponse = HttpRequestForTesting.sendJsonDELETERequestWithQueryParams(
                process.getProcess(), "",
                "http://localhost:3567/recipe/webauthn/user/credential/remove",
                Map.of("recipeUserId", webauthnRecipeUserId, "webauthnCredentialId", credentialId), 1000, 1000,
                null, SemVer.v5_3.get(), "webauthn");

        assertEquals("OK", removeCredentialResponse.get("status").getAsString());

        JsonObject listCredentialsResponse = HttpRequestForTesting.sendGETRequest(process.getProcess(), null,
                "http://localhost:3567/recipe/webauthn/user/credential/list",
                Map.of("recipeUserId", webauthnRecipeUserId), 1000, 1000, null, SemVer.v5_3.get(), "webauthn");

        assertEquals("OK", listCredentialsResponse.get("status").getAsString());

        //System.out.println(listCredentialsResponse);

        //create and register credential for user
        JsonObject registerCredentialResponse = Utils.registerCredentialForUser(process.getProcess(),
                signInResponse.get("user").getAsJsonObject().get("emails").getAsJsonArray().get(0).getAsString(),
                webauthnRecipeUserId);

        //System.out.println(registerCredentialResponse);
        assertEquals("OK", registerCredentialResponse.get("status").getAsString());

    }

}
