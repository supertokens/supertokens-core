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

package io.supertokens.test.userIdMapping.api;

import com.google.gson.JsonObject;
import io.supertokens.ProcessState;
import io.supertokens.featureflag.EE_FEATURES;
import io.supertokens.featureflag.FeatureFlagTestContent;
import io.supertokens.featureflag.exceptions.FeatureNotEnabledException;
import io.supertokens.multitenancy.Multitenancy;
import io.supertokens.multitenancy.exception.BadPermissionException;
import io.supertokens.multitenancy.exception.CannotModifyBaseConfigException;
import io.supertokens.pluginInterface.exceptions.InvalidConfigException;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.multitenancy.*;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.pluginInterface.useridmapping.UserIdMappingStorage;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.test.TestingProcessManager;
import io.supertokens.test.Utils;
import io.supertokens.test.httpRequest.HttpRequestForTesting;
import io.supertokens.test.httpRequest.HttpResponseException;
import io.supertokens.thirdparty.InvalidProviderConfigException;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.HashMap;

import static org.junit.Assert.*;

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
                            new ThirdPartyConfig(false, null),
                            new PasswordlessConfig(false),
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
                            new ThirdPartyConfig(false, null),
                            new PasswordlessConfig(false),
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
                            new ThirdPartyConfig(false, null),
                            new PasswordlessConfig(false),
                            config
                    )
            );
        }

        t1 = new TenantIdentifier(null, "a1", null);
        t2 = new TenantIdentifier(null, "a1", "t1");
        t3 = new TenantIdentifier(null, "a1", "t2");
    }

    private JsonObject emailPasswordSignUp(TenantIdentifier tenantIdentifier, String email, String password)
            throws HttpResponseException, IOException {
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("email", email);
        requestBody.addProperty("password", password);
        JsonObject signUpResponse = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                HttpRequestForTesting.getMultitenantUrl(tenantIdentifier, "/recipe/signup"),
                requestBody, 1000, 1000, null,
                Utils.getCdiVersionStringLatestForTests(), "emailpassword");
        assertEquals("OK", signUpResponse.getAsJsonPrimitive("status").getAsString());
        return signUpResponse.getAsJsonObject("user");
    }

    private void successfulCreateUserIdMapping(TenantIdentifier tenantIdentifier, String supertokensUserId, String externalUserId)
            throws HttpResponseException, IOException {
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("superTokensUserId", supertokensUserId);
        requestBody.addProperty("externalUserId", externalUserId);

        JsonObject response = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                HttpRequestForTesting.getMultitenantUrl(tenantIdentifier, "/recipe/userid/map"), requestBody,
                1000, 1000, null,
                Utils.getCdiVersionStringLatestForTests(), "useridmapping");
        assertEquals("OK", response.get("status").getAsString());
    }

    private void mappingAlreadyExistsWithCreateUserIdMapping(TenantIdentifier tenantIdentifier, String supertokensUserId, String externalUserId)
            throws HttpResponseException, IOException {
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("superTokensUserId", supertokensUserId);
        requestBody.addProperty("externalUserId", externalUserId);

        JsonObject response = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                HttpRequestForTesting.getMultitenantUrl(tenantIdentifier, "/recipe/userid/map"), requestBody,
                1000, 1000, null,
                Utils.getCdiVersionStringLatestForTests(), "useridmapping");
        assertEquals("USER_ID_MAPPING_ALREADY_EXISTS_ERROR", response.get("status").getAsString());
    }

    private JsonObject getUserIdMapping(TenantIdentifier tenantIdentifier, String userId, String userIdType)
            throws HttpResponseException, IOException {
        HashMap<String, String> QUERY_PARAM = new HashMap<>();
        QUERY_PARAM.put("userId", userId);
        QUERY_PARAM.put("userIdType", userIdType);

        JsonObject response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                HttpRequestForTesting.getMultitenantUrl(tenantIdentifier, "/recipe/userid/map"), QUERY_PARAM, 1000, 1000, null,
                Utils.getCdiVersionStringLatestForTests(), "useridmapping");
        assertEquals("OK", response.get("status").getAsString());
        return response;
    }

    private void getUnknownUserIdMapping(TenantIdentifier tenantIdentifier, String userId, String userIdType)
            throws HttpResponseException, IOException {
        HashMap<String, String> QUERY_PARAM = new HashMap<>();
        QUERY_PARAM.put("userId", userId);
        QUERY_PARAM.put("userIdType", userIdType);

        JsonObject response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                HttpRequestForTesting.getMultitenantUrl(tenantIdentifier, "/recipe/userid/map"), QUERY_PARAM, 1000, 1000, null,
                Utils.getCdiVersionStringLatestForTests(), "useridmapping");
        assertEquals("UNKNOWN_MAPPING_ERROR", response.get("status").getAsString());
    }

    private void successfulRemoveUserIdMapping(TenantIdentifier tenantIdentifier, String userId, String userIdType)
            throws HttpResponseException, IOException {
        JsonObject request = new JsonObject();
        request.addProperty("userId", userId);
        request.addProperty("userIdType", userIdType);

        JsonObject response = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                HttpRequestForTesting.getMultitenantUrl(tenantIdentifier, "/recipe/userid/map/remove"), request,
                1000, 1000, null,
                Utils.getCdiVersionStringLatestForTests(), "useridmapping");
        assertEquals(2, response.entrySet().size());
        assertEquals("OK", response.get("status").getAsString());
        assertTrue(response.get("didMappingExist").getAsBoolean());
    }

    @Test
    public void testUserIdMappingWorksCorrectlyAcrossTenants() throws Exception {
        JsonObject user1 = emailPasswordSignUp(t1, "user@example.com", "password1");
        JsonObject user2 = emailPasswordSignUp(t2, "user@example.com", "password2");
        JsonObject user3 = emailPasswordSignUp(t3, "user@example.com", "password3");

        user1.addProperty("externalUserId", "euserid1");
        user2.addProperty("externalUserId", "euserid2");
        user3.addProperty("externalUserId", "euserid3");


        for (TenantIdentifier createTenant: new TenantIdentifier[]{t1, t2, t3}) {
            successfulCreateUserIdMapping(createTenant, user1.get("id").getAsString(), "euserid1");
            successfulCreateUserIdMapping(createTenant, user2.get("id").getAsString(), "euserid2");
            successfulCreateUserIdMapping(createTenant, user3.get("id").getAsString(), "euserid3");

            for (TenantIdentifier queryTenant : new TenantIdentifier[]{t1, t2, t3}) {
                for (JsonObject user : new JsonObject[]{user1, user2, user3}) {
                    {
                        JsonObject mapping = getUserIdMapping(queryTenant, user.get("id").getAsString(), "SUPERTOKENS");
                        assertEquals(user.get("id").getAsString(), mapping.get("superTokensUserId").getAsString());
                        assertEquals(user.get("externalUserId").getAsString(),
                                mapping.get("externalUserId").getAsString());
                    }
                    {
                        JsonObject mapping = getUserIdMapping(queryTenant, user.get("id").getAsString(), "ANY");
                        assertEquals(user.get("id").getAsString(), mapping.get("superTokensUserId").getAsString());
                        assertEquals(user.get("externalUserId").getAsString(),
                                mapping.get("externalUserId").getAsString());
                    }
                    {
                        JsonObject mapping = getUserIdMapping(queryTenant, user.get("externalUserId").getAsString(), "EXTERNAL");
                        assertEquals(user.get("id").getAsString(), mapping.get("superTokensUserId").getAsString());
                        assertEquals(user.get("externalUserId").getAsString(),
                                mapping.get("externalUserId").getAsString());
                    }
                    {
                        JsonObject mapping = getUserIdMapping(queryTenant, user.get("externalUserId").getAsString(), "ANY");
                        assertEquals(user.get("id").getAsString(), mapping.get("superTokensUserId").getAsString());
                        assertEquals(user.get("externalUserId").getAsString(),
                                mapping.get("externalUserId").getAsString());
                    }
                }
            }

            successfulRemoveUserIdMapping(createTenant, user1.get("id").getAsString(), "SUPERTOKENS");
            successfulRemoveUserIdMapping(createTenant, user2.get("id").getAsString(), "SUPERTOKENS");
            successfulRemoveUserIdMapping(createTenant, user3.get("id").getAsString(), "SUPERTOKENS");
        }
    }

    @Test
    public void testSameExternalIdIsDisallowedIrrespectiveOfUserPool() throws Exception {
        TenantIdentifier[] tenants = new TenantIdentifier[]{t1, t2, t3};
        int userCount = 0;
        int testcase = 0;

        for (TenantIdentifier tx : tenants) {
            for (TenantIdentifier ty : tenants) {
                JsonObject user1 = emailPasswordSignUp(tx, "user" + (userCount++) + "@example.com", "password");
                JsonObject user2 = emailPasswordSignUp(ty, "user" + (userCount++) + "@example.com", "password");

                String externalUserId = "euserid" + (testcase++);

                successfulCreateUserIdMapping(tx, user1.get("id").getAsString(), externalUserId);
                mappingAlreadyExistsWithCreateUserIdMapping(ty, user2.get("id").getAsString(), externalUserId);
            }
        }
    }

    @Test
    public void testRemoveMappingWorksAppWide() throws Exception {
        TenantIdentifier[] tenants = new TenantIdentifier[]{t1, t2, t3};
        int userCount = 0;

        for (TenantIdentifier userTenant : tenants) {
            JsonObject user = emailPasswordSignUp(userTenant, "user" + (userCount++) + "@example.com", "password");
            String externalUserId = "euserid" + userCount;
            user.addProperty("externalUserId", externalUserId);

            for (TenantIdentifier tx : tenants) {
                for (TenantIdentifier ty : tenants) {
                    {
                        successfulCreateUserIdMapping(tx, user.get("id").getAsString(), externalUserId);
                        getUserIdMapping(ty, user.get("id").getAsString(), "SUPERTOKENS");
                        successfulRemoveUserIdMapping(ty, user.get("id").getAsString(), "SUPERTOKENS");
                        getUnknownUserIdMapping(ty, user.get("id").getAsString(), "SUPERTOKENS");
                    }
                    {
                        successfulCreateUserIdMapping(tx, user.get("id").getAsString(), externalUserId);
                        getUserIdMapping(ty, user.get("id").getAsString(), "ANY");
                        successfulRemoveUserIdMapping(ty, user.get("id").getAsString(), "ANY");
                        getUnknownUserIdMapping(ty, user.get("id").getAsString(), "ANY");
                    }
                    {
                        successfulCreateUserIdMapping(tx, user.get("id").getAsString(), externalUserId);
                        getUserIdMapping(ty, user.get("externalUserId").getAsString(), "EXTERNAL");
                        successfulRemoveUserIdMapping(ty, user.get("externalUserId").getAsString(), "EXTERNAL");
                        getUnknownUserIdMapping(ty, user.get("externalUserId").getAsString(), "EXTERNAL");
                    }
                    {
                        successfulCreateUserIdMapping(tx, user.get("id").getAsString(), externalUserId);
                        getUserIdMapping(ty, user.get("externalUserId").getAsString(), "ANY");
                        successfulRemoveUserIdMapping(ty, user.get("externalUserId").getAsString(), "ANY");
                        getUnknownUserIdMapping(ty, user.get("externalUserId").getAsString(), "ANY");
                    }
                }
            }
        }
    }

    @Test
    public void testSameExternalIdAcrossUserPoolPrioritizesTenantOfInterest() throws Exception {
        JsonObject user1 = emailPasswordSignUp(t1, "user@example.com", "password1");
        JsonObject user2 = emailPasswordSignUp(t2, "user@example.com", "password2");

        ((UserIdMappingStorage)StorageLayer.getStorage(t1, process.getProcess())).createUserIdMapping(
                t1.toAppIdentifier(), user1.get("id").getAsString(), "euserid", null);

        ((UserIdMappingStorage)StorageLayer.getStorage(t2, process.getProcess())).createUserIdMapping(
                t2.toAppIdentifier(), user2.get("id").getAsString(), "euserid", null);

        {
            JsonObject mapping = getUserIdMapping(t1, "euserid", "EXTERNAL");
            assertEquals(user1.get("id").getAsString(), mapping.get("superTokensUserId").getAsString());
        }
        {
            JsonObject mapping = getUserIdMapping(t1, "euserid", "ANY");
            assertEquals(user1.get("id").getAsString(), mapping.get("superTokensUserId").getAsString());
        }

        {
            JsonObject mapping = getUserIdMapping(t2, "euserid", "EXTERNAL");
            assertEquals(user2.get("id").getAsString(), mapping.get("superTokensUserId").getAsString());
        }
        {
            JsonObject mapping = getUserIdMapping(t2, "euserid", "ANY");
            assertEquals(user2.get("id").getAsString(), mapping.get("superTokensUserId").getAsString());
        }

        {
            JsonObject mapping = getUserIdMapping(t3, "euserid", "EXTERNAL");
            assertEquals(user2.get("id").getAsString(), mapping.get("superTokensUserId").getAsString());
        }
        {
            JsonObject mapping = getUserIdMapping(t3, "euserid", "ANY");
            assertEquals(user2.get("id").getAsString(), mapping.get("superTokensUserId").getAsString());
        }
    }
}
