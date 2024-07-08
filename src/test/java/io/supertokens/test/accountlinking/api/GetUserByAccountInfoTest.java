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
import io.supertokens.webserver.WebserverAPI;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;

public class GetUserByAccountInfoTest {
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

    private JsonObject getUserById(Main main, String userId) throws Exception {
        Map<String, String> params = new HashMap<>();
        params.put("userId", userId);
        JsonObject response = HttpRequestForTesting.sendGETRequest(main, "",
                "http://localhost:3567/user/id", params, 1000, 1000, null,
                WebserverAPI.getLatestCDIVersion().get(), "");
        return response.get("user").getAsJsonObject();
    }

    private JsonArray getUsersByAccountInfo(Main main, boolean doUnionOfAccountInfo, String email, String phoneNumber,
                                            String thirdPartyId, String thirdPartyUserId) throws Exception {
        Map<String, String> params = new HashMap<>();
        params.put("doUnionOfAccountInfo", String.valueOf(doUnionOfAccountInfo));
        if (email != null) {
            params.put("email", email);
        }
        if (phoneNumber != null) {
            params.put("phoneNumber", phoneNumber);
        }
        if (thirdPartyId != null) {
            params.put("thirdPartyId", thirdPartyId);
        }
        if (thirdPartyUserId != null) {
            params.put("thirdPartyUserId", thirdPartyUserId);
        }
        JsonObject response = HttpRequestForTesting.sendGETRequest(main, "",
                "http://localhost:3567/users/by-accountinfo", params, 1000, 1000, null,
                WebserverAPI.getLatestCDIVersion().get(), "");
        return response.get("users").getAsJsonArray();
    }

