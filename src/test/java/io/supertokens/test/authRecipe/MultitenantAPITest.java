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
import io.supertokens.emailpassword.EmailPassword;
import io.supertokens.featureflag.EE_FEATURES;
import io.supertokens.featureflag.FeatureFlagTestContent;
import io.supertokens.featureflag.exceptions.FeatureNotEnabledException;
import io.supertokens.multitenancy.Multitenancy;
import io.supertokens.multitenancy.exception.BadPermissionException;
import io.supertokens.multitenancy.exception.CannotModifyBaseConfigException;
import io.supertokens.passwordless.Passwordless;
import io.supertokens.passwordless.exceptions.*;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.pluginInterface.emailpassword.UserInfo;
import io.supertokens.pluginInterface.emailpassword.exceptions.DuplicateEmailException;
import io.supertokens.pluginInterface.exceptions.InvalidConfigException;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.exceptions.StorageTransactionLogicException;
import io.supertokens.pluginInterface.multitenancy.*;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.pluginInterface.passwordless.exception.DuplicateLinkCodeHashException;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.test.TestingProcessManager;
import io.supertokens.test.Utils;
import io.supertokens.test.httpRequest.HttpRequestForTesting;
import io.supertokens.test.httpRequest.HttpResponseException;
import io.supertokens.test.multitenant.api.TestMultitenancyAPIHelper;
import io.supertokens.thirdparty.InvalidProviderConfigException;
import io.supertokens.thirdparty.ThirdParty;
import io.supertokens.utils.SemVer;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;

import static org.junit.Assert.*;

public class MultitenantAPITest {
    TestingProcessManager.TestingProcess process;
    TenantIdentifier t1, t2, t3;

