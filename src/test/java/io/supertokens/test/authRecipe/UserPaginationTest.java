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

package io.supertokens.test.authRecipe;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.supertokens.ProcessState;
import io.supertokens.authRecipe.AuthRecipe;
import io.supertokens.authRecipe.UserPaginationContainer;
import io.supertokens.emailpassword.EmailPassword;
import io.supertokens.emailpassword.exceptions.EmailChangeNotAllowedException;
import io.supertokens.featureflag.EE_FEATURES;
import io.supertokens.featureflag.FeatureFlagTestContent;
import io.supertokens.featureflag.exceptions.FeatureNotEnabledException;
import io.supertokens.multitenancy.Multitenancy;
import io.supertokens.multitenancy.exception.BadPermissionException;
import io.supertokens.multitenancy.exception.CannotModifyBaseConfigException;
import io.supertokens.passwordless.Passwordless;
import io.supertokens.passwordless.exceptions.*;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.pluginInterface.Storage;
import io.supertokens.pluginInterface.authRecipe.AuthRecipeUserInfo;
import io.supertokens.pluginInterface.authRecipe.LoginMethod;
import io.supertokens.pluginInterface.emailpassword.exceptions.DuplicateEmailException;
import io.supertokens.pluginInterface.exceptions.InvalidConfigException;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.exceptions.StorageTransactionLogicException;
import io.supertokens.pluginInterface.multitenancy.*;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.pluginInterface.passwordless.exception.DuplicateLinkCodeHashException;
import io.supertokens.pluginInterface.thirdparty.sqlStorage.ThirdPartySQLStorage;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.test.TestingProcessManager;
import io.supertokens.test.Utils;
import io.supertokens.test.multitenant.api.TestMultitenancyAPIHelper;
import io.supertokens.thirdparty.InvalidProviderConfigException;
import io.supertokens.thirdparty.ThirdParty;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.*;

public class UserPaginationTest {
    TestingProcessManager.TestingProcess process;
    TenantIdentifier t1, t2, t3;
    HashMap<TenantIdentifier, ArrayList<String>> tenantToUsers = new HashMap<>();
    HashMap<String, ArrayList<String>> recipeToUsers = new HashMap<>();

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
                            new EmailPasswordConfig(true),
                            new ThirdPartyConfig(true, null),
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
                            new EmailPasswordConfig(true),
                            new ThirdPartyConfig(true, null),
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
                            new EmailPasswordConfig(true),
                            new ThirdPartyConfig(true, null),
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

