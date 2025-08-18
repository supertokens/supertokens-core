/*
 *    Copyright (c) 2025, VRAI Labs and/or its affiliates. All rights reserved.
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
import io.supertokens.authRecipe.exception.AccountInfoAlreadyAssociatedWithAnotherPrimaryUserIdException;
import io.supertokens.authRecipe.exception.InputUserIdIsNotAPrimaryUserException;
import io.supertokens.featureflag.EE_FEATURES;
import io.supertokens.featureflag.FeatureFlagTestContent;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.pluginInterface.authRecipe.AuthRecipeUserInfo;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.test.TestingProcessManager;
import io.supertokens.test.httpRequest.HttpRequestForTesting;
import io.supertokens.test.httpRequest.HttpResponseException;
import io.supertokens.utils.SemVer;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.Assert.*;

public class WebauthNAccountLinkingFlowTest {

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
    public void linkAccountsWithBothEmailsVerifiedEPPrimary() throws Exception {
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

        //create users - webauthn and emailpassword
        List<JsonObject> users = Utils.registerUsers(process.getProcess(), numberOfUsers);
        List<AuthRecipeUserInfo> epUsers = Utils.createEmailPasswordUsers(process.getProcess(), numberOfUsers, true);

        //create userid mapping and verify email for all wa users
        int w = 0;
        for (JsonObject user : users) {
            String userId = user.getAsJsonObject("user").get("id").getAsString();
            Utils.createUserIdMapping(process.getProcess(), userId, "waexternal_" + userId);
            Utils.verifyEmailFor(process.getProcess(), userId, "user" + w++ + "@example.com");
        }


        //create userid mapping and verify email for ep users
        int i = 0;
        for (AuthRecipeUserInfo user : epUsers) {
            Utils.createUserIdMapping(process.getProcess(), user.getSupertokensUserId(), "external_" + user.getSupertokensUserId());
            Utils.verifyEmailFor(process.getProcess(), user.getSupertokensUserId(), "user" + i++ + "@example.com");
        }

        //link accounts
        Utils.linkAccounts(process.getProcess(),
                epUsers.stream().map(AuthRecipeUserInfo::getSupertokensUserId).collect(
                        Collectors.toList()),
                users.stream().map(jsonObject -> jsonObject.getAsJsonObject("user").get("id").getAsString()).collect(Collectors.toList()));



        JsonObject signInResponse = Utils.signInWithUser(process.getProcess(), users.get(0));

        JsonObject signInEPRequest = new JsonObject();
        signInEPRequest.addProperty("email", "user0@example.com");
        signInEPRequest.addProperty("password", "password");

        Thread.sleep(1);
        JsonObject ePSignInResponse = signInWithRequest(process, signInEPRequest);
        assertEquals("OK", ePSignInResponse.get("status").getAsString());

        ePSignInResponse.remove("recipeUserId");
        signInResponse.remove("recipeUserId");

        assertEquals(signInResponse, ePSignInResponse);
    }

    @Test
    public void linkAccountsWithEPEmailVerifiedEPPrimary() throws Exception {
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

        //create users - webauthn and emailpassword
        List<JsonObject> users = Utils.registerUsers(process.getProcess(), numberOfUsers);
        List<AuthRecipeUserInfo> epUsers = Utils.createEmailPasswordUsers(process.getProcess(), numberOfUsers, true);

        //create userid mapping and verify email for all wa users
        int w = 0;
        for (JsonObject user : users) {
            String userId = user.getAsJsonObject("user").get("id").getAsString();
            Utils.createUserIdMapping(process.getProcess(), userId, "waexternal_" + userId);
        }

        //create userid mapping and verify email for ep users
        int i = 0;
        for (AuthRecipeUserInfo user : epUsers) {
            Utils.createUserIdMapping(process.getProcess(), user.getSupertokensUserId(), "external_" + user.getSupertokensUserId());
            Utils.verifyEmailFor(process.getProcess(), user.getSupertokensUserId(), "user" + i++ + "@example.com");
        }

        //link accounts
        Utils.linkAccounts(process.getProcess(),
                epUsers.stream().map(AuthRecipeUserInfo::getSupertokensUserId).collect(
                        Collectors.toList()),
                users.stream().map(jsonObject -> jsonObject.getAsJsonObject("user").get("id").getAsString()).collect(Collectors.toList()));



        JsonObject signInResponse = Utils.signInWithUser(process.getProcess(), users.get(0));

        JsonObject signInEPRequest = new JsonObject();
        signInEPRequest.addProperty("email", "user0@example.com");
        signInEPRequest.addProperty("password", "password");

        Thread.sleep(1);
        JsonObject ePSignInResponse = signInWithRequest(process, signInEPRequest);
        assertEquals("OK", ePSignInResponse.get("status").getAsString());

        ePSignInResponse.remove("recipeUserId");
        signInResponse.remove("recipeUserId");

        assertEquals(signInResponse, ePSignInResponse);
    }

    @Test
    public void linkAccountsWithWANEmailVerifiedEPPrimary() throws Exception {
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

        //create users - webauthn and emailpassword
        List<JsonObject> users = Utils.registerUsers(process.getProcess(), numberOfUsers);
        List<AuthRecipeUserInfo> epUsers = Utils.createEmailPasswordUsers(process.getProcess(), numberOfUsers, true);

        //create userid mapping and verify email for all wa users
        int w = 0;
        for (JsonObject user : users) {
            String userId = user.getAsJsonObject("user").get("id").getAsString();
            Utils.createUserIdMapping(process.getProcess(), userId, "waexternal_" + userId);
            Utils.verifyEmailFor(process.getProcess(), userId, "user" + w++ + "@example.com");
        }


        //create userid mapping
        int i = 0;
        for (AuthRecipeUserInfo user : epUsers) {
            Utils.createUserIdMapping(process.getProcess(), user.getSupertokensUserId(), "external_" + user.getSupertokensUserId());
        }

        //link accounts
        Utils.linkAccounts(process.getProcess(),
                epUsers.stream().map(AuthRecipeUserInfo::getSupertokensUserId).collect(
                        Collectors.toList()),
                users.stream().map(jsonObject -> jsonObject.getAsJsonObject("user").get("id").getAsString()).collect(Collectors.toList()));



        JsonObject signInResponse = io.supertokens.test.webauthn.Utils.signInWithUser(process.getProcess(), users.get(0));

        JsonObject signInEPRequest = new JsonObject();
        signInEPRequest.addProperty("email", "user0@example.com");
        signInEPRequest.addProperty("password", "password");

        Thread.sleep(1);
        JsonObject ePSignInResponse = signInWithRequest(process, signInEPRequest);
        assertEquals("OK", ePSignInResponse.get("status").getAsString());

        ePSignInResponse.remove("recipeUserId");
        signInResponse.remove("recipeUserId");

        assertEquals(signInResponse, ePSignInResponse);
    }

    @Test
    public void linkAccountsWithEPEmailVerifiedWANPrimary() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{
                        EE_FEATURES.ACCOUNT_LINKING, EE_FEATURES.MULTI_TENANCY});
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        //create users - webauthn and emailpassword
        List<JsonObject> users = Utils.registerUsers(process.getProcess(), 1);
        List<AuthRecipeUserInfo> epUsers = Utils.createEmailPasswordUsers(process.getProcess(), 1, false);

        JsonObject user = users.get(0);
        String userId = user.getAsJsonObject("user").get("id").getAsString();
        Utils.createUserIdMapping(process.getProcess(), userId, "waexternal_" + userId);
        Utils.makePrimaryUserFrom(process.getProcess(), userId);

        AuthRecipeUserInfo auser = epUsers.get(0);
        Utils.createUserIdMapping(process.getProcess(), auser.getSupertokensUserId(), "external_" + auser.getSupertokensUserId());
        Utils.verifyEmailFor(process.getProcess(), auser.getSupertokensUserId(), "user0@example.com");


        //link accounts
        try {
            Utils.linkAccounts(process.getProcess(),
                    epUsers.stream().map(AuthRecipeUserInfo::getSupertokensUserId).collect(
                            Collectors.toList()),
                    users.stream().map(jsonObject -> jsonObject.getAsJsonObject("user").get("id").getAsString())
                            .collect(Collectors.toList()));
            fail();
        } catch (InputUserIdIsNotAPrimaryUserException e) {
            //expected
        }

        Utils.linkAccounts(process.getProcess(),
                users.stream().map(jsonObject -> jsonObject.getAsJsonObject("user").get("id").getAsString())
                        .collect(Collectors.toList()),
                epUsers.stream().map(AuthRecipeUserInfo::getSupertokensUserId).collect(
                        Collectors.toList())
        );

        JsonObject signInResponse = Utils.signInWithUser(process.getProcess(), users.get(0));

        JsonObject signInEPRequest = new JsonObject();
        signInEPRequest.addProperty("email", "user0@example.com");
        signInEPRequest.addProperty("password", "password");

        Thread.sleep(1);
        JsonObject ePSignInResponse = signInWithRequest(process, signInEPRequest);
        assertEquals("OK", ePSignInResponse.get("status").getAsString());

        ePSignInResponse.remove("recipeUserId");
        signInResponse.remove("recipeUserId");

        assertEquals(signInResponse, ePSignInResponse);
    }

    @Test
    public void linkAccountsWithWANEmailVerifiedWAPrimary() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{
                        EE_FEATURES.ACCOUNT_LINKING, EE_FEATURES.MULTI_TENANCY});
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        //create users - webauthn and emailpassword
        List<JsonObject> users = Utils.registerUsers(process.getProcess(), 1);
        List<AuthRecipeUserInfo> epUsers = Utils.createEmailPasswordUsers(process.getProcess(), 1, false);

        //create userid mapping and verify email for all wa users

        String userId = users.get(0).getAsJsonObject("user").get("id").getAsString();
        Utils.createUserIdMapping(process.getProcess(), userId, "waexternal_" + userId);
        Utils.verifyEmailFor(process.getProcess(), userId, "user0@example.com");
        Utils.makePrimaryUserFrom(process.getProcess(), userId);


        //create userid mapping
        int i = 0;
        for (AuthRecipeUserInfo user : epUsers) {
            Utils.createUserIdMapping(process.getProcess(), user.getSupertokensUserId(), "external_" + user.getSupertokensUserId());
        }

        //link accounts
        try {
            Utils.linkAccounts(process.getProcess(),
                    epUsers.stream().map(authRecipeUserInfo -> authRecipeUserInfo.getSupertokensUserId()).collect(
                            Collectors.toList()),
                    users.stream().map(jsonObject -> jsonObject.getAsJsonObject("user").get("id").getAsString()).collect(Collectors.toList()));

            fail();
        } catch (InputUserIdIsNotAPrimaryUserException e){
            //expected
        }

        JsonObject signInResponse = io.supertokens.test.webauthn.Utils.signInWithUser(process.getProcess(), users.get(0));

        JsonObject signInEPRequest = new JsonObject();
        signInEPRequest.addProperty("email", "user0@example.com");
        signInEPRequest.addProperty("password", "password");

        Thread.sleep(1);
        JsonObject ePSignInResponse = signInWithRequest(process, signInEPRequest);
        assertEquals("OK", ePSignInResponse.get("status").getAsString());

        ePSignInResponse.remove("recipeUserId");
        signInResponse.remove("recipeUserId");

        assertNotEquals(signInResponse, ePSignInResponse);
    }

    @Test
    public void linkAccountsWithWANEmailVerifiedBothrimary() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{
                        EE_FEATURES.ACCOUNT_LINKING, EE_FEATURES.MULTI_TENANCY});
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        //create users - webauthn and emailpassword
        List<JsonObject> users = Utils.registerUsers(process.getProcess(), 1);
        List<AuthRecipeUserInfo> epUsers = Utils.createEmailPasswordUsers(process.getProcess(), 1, true);

        //create userid mapping and verify email for all wa users

        String userId = users.get(0).getAsJsonObject("user").get("id").getAsString();
        Utils.createUserIdMapping(process.getProcess(), userId, "waexternal_" + userId);
        Utils.verifyEmailFor(process.getProcess(), userId, "user0@example.com");
        try {
            Utils.makePrimaryUserFrom(process.getProcess(), userId);
            fail();
        } catch (AccountInfoAlreadyAssociatedWithAnotherPrimaryUserIdException ignored) {
            //expected
        }

    }

    @Test
    public void linkAccountsWithNoEmailsVerifiedEPPrimary() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{
                        EE_FEATURES.ACCOUNT_LINKING, EE_FEATURES.MULTI_TENANCY});
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        //create users - webauthn and emailpassword
        List<JsonObject> users = Utils.registerUsers(process.getProcess(), 1);
        List<AuthRecipeUserInfo> epUsers = Utils.createEmailPasswordUsers(process.getProcess(), 1, true);

        String userId = users.get(0).getAsJsonObject("user").get("id").getAsString();
        Utils.createUserIdMapping(process.getProcess(), userId, "waexternal_" + userId);

        //link accounts
        Utils.linkAccounts(process.getProcess(),
                epUsers.stream().map(AuthRecipeUserInfo::getSupertokensUserId).collect(Collectors.toList()),
                users.stream().map(jsonObject -> jsonObject.getAsJsonObject("user").get("id").getAsString()).collect(Collectors.toList()));

        JsonObject signInResponse = Utils.signInWithUser(process.getProcess(), users.get(0));

        JsonObject signInEPRequest = new JsonObject();
        signInEPRequest.addProperty("email", "user0@example.com");
        signInEPRequest.addProperty("password", "password");

        Thread.sleep(1);
        JsonObject ePSignInResponse = signInWithRequest(process, signInEPRequest);
        assertEquals("OK", ePSignInResponse.get("status").getAsString());

        ePSignInResponse.remove("recipeUserId");
        signInResponse.remove("recipeUserId");

        assertEquals(signInResponse, ePSignInResponse);
    }

    @Test
    public void linkAccountsWithNoEmailsVerifiedNoPrimary() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{
                        EE_FEATURES.ACCOUNT_LINKING, EE_FEATURES.MULTI_TENANCY});
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        //create users - webauthn and emailpassword
        List<JsonObject> users = Utils.registerUsers(process.getProcess(), 1);
        List<AuthRecipeUserInfo> epUsers = Utils.createEmailPasswordUsers(process.getProcess(), 1, false);

        String userId = users.get(0).getAsJsonObject("user").get("id").getAsString();
        Utils.createUserIdMapping(process.getProcess(), userId, "waexternal_" + userId);

        //link accounts
        try {
            Utils.linkAccounts(process.getProcess(),
                    epUsers.stream().map(AuthRecipeUserInfo::getSupertokensUserId).collect(Collectors.toList()),
                    users.stream().map(jsonObject -> jsonObject.getAsJsonObject("user").get("id").getAsString())
                            .collect(Collectors.toList()));
            fail();
        } catch (InputUserIdIsNotAPrimaryUserException e) {
            //expected
        }

        JsonObject signInResponse = Utils.signInWithUser(process.getProcess(), users.get(0));

        JsonObject signInEPRequest = new JsonObject();
        signInEPRequest.addProperty("email", "user0@example.com");
        signInEPRequest.addProperty("password", "password");

        Thread.sleep(1);
        JsonObject ePSignInResponse = signInWithRequest(process, signInEPRequest);
        assertEquals("OK", ePSignInResponse.get("status").getAsString());

        ePSignInResponse.remove("recipeUserId");
        signInResponse.remove("recipeUserId");

        assertNotEquals(signInResponse, ePSignInResponse);
    }

    private static JsonObject signInWithRequest(TestingProcessManager.TestingProcess process, JsonObject signInEPRequest)
            throws IOException, HttpResponseException {
        return HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/signin", signInEPRequest, 1000, 1000, null, SemVer.v5_3.get(),
                "emailpassword");
    }


}
