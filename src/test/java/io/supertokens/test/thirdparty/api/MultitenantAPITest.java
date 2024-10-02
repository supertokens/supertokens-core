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

package io.supertokens.test.thirdparty.api;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.supertokens.ProcessState;
import io.supertokens.featureflag.EE_FEATURES;
import io.supertokens.featureflag.FeatureFlagTestContent;
import io.supertokens.featureflag.exceptions.FeatureNotEnabledException;
import io.supertokens.multitenancy.Multitenancy;
import io.supertokens.multitenancy.exception.BadPermissionException;
import io.supertokens.multitenancy.exception.CannotModifyBaseConfigException;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.pluginInterface.exceptions.InvalidConfigException;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.multitenancy.*;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.test.TestingProcessManager;
import io.supertokens.test.Utils;
import io.supertokens.test.httpRequest.HttpRequestForTesting;
import io.supertokens.test.httpRequest.HttpResponseException;
import io.supertokens.thirdparty.InvalidProviderConfigException;
import io.supertokens.utils.SemVer;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.HashMap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class MultitenantAPITest {
    TestingProcessManager.TestingProcess process;
    TenantIdentifier t1, t2, t3;

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
                            new EmailPasswordConfig(false),
                            new ThirdPartyConfig(true, null),
                            new PasswordlessConfig(false),
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
                            new EmailPasswordConfig(false),
                            new ThirdPartyConfig(true, null),
                            new PasswordlessConfig(false),
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
                            new EmailPasswordConfig(false),
                            new ThirdPartyConfig(true, null),
                            new PasswordlessConfig(false),
                            null, null,
                            config
                    )
            );
        }

        t1 = new TenantIdentifier(null, "a1", null);
        t2 = new TenantIdentifier(null, "a1", "t1");
        t3 = new TenantIdentifier(null, "a1", "t2");
    }

    public JsonObject signInUp(TenantIdentifier tenantIdentifier, String thirdPartyId, String thirdPartyUserId,
                               String email)
            throws HttpResponseException, IOException {
        JsonObject emailObject = new JsonObject();
        emailObject.addProperty("id", email);

        JsonObject signUpRequestBody = new JsonObject();
        signUpRequestBody.addProperty("thirdPartyId", thirdPartyId);
        signUpRequestBody.addProperty("thirdPartyUserId", thirdPartyUserId);
        signUpRequestBody.add("email", emailObject);

        JsonObject response = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                HttpRequestForTesting.getMultitenantUrl(tenantIdentifier, "/recipe/signinup"), signUpRequestBody,
                1000, 1000, null,
                SemVer.v3_0.get(), "thirdparty");
        assertEquals("OK", response.get("status").getAsString());
        assertEquals(3, response.entrySet().size());

        return response.get("user").getAsJsonObject();
    }

    private JsonObject getUserUsingId(TenantIdentifier tenantIdentifier, String userId)
            throws HttpResponseException, IOException {
        HashMap<String, String> map = new HashMap<>();
        map.put("userId", userId);
        JsonObject userResponse = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                HttpRequestForTesting.getMultitenantUrl(tenantIdentifier, "/recipe/user"),
                map, 1000, 1000, null, SemVer.v3_0.get(),
                "thirdparty");
        assertEquals("OK", userResponse.getAsJsonPrimitive("status").getAsString());
        return userResponse.getAsJsonObject("user");
    }

    private JsonObject getUserUsingThirdPartyUserId(TenantIdentifier tenantIdentifier, String thirdPartyId,
                                                    String thirdPartyUserId)
            throws HttpResponseException, IOException {
        HashMap<String, String> map = new HashMap<>();
        map.put("thirdPartyId", thirdPartyId);
        map.put("thirdPartyUserId", thirdPartyUserId);
        JsonObject userResponse = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                HttpRequestForTesting.getMultitenantUrl(tenantIdentifier, "/recipe/user"),
                map, 1000, 1000, null, SemVer.v3_0.get(),
                "thirdparty");
        assertEquals("OK", userResponse.getAsJsonPrimitive("status").getAsString());
        return userResponse.getAsJsonObject("user");
    }

    private JsonObject[] getUsersUsingEmail(TenantIdentifier tenantIdentifier, String email)
            throws HttpResponseException, IOException {
        HashMap<String, String> map = new HashMap<>();
        map.put("email", email);
        JsonObject userResponse = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                HttpRequestForTesting.getMultitenantUrl(tenantIdentifier, "/recipe/users/by-email"),
                map, 1000, 1000, null, SemVer.v3_0.get(),
                "thirdparty");
        assertEquals("OK", userResponse.getAsJsonPrimitive("status").getAsString());

        JsonArray userObjects = userResponse.getAsJsonArray("users");
        JsonObject[] users = new JsonObject[userObjects.size()];
        for (int i = 0; i < userObjects.size(); i++) {
            JsonElement jsonElement = userObjects.get(i);
            if (jsonElement.isJsonObject()) {
                users[i] = jsonElement.getAsJsonObject();
            }
        }
        return users;
    }

    @Test
    public void testSameThirdPartyUserCanLoginAcrossTenants() throws Exception {
        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        JsonObject user1 = signInUp(t1, "google", "google-user-id", "user@gmail.com");
        JsonObject user2 = signInUp(t2, "google", "google-user-id", "user@gmail.com");
        JsonObject user3 = signInUp(t3, "google", "google-user-id", "user@gmail.com");

        assertEquals("user@gmail.com", user1.get("email").getAsString());
        assertEquals("user@gmail.com", user2.get("email").getAsString());
        assertEquals("user@gmail.com", user3.get("email").getAsString());
    }

    @Test
    public void testGetUserUsingIdReturnsUserFromTheRightTenantWhileQueryingFromAnyTenantInTheSameApp()
            throws Exception {
        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        JsonObject user1 = signInUp(t1, "google", "google-user-id", "user@gmail.com");
        JsonObject user2 = signInUp(t2, "google", "google-user-id", "user@gmail.com");
        JsonObject user3 = signInUp(t3, "google", "google-user-id", "user@gmail.com");

        for (TenantIdentifier tenant : new TenantIdentifier[]{t1}) { // Only public tenant can get user by id
            assertEquals(user1, getUserUsingId(tenant, user1.get("id").getAsString()));
            assertEquals(user2, getUserUsingId(tenant, user2.get("id").getAsString()));
            assertEquals(user3, getUserUsingId(tenant, user3.get("id").getAsString()));
        }
    }

    @Test
    public void testGetUserByThirdPartyIdReturnsTenantSpecificUser() throws Exception {
        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        JsonObject user1 = signInUp(t1, "google", "google-user-id", "user@gmail.com");
        JsonObject user2 = signInUp(t2, "google", "google-user-id", "user@gmail.com");
        JsonObject user3 = signInUp(t3, "google", "google-user-id", "user@gmail.com");

        assertEquals(user1, getUserUsingThirdPartyUserId(t1, "google", "google-user-id"));
        assertEquals(user2, getUserUsingThirdPartyUserId(t2, "google", "google-user-id"));
        assertEquals(user3, getUserUsingThirdPartyUserId(t3, "google", "google-user-id"));
    }

    @Test
    public void testGetUserByEmailReturnsTenantSpecificUser() throws Exception {
        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        JsonObject user1 = signInUp(t1, "google", "google-user-id", "user@gmail.com");
        JsonObject user2 = signInUp(t2, "google", "google-user-id", "user@gmail.com");
        JsonObject user3 = signInUp(t3, "google", "google-user-id", "user@gmail.com");

        assertEquals(user1, getUsersUsingEmail(t1, user1.get("email").getAsString())[0]);
        assertEquals(user2, getUsersUsingEmail(t2, user2.get("email").getAsString())[0]);
        assertEquals(user3, getUsersUsingEmail(t3, user3.get("email").getAsString())[0]);
    }
}