    private void createUsers(TenantIdentifier tenantIdentifier, int numUsers, String prefix)
            throws TenantOrAppNotFoundException, DuplicateEmailException, StorageQueryException,
            BadPermissionException, DuplicateLinkCodeHashException, NoSuchAlgorithmException, IOException,
            RestartFlowException, InvalidKeyException, Base64EncodingException, DeviceIdHashMismatchException,
            StorageTransactionLogicException, IncorrectUserInputCodeException, ExpiredUserInputCodeException,
            EmailChangeNotAllowedException {

        if (tenantToUsers.get(tenantIdentifier) == null) {
            tenantToUsers.put(tenantIdentifier, new ArrayList<>());
        }

        Storage storage = (
                StorageLayer.getStorage(tenantIdentifier, process.getProcess()));
        for (int i = 0; i < numUsers; i++) {
            {
                AuthRecipeUserInfo user = EmailPassword.signUp(
                        tenantIdentifier, storage, process.getProcess(),
                        prefix + "epuser" + i + "@example.com", "password" + i);
                tenantToUsers.get(tenantIdentifier).add(user.getSupertokensUserId());
                if (!recipeToUsers.containsKey("emailpassword")) {
                    recipeToUsers.put("emailpassword", new ArrayList<>());
                }
                recipeToUsers.get("emailpassword").add(user.getSupertokensUserId());
            }
            {
                Passwordless.CreateCodeResponse codeResponse = Passwordless.createCode(
                        tenantIdentifier, storage,
                        process.getProcess(),
                        prefix + "pluser" + i + "@example.com",
                        null, null,
                        "abcd"
                );
                Passwordless.ConsumeCodeResponse response = Passwordless.consumeCode(
                        tenantIdentifier, storage,
                        process.getProcess(), codeResponse.deviceId,
                        codeResponse.deviceIdHash, "abcd", null);
                tenantToUsers.get(tenantIdentifier).add(response.user.getSupertokensUserId());

                if (!recipeToUsers.containsKey("passwordless")) {
                    recipeToUsers.put("passwordless", new ArrayList<>());
                }
                recipeToUsers.get("passwordless").add(response.user.getSupertokensUserId());
            }
            {
                ThirdParty.SignInUpResponse user1 = ThirdParty.signInUp(
                        tenantIdentifier, storage,
                        process.getProcess(), "google", "googleid" + i, prefix + "tpuser" + i + "@example.com");
                tenantToUsers.get(tenantIdentifier).add(user1.user.getSupertokensUserId());
                ThirdParty.SignInUpResponse user2 = ThirdParty.signInUp(
                        tenantIdentifier, storage,
                        process.getProcess(), "facebook", "fbid" + i, prefix + "tpuser" + i + "@example.com");
                tenantToUsers.get(tenantIdentifier).add(user2.user.getSupertokensUserId());


                if (!recipeToUsers.containsKey("thirdparty")) {
                    recipeToUsers.put("thirdparty", new ArrayList<>());
                }
                recipeToUsers.get("thirdparty").add(user1.user.getSupertokensUserId());
                recipeToUsers.get("thirdparty").add(user2.user.getSupertokensUserId());
            }
        }
    }

    @Test
    public void testUserPaginationWorksCorrectlyForEachTenant() throws Exception {
        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        createTenants();

        tenantToUsers = new HashMap<>();
        recipeToUsers = new HashMap<>();

        createUsers(t1, 50, "t1");
        createUsers(t2, 50, "t2");
        createUsers(t3, 50, "t3");

        for (TenantIdentifier tenantIdentifier : new TenantIdentifier[]{t1, t2, t3}) {
            { // All recipes
                Set<String> userIdSet = new HashSet<>();

                JsonObject userList = TestMultitenancyAPIHelper.listUsers(tenantIdentifier, null, "10", null,
                        process.getProcess());
                String paginationToken = userList.get("nextPaginationToken").getAsString();

                JsonArray users = userList.get("users").getAsJsonArray();
                for (JsonElement user : users) {
                    String userId = user.getAsJsonObject().get("user").getAsJsonObject().get("id").getAsString();
                    assertFalse(userIdSet.contains(userId));
                    userIdSet.add(userId);

                    assertTrue(tenantToUsers.get(tenantIdentifier).contains(userId));
                }

                while (paginationToken != null) {
                    userList = TestMultitenancyAPIHelper.listUsers(tenantIdentifier, paginationToken, "10", null,
                            process.getProcess());
                    users = userList.get("users").getAsJsonArray();

                    for (JsonElement user : users) {
                        String userId = user.getAsJsonObject().get("user").getAsJsonObject().get("id").getAsString();
                        assertFalse(userIdSet.contains(userId));
                        userIdSet.add(userId);

                        assertTrue(tenantToUsers.get(tenantIdentifier).contains(userId));
                    }

                    paginationToken = null;
                    if (userList.has("nextPaginationToken")) {
                        paginationToken = userList.get("nextPaginationToken").getAsString();
                    }
                }

                assertEquals(200, userIdSet.size());
            }

            { // recipe combinations
                String[] combinations = new String[]{"emailpassword", "passwordless", "thirdparty",
                        "emailpassword,passwordless", "emailpassword,thirdparty", "passwordless,thirdparty"};
                int[] userCounts = new int[]{50, 50, 100, 100, 150, 150};

                for (int i = 0; i < combinations.length; i++) {
                    String includeRecipeIds = combinations[i];
                    int userCount = userCounts[i];

                    Set<String> userIdSet = new HashSet<>();

                    JsonObject userList = TestMultitenancyAPIHelper.listUsers(tenantIdentifier, null, "10",
                            includeRecipeIds, process.getProcess());
                    String paginationToken = userList.get("nextPaginationToken").getAsString();

                    JsonArray users = userList.get("users").getAsJsonArray();
                    for (JsonElement user : users) {
                        String userId = user.getAsJsonObject().get("user").getAsJsonObject().get("id").getAsString();
                        String recipeId = user.getAsJsonObject().get("recipeId").getAsString();
                        assertFalse(userIdSet.contains(userId));
                        userIdSet.add(userId);

                        assertTrue(tenantToUsers.get(tenantIdentifier).contains(userId));
                        assertTrue(includeRecipeIds.contains(recipeId));
                        assertTrue(recipeToUsers.get(recipeId).contains(userId));
                    }

                    while (paginationToken != null) {
                        userList = TestMultitenancyAPIHelper.listUsers(tenantIdentifier, paginationToken, "10",
                                includeRecipeIds, process.getProcess());
                        users = userList.get("users").getAsJsonArray();

                        for (JsonElement user : users) {
                            String userId = user.getAsJsonObject().get("user").getAsJsonObject().get("id")
                                    .getAsString();
                            String recipeId = user.getAsJsonObject().get("recipeId").getAsString();
                            assertFalse(userIdSet.contains(userId));
                            userIdSet.add(userId);

                            assertTrue(tenantToUsers.get(tenantIdentifier).contains(userId));
                            assertTrue(includeRecipeIds.contains(recipeId));
                            assertTrue(recipeToUsers.get(recipeId).contains(userId));
                        }

                        paginationToken = null;
                        if (userList.has("nextPaginationToken")) {
                            paginationToken = userList.get("nextPaginationToken").getAsString();
                        }
                    }

                    assertEquals(userCount, userIdSet.size());
                }
            }
        }
    }

