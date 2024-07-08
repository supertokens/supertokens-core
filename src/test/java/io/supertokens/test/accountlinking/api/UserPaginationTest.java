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

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
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
import io.supertokens.utils.SemVer;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.*;

import static org.junit.Assert.*;
import static org.junit.Assert.assertTrue;

public class UserPaginationTest {
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

    private JsonObject getUsers(Main main) throws Exception {
        Map<String, String> params = new HashMap<>();
        return HttpRequestForTesting.sendGETRequest(main, "",
                "http://localhost:3567/users", params, 1000, 1000, null,
                SemVer.v4_0.get(), "");
    }

    private JsonArray getUsersFromAllPages(Main main, int pageSize, String[] recipeFilters) throws Exception {
        Map<String, String> params = new HashMap<>();

        if (recipeFilters != null) {
            params.put("includeRecipeIds", String.join(",", recipeFilters));
        }

        params.put("limit", String.valueOf(pageSize));

        JsonArray result = new JsonArray();

        JsonObject response = HttpRequestForTesting.sendGETRequest(main, "",
                "http://localhost:3567/users", params, 1000, 1000, null,
                SemVer.v4_0.get(), "");

        result.addAll(response.get("users").getAsJsonArray());
        while (response.get("nextPaginationToken") != null) {
            String paginationToken = response.get("nextPaginationToken").getAsString();
            params.put("paginationToken", paginationToken);

            response = HttpRequestForTesting.sendGETRequest(main, "",
                    "http://localhost:3567/users", params, 1000, 1000, null,
                    SemVer.v4_0.get(), "");
            result.addAll(response.get("users").getAsJsonArray());
        }

        return result;
    }

    @Test
    public void testUserPaginationResultJson() throws Exception {
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

        AuthRecipeUserInfo user1 = createEmailPasswordUser(process.getProcess(), "test1@example.com", "password");
        AuthRecipeUserInfo user2 = createEmailPasswordUser(process.getProcess(), "test2@example.com", "password");
        AuthRecipeUserInfo user3 = createPasswordlessUserWithEmail(process.getProcess(), "test3@example.com");
        AuthRecipeUserInfo user4 = createPasswordlessUserWithEmail(process.getProcess(), "test4@example.com");
        AuthRecipeUserInfo user5 = createPasswordlessUserWithPhone(process.getProcess(), "+1234567890");
        AuthRecipeUserInfo user6 = createPasswordlessUserWithPhone(process.getProcess(), "+1234567891");
        AuthRecipeUserInfo user7 = createThirdPartyUser(process.getProcess(), "google", "test7", "test7@example.com");
        AuthRecipeUserInfo user8 = createThirdPartyUser(process.getProcess(), "google", "test8", "test8@example.com");

        {
            AuthRecipeUserInfo primaryUser = AuthRecipe.createPrimaryUser(process.getProcess(),
                    user1.getSupertokensUserId()).user;
            AuthRecipe.linkAccounts(process.getProcess(), user3.getSupertokensUserId(),
                    primaryUser.getSupertokensUserId());
        }

        {
            AuthRecipeUserInfo primaryUser = AuthRecipe.createPrimaryUser(process.getProcess(),
                    user2.getSupertokensUserId()).user;
            AuthRecipe.linkAccounts(process.getProcess(), user5.getSupertokensUserId(),
                    primaryUser.getSupertokensUserId());
            AuthRecipe.linkAccounts(process.getProcess(), user7.getSupertokensUserId(),
                    primaryUser.getSupertokensUserId());
        }

        {
            AuthRecipeUserInfo primaryUser = AuthRecipe.createPrimaryUser(process.getProcess(),
                    user6.getSupertokensUserId()).user;
            AuthRecipe.linkAccounts(process.getProcess(), user8.getSupertokensUserId(),
                    primaryUser.getSupertokensUserId());
        }


        Map<String, String> params = new HashMap<>();
        JsonObject response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                "http://localhost:3567/users", params, 1000, 1000, null,
                SemVer.v4_0.get(), "");

        JsonArray users = response.get("users").getAsJsonArray();
        assertEquals(4, users.size());

