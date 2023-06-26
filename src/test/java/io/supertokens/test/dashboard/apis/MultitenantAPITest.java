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

package io.supertokens.test.dashboard.apis;

import com.google.gson.JsonObject;
import io.supertokens.ProcessState;
import io.supertokens.dashboard.Dashboard;
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
import io.supertokens.thirdparty.InvalidProviderConfigException;
import io.supertokens.utils.SemVer;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

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

    @Test
    public void testSessionBehavior() throws Exception {
        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        // create dashboard user, create session and verify session
        String email = "test@example.com";
        String password = "testPass123";

        AppIdentifierWithStorage appIdentifierWithStorage = t1.toAppIdentifier().withStorage(
                StorageLayer.getStorage(t1, process.getProcess()));
        Dashboard.signUpDashboardUser(appIdentifierWithStorage, process.getProcess(), email, password);

        // create a session
        String sessionId;

        {
            JsonObject requestBody = new JsonObject();
            requestBody.addProperty("email", email);
            requestBody.addProperty("password", password);
            JsonObject response = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                    HttpRequestForTesting.getMultitenantUrl(t1, "/recipe/dashboard/signin"), requestBody,
                    1000, 1000, null,
                    SemVer.v2_18.get(), "dashboard");
            assertEquals(2, response.entrySet().size());
            assertEquals("OK", response.get("status").getAsString());
            assertNotNull(response.get("sessionId").getAsString());
            sessionId = response.get("sessionId").getAsString();
        }

        {
            JsonObject requestBody = new JsonObject();
            requestBody.addProperty("sessionId", sessionId);
            JsonObject verifyResponse1 = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                    HttpRequestForTesting.getMultitenantUrl(t1, "/recipe/dashboard/session/verify"), requestBody,
                    1000, 1000, null,
                    SemVer.v2_18.get(), "dashboard");
            assertEquals(1, verifyResponse1.entrySet().size());
            assertEquals("OK", verifyResponse1.get("status").getAsString());
        }

        {
            // revoke session
            HashMap<String, String> requestParams = new HashMap<>();
            requestParams.put("sessionId", sessionId);

            JsonObject response = HttpRequestForTesting.sendJsonDELETERequestWithQueryParams(process.getProcess(), "",
                    HttpRequestForTesting.getMultitenantUrl(t1, "/recipe/dashboard/session"), requestParams,
                    1000, 1000, null,
                    SemVer.v2_18.get(), "dashboard");
            assertEquals(1, response.entrySet().size());
            assertEquals("OK", response.get("status").getAsString());
        }

        {
            JsonObject requestBody = new JsonObject();
            requestBody.addProperty("sessionId", sessionId);
            JsonObject verifyResponse2 = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                    HttpRequestForTesting.getMultitenantUrl(t1, "/recipe/dashboard/session/verify"), requestBody,
                    1000, 1000, null,
                    SemVer.v2_18.get(), "dashboard");

            assertEquals(1, verifyResponse2.entrySet().size());
            assertEquals("INVALID_SESSION_ERROR", verifyResponse2.get("status").getAsString());
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

}
