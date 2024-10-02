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
import io.supertokens.pluginInterface.STORAGE_TYPE;
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
import io.supertokens.utils.SemVer;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.HashMap;

import static org.junit.Assert.*;

public class MultitenantAPITest {
    TestingProcessManager.TestingProcess process;
    TenantIdentifier t1, t2, t3, t4;

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
        // User pool 1 - (null, a1, null), (null, a2, null)
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
                            null, null, config
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
                            null, null, config
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
                            null, null, config
                    )
            );
        }

        { // tenant 4
            JsonObject config = new JsonObject();
            TenantIdentifier tenantIdentifier = new TenantIdentifier(null, "a2", null);

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
                            null, null, config
                    )
            );
        }

        t1 = new TenantIdentifier(null, "a1", null);
        t2 = new TenantIdentifier(null, "a1", "t1");
        t3 = new TenantIdentifier(null, "a1", "t2");
        t4 = new TenantIdentifier(null, "a2", null);
    }

    private JsonObject emailPasswordSignUp(TenantIdentifier tenantIdentifier, String email, String password)
            throws HttpResponseException, IOException {
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("email", email);
        requestBody.addProperty("password", password);
        JsonObject signUpResponse = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                HttpRequestForTesting.getMultitenantUrl(tenantIdentifier, "/recipe/signup"),
                requestBody, 1000, 1000, null,
                SemVer.v3_0.get(), "emailpassword");
        assertEquals("OK", signUpResponse.getAsJsonPrimitive("status").getAsString());
        return signUpResponse.getAsJsonObject("user");
    }

    private void successfulCreateUserIdMapping(TenantIdentifier tenantIdentifier, String supertokensUserId,
                                               String externalUserId)
            throws HttpResponseException, IOException {
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("superTokensUserId", supertokensUserId);
        requestBody.addProperty("externalUserId", externalUserId);

        JsonObject response = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                HttpRequestForTesting.getMultitenantUrl(tenantIdentifier, "/recipe/userid/map"), requestBody,
                1000, 1000, null,
                SemVer.v3_0.get(), "useridmapping");
        assertEquals("OK", response.get("status").getAsString());
    }

    private void mappingAlreadyExistsWithCreateUserIdMapping(TenantIdentifier tenantIdentifier,
                                                             String supertokensUserId, String externalUserId)
            throws HttpResponseException, IOException {
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("superTokensUserId", supertokensUserId);
        requestBody.addProperty("externalUserId", externalUserId);

        JsonObject response = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                HttpRequestForTesting.getMultitenantUrl(tenantIdentifier, "/recipe/userid/map"), requestBody,
                1000, 1000, null,
                SemVer.v3_0.get(), "useridmapping");
        assertEquals("USER_ID_MAPPING_ALREADY_EXISTS_ERROR", response.get("status").getAsString());
    }

    private JsonObject getUserIdMapping(TenantIdentifier tenantIdentifier, String userId, String userIdType)
            throws HttpResponseException, IOException {
        return getUserIdMapping(tenantIdentifier, userId, userIdType, SemVer.v3_0);
    }

    private JsonObject getUserIdMapping(TenantIdentifier tenantIdentifier, String userId, String userIdType,
                                        SemVer version)
            throws HttpResponseException, IOException {
        HashMap<String, String> QUERY_PARAM = new HashMap<>();
        QUERY_PARAM.put("userId", userId);
        QUERY_PARAM.put("userIdType", userIdType);

        JsonObject response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                HttpRequestForTesting.getMultitenantUrl(tenantIdentifier, "/recipe/userid/map"), QUERY_PARAM, 1000,
                1000, null,
                version.get(), "useridmapping");
        assertEquals("OK", response.get("status").getAsString());
        return response;
    }

    private void getUnknownUserIdMapping(TenantIdentifier tenantIdentifier, String userId, String userIdType)
            throws HttpResponseException, IOException {
        HashMap<String, String> QUERY_PARAM = new HashMap<>();
        QUERY_PARAM.put("userId", userId);
        QUERY_PARAM.put("userIdType", userIdType);

        JsonObject response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                HttpRequestForTesting.getMultitenantUrl(tenantIdentifier, "/recipe/userid/map"), QUERY_PARAM, 1000,
                1000, null,
                SemVer.v3_0.get(), "useridmapping");
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
                SemVer.v3_0.get(), "useridmapping");
        assertEquals(2, response.entrySet().size());
        assertEquals("OK", response.get("status").getAsString());
        assertTrue(response.get("didMappingExist").getAsBoolean());
    }

    @Test
    public void testUserIdMappingWorksCorrectlyAcrossTenants() throws Exception {
        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        JsonObject user1 = emailPasswordSignUp(t1, "user@example.com", "password1");
        JsonObject user2 = emailPasswordSignUp(t2, "user@example.com", "password2");
        JsonObject user3 = emailPasswordSignUp(t3, "user@example.com", "password3");

        user1.addProperty("externalUserId", "euserid1");
        user2.addProperty("externalUserId", "euserid2");
        user3.addProperty("externalUserId", "euserid3");


        for (TenantIdentifier createTenant : new TenantIdentifier[]{t1}) {
            successfulCreateUserIdMapping(createTenant, user1.get("id").getAsString(), "euserid1");
            successfulCreateUserIdMapping(createTenant, user2.get("id").getAsString(), "euserid2");
            successfulCreateUserIdMapping(createTenant, user3.get("id").getAsString(), "euserid3");

            for (TenantIdentifier queryTenant : new TenantIdentifier[]{t1}) {
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
                        JsonObject mapping = getUserIdMapping(queryTenant, user.get("externalUserId").getAsString(),
                                "EXTERNAL");
                        assertEquals(user.get("id").getAsString(), mapping.get("superTokensUserId").getAsString());
                        assertEquals(user.get("externalUserId").getAsString(),
                                mapping.get("externalUserId").getAsString());
                    }
                    {
                        JsonObject mapping = getUserIdMapping(queryTenant, user.get("externalUserId").getAsString(),
                                "ANY");
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
        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        TenantIdentifier[] tenants = new TenantIdentifier[]{t1, t2, t3};
        int userCount = 0;
        int testcase = 0;

        for (TenantIdentifier tx : tenants) {
            for (TenantIdentifier ty : tenants) {
                JsonObject user1 = emailPasswordSignUp(tx, "user" + (userCount++) + "@example.com", "password");
                JsonObject user2 = emailPasswordSignUp(ty, "user" + (userCount++) + "@example.com", "password");

                String externalUserId = "euserid" + (testcase++);

                successfulCreateUserIdMapping(t1, user1.get("id").getAsString(), externalUserId);
                mappingAlreadyExistsWithCreateUserIdMapping(t1, user2.get("id").getAsString(), externalUserId);
            }
        }
    }

    @Test
    public void testRemoveMappingWorksAppWide() throws Exception {
        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        TenantIdentifier[] tenants = new TenantIdentifier[]{t1, t2, t3};
        int userCount = 0;

        for (TenantIdentifier userTenant : tenants) {
            JsonObject user = emailPasswordSignUp(userTenant, "user" + (userCount++) + "@example.com", "password");
            String externalUserId = "euserid" + userCount;
            user.addProperty("externalUserId", externalUserId);

            {
                successfulCreateUserIdMapping(t1, user.get("id").getAsString(), externalUserId);
                getUserIdMapping(t1, user.get("id").getAsString(), "SUPERTOKENS");
                successfulRemoveUserIdMapping(t1, user.get("id").getAsString(), "SUPERTOKENS");
                getUnknownUserIdMapping(t1, user.get("id").getAsString(), "SUPERTOKENS");
            }
            {
                successfulCreateUserIdMapping(t1, user.get("id").getAsString(), externalUserId);
                getUserIdMapping(t1, user.get("id").getAsString(), "ANY");
                successfulRemoveUserIdMapping(t1, user.get("id").getAsString(), "ANY");
                getUnknownUserIdMapping(t1, user.get("id").getAsString(), "ANY");
            }
            {
                successfulCreateUserIdMapping(t1, user.get("id").getAsString(), externalUserId);
                getUserIdMapping(t1, user.get("externalUserId").getAsString(), "EXTERNAL");
                successfulRemoveUserIdMapping(t1, user.get("externalUserId").getAsString(), "EXTERNAL");
                getUnknownUserIdMapping(t1, user.get("externalUserId").getAsString(), "EXTERNAL");
            }
            {
                successfulCreateUserIdMapping(t1, user.get("id").getAsString(), externalUserId);
                getUserIdMapping(t1, user.get("externalUserId").getAsString(), "ANY");
                successfulRemoveUserIdMapping(t1, user.get("externalUserId").getAsString(), "ANY");
                getUnknownUserIdMapping(t1, user.get("externalUserId").getAsString(), "ANY");
            }
        }
    }

    @Test
    public void testSameExternalIdAcrossUserPoolJustReturnsOneOfThem() throws Exception {
        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        if (StorageLayer.isInMemDb(process.getProcess())) {
            return;
        }

        JsonObject user1 = emailPasswordSignUp(t1, "user@example.com", "password1");
        JsonObject user2 = emailPasswordSignUp(t2, "user@example.com", "password2");

        ((UserIdMappingStorage) StorageLayer.getStorage(t1, process.getProcess())).createUserIdMapping(
                t1.toAppIdentifier(), user1.get("id").getAsString(), "euserid", null);

        ((UserIdMappingStorage) StorageLayer.getStorage(t2, process.getProcess())).createUserIdMapping(
                t2.toAppIdentifier(), user2.get("id").getAsString(), "euserid", null);

        {
            JsonObject mapping = getUserIdMapping(t1, "euserid", "EXTERNAL");
            assert mapping.get("superTokensUserId").getAsString().equals(user1.get("id").getAsString())
                    || mapping.get("superTokensUserId").getAsString().equals(user2.get("id").getAsString());
        }
        {
            JsonObject mapping = getUserIdMapping(t1, "euserid", "ANY");
            assert mapping.get("superTokensUserId").getAsString().equals(user1.get("id").getAsString())
                    || mapping.get("superTokensUserId").getAsString().equals(user2.get("id").getAsString());
        }

        {
            JsonObject mapping = getUserIdMapping(t2, "euserid", "EXTERNAL");
            assert mapping.get("superTokensUserId").getAsString().equals(user1.get("id").getAsString())
                    || mapping.get("superTokensUserId").getAsString().equals(user2.get("id").getAsString());
        }
        {
            JsonObject mapping = getUserIdMapping(t2, "euserid", "ANY");
            assert mapping.get("superTokensUserId").getAsString().equals(user1.get("id").getAsString())
                    || mapping.get("superTokensUserId").getAsString().equals(user2.get("id").getAsString());
        }
    }

    @Test
    public void testSameExternalIdAcrossUserPoolJustReturnsOneOfThem_v5() throws Exception {
        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        if (StorageLayer.isInMemDb(process.getProcess())) {
            return;
        }

        JsonObject user1 = emailPasswordSignUp(t1, "user@example.com", "password1");
        JsonObject user2 = emailPasswordSignUp(t2, "user@example.com", "password2");

        ((UserIdMappingStorage) StorageLayer.getStorage(t1, process.getProcess())).createUserIdMapping(
                t1.toAppIdentifier(), user1.get("id").getAsString(), "euserid", null);

        ((UserIdMappingStorage) StorageLayer.getStorage(t2, process.getProcess())).createUserIdMapping(
                t2.toAppIdentifier(), user2.get("id").getAsString(), "euserid", null);

        {
            JsonObject mapping = getUserIdMapping(t1, "euserid", "EXTERNAL");
            assert mapping.get("superTokensUserId").getAsString().equals(user1.get("id").getAsString())
                    || mapping.get("superTokensUserId").getAsString().equals(user2.get("id").getAsString());
        }
        {
            JsonObject mapping = getUserIdMapping(t1, "euserid", "ANY");
            assert mapping.get("superTokensUserId").getAsString().equals(user1.get("id").getAsString())
                    || mapping.get("superTokensUserId").getAsString().equals(user2.get("id").getAsString());
        }

        {
            try {
                JsonObject mapping = getUserIdMapping(t2, "euserid", "EXTERNAL", SemVer.v5_0);
                fail();
            } catch (HttpResponseException e) {
                assertEquals(403, e.statusCode);
            }
        }
        {
            try {
                JsonObject mapping = getUserIdMapping(t2, "euserid", "ANY", SemVer.v5_0);
                fail();
            } catch (HttpResponseException e) {
                assertEquals(403, e.statusCode);
            }
        }
    }

    @Test
    public void testUserIdFromDifferentAppIsAllowedForUserIdMapping() throws Exception {
        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        JsonObject user1 = emailPasswordSignUp(t1, "user@example.com", "password1");
        JsonObject user2 = emailPasswordSignUp(t4, "user1@example.com", "password2");
        JsonObject user3 = emailPasswordSignUp(t4, "user2@example.com", "password3");

        successfulCreateUserIdMapping(t4, user2.get("id").getAsString(), user1.get("id").getAsString());
        successfulCreateUserIdMapping(t1, user1.get("id").getAsString(), user2.get("id").getAsString());

        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("superTokensUserId", user3.get("id").getAsString());
        requestBody.addProperty("externalUserId", user2.get("id").getAsString());

        try {
            JsonObject response = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                    HttpRequestForTesting.getMultitenantUrl(t4, "/recipe/userid/map"), requestBody,
                    1000, 1000, null,
                    SemVer.v3_0.get(), "useridmapping");
            fail();
        } catch (HttpResponseException e) {
            assertEquals(400, e.statusCode);
            assertEquals(
                    "Http error. Status Code: 400. Message: Cannot create a userId mapping where the externalId is " +
                            "also a SuperTokens userID",
                    e.getMessage());
        }
    }
}
