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

package io.supertokens.test.session;

import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import io.supertokens.ProcessState;
import io.supertokens.authRecipe.AuthRecipe;
import io.supertokens.emailpassword.EmailPassword;
import io.supertokens.featureflag.EE_FEATURES;
import io.supertokens.featureflag.FeatureFlagTestContent;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.pluginInterface.authRecipe.AuthRecipeUserInfo;
import io.supertokens.pluginInterface.multitenancy.AppIdentifier;
import io.supertokens.pluginInterface.useridmapping.UserIdMapping;
import io.supertokens.pluginInterface.useridmapping.UserIdMappingStorage;
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
import static org.junit.Assert.*;

public class UserIdMappingTest {
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
    public void testCreatingAUserIdMappingAndSession() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{
                        EE_FEATURES.ACCOUNT_LINKING, EE_FEATURES.MULTI_TENANCY});
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        UserIdMappingStorage storage = (UserIdMappingStorage) StorageLayer.getStorage(process.main);

        // create a User
        AuthRecipeUserInfo userInfo = EmailPassword.signUp(process.main, "test@example.com", "testPass123");
        AuthRecipeUserInfo userInfo2 = EmailPassword.signUp(process.main, "test2@example.com", "testPass123");

        AuthRecipe.createPrimaryUser(process.main, userInfo.getSupertokensUserId());

        AuthRecipe.linkAccounts(process.main, userInfo2.getSupertokensUserId(),
                userInfo.getSupertokensUserId());

        String superTokensUserId = userInfo.getSupertokensUserId();
        String externalUserId = "externaluserId";
        String externalUserIdInfo = "externUserIdInfo";

        JsonObject requestBody = new JsonObject();

        requestBody.addProperty("superTokensUserId", superTokensUserId);
        requestBody.addProperty("externalUserId", externalUserId);
        requestBody.addProperty("externalUserIdInfo", externalUserIdInfo);

        JsonObject response = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/userid/map", requestBody, 1000, 1000, null,
                SemVer.v2_15.get(), "useridmapping");

        assertEquals(1, response.entrySet().size());
        assertEquals("OK", response.get("status").getAsString());

        // check that userIdMapping was created

        UserIdMapping userIdMapping = storage.getUserIdMapping(new AppIdentifier(null, null),
                superTokensUserId, true);

        assertEquals(superTokensUserId, userIdMapping.superTokensUserId);
        assertEquals(externalUserId, userIdMapping.externalUserId);
        assertEquals(externalUserIdInfo, userIdMapping.externalUserIdInfo);

        JsonObject signInRequest = new JsonObject();
        signInRequest.addProperty("email", "test2@example.com");
        signInRequest.addProperty("password", "testPass123");

        Thread.sleep(1); // add a small delay to ensure a unique timestamp
        long beforeSignIn = System.currentTimeMillis();

        JsonObject signInResponse = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/signin", signInRequest, 1000, 1000, null, SemVer.v4_0.get(),
                "emailpassword");

        JsonObject userDataInJWT = new JsonObject();
        userDataInJWT.add("nullProp", JsonNull.INSTANCE);
        userDataInJWT.addProperty("key", "value");
        JsonObject userDataInDatabase = new JsonObject();
        userDataInDatabase.addProperty("key", "value");

        JsonObject request = new JsonObject();
        request.addProperty("userId", signInResponse.get("recipeUserId").getAsString());
        request.add("userDataInJWT", userDataInJWT);
        request.add("userDataInDatabase", userDataInDatabase);
        request.addProperty("enableAntiCsrf", false);

        JsonObject sessionInfo = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/session", request, 1000, 1000, null, SemVer.v5_0.get(),
                "session");
        assertEquals(sessionInfo.get("status").getAsString(), "OK");

        JsonObject sessionRefreshBody = new JsonObject();

        sessionRefreshBody.addProperty("refreshToken",
                sessionInfo.get("refreshToken").getAsJsonObject().get("token").getAsString());
        sessionRefreshBody.addProperty("enableAntiCsrf", false);
        sessionRefreshBody.addProperty("useDynamicSigningKey", true);

        JsonObject sessionRefreshResponse = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/session/refresh", sessionRefreshBody, 1000, 1000, null,
                SemVer.v5_0.get(), "session");

        assertEquals(
                sessionInfo.get("session").getAsJsonObject().get("handle").getAsString(),
                sessionRefreshResponse.get("session").getAsJsonObject().get("handle").getAsString()
        );
        assertEquals(
                sessionInfo.get("session").getAsJsonObject().get("userId").getAsString(),
                sessionRefreshResponse.get("session").getAsJsonObject().get("userId").getAsString()
        );
        assertEquals(
                sessionInfo.get("session").getAsJsonObject().get("recipeUserId").getAsString(),
                sessionRefreshResponse.get("session").getAsJsonObject().get("recipeUserId").getAsString()
        );

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testCreatingAUserIdMappingAndSessionWithRecipeUserId() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{
                        EE_FEATURES.ACCOUNT_LINKING, EE_FEATURES.MULTI_TENANCY});
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        UserIdMappingStorage storage = (UserIdMappingStorage) StorageLayer.getStorage(process.main);

        // create a User
        AuthRecipeUserInfo userInfo = EmailPassword.signUp(process.main, "test@example.com", "testPass123");
        AuthRecipeUserInfo userInfo2 = EmailPassword.signUp(process.main, "test2@example.com", "testPass123");

        AuthRecipe.createPrimaryUser(process.main, userInfo.getSupertokensUserId());

        AuthRecipe.linkAccounts(process.main, userInfo2.getSupertokensUserId(),
                userInfo.getSupertokensUserId());

        String superTokensUserId = userInfo2.getSupertokensUserId();
        String externalUserId = "externaluserId";
        String externalUserIdInfo = "externUserIdInfo";

        JsonObject requestBody = new JsonObject();

        requestBody.addProperty("superTokensUserId", superTokensUserId);
        requestBody.addProperty("externalUserId", externalUserId);
        requestBody.addProperty("externalUserIdInfo", externalUserIdInfo);

        JsonObject response = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/userid/map", requestBody, 1000, 1000, null,
                SemVer.v2_15.get(), "useridmapping");

        assertEquals(1, response.entrySet().size());
        assertEquals("OK", response.get("status").getAsString());

        // check that userIdMapping was created

        UserIdMapping userIdMapping = storage.getUserIdMapping(new AppIdentifier(null, null),
                superTokensUserId, true);

        assertEquals(superTokensUserId, userIdMapping.superTokensUserId);
        assertEquals(externalUserId, userIdMapping.externalUserId);
        assertEquals(externalUserIdInfo, userIdMapping.externalUserIdInfo);

        JsonObject signInRequest = new JsonObject();
        signInRequest.addProperty("email", "test2@example.com");
        signInRequest.addProperty("password", "testPass123");

        Thread.sleep(1); // add a small delay to ensure a unique timestamp
        long beforeSignIn = System.currentTimeMillis();

        JsonObject signInResponse = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/signin", signInRequest, 1000, 1000, null, SemVer.v4_0.get(),
                "emailpassword");

        JsonObject userDataInJWT = new JsonObject();
        userDataInJWT.add("nullProp", JsonNull.INSTANCE);
        userDataInJWT.addProperty("key", "value");
        JsonObject userDataInDatabase = new JsonObject();
        userDataInDatabase.addProperty("key", "value");

        JsonObject request = new JsonObject();
        request.addProperty("userId", signInResponse.get("recipeUserId").getAsString());
        request.add("userDataInJWT", userDataInJWT);
        request.add("userDataInDatabase", userDataInDatabase);
        request.addProperty("enableAntiCsrf", false);

        JsonObject sessionInfo = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/session", request, 1000, 1000, null, SemVer.v5_0.get(),
                "session");
        assertEquals(sessionInfo.get("status").getAsString(), "OK");

        JsonObject sessionRefreshBody = new JsonObject();

        sessionRefreshBody.addProperty("refreshToken",
                sessionInfo.get("refreshToken").getAsJsonObject().get("token").getAsString());
        sessionRefreshBody.addProperty("enableAntiCsrf", false);
        sessionRefreshBody.addProperty("useDynamicSigningKey", true);

        JsonObject sessionRefreshResponse = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/session/refresh", sessionRefreshBody, 1000, 1000, null,
                SemVer.v5_0.get(), "session");

        assertEquals(
                sessionInfo.get("session").getAsJsonObject().get("handle").getAsString(),
                sessionRefreshResponse.get("session").getAsJsonObject().get("handle").getAsString()
        );
        assertEquals(
                sessionInfo.get("session").getAsJsonObject().get("userId").getAsString(),
                sessionRefreshResponse.get("session").getAsJsonObject().get("userId").getAsString()
        );
        assertEquals(
                sessionInfo.get("session").getAsJsonObject().get("recipeUserId").getAsString(),
                sessionRefreshResponse.get("session").getAsJsonObject().get("recipeUserId").getAsString()
        );

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

}