    @Test
    public void testUserPaginationWithSameTimeJoined() throws Exception {
        if (StorageLayer.getBaseStorage(process.main).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        ThirdPartySQLStorage storage = (ThirdPartySQLStorage) StorageLayer.getBaseStorage(process.getProcess());

        Set<String> userIds = new HashSet<>();

        long timeJoined = System.currentTimeMillis();
        for (int i = 0; i < 100; i++) {
            String userId = io.supertokens.utils.Utils.getUUID();
            storage.signUp(TenantIdentifier.BASE_TENANT, userId, "test" + i + "@example.com",
                    new LoginMethod.ThirdParty("google", userId), timeJoined);
            userIds.add(userId);
        }

        // Test ascending
        {
            Set<String> paginationUserIds = new HashSet<>();
            UserPaginationContainer usersRes = AuthRecipe.getUsers(process.getProcess(), 10,
                    "ASC", null, null, null);

            while (true) {
                for (AuthRecipeUserInfo user : usersRes.users) {
                    paginationUserIds.add(user.getSupertokensUserId());
                }

                if (usersRes.nextPaginationToken == null) {
                    break;
                }
                usersRes = AuthRecipe.getUsers(process.getProcess(), 10,
                        "ASC", usersRes.nextPaginationToken, null, null);
            }

            assertEquals(userIds.size(), paginationUserIds.size());
            assertEquals(userIds, paginationUserIds);
        }

        // Test descending
        {
            Set<String> paginationUserIds = new HashSet<>();
            UserPaginationContainer usersRes = AuthRecipe.getUsers(process.getProcess(), 10,
                    "DESC", null, null, null);

            while (true) {
                for (AuthRecipeUserInfo user : usersRes.users) {
                    paginationUserIds.add(user.getSupertokensUserId());
                }

                if (usersRes.nextPaginationToken == null) {
                    break;
                }
                usersRes = AuthRecipe.getUsers(process.getProcess(), 10,
                        "DESC", usersRes.nextPaginationToken, null, null);
            }

            assertEquals(userIds.size(), paginationUserIds.size());
            assertEquals(userIds, paginationUserIds);
        }
    }
}