    @Test
    public void testListUsersByAccountInfoForUnlinkedAccounts() throws Exception {
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

        AuthRecipeUserInfo user1 = createEmailPasswordUser(process.getProcess(), "test1@example.com", "password1");
        AuthRecipeUserInfo user2 = createThirdPartyUser(process.getProcess(), "google", "userid1", "test2@example.com");
        AuthRecipeUserInfo user3 = createPasswordlessUserWithEmail(process.getProcess(), "test3@example.com");
        AuthRecipeUserInfo user4 = createPasswordlessUserWithPhone(process.getProcess(), "+919876543210");

        JsonObject user1json = getUserById(process.getProcess(), user1.getSupertokensUserId());
        JsonObject user2json = getUserById(process.getProcess(), user2.getSupertokensUserId());
        JsonObject user3json = getUserById(process.getProcess(), user3.getSupertokensUserId());
        JsonObject user4json = getUserById(process.getProcess(), user4.getSupertokensUserId());

        // test for result
        assertEquals(user1json,
                getUsersByAccountInfo(process.getProcess(), false, "test1@example.com", null, null, null).get(0));
        assertEquals(user2json,
                getUsersByAccountInfo(process.getProcess(), false, null, null, "google", "userid1").get(0));
        assertEquals(user2json,
                getUsersByAccountInfo(process.getProcess(), false, "test2@example.com", null, "google", "userid1").get(
                        0));
        assertEquals(user3json,
                getUsersByAccountInfo(process.getProcess(), false, "test3@example.com", null, null, null).get(0));
        assertEquals(user4json,
                getUsersByAccountInfo(process.getProcess(), false, null, "+919876543210", null, null).get(0));

        // test for no result
        assertEquals(0, getUsersByAccountInfo(process.getProcess(), false, "test1@example.com", "+919876543210", null,
                null).size());
        assertEquals(0, getUsersByAccountInfo(process.getProcess(), false, "test2@example.com", "+919876543210", null,
                null).size());
        assertEquals(0, getUsersByAccountInfo(process.getProcess(), false, "test3@example.com", "+919876543210", null,
                null).size());
        assertEquals(0,
                getUsersByAccountInfo(process.getProcess(), false, null, "+919876543210", "google", "userid1").size());
        assertEquals(0, getUsersByAccountInfo(process.getProcess(), false, "test1@gmail.com", null, "google",
                "userid1").size());
        assertEquals(0, getUsersByAccountInfo(process.getProcess(), false, "test3@gmail.com", null, "google",
                "userid1").size());

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testListUserByAccountInfoByUnnormalisedPhoneNumber() throws Exception {
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

        String phoneNumber = "+44-207 183 8750";
        String normalisedPhoneNumber = io.supertokens.utils.Utils.normalizeIfPhoneNumber(phoneNumber);

        AuthRecipeUserInfo user = createPasswordlessUserWithPhone(process.getProcess(), normalisedPhoneNumber);

        JsonObject userJSON = getUserById(process.getProcess(), user.getSupertokensUserId());

        assertEquals(userJSON,
                getUsersByAccountInfo(process.getProcess(), false, null, phoneNumber, null, null).get(0));

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testListUsersByAccountInfoForUnlinkedAccountsWithUnionOption() throws Exception {
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

        AuthRecipeUserInfo user1 = createEmailPasswordUser(process.getProcess(), "test1@example.com", "password1");
        AuthRecipeUserInfo user2 = createThirdPartyUser(process.getProcess(), "google", "userid1", "test2@example.com");
        AuthRecipeUserInfo user3 = createPasswordlessUserWithEmail(process.getProcess(), "test3@example.com");
        AuthRecipeUserInfo user4 = createPasswordlessUserWithPhone(process.getProcess(), "+919876543210");

        JsonObject user1json = getUserById(process.getProcess(), user1.getSupertokensUserId());
        JsonObject user2json = getUserById(process.getProcess(), user2.getSupertokensUserId());
        JsonObject user3json = getUserById(process.getProcess(), user3.getSupertokensUserId());
        JsonObject user4json = getUserById(process.getProcess(), user4.getSupertokensUserId());

        {
            JsonArray users = getUsersByAccountInfo(process.getProcess(), true, "test1@example.com", "+919876543210",
                    null, null);
            assertEquals(2, users.size());
            users.contains(user1json);
            users.contains(user4json);
        }
        {
            JsonArray users = getUsersByAccountInfo(process.getProcess(), true, "test1@example.com", null, "google",
                    "userid1");
            assertEquals(2, users.size());
            users.contains(user1json);
            users.contains(user2json);
        }
        {
            JsonArray users = getUsersByAccountInfo(process.getProcess(), true, null, "+919876543210", "google",
                    "userid1");
            assertEquals(2, users.size());
            users.contains(user4json);
            users.contains(user2json);
        }
        {
            JsonArray users = getUsersByAccountInfo(process.getProcess(), true, "test1@example.com", "+919876543210",
                    "google", "userid1");
            assertEquals(3, users.size());
            users.contains(user1json);
            users.contains(user2json);
            users.contains(user4json);
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testUnknownAccountInfo() throws Exception {
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

        assertEquals(0,
                getUsersByAccountInfo(process.getProcess(), false, "test1@example.com", null, null, null).size());
        assertEquals(0, getUsersByAccountInfo(process.getProcess(), false, null, null, "google", "userid1").size());
        assertEquals(0,
                getUsersByAccountInfo(process.getProcess(), false, "test3@example.com", null, null, null).size());
        assertEquals(0, getUsersByAccountInfo(process.getProcess(), false, null, "+919876543210", null, null).size());

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testListUserByAccountInfoWhenAccountsAreLinked1() throws Exception {
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

        AuthRecipeUserInfo user1 = EmailPassword.signUp(process.getProcess(), "test1@example.com", "password1");
        Thread.sleep(50);
        AuthRecipeUserInfo user2 = ThirdParty.signInUp(process.getProcess(), "google", "userid1",
                "test2@example.com").user;

        AuthRecipeUserInfo primaryUser = AuthRecipe.createPrimaryUser(process.getProcess(),
                user1.getSupertokensUserId()).user;
        AuthRecipe.linkAccounts(process.getProcess(), user2.getSupertokensUserId(), primaryUser.getSupertokensUserId());

        JsonObject primaryUserJson = getUserById(process.getProcess(), user1.getSupertokensUserId());

        assertEquals(primaryUserJson, getUsersByAccountInfo(process.getProcess(), false,
                "test1@example.com", null, null, null).get(0));
        assertEquals(primaryUserJson, getUsersByAccountInfo(process.getProcess(), false,
                "test2@example.com", null, null, null).get(0));
        assertEquals(primaryUserJson, getUsersByAccountInfo(process.getProcess(), false,
                null, null, "google", "userid1").get(0));
        assertEquals(primaryUserJson, getUsersByAccountInfo(process.getProcess(), false,
                "test1@example.com", null, "google", "userid1").get(0));
        assertEquals(primaryUserJson, getUsersByAccountInfo(process.getProcess(), false,
                "test2@example.com", null, "google", "userid1").get(0));

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testListUserByAccountInfoWhenAccountsAreLinked2() throws Exception {
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

        AuthRecipeUserInfo user1 = EmailPassword.signUp(process.getProcess(), "test1@example.com", "password1");
        Thread.sleep(50);
        AuthRecipeUserInfo user2 = EmailPassword.signUp(process.getProcess(), "test2@example.com", "password2");

        AuthRecipeUserInfo primaryUser = AuthRecipe.createPrimaryUser(process.getProcess(),
                user1.getSupertokensUserId()).user;
        AuthRecipe.linkAccounts(process.getProcess(), user2.getSupertokensUserId(), primaryUser.getSupertokensUserId());

        JsonObject primaryUserJson = getUserById(process.getProcess(), user1.getSupertokensUserId());

        assertEquals(primaryUserJson, getUsersByAccountInfo(process.getProcess(), false,
                "test1@example.com", null, null, null).get(0));
        assertEquals(primaryUserJson, getUsersByAccountInfo(process.getProcess(), false,
                "test2@example.com", null, null, null).get(0));

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testListUserByAccountInfoWhenAccountsAreLinked3() throws Exception {
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

        AuthRecipeUserInfo user1 = createEmailPasswordUser(process.getProcess(), "test1@example.com", "password1");
        Thread.sleep(50);
        AuthRecipeUserInfo user2 = createPasswordlessUserWithEmail(process.getProcess(), "test2@example.com");

        AuthRecipeUserInfo primaryUser = AuthRecipe.createPrimaryUser(process.getProcess(),
                user1.getSupertokensUserId()).user;
        AuthRecipe.linkAccounts(process.getProcess(), user2.getSupertokensUserId(), primaryUser.getSupertokensUserId());

        JsonObject primaryUserJson = getUserById(process.getProcess(), user1.getSupertokensUserId());

        assertEquals(primaryUserJson, getUsersByAccountInfo(process.getProcess(), false,
                "test1@example.com", null, null, null).get(0));
        assertEquals(primaryUserJson, getUsersByAccountInfo(process.getProcess(), false,
                "test2@example.com", null, null, null).get(0));

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testListUserByAccountInfoWhenAccountsAreLinked4() throws Exception {
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

        AuthRecipeUserInfo user1 = createEmailPasswordUser(process.getProcess(), "test1@example.com", "password1");
        Thread.sleep(50);
        AuthRecipeUserInfo user2 = createPasswordlessUserWithPhone(process.getProcess(), "+919876543210");

        AuthRecipeUserInfo primaryUser = AuthRecipe.createPrimaryUser(process.getProcess(),
                user1.getSupertokensUserId()).user;
        AuthRecipe.linkAccounts(process.getProcess(), user2.getSupertokensUserId(), primaryUser.getSupertokensUserId());

        JsonObject primaryUserJson = getUserById(process.getProcess(), user1.getSupertokensUserId());

        assertEquals(primaryUserJson, getUsersByAccountInfo(process.getProcess(), false,
                "test1@example.com", null, null, null).get(0));
        assertEquals(primaryUserJson, getUsersByAccountInfo(process.getProcess(), false,
                null, "+919876543210", null, null).get(0));
        assertEquals(primaryUserJson, getUsersByAccountInfo(process.getProcess(), false,
                "test1@example.com", "+919876543210", null, null).get(0));

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testListUserByAccountInfoWhenAccountsAreLinked5() throws Exception {
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

        AuthRecipeUserInfo user1 = createEmailPasswordUser(process.getProcess(), "test1@example.com", "password1");
        Thread.sleep(50);
        AuthRecipeUserInfo user2 = createThirdPartyUser(process.getProcess(), "google", "userid1", "test2@example.com");

        AuthRecipeUserInfo primaryUser = AuthRecipe.createPrimaryUser(process.getProcess(),
                user1.getSupertokensUserId()).user;
        AuthRecipe.linkAccounts(process.getProcess(), user2.getSupertokensUserId(), primaryUser.getSupertokensUserId());

        JsonObject primaryUserJson = getUserById(process.getProcess(), user1.getSupertokensUserId());

        assertEquals(primaryUserJson, getUsersByAccountInfo(process.getProcess(), false,
                "test1@example.com", null, null, null).get(0));
        assertEquals(primaryUserJson, getUsersByAccountInfo(process.getProcess(), false,
                "test2@example.com", null, null, null).get(0));
        assertEquals(primaryUserJson, getUsersByAccountInfo(process.getProcess(), false,
                null, null, "google", "userid1").get(0));
        assertEquals(primaryUserJson, getUsersByAccountInfo(process.getProcess(), false,
                "test1@example.com", null, "google", "userid1").get(0));
        assertEquals(primaryUserJson, getUsersByAccountInfo(process.getProcess(), false,
                "test2@example.com", null, "google", "userid1").get(0));

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testWithUserIdMapping() throws Exception {
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
        AuthRecipeUserInfo user4 = createPasswordlessUserWithPhone(process.getProcess(), "+919876543210");

        UserIdMapping.createUserIdMapping(process.getProcess(), user1.getSupertokensUserId(), "ext1", "", false);
        UserIdMapping.createUserIdMapping(process.getProcess(), user2.getSupertokensUserId(), "ext2", "", false);
        UserIdMapping.createUserIdMapping(process.getProcess(), user3.getSupertokensUserId(), "ext3", "", false);

        AuthRecipeUserInfo primaryUser = AuthRecipe.createPrimaryUser(process.getProcess(),
                user1.getSupertokensUserId()).user;
        AuthRecipe.linkAccounts(process.getProcess(), user2.getSupertokensUserId(), primaryUser.getSupertokensUserId());
        AuthRecipe.linkAccounts(process.getProcess(), user3.getSupertokensUserId(), primaryUser.getSupertokensUserId());
        AuthRecipe.linkAccounts(process.getProcess(), user4.getSupertokensUserId(), primaryUser.getSupertokensUserId());

        JsonObject primaryUserInfo = getUsersByAccountInfo(process.getProcess(), false,
                "test@example.com", null, null, null).get(0).getAsJsonObject();
        assertEquals("ext1",
                primaryUserInfo.get("loginMethods").getAsJsonArray().get(0).getAsJsonObject().get("recipeUserId")
                        .getAsString());
        assertEquals("ext2",
                primaryUserInfo.get("loginMethods").getAsJsonArray().get(1).getAsJsonObject().get("recipeUserId")
                        .getAsString());
        assertEquals("ext3",
                primaryUserInfo.get("loginMethods").getAsJsonArray().get(2).getAsJsonObject().get("recipeUserId")
                        .getAsString());

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }
}