    HashMap<TenantIdentifier, ArrayList<String>> tenantToUsers;
    HashMap<String, ArrayList<String>> recipeToUsers;

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
                            config
                    )
            );
        }

        t1 = new TenantIdentifier(null, "a1", null);
        t2 = new TenantIdentifier(null, "a1", "t1");
        t3 = new TenantIdentifier(null, "a1", "t2");
    }

    private void createUsers()
            throws TenantOrAppNotFoundException, DuplicateEmailException, StorageQueryException,
            BadPermissionException, DuplicateLinkCodeHashException, NoSuchAlgorithmException, IOException,
            RestartFlowException, InvalidKeyException, Base64EncodingException, DeviceIdHashMismatchException,
            StorageTransactionLogicException, IncorrectUserInputCodeException, ExpiredUserInputCodeException {
        tenantToUsers = new HashMap<>();
        recipeToUsers = new HashMap<>();

        { // emailpassword users
            recipeToUsers.put("emailpassword", new ArrayList<>());
            int pcount = 1;
            for (TenantIdentifier tenant : new TenantIdentifier[]{t1, t2, t3}) {
                if (tenantToUsers.get(tenant) == null) {
                    tenantToUsers.put(tenant, new ArrayList<>());
                }

                UserInfo user1 = EmailPassword.signUp(
                        tenant.withStorage(StorageLayer.getStorage(tenant, process.getProcess())),
                        process.getProcess(),
                        "user@example.com",
                        "password" + (pcount++)
                );
                tenantToUsers.get(tenant).add(user1.id);
                recipeToUsers.get("emailpassword").add(user1.id);
                UserInfo user2 = EmailPassword.signUp(
                        tenant.withStorage(StorageLayer.getStorage(tenant, process.getProcess())),
                        process.getProcess(),
                        "user@gmail.com",
                        "password2" + (pcount++)
                );
                tenantToUsers.get(tenant).add(user2.id);
                recipeToUsers.get("emailpassword").add(user2.id);
            }
        }
        { // passwordless users
            recipeToUsers.put("passwordless", new ArrayList<>());
            for (TenantIdentifier tenant : new TenantIdentifier[]{t1, t2, t3}) {
                TenantIdentifierWithStorage tenantIdentifierWithStorage = tenant.withStorage(
                        StorageLayer.getStorage(tenant, process.getProcess()));
                {
                    if (tenantToUsers.get(tenant) == null) {
                        tenantToUsers.put(tenant, new ArrayList<>());
                    }

                    Passwordless.CreateCodeResponse codeResponse = Passwordless.createCode(
                            tenantIdentifierWithStorage,
                            process.getProcess(),
                            "user@example.com",
                            null, null,
                            "abcd"
                    );
                    Passwordless.ConsumeCodeResponse response = Passwordless.consumeCode(tenantIdentifierWithStorage,
                            process.getProcess(), codeResponse.deviceId,
                            codeResponse.deviceIdHash, "abcd", null);
                    tenantToUsers.get(tenant).add(response.user.id);
                    recipeToUsers.get("passwordless").add(response.user.id);
                }
                {
                    Passwordless.CreateCodeResponse codeResponse = Passwordless.createCode(
                            tenantIdentifierWithStorage,
                            process.getProcess(),
                            "user@gmail.com",
                            null, null,
                            "abcd"
                    );
                    Passwordless.ConsumeCodeResponse response = Passwordless.consumeCode(tenantIdentifierWithStorage, process.getProcess(), codeResponse.deviceId,
                            codeResponse.deviceIdHash, "abcd", null);
                    tenantToUsers.get(tenant).add(response.user.id);
                    recipeToUsers.get("passwordless").add(response.user.id);
                }
                {
                    Passwordless.CreateCodeResponse codeResponse = Passwordless.createCode(
                            tenantIdentifierWithStorage,
                            process.getProcess(),
                            null,
                            "+1234567890", null,
                            "abcd"
                    );
                    Passwordless.ConsumeCodeResponse response = Passwordless.consumeCode(tenantIdentifierWithStorage, process.getProcess(), codeResponse.deviceId,
                            codeResponse.deviceIdHash, "abcd", null);
                    tenantToUsers.get(tenant).add(response.user.id);
                    recipeToUsers.get("passwordless").add(response.user.id);
                }
                {
                    Passwordless.CreateCodeResponse codeResponse = Passwordless.createCode(
                            tenantIdentifierWithStorage,
                            process.getProcess(),
                            null,
                            "+9876543210", null,
                            "abcd"
                    );
                    Passwordless.ConsumeCodeResponse response = Passwordless.consumeCode(tenantIdentifierWithStorage, process.getProcess(), codeResponse.deviceId,
                            codeResponse.deviceIdHash, "abcd", null);
                    tenantToUsers.get(tenant).add(response.user.id);
                    recipeToUsers.get("passwordless").add(response.user.id);
                }
            }
        }

        { // thirdparty users
            recipeToUsers.put("thirdparty", new ArrayList<>());
            for (TenantIdentifier tenant : new TenantIdentifier[]{t1, t2, t3}) {
                if (tenantToUsers.get(tenant) == null) {
                    tenantToUsers.put(tenant, new ArrayList<>());
                }

                TenantIdentifierWithStorage tenantIdentifierWithStorage = tenant.withStorage(
                        StorageLayer.getStorage(tenant, process.getProcess()));

                ThirdParty.SignInUpResponse user1 = ThirdParty.signInUp(tenantIdentifierWithStorage,
                        process.getProcess(), "google", "googleid1", "user@example.com");
                tenantToUsers.get(tenant).add(user1.user.id);
                recipeToUsers.get("thirdparty").add(user1.user.id);

                ThirdParty.SignInUpResponse user2 = ThirdParty.signInUp(tenantIdentifierWithStorage,
                        process.getProcess(), "google", "googleid2", "user@gmail.com");
                tenantToUsers.get(tenant).add(user2.user.id);
                recipeToUsers.get("thirdparty").add(user2.user.id);

                ThirdParty.SignInUpResponse user3 = ThirdParty.signInUp(tenantIdentifierWithStorage,
                        process.getProcess(), "facebook", "facebookid1", "user@example.com");
                tenantToUsers.get(tenant).add(user3.user.id);
                recipeToUsers.get("thirdparty").add(user3.user.id);

                ThirdParty.SignInUpResponse user4 = ThirdParty.signInUp(tenantIdentifierWithStorage,
                        process.getProcess(), "facebook", "facebookid2", "user@gmail.com");
                tenantToUsers.get(tenant).add(user4.user.id);
                recipeToUsers.get("thirdparty").add(user4.user.id);
            }
        }
    }

    private long getUserCount(TenantIdentifier tenantIdentifier, String []recipeIds, boolean includeAllTenants)
            throws HttpResponseException, IOException {
        HashMap<String, String> params = new HashMap<>();
        if (recipeIds != null) {
            params.put("includeRecipeIds", String.join(",", recipeIds));
        }
        if (includeAllTenants) {
            params.put("includeAllTenants", "true");
        }

        JsonObject response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                HttpRequestForTesting.getMultitenantUrl(tenantIdentifier, "/users/count"),
                params, 1000, 1000, null,
                SemVer.v3_0.get(), null);

        assertEquals("OK", response.get("status").getAsString());

        return response.get("count").getAsLong();
    }

    private String[] getUsers(TenantIdentifier tenantIdentifier, String []recipeIds)
            throws HttpResponseException, IOException {
        HashMap<String, String> params = new HashMap<>();
        if (recipeIds != null) {
            params.put("includeRecipeIds", String.join(",", recipeIds));
        }

        JsonObject response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                HttpRequestForTesting.getMultitenantUrl(tenantIdentifier, "/users"),
                params, 1000, 1000, null,
                SemVer.v3_0.get(), null);

        assertEquals("OK", response.get("status").getAsString());

        JsonArray users = response.get("users").getAsJsonArray();
        String[] userIds = new String[users.size()];
        int i = 0;
        for (JsonElement user : users) {
            userIds[i++] = user.getAsJsonObject().get("user").getAsJsonObject().get("id").getAsString();
        }

        return userIds;
    }

    private String[] getUsers(TenantIdentifier tenantIdentifier, String[] emails, String[] phoneNumbers, String[] providers)
            throws HttpResponseException, IOException {
        HashMap<String, String> params = new HashMap<>();
        if (emails != null) {
            params.put("email", String.join(";", emails));
        }
        if (phoneNumbers != null) {
            params.put("phone", String.join(";", phoneNumbers));
        }
        if (providers != null) {
            params.put("provider", String.join(";", providers));
        }

        JsonObject response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                HttpRequestForTesting.getMultitenantUrl(tenantIdentifier, "/users"),
                params, 1000, 1000, null,
                SemVer.v3_0.get(), null);

        assertEquals("OK", response.get("status").getAsString());

        JsonArray users = response.get("users").getAsJsonArray();
        String[] userIds = new String[users.size()];
        int i = 0;
        for (JsonElement user : users) {
            userIds[i++] = user.getAsJsonObject().get("user").getAsJsonObject().get("id").getAsString();
        }

        return userIds;
    }

    @Test
    public void testUserCount() throws Exception {
        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        createUsers();

        assertEquals(30, getUserCount(t1, null, true));
        assertEquals(6, getUserCount(t1, new String[]{"emailpassword"}, true));
        assertEquals(12, getUserCount(t1, new String[]{"passwordless"}, true));
        assertEquals(12, getUserCount(t1, new String[]{"thirdparty"}, true));
        assertEquals(18, getUserCount(t1, new String[]{"emailpassword", "passwordless"}, true));
        assertEquals(24, getUserCount(t1, new String[]{"thirdparty", "passwordless"}, true));

        for (TenantIdentifier tenant : new TenantIdentifier[]{t1, t2, t3}) {
            TenantIdentifier publicTenant = tenant.toAppIdentifier().getAsPublicTenantIdentifier();
            assertEquals(10, getUserCount(tenant, null, false));
            assertEquals(2, getUserCount(tenant, new String[]{"emailpassword"}, false));
            assertEquals(4, getUserCount(tenant, new String[]{"passwordless"}, false));
            assertEquals(4, getUserCount(tenant, new String[]{"thirdparty"}, false));
            assertEquals(6, getUserCount(tenant, new String[]{"emailpassword", "passwordless"}, false));
            assertEquals(8, getUserCount(tenant, new String[]{"thirdparty", "passwordless"}, false));
        }
    }

    @Test
    public void testGetUsers() throws Exception {
        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        createUsers();

        for (TenantIdentifier tenant : new TenantIdentifier[]{t1, t2, t3}) {
            {
                String[] users = getUsers(tenant, null);
                for (String user : users) {
                    assertTrue(tenantToUsers.get(tenant).contains(user));
                }
            }

            {
                String[] users = getUsers(tenant, new String[]{"emailpassword"});
                for (String user : users) {
                    assertTrue(tenantToUsers.get(tenant).contains(user));
                    assertTrue(recipeToUsers.get("emailpassword").contains(user));
                }
            }

            {
                String[] users = getUsers(tenant, new String[]{"passwordless"});
                for (String user : users) {
                    assertTrue(tenantToUsers.get(tenant).contains(user));
                    assertTrue(recipeToUsers.get("passwordless").contains(user));
                }
            }

            {
                String[] users = getUsers(tenant, new String[]{"thirdparty"});
                for (String user : users) {
                    assertTrue(tenantToUsers.get(tenant).contains(user));
                    assertTrue(recipeToUsers.get("thirdparty").contains(user));
                }
            }

            {
                String[] users = getUsers(tenant, new String[]{"emailpassword", "passwordless"});
                for (String user : users) {
                    assertTrue(tenantToUsers.get(tenant).contains(user));
                    assertTrue(recipeToUsers.get("emailpassword").contains(user) || recipeToUsers.get("passwordless").contains(user));
                }
            }

            {
                String[] users = getUsers(tenant, new String[]{"thirdparty", "passwordless"});
                for (String user : users) {
                    assertTrue(tenantToUsers.get(tenant).contains(user));
                    assertTrue(recipeToUsers.get("thirdparty").contains(user) || recipeToUsers.get("passwordless").contains(user));
                }
            }
        }
    }

    @Test
    public void testSearch() throws Exception {
        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        createUsers();

        for (TenantIdentifier tenant : new TenantIdentifier[]{t1, t2, t3}) {
            {
                String[] users = getUsers(tenant, new String[]{"user"}, null, null);
                assertEquals(8, users.length);
                for (String user : users) {
                    assertTrue(tenantToUsers.get(tenant).contains(user));
                }
            }
            {
                String[] users = getUsers(tenant, new String[]{"gmail.com"}, null, null);
                assertEquals(4, users.length);
                for (String user : users) {
                    assertTrue(tenantToUsers.get(tenant).contains(user));
                }
            }
            {
                String[] users = getUsers(tenant, null, new String[]{"+1234"}, null);
                assertEquals(1, users.length);
                for (String user : users) {
                    assertTrue(tenantToUsers.get(tenant).contains(user));
                }
            }
            {
                String[] users = getUsers(tenant, null, null, new String[]{"goog"});
                assertEquals(2, users.length);
                for (String user : users) {
                    assertTrue(tenantToUsers.get(tenant).contains(user));
                }
            }
            {
                String[] users = getUsers(tenant, null, null, new String[]{"face"});
                assertEquals(2, users.length);
                for (String user : users) {
                    assertTrue(tenantToUsers.get(tenant).contains(user));
                }
            }
            {
                String[] users = getUsers(tenant, null, null, new String[]{"goog", "face"});
                assertEquals(4, users.length);
                for (String user : users) {
                    assertTrue(tenantToUsers.get(tenant).contains(user));
                }
            }
            {
                String[] users = getUsers(tenant, new String[]{"gmail.com"}, null, new String[]{"goog"});
                assertEquals(1, users.length);
                for (String user : users) {
                    assertTrue(tenantToUsers.get(tenant).contains(user));
                }
            }
            {
                String[] users = getUsers(tenant, new String[]{"gmail.com"}, null, new String[]{"goog", "face"});
                assertEquals(2, users.length);
                for (String user : users) {
                    assertTrue(tenantToUsers.get(tenant).contains(user));
                }
            }
        }
    }

    @Test
    public void testSearchWhenUsersAreSharedAcrossTenants() throws Exception {
        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        createUsers();
        { // associate across tenants
            for (String userId : tenantToUsers.get(t2)) {
                TestMultitenancyAPIHelper.associateUserToTenant(t3, userId, process.getProcess());
            }
        }

        for (TenantIdentifier tenant : new TenantIdentifier[]{t1, t2, t3}) {
            {
                String[] users = getUsers(tenant, new String[]{"user"}, null, null);
                assertEquals(8, users.length);
                for (String user : users) {
                    assertTrue(tenantToUsers.get(tenant).contains(user));
                }
            }
            {
                String[] users = getUsers(tenant, new String[]{"gmail.com"}, null, null);
                assertEquals(4, users.length);
                for (String user : users) {
                    assertTrue(tenantToUsers.get(tenant).contains(user));
                }
            }
            {
                String[] users = getUsers(tenant, null, new String[]{"+1234"}, null);
                assertEquals(1, users.length);
                for (String user : users) {
                    assertTrue(tenantToUsers.get(tenant).contains(user));
                }
            }
            {
                String[] users = getUsers(tenant, null, null, new String[]{"goog"});
                assertEquals(2, users.length);
                for (String user : users) {
                    assertTrue(tenantToUsers.get(tenant).contains(user));
                }
            }
            {
                String[] users = getUsers(tenant, null, null, new String[]{"face"});
                assertEquals(2, users.length);
                for (String user : users) {
                    assertTrue(tenantToUsers.get(tenant).contains(user));
                }
            }
            {
                String[] users = getUsers(tenant, null, null, new String[]{"goog", "face"});
                assertEquals(4, users.length);
                for (String user : users) {
                    assertTrue(tenantToUsers.get(tenant).contains(user));
                }
            }
            {
                String[] users = getUsers(tenant, new String[]{"gmail.com"}, null, new String[]{"goog"});
                assertEquals(1, users.length);
                for (String user : users) {
                    assertTrue(tenantToUsers.get(tenant).contains(user));
                }
            }
            {
                String[] users = getUsers(tenant, new String[]{"gmail.com"}, null, new String[]{"goog", "face"});
                assertEquals(2, users.length);
                for (String user : users) {
                    assertTrue(tenantToUsers.get(tenant).contains(user));
                }
            }
        }
    }
}
