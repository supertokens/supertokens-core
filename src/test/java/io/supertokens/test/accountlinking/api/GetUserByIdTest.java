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
import io.supertokens.useridmapping.UserIdMapping;
import io.supertokens.utils.SemVer;
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
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.*;

public class GetUserByIdTest {
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
    public void testJsonStructure() throws Exception {
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

        AuthRecipeUserInfo primaryUser = AuthRecipe.createPrimaryUser(process.getProcess(),
                user1.getSupertokensUserId()).user;
        AuthRecipe.linkAccounts(process.getProcess(), user2.getSupertokensUserId(), primaryUser.getSupertokensUserId());
        AuthRecipe.linkAccounts(process.getProcess(), user3.getSupertokensUserId(), primaryUser.getSupertokensUserId());
        AuthRecipe.linkAccounts(process.getProcess(), user4.getSupertokensUserId(), primaryUser.getSupertokensUserId());

        for (String userId : new String[]{user1.getSupertokensUserId(), user2.getSupertokensUserId(),
                user3.getSupertokensUserId(), user4.getSupertokensUserId()}) {
            Map<String, String> params = new HashMap<>();
            params.put("userId", userId);
            JsonObject response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                    "http://localhost:3567/user/id", params, 1000, 1000, null,
                    WebserverAPI.getLatestCDIVersion().get(), "");
            JsonObject user = response.get("user").getAsJsonObject();
            assertEquals(user.entrySet().size(), 8);
            assertTrue(user.get("isPrimaryUser").getAsBoolean());
            assertEquals(1, user.get("tenantIds").getAsJsonArray().size());
            assertEquals("public", user.get("tenantIds").getAsJsonArray().get(0).getAsString());
            assertEquals(1, user.get("thirdParty").getAsJsonArray().size());
            assertEquals(4, user.get("loginMethods").getAsJsonArray().size());
            for (JsonElement loginMethodElem : user.get("loginMethods").getAsJsonArray()) {
                JsonObject loginMethod = loginMethodElem.getAsJsonObject();
                if (loginMethod.get("recipeId").getAsString().equals("thirdparty")) {
                    assertEquals(7, loginMethod.entrySet().size());
                    assertTrue(loginMethod.has("thirdParty"));
                    assertEquals(2, loginMethod.get("thirdParty").getAsJsonObject().entrySet().size());
                } else {
                    assertEquals(6, loginMethod.entrySet().size());
                }
                if (loginMethod.has("email")) {
                    assertEquals("test@example.com", loginMethod.get("email").getAsString());
                } else if (loginMethod.has("phoneNumber")) {
                    assertEquals("+919876543210", loginMethod.get("phoneNumber").getAsString());
                    assertTrue(loginMethod.get("verified").getAsBoolean());
                } else {
                    fail();
                }
            }
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testThatEmailIsAUnionOfLinkedAccounts1() throws Exception {
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
        Thread.sleep(50);
        AuthRecipeUserInfo user3 = createPasswordlessUserWithEmail(process.getProcess(), "test3@example.com");

        AuthRecipeUserInfo primaryUser = AuthRecipe.createPrimaryUser(process.getProcess(),
                user1.getSupertokensUserId()).user;
        AuthRecipe.linkAccounts(process.getProcess(), user2.getSupertokensUserId(), primaryUser.getSupertokensUserId());
        AuthRecipe.linkAccounts(process.getProcess(), user3.getSupertokensUserId(), primaryUser.getSupertokensUserId());

        for (String userId : new String[]{user1.getSupertokensUserId(), user2.getSupertokensUserId(),
                user3.getSupertokensUserId()}) {
            Map<String, String> params = new HashMap<>();
            params.put("userId", userId);
            JsonObject response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                    "http://localhost:3567/user/id", params, 1000, 1000, null,
                    WebserverAPI.getLatestCDIVersion().get(), "");
            JsonObject user = response.get("user").getAsJsonObject();

            Set<String> emails = new HashSet<>();
            for (JsonElement emailElem : user.get("emails").getAsJsonArray()) {
                emails.add(emailElem.getAsString());
            }
            assertEquals(3, emails.size());
            assertTrue(emails.contains("test1@example.com"));
            assertTrue(emails.contains("test2@example.com"));
            assertTrue(emails.contains("test3@example.com"));
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testThatEmailIsAUnionOfLinkedAccounts2() throws Exception {
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

        for (String userId : new String[]{user1.getSupertokensUserId(), user2.getSupertokensUserId()}) {
            Map<String, String> params = new HashMap<>();
            params.put("userId", userId);
            JsonObject response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                    "http://localhost:3567/user/id", params, 1000, 1000, null,
                    WebserverAPI.getLatestCDIVersion().get(), "");
            JsonObject user = response.get("user").getAsJsonObject();

            Set<String> emails = new HashSet<>();
            for (JsonElement emailElem : user.get("emails").getAsJsonArray()) {
                emails.add(emailElem.getAsString());
            }
            assertEquals(2, emails.size());
            assertTrue(emails.contains("test1@example.com"));
            assertTrue(emails.contains("test2@example.com"));
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testThatEmailIsAUnionOfLinkedAccounts3() throws Exception {
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

        for (String userId : new String[]{user1.getSupertokensUserId(), user2.getSupertokensUserId()}) {
            Map<String, String> params = new HashMap<>();
            params.put("userId", userId);
            JsonObject response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                    "http://localhost:3567/user/id", params, 1000, 1000, null,
                    WebserverAPI.getLatestCDIVersion().get(), "");
            JsonObject user = response.get("user").getAsJsonObject();

            Set<String> emails = new HashSet<>();
            for (JsonElement emailElem : user.get("emails").getAsJsonArray()) {
                emails.add(emailElem.getAsString());
            }
            assertEquals(2, emails.size());
            assertTrue(emails.contains("test1@example.com"));
            assertTrue(emails.contains("test2@example.com"));
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testThatEmailIsAUnionOfLinkedAccounts4() throws Exception {
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

        AuthRecipeUserInfo user1 = createThirdPartyUser(process.getProcess(), "google", "googleid",
                "test1@example.com");
        Thread.sleep(50);
        AuthRecipeUserInfo user2 = createPasswordlessUserWithEmail(process.getProcess(), "test2@example.com");

        AuthRecipeUserInfo primaryUser = AuthRecipe.createPrimaryUser(process.getProcess(),
                user1.getSupertokensUserId()).user;
        AuthRecipe.linkAccounts(process.getProcess(), user2.getSupertokensUserId(), primaryUser.getSupertokensUserId());

        for (String userId : new String[]{user1.getSupertokensUserId(), user2.getSupertokensUserId()}) {
            Map<String, String> params = new HashMap<>();
            params.put("userId", userId);
            JsonObject response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                    "http://localhost:3567/user/id", params, 1000, 1000, null,
                    WebserverAPI.getLatestCDIVersion().get(), "");
            JsonObject user = response.get("user").getAsJsonObject();

            Set<String> emails = new HashSet<>();
            for (JsonElement emailElem : user.get("emails").getAsJsonArray()) {
                emails.add(emailElem.getAsString());
            }
            assertEquals(2, emails.size());
            assertTrue(emails.contains("test1@example.com"));
            assertTrue(emails.contains("test2@example.com"));
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testThatPhoneNumberIsUnionOfLinkedAccounts1() throws Exception {
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

        AuthRecipeUserInfo user1 = createPasswordlessUserWithPhone(process.getProcess(), "+919876543210");
        Thread.sleep(50);
        AuthRecipeUserInfo user2 = createPasswordlessUserWithPhone(process.getProcess(), "+911234567890");

        AuthRecipeUserInfo primaryUser = AuthRecipe.createPrimaryUser(process.getProcess(),
                user1.getSupertokensUserId()).user;
        AuthRecipe.linkAccounts(process.getProcess(), user2.getSupertokensUserId(), primaryUser.getSupertokensUserId());

        for (String userId : new String[]{user1.getSupertokensUserId(), user2.getSupertokensUserId()}) {
            Map<String, String> params = new HashMap<>();
            params.put("userId", userId);
            JsonObject response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                    "http://localhost:3567/user/id", params, 1000, 1000, null,
                    WebserverAPI.getLatestCDIVersion().get(), "");
            JsonObject user = response.get("user").getAsJsonObject();

            Set<String> phoneNumbers = new HashSet<>();
            for (JsonElement phoneNumberElem : user.get("phoneNumbers").getAsJsonArray()) {
                phoneNumbers.add(phoneNumberElem.getAsString());
            }
            assertEquals(2, phoneNumbers.size());
            assertTrue(phoneNumbers.contains("+919876543210"));
            assertTrue(phoneNumbers.contains("+911234567890"));
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testThatPhoneNumberIsUnionOfLinkedAccounts2() throws Exception {
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

        AuthRecipeUserInfo user1 = createPasswordlessUserWithPhone(process.getProcess(), "+919876543210");
        Thread.sleep(50);
        AuthRecipeUserInfo user2 = createPasswordlessUserWithPhone(process.getProcess(), "+911234567890");
        Thread.sleep(50);
        AuthRecipeUserInfo user3 = createThirdPartyUser(process.getProcess(), "google", "googleid", "test@example.com");

        AuthRecipeUserInfo primaryUser = AuthRecipe.createPrimaryUser(process.getProcess(),
                user1.getSupertokensUserId()).user;
        AuthRecipe.linkAccounts(process.getProcess(), user2.getSupertokensUserId(), primaryUser.getSupertokensUserId());
        AuthRecipe.linkAccounts(process.getProcess(), user3.getSupertokensUserId(), primaryUser.getSupertokensUserId());

        for (String userId : new String[]{user1.getSupertokensUserId(), user2.getSupertokensUserId()}) {
            Map<String, String> params = new HashMap<>();
            params.put("userId", userId);
            JsonObject response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                    "http://localhost:3567/user/id", params, 1000, 1000, null,
                    WebserverAPI.getLatestCDIVersion().get(), "");
            JsonObject user = response.get("user").getAsJsonObject();

            Set<String> phoneNumbers = new HashSet<>();
            for (JsonElement phoneNumberElem : user.get("phoneNumbers").getAsJsonArray()) {
                phoneNumbers.add(phoneNumberElem.getAsString());
            }
            assertEquals(2, phoneNumbers.size());
            assertTrue(phoneNumbers.contains("+919876543210"));
            assertTrue(phoneNumbers.contains("+911234567890"));
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testThatPhoneNumberIsUnionOfLinkedAccounts3() throws Exception {
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

        AuthRecipeUserInfo user1 = createPasswordlessUserWithPhone(process.getProcess(), "+919876543210");
        Thread.sleep(50);
        AuthRecipeUserInfo user2 = createPasswordlessUserWithPhone(process.getProcess(), "+911234567890");
        Thread.sleep(50);
        AuthRecipeUserInfo user3 = createEmailPasswordUser(process.getProcess(), "test@example.com", "password");

        AuthRecipeUserInfo primaryUser = AuthRecipe.createPrimaryUser(process.getProcess(),
                user1.getSupertokensUserId()).user;
        AuthRecipe.linkAccounts(process.getProcess(), user2.getSupertokensUserId(), primaryUser.getSupertokensUserId());
        AuthRecipe.linkAccounts(process.getProcess(), user3.getSupertokensUserId(), primaryUser.getSupertokensUserId());

        for (String userId : new String[]{user1.getSupertokensUserId(), user2.getSupertokensUserId()}) {
            Map<String, String> params = new HashMap<>();
            params.put("userId", userId);
            JsonObject response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                    "http://localhost:3567/user/id", params, 1000, 1000, null,
                    WebserverAPI.getLatestCDIVersion().get(), "");
            JsonObject user = response.get("user").getAsJsonObject();

            Set<String> phoneNumbers = new HashSet<>();
            for (JsonElement phoneNumberElem : user.get("phoneNumbers").getAsJsonArray()) {
                phoneNumbers.add(phoneNumberElem.getAsString());
            }
            assertEquals(2, phoneNumbers.size());
            assertTrue(phoneNumbers.contains("+919876543210"));
            assertTrue(phoneNumbers.contains("+911234567890"));
        }

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

        for (String userId : new String[]{user1.getSupertokensUserId(), user2.getSupertokensUserId(),
                user3.getSupertokensUserId(), user4.getSupertokensUserId(), "ext1", "ext2", "ext3"}) {
            Map<String, String> params = new HashMap<>();
            params.put("userId", userId);
            JsonObject response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                    "http://localhost:3567/user/id", params, 1000, 1000, null,
                    SemVer.v4_0.get(), "");
            JsonObject user = response.get("user").getAsJsonObject();
            assertEquals("ext1", user.get("id").getAsString());
            assertEquals("ext1", user.get("loginMethods").getAsJsonArray().get(0).getAsJsonObject().get("recipeUserId")
                    .getAsString());
            assertEquals("ext2", user.get("loginMethods").getAsJsonArray().get(1).getAsJsonObject().get("recipeUserId")
                    .getAsString());
            assertEquals("ext3", user.get("loginMethods").getAsJsonArray().get(2).getAsJsonObject().get("recipeUserId")
                    .getAsString());
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testUnknownUser() throws Exception {
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

        Map<String, String> params = new HashMap<>();
        params.put("userId", "unknownid");
        JsonObject response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                "http://localhost:3567/user/id", params, 1000, 1000, null,
                WebserverAPI.getLatestCDIVersion().get(), "");
        assertEquals("UNKNOWN_USER_ID_ERROR", response.get("status").getAsString());

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }
}