        {
            params = new HashMap<>();
            params.put("userId", user1.getSupertokensUserId());
            JsonObject userResponse = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                    "http://localhost:3567/user/id", params, 1000, 1000, null,
                    SemVer.v4_0.get(), "");
            userResponse.remove("status");
            assertEquals(userResponse.get("user"), users.get(0));
        }

        {
            params = new HashMap<>();
            params.put("userId", user2.getSupertokensUserId());
            JsonObject userResponse = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                    "http://localhost:3567/user/id", params, 1000, 1000, null,
                    SemVer.v4_0.get(), "");
            userResponse.remove("status");
            assertEquals(userResponse.get("user"), users.get(1));
        }

        {
            params = new HashMap<>();
            params.put("userId", user4.getSupertokensUserId());
            JsonObject userResponse = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                    "http://localhost:3567/user/id", params, 1000, 1000, null,
                    SemVer.v4_0.get(), "");
            userResponse.remove("status");
            assertEquals(userResponse.get("user"), users.get(2));
        }

        {
            params = new HashMap<>();
            params.put("userId", user6.getSupertokensUserId());
            JsonObject userResponse = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                    "http://localhost:3567/user/id", params, 1000, 1000, null,
                    SemVer.v4_0.get(), "");
            userResponse.remove("status");
            assertEquals(userResponse.get("user"), users.get(3));
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testUserPaginationWithManyUsers() throws Exception {
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

        Map<String, AuthRecipeUserInfo> userInfoMap = new HashMap<>();
        Set<String> userIds = new HashSet<>();
        Set<String> emailPasswordUsers = new HashSet<>();
        Set<String> passwordlessUsers = new HashSet<>();
        Set<String> thirdPartyUsers = new HashSet<>();

        // emailpassword users
        for (int i = 0; i < 200; i++) {
            AuthRecipeUserInfo user = createEmailPasswordUser(process.getProcess(), "epuser" + i + "@gmail.com",
                    "password" + i);
            userInfoMap.put(user.getSupertokensUserId(), user);
            userIds.add(user.getSupertokensUserId());
            emailPasswordUsers.add(user.getSupertokensUserId());
            Thread.sleep(10);
        }

        // passwordless users with email
        for (int i = 0; i < 200; i++) {
            AuthRecipeUserInfo user = createPasswordlessUserWithEmail(process.getProcess(),
                    "pluser" + i + "@gmail.com");
            userInfoMap.put(user.getSupertokensUserId(), user);
            userIds.add(user.getSupertokensUserId());
            passwordlessUsers.add(user.getSupertokensUserId());
            Thread.sleep(10);
        }

        // passwordless users with phone
        for (int i = 0; i < 200; i++) {
            AuthRecipeUserInfo user = createPasswordlessUserWithPhone(process.getProcess(), "+1234567890" + i);
            userInfoMap.put(user.getSupertokensUserId(), user);
            userIds.add(user.getSupertokensUserId());
            passwordlessUsers.add(user.getSupertokensUserId());
            Thread.sleep(10);
        }

        // thirdparty users
        for (int i = 0; i < 200; i++) {
            AuthRecipeUserInfo user = createThirdPartyUser(process.getProcess(), "google", "tpuser" + i,
                    "tpuser" + i + "@gmail.com");
            userInfoMap.put(user.getSupertokensUserId(), user);
            userIds.add(user.getSupertokensUserId());
            thirdPartyUsers.add(user.getSupertokensUserId());
            Thread.sleep(10);
        }

        Map<String, String> primaryUserIdMap = new HashMap<>();
        List<String> primaryUserIds = new ArrayList<>();

        // Randomly link accounts
        Random rand = new Random();
        while (!userIds.isEmpty()) {
            int numAccountsToLink = Math.min(rand.nextInt(3) + 1, userIds.size());
            List<String> userIdsToLink = new ArrayList<>();

            for (int i = 0; i < numAccountsToLink; i++) {
                String[] userIdsArray = userIds.toArray(new String[0]);
                String userId = userIdsArray[rand.nextInt(userIds.size())];
                userIdsToLink.add(userId);
                userIds.remove(userId);
            }

            for (String userId : userIdsToLink) {
                primaryUserIdMap.put(userId, userIdsToLink.get(0));
            }

            if (userIdsToLink.size() > 1) {
                AuthRecipeUserInfo primaryUser = AuthRecipe.createPrimaryUser(process.getProcess(),
                        userIdsToLink.get(0)).user;
                primaryUserIds.add(primaryUser.getSupertokensUserId());

                for (int i = 1; i < userIdsToLink.size(); i++) {
                    AuthRecipe.linkAccounts(process.getProcess(), userIdsToLink.get(i),
                            primaryUser.getSupertokensUserId());
                }
            } else {
                primaryUserIds.add(userIdsToLink.get(0));
            }
        }

        // Pagination tests
        {
            JsonArray usersResult = getUsersFromAllPages(process.getProcess(), 10, null);
            assertEquals(primaryUserIds.size(), usersResult.size());
        }

        // Test pagination with recipe filters
        {
            JsonArray usersResult = getUsersFromAllPages(process.getProcess(), 20, new String[]{"emailpassword"});
            Set<String> primaryUsers = new HashSet<>();
            for (String userId : emailPasswordUsers) {
                primaryUsers.add(primaryUserIdMap.get(userId));
            }

            assertEquals(primaryUsers.size(), usersResult.size());
        }
        {
            JsonArray usersResult = getUsersFromAllPages(process.getProcess(), 20, new String[]{"passwordless"});
            Set<String> primaryUsers = new HashSet<>();
            for (String userId : passwordlessUsers) {
                primaryUsers.add(primaryUserIdMap.get(userId));
            }

            assertEquals(primaryUsers.size(), usersResult.size());
        }
        {
            JsonArray usersResult = getUsersFromAllPages(process.getProcess(), 20, new String[]{"thirdparty"});
            Set<String> primaryUsers = new HashSet<>();
            for (String userId : thirdPartyUsers) {
                primaryUsers.add(primaryUserIdMap.get(userId));
            }

            assertEquals(primaryUsers.size(), usersResult.size());
        }
        {
            JsonArray usersResult = getUsersFromAllPages(process.getProcess(), 20,
                    new String[]{"emailpassword", "passwordless"});
            Set<String> primaryUsers = new HashSet<>();
            for (String userId : emailPasswordUsers) {
                primaryUsers.add(primaryUserIdMap.get(userId));
            }
            for (String userId : passwordlessUsers) {
                primaryUsers.add(primaryUserIdMap.get(userId));
            }

            assertEquals(primaryUsers.size(), usersResult.size());
        }
        {
            JsonArray usersResult = getUsersFromAllPages(process.getProcess(), 20,
                    new String[]{"thirdparty", "passwordless"});
            Set<String> primaryUsers = new HashSet<>();
            for (String userId : thirdPartyUsers) {
                primaryUsers.add(primaryUserIdMap.get(userId));
            }
            for (String userId : passwordlessUsers) {
                primaryUsers.add(primaryUserIdMap.get(userId));
            }

            assertEquals(primaryUsers.size(), usersResult.size());
        }
        {
            JsonArray usersResult = getUsersFromAllPages(process.getProcess(), 20,
                    new String[]{"thirdparty", "passwordless", "emailpassword"});
            assertEquals(primaryUserIds.size(), usersResult.size());
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testUserPaginationFromOldVersion() throws Exception {
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

        AuthRecipeUserInfo user1 = createEmailPasswordUser(process.getProcess(), "test@example.com", "password1");
        Thread.sleep(50);
        AuthRecipeUserInfo user2 = createThirdPartyUser(process.getProcess(), "google", "userid1", "test@example.com");
        Thread.sleep(50);
        AuthRecipeUserInfo user3 = createPasswordlessUserWithEmail(process.getProcess(), "test@example.com");
        Thread.sleep(50);

        AuthRecipeUserInfo primaryUser = AuthRecipe.createPrimaryUser(process.getProcess(),
                user2.getSupertokensUserId()).user;
        AuthRecipe.linkAccounts(process.getProcess(), user1.getSupertokensUserId(), primaryUser.getSupertokensUserId());
        AuthRecipe.linkAccounts(process.getProcess(), user3.getSupertokensUserId(), primaryUser.getSupertokensUserId());

        Map<String, String> params = new HashMap<>();
        JsonObject response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                "http://localhost:3567/users", params, 1000, 1000, null,
                SemVer.v3_0.get(), "");

        assertEquals(1, response.get("users").getAsJsonArray().size());
        JsonObject user = response.get("users").getAsJsonArray().get(0).getAsJsonObject().get("user").getAsJsonObject();

        assertEquals(user1.getSupertokensUserId(), user.get("id").getAsString()); // oldest login method

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }
}
