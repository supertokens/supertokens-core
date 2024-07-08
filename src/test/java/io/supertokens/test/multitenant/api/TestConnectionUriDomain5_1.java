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

package io.supertokens.test.multitenant.api;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.supertokens.Main;
import io.supertokens.ProcessState;
import io.supertokens.featureflag.EE_FEATURES;
import io.supertokens.featureflag.FeatureFlagTestContent;
import io.supertokens.featureflag.exceptions.FeatureNotEnabledException;
import io.supertokens.multitenancy.exception.BadPermissionException;
import io.supertokens.multitenancy.exception.CannotModifyBaseConfigException;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.pluginInterface.exceptions.InvalidConfigException;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifier;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.test.TestingProcessManager;
import io.supertokens.test.Utils;
import io.supertokens.test.httpRequest.HttpRequestForTesting;
import io.supertokens.test.httpRequest.HttpResponseException;
import io.supertokens.thirdparty.InvalidProviderConfigException;
import io.supertokens.utils.SemVer;
import io.supertokens.webserver.Webserver;
import io.supertokens.webserver.WebserverAPI;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.Set;

import static org.junit.Assert.*;

public class TestConnectionUriDomain5_1 {
    TestingProcessManager.TestingProcess process;

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
    }

    @Test
    public void testCreateConnectionUriDomainWorks() throws Exception {
        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        if (StorageLayer.isInMemDb(process.getProcess())) {
            return;
        }

        JsonObject coreConfig = new JsonObject();

        StorageLayer.getStorage(new TenantIdentifier(null, null, null), process.getProcess())
                .modifyConfigToAddANewUserPoolForTesting(coreConfig, 1);

        createConnectionUriDomain(
                process.getProcess(),
                new TenantIdentifier(null, null, null),
                "127.0.0.1:3567", true, new String[]{"emailpassword", "thirdparty", "otp-email"}, false, null,
                coreConfig);

        JsonObject result = listConnectionUriDomains(new TenantIdentifier(null, null, null), process.getProcess());
        assertTrue(result.has("connectionUriDomains"));

        boolean found = false;
        for (JsonElement cud : result.get("connectionUriDomains").getAsJsonArray()) {
            JsonObject cudObj = cud.getAsJsonObject();
            if (cudObj.get("connectionUriDomain").getAsString().equals("127.0.0.1")) {
                found = true;

                for (JsonElement app : cudObj.get("apps").getAsJsonArray()) {
                    JsonObject appObj = app.getAsJsonObject();

                    for (JsonElement tenant : appObj.get("tenants").getAsJsonArray()) {
                        JsonObject tenantObj = tenant.getAsJsonObject();
                        assertEquals(4, tenantObj.entrySet().size());
                        assertEquals("public", tenantObj.get("tenantId").getAsString());
                        assertEquals(1, tenantObj.get("thirdParty").getAsJsonObject().entrySet().size());
                        assertEquals(3, tenantObj.get("firstFactors").getAsJsonArray().size());
                        assertEquals(coreConfig, tenantObj.get("coreConfig").getAsJsonObject());
                    }
                }
            }
        }
        assertTrue(found);
    }

    @Test
    public void testUpdateConnectionUriDomainWorks() throws Exception {
        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        if (StorageLayer.isInMemDb(process.getProcess())) {
            return;
        }

        JsonObject coreConfig = new JsonObject();

        StorageLayer.getStorage(new TenantIdentifier(null, null, null), process.getProcess())
                .modifyConfigToAddANewUserPoolForTesting(coreConfig, 1);

        // Create
        createConnectionUriDomain(
                process.getProcess(),
                new TenantIdentifier(null, null, null),
                "127.0.0.1:3567", true, new String[]{"emailpassword", "thirdparty", "otp-email"}, false, null,
                coreConfig);

        JsonObject newConfig = new JsonObject();
        newConfig.addProperty("email_verification_token_lifetime", 2000);
        coreConfig.addProperty("email_verification_token_lifetime", 2000);

        // Update
        createConnectionUriDomain(
                process.getProcess(),
                new TenantIdentifier(null, null, null),
                "127.0.0.1:3567", true, new String[]{"emailpassword", "thirdparty", "otp-email"}, false, null,
                newConfig);

        JsonObject result = listConnectionUriDomains(new TenantIdentifier(null, null, null), process.getProcess());
        assertTrue(result.has("connectionUriDomains"));

        boolean found = false;
        for (JsonElement cud : result.get("connectionUriDomains").getAsJsonArray()) {
            JsonObject cudObj = cud.getAsJsonObject();
            if (cudObj.get("connectionUriDomain").getAsString().equals("127.0.0.1")) {
                found = true;

                for (JsonElement app : cudObj.get("apps").getAsJsonArray()) {
                    JsonObject appObj = app.getAsJsonObject();

                    for (JsonElement tenant : appObj.get("tenants").getAsJsonArray()) {
                        JsonObject tenantObj = tenant.getAsJsonObject();
                        assertEquals(4, tenantObj.entrySet().size());
                        assertEquals("public", tenantObj.get("tenantId").getAsString());
                        assertEquals(1, tenantObj.get("thirdParty").getAsJsonObject().entrySet().size());
                        assertEquals(3, tenantObj.get("firstFactors").getAsJsonArray().size());
                        assertEquals(coreConfig, tenantObj.get("coreConfig").getAsJsonObject());
                    }
                }
            }
        }
        assertTrue(found);
    }

    @Test
    public void testUpdateConnectionUriDomainFromSameConnectionUriDomainWorks() throws Exception {
        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        if (StorageLayer.isInMemDb(process.getProcess())) {
            return;
        }

        JsonObject coreConfig = new JsonObject();

        StorageLayer.getStorage(new TenantIdentifier(null, null, null), process.getProcess())
                .modifyConfigToAddANewUserPoolForTesting(coreConfig, 1);

        // Create
        createConnectionUriDomain(
                process.getProcess(),
                new TenantIdentifier(null, null, null),
                "127.0.0.1:3567", true, new String[]{"emailpassword", "thirdparty", "otp-email"}, false, null,
                coreConfig);

        JsonObject newConfig = new JsonObject();
        newConfig.addProperty("email_verification_token_lifetime", 2000);
        coreConfig.addProperty("email_verification_token_lifetime", 2000);

        // Update
        createConnectionUriDomain(
                process.getProcess(),
                new TenantIdentifier("127.0.0.1", null, null),
                "127.0.0.1:3567", true, new String[]{"emailpassword", "thirdparty", "otp-email"}, false, null,
                newConfig);

        JsonObject result = listConnectionUriDomains(new TenantIdentifier(null, null, null), process.getProcess());
        assertTrue(result.has("connectionUriDomains"));

        boolean found = false;
        for (JsonElement cud : result.get("connectionUriDomains").getAsJsonArray()) {
            JsonObject cudObj = cud.getAsJsonObject();
            if (cudObj.get("connectionUriDomain").getAsString().equals("127.0.0.1")) {
                found = true;

                for (JsonElement app : cudObj.get("apps").getAsJsonArray()) {
                    JsonObject appObj = app.getAsJsonObject();

                    for (JsonElement tenant : appObj.get("tenants").getAsJsonArray()) {
                        JsonObject tenantObj = tenant.getAsJsonObject();
                        assertEquals(4, tenantObj.entrySet().size());
                        assertEquals("public", tenantObj.get("tenantId").getAsString());
                        assertEquals(1, tenantObj.get("thirdParty").getAsJsonObject().entrySet().size());
                        assertEquals(3, tenantObj.get("firstFactors").getAsJsonArray().size());
                        assertEquals(coreConfig, tenantObj.get("coreConfig").getAsJsonObject());
                    }
                }
            }
        }
        assertTrue(found);
    }

    @Test
    public void testUpdateWithNullValueDeletesTheSetting() throws Exception {
        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        if (StorageLayer.isInMemDb(process.getProcess())) {
            return;
        }

        JsonObject coreConfig = new JsonObject();

        StorageLayer.getStorage(new TenantIdentifier(null, null, null), process.getProcess())
                .modifyConfigToAddANewUserPoolForTesting(coreConfig, 1);
        coreConfig.addProperty("email_verification_token_lifetime", 2000);

        // Create
        createConnectionUriDomain(
                process.getProcess(),
                new TenantIdentifier(null, null, null),
                "127.0.0.1:3567", true, new String[]{"emailpassword", "thirdparty", "otp-email"}, false, null,
                coreConfig);

        JsonObject newConfig = new JsonObject();
        newConfig.add("email_verification_token_lifetime", null);
        coreConfig.remove("email_verification_token_lifetime"); // for verification

        // Update
        createConnectionUriDomain(
                process.getProcess(),
                new TenantIdentifier(null, null, null),
                "127.0.0.1:3567", true, new String[]{"emailpassword", "thirdparty", "otp-email"}, false, null,
                newConfig);

        JsonObject result = listConnectionUriDomains(new TenantIdentifier(null, null, null), process.getProcess());
        assertTrue(result.has("connectionUriDomains"));

        boolean found = false;
        for (JsonElement cud : result.get("connectionUriDomains").getAsJsonArray()) {
            JsonObject cudObj = cud.getAsJsonObject();
            if (cudObj.get("connectionUriDomain").getAsString().equals("127.0.0.1")) {
                found = true;

                for (JsonElement app : cudObj.get("apps").getAsJsonArray()) {
                    JsonObject appObj = app.getAsJsonObject();

                    for (JsonElement tenant : appObj.get("tenants").getAsJsonArray()) {
                        JsonObject tenantObj = tenant.getAsJsonObject();
                        assertEquals(coreConfig, tenantObj.get("coreConfig").getAsJsonObject());
                    }
                }
            }
        }
        assertTrue(found);
    }

    @Test
    public void testDeleteConnectionUriDomainWorks() throws Exception {
        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        if (StorageLayer.isInMemDb(process.getProcess())) {
            return;
        }

        JsonObject coreConfig = new JsonObject();

        StorageLayer.getStorage(new TenantIdentifier(null, null, null), process.getProcess())
                .modifyConfigToAddANewUserPoolForTesting(coreConfig, 1);

        createConnectionUriDomain(
                process.getProcess(),
                new TenantIdentifier(null, null, null),
                "127.0.0.1:3567", true, new String[]{"emailpassword", "thirdparty", "otp-email"}, false, null,
                coreConfig);

        JsonObject result = listConnectionUriDomains(new TenantIdentifier(null, null, null), process.getProcess());
        assertTrue(result.has("connectionUriDomains"));
        assertEquals(2, result.get("connectionUriDomains").getAsJsonArray().size());

        JsonObject response = deleteConnectionUriDomain(new TenantIdentifier(null, null, null),
                "127.0.0.1:3567", process.getProcess());
        assertTrue(response.get("didExist").getAsBoolean());

        result = listConnectionUriDomains(new TenantIdentifier(null, null, null), process.getProcess());
        assertTrue(result.has("connectionUriDomains"));
        assertEquals(1, result.get("connectionUriDomains").getAsJsonArray().size());

        response = deleteConnectionUriDomain(new TenantIdentifier(null, null, null),
                "127.0.0.1:3567", process.getProcess());
        assertFalse(response.get("didExist").getAsBoolean());
    }

    @Test
    public void testDifferentValuesForCUDThatShouldWork() throws Exception {
        String[] valueForCreate = new String[]{"localhost:3567", "LOCALHOST:3567", "loCalHost:3567", "127.0.0.1:3567"};
        String[] valueForQuery = new String[]{"localhost:3567", "LOCALHOST:3567", "LOCALhoST:3567", "127.0.0.1:3567"};

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        if (StorageLayer.isInMemDb(process.getProcess())) {
            return;
        }

        JsonObject config = new JsonObject();
        StorageLayer.getBaseStorage(process.getProcess()).modifyConfigToAddANewUserPoolForTesting(config, 1);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));

        for (int i = 0; i < valueForCreate.length; i++) {
            String[] args = {"../"};
            this.process = TestingProcessManager.start(args);
            FeatureFlagTestContent.getInstance(process.getProcess())
                    .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{EE_FEATURES
                            .MULTI_TENANCY});
            process.startProcess();
            assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

            Webserver.getInstance(process.getProcess()).addAPI(new WebserverAPI(process.getProcess(), "") {

                private static final long serialVersionUID = 1L;

                @Override
                public boolean checkAPIKey(HttpServletRequest req) {
                    return false;
                }

                @Override
                public String getPath() {
                    return "/get-cud";
                }

                @Override
                protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException,
                        ServletException {
                    try {
                        getTenantStorage(req);
                        super.sendTextResponse(200, this.getTenantIdentifier(req).getConnectionUriDomain(), resp);
                    } catch (TenantOrAppNotFoundException e) {
                        throw new ServletException(e);
                    }
                }
            });

            createConnectionUriDomain(
                    process.getProcess(),
                    new TenantIdentifier(null, null, null),
                    valueForCreate[i], true, new String[]{"emailpassword", "thirdparty", "otp-email"}, false, null,
                    config);

            String response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                    "http://" + valueForQuery[i] + "/get-cud", null, 1000, 1000,
                    null, WebserverAPI.getLatestCDIVersion().get(), null);

            assertEquals(valueForCreate[i].toLowerCase().split(":")[0], response);

            process.kill();
            assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
        }
    }

    @Test
    public void testCUDsThatAreSame() throws Exception {
        String[] valueForCreate = new String[]{"localhost:3567", "LOCALHOST:3567", "loCalHost:3567", "localhost",
                "localhost:12345"};

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        if (StorageLayer.isInMemDb(process.getProcess())) {
            return;
        }

        JsonObject config = new JsonObject();
        StorageLayer.getBaseStorage(process.getProcess()).modifyConfigToAddANewUserPoolForTesting(config, 1);

        for (int i = 0; i < valueForCreate.length; i++) {
            JsonObject response = createConnectionUriDomain(
                    process.getProcess(),
                    new TenantIdentifier(null, null, null),
                    valueForCreate[i], true, new String[]{"emailpassword", "thirdparty", "otp-email"}, false, null,
                    config);

            if (i == 0) {
                assertTrue(response.get("createdNew").getAsBoolean());
            } else {
                assertFalse(response.get("createdNew").getAsBoolean());
            }
        }
    }

    @Test
    public void testDifferentValuesForCUDThatShouldNotWork() throws Exception {
        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        String[] valueForCreate = new String[]{"http://localhost_com", "localhost:", "domain.com:abcd"};
        for (int i = 0; i < valueForCreate.length; i++) {
            try {
                JsonObject config = new JsonObject();
                StorageLayer.getBaseStorage(process.getProcess()).modifyConfigToAddANewUserPoolForTesting(config, 1);

                createConnectionUriDomain(
                        process.getProcess(),
                        new TenantIdentifier(null, null, null),
                        valueForCreate[i], true, new String[]{"emailpassword", "thirdparty", "otp-email"}, false, null,
                        config);
                fail(valueForCreate[i]);
            } catch (HttpResponseException e) {
                assertTrue(e.getMessage().contains("connectionUriDomain is invalid"));
            }
        }
    }

    @Test
    public void testDefaultRecipesEnabledWhileCreatingCUD() throws Exception {
        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        if (StorageLayer.isInMemDb(process.getProcess())) {
            return;
        }

        JsonObject config = new JsonObject();
        StorageLayer.getBaseStorage(process.getProcess()).modifyConfigToAddANewUserPoolForTesting(config, 1);

        JsonObject response = createConnectionUriDomain(
                process.getProcess(),
                new TenantIdentifier(null, null, null),
                "localhost:3567", false, null, false, null,
                config);

        assertTrue(response.get("createdNew").getAsBoolean());

        JsonObject tenant = getTenant(new TenantIdentifier("localhost", null, null),
                process.getProcess());
        assertFalse(tenant.has("firstFactors")); // use static config
    }

    @Test
    public void testFirstFactorsArray() throws Exception {
        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        if (StorageLayer.isInMemDb(process.getProcess())) {
            return;
        }

        JsonObject config = new JsonObject();
        StorageLayer.getBaseStorage(process.getProcess()).modifyConfigToAddANewUserPoolForTesting(config, 1);

        JsonObject response = createConnectionUriDomain(
                process.getProcess(),
                new TenantIdentifier(null, null, null),
                "127.0.0.1", false, null, false, null,
                config);

        assertTrue(response.get("createdNew").getAsBoolean());

        JsonObject tenant = getTenant(new TenantIdentifier("127.0.0.1", null, null),
                process.getProcess());
        assertEquals(4, tenant.entrySet().size());
        assertFalse(tenant.has("firstFactors"));

        // builtin firstFactor
        String[] firstFactors = new String[]{"otp-phone"};
        response = createConnectionUriDomain(
                process.getProcess(),
                new TenantIdentifier(null, null, null),
                "127.0.0.1", true, new String[]{"otp-phone"}, false, null,
                config);
        assertFalse(response.get("createdNew").getAsBoolean());

        tenant = getTenant(new TenantIdentifier("127.0.0.1", null, null),
                process.getProcess());
        assertEquals(5, tenant.entrySet().size());
        assertTrue(tenant.get("firstFactors").isJsonArray());
        assertEquals(1, tenant.get("firstFactors").getAsJsonArray().size());
        assertEquals(firstFactors, new Gson().fromJson(tenant.get("firstFactors").getAsJsonArray(), String[].class));

        response = createConnectionUriDomain(
                process.getProcess(),
                new TenantIdentifier(null, null, null),
                "127.0.0.1", false, null, false, null,
                config);
        assertFalse(response.get("createdNew").getAsBoolean());

        tenant = getTenant(new TenantIdentifier("127.0.0.1", null, null),
                process.getProcess());
        assertEquals(5, tenant.entrySet().size());
        assertTrue(tenant.get("firstFactors").isJsonArray());
        assertEquals(1, tenant.get("firstFactors").getAsJsonArray().size());
        assertEquals(firstFactors, new Gson().fromJson(tenant.get("firstFactors").getAsJsonArray(), String[].class));

        // custom factors
        firstFactors = new String[]{"biometric"};
        response = createConnectionUriDomain(
                process.getProcess(),
                new TenantIdentifier(null, null, null),
                "127.0.0.1", true, firstFactors, false, null,
                config);
        assertFalse(response.get("createdNew").getAsBoolean());

        tenant = getTenant(new TenantIdentifier("127.0.0.1", null, null),
                process.getProcess());
        assertEquals(5, tenant.entrySet().size());
        assertTrue(tenant.get("firstFactors").isJsonArray());
        assertEquals(1, tenant.get("firstFactors").getAsJsonArray().size());
        assertEquals(firstFactors, new Gson().fromJson(tenant.get("firstFactors").getAsJsonArray(), String[].class));

        // test both
        firstFactors = new String[]{"otp-phone", "emailpassword", "biometric", "custom"};
        response = createConnectionUriDomain(
                process.getProcess(),
                new TenantIdentifier(null, null, null),
                "127.0.0.1", true, firstFactors, false, null,
                config);
        assertFalse(response.get("createdNew").getAsBoolean());

        tenant = getTenant(new TenantIdentifier("127.0.0.1", null, null),
                process.getProcess());
        assertEquals(5, tenant.entrySet().size());
        assertTrue(tenant.get("firstFactors").isJsonArray());
        assertEquals(4, tenant.get("firstFactors").getAsJsonArray().size());
        assertEquals(Set.of(firstFactors),
                Set.of(new Gson().fromJson(tenant.get("firstFactors").getAsJsonArray(), String[].class)));

        response = createConnectionUriDomain(
                process.getProcess(),
                new TenantIdentifier(null, null, null),
                "127.0.0.1", true, null, false, null,
                config);
        assertFalse(response.get("createdNew").getAsBoolean());

        tenant = getTenant(new TenantIdentifier("127.0.0.1", null, null),
                process.getProcess());
        assertEquals(4, tenant.entrySet().size());
        assertFalse(tenant.has("firstFactors"));
    }

    @Test
    public void testRequiredSecondaryFactorsArray() throws Exception {
        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        if (StorageLayer.isInMemDb(process.getProcess())) {
            return;
        }

        JsonObject config = new JsonObject();
        StorageLayer.getBaseStorage(process.getProcess()).modifyConfigToAddANewUserPoolForTesting(config, 1);

        JsonObject response = createConnectionUriDomain(
                process.getProcess(),
                new TenantIdentifier(null, null, null),
                "127.0.0.1", false, null, false, null, config);

        assertTrue(response.get("createdNew").getAsBoolean());

        JsonObject tenant = getTenant(new TenantIdentifier("127.0.0.1", null, null),
                process.getProcess());
        assertEquals(4, tenant.entrySet().size());
        assertFalse(tenant.has("requiredSecondaryFactors"));

        // builtin firstFactor
        String[] requiredSecondaryFactors = new String[]{"otp-phone"};
        response = createConnectionUriDomain(
                process.getProcess(),
                new TenantIdentifier(null, null, null),
                "127.0.0.1", false, null, true, new String[]{"otp-phone"},
                config);
        assertFalse(response.get("createdNew").getAsBoolean());

        tenant = getTenant(new TenantIdentifier("127.0.0.1", null, null),
                process.getProcess());
        assertEquals(5, tenant.entrySet().size());
        assertTrue(tenant.get("requiredSecondaryFactors").isJsonArray());
        assertEquals(1, tenant.get("requiredSecondaryFactors").getAsJsonArray().size());
        assertEquals(requiredSecondaryFactors,
                new Gson().fromJson(tenant.get("requiredSecondaryFactors").getAsJsonArray(), String[].class));

        response = createConnectionUriDomain(
                process.getProcess(),
                new TenantIdentifier(null, null, null),
                "127.0.0.1", false, null, false, null,
                config);
        assertFalse(response.get("createdNew").getAsBoolean());

        tenant = getTenant(new TenantIdentifier("127.0.0.1", null, null),
                process.getProcess());
        assertEquals(5, tenant.entrySet().size());
        assertTrue(tenant.get("requiredSecondaryFactors").isJsonArray());
        assertEquals(1, tenant.get("requiredSecondaryFactors").getAsJsonArray().size());
        assertEquals(requiredSecondaryFactors,
                new Gson().fromJson(tenant.get("requiredSecondaryFactors").getAsJsonArray(), String[].class));

        // custom factors
        requiredSecondaryFactors = new String[]{"biometric"};
        response = createConnectionUriDomain(
                process.getProcess(),
                new TenantIdentifier(null, null, null),
                "127.0.0.1", false, null, true, requiredSecondaryFactors,
                config);
        assertFalse(response.get("createdNew").getAsBoolean());

        tenant = getTenant(new TenantIdentifier("127.0.0.1", null, null),
                process.getProcess());
        assertEquals(5, tenant.entrySet().size());
        assertTrue(tenant.get("requiredSecondaryFactors").isJsonArray());
        assertEquals(1, tenant.get("requiredSecondaryFactors").getAsJsonArray().size());
        assertEquals(requiredSecondaryFactors,
                new Gson().fromJson(tenant.get("requiredSecondaryFactors").getAsJsonArray(), String[].class));

        // test both
        requiredSecondaryFactors = new String[]{"otp-phone", "emailpassword", "biometric", "custom"};
        response = createConnectionUriDomain(
                process.getProcess(),
                new TenantIdentifier(null, null, null),
                "127.0.0.1", false, null, true, requiredSecondaryFactors,
                config);
        assertFalse(response.get("createdNew").getAsBoolean());

        tenant = getTenant(new TenantIdentifier("127.0.0.1", null, null),
                process.getProcess());
        assertEquals(5, tenant.entrySet().size());
        assertTrue(tenant.get("requiredSecondaryFactors").isJsonArray());
        assertEquals(4, tenant.get("requiredSecondaryFactors").getAsJsonArray().size());
        assertEquals(Set.of(requiredSecondaryFactors),
                Set.of(new Gson().fromJson(tenant.get("requiredSecondaryFactors").getAsJsonArray(), String[].class)));

        response = createConnectionUriDomain(
                process.getProcess(),
                new TenantIdentifier(null, null, null),
                "127.0.0.1", false, null, true, null,
                config);
        assertFalse(response.get("createdNew").getAsBoolean());

        tenant = getTenant(new TenantIdentifier("127.0.0.1", null, null),
                process.getProcess());
        assertEquals(4, tenant.entrySet().size());
        assertFalse(tenant.has("requiredSecondaryFactors"));
    }

    @Test
    public void testDuplicateValuesInFirstFactorsAndRequiredSecondaryFactors() throws Exception {
        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        if (StorageLayer.isInMemDb(process.getProcess())) {
            return;
        }

        JsonObject config = new JsonObject();
        StorageLayer.getBaseStorage(process.getProcess()).modifyConfigToAddANewUserPoolForTesting(config, 1);

        String[] factors = new String[]{"duplicate", "emailpassword", "duplicate", "custom"};
        try {
            createConnectionUriDomain(
                    process.getProcess(),
                    new TenantIdentifier(null, null, null),
                    "127.0.0.1",
                    true, factors, false, null,
                    config);
            fail();
        } catch (HttpResponseException e) {
            assertEquals(400, e.statusCode);
            assertEquals(
                    "Http error. Status Code: 400. Message: firstFactors input should not contain duplicate values",
                    e.getMessage());
        }

        try {
            createConnectionUriDomain(
                    process.getProcess(),
                    new TenantIdentifier(null, null, null),
                    "127.0.0.1",
                    false, null, true, factors,
                    config);
            fail();
        } catch (HttpResponseException e) {
            assertEquals(400, e.statusCode);
            assertEquals(
                    "Http error. Status Code: 400. Message: requiredSecondaryFactors input should not contain " +
                            "duplicate values",
                    e.getMessage());
        }
    }

    private static JsonObject createConnectionUriDomain(Main main, TenantIdentifier sourceTenant,
                                                        String connectionUriDomain,
                                                        boolean setFirstFactors, String[] firstFactors,
                                                        boolean setRequiredSecondaryFactors,
                                                        String[] requiredSecondaryFactors,
                                                        JsonObject coreConfig)
            throws HttpResponseException, IOException {
        JsonObject requestBody = new JsonObject();
        if (connectionUriDomain != null) {
            requestBody.addProperty("connectionUriDomain", connectionUriDomain);
        }
        if (setFirstFactors || firstFactors != null) {
            requestBody.add("firstFactors", new Gson().toJsonTree(firstFactors));
        }
        if (setRequiredSecondaryFactors || requiredSecondaryFactors != null) {
            requestBody.add("requiredSecondaryFactors", new Gson().toJsonTree(requiredSecondaryFactors));
        }

        requestBody.add("coreConfig", coreConfig);

        JsonObject response = HttpRequestForTesting.sendJsonPUTRequest(main, "",
                HttpRequestForTesting.getMultitenantUrl(sourceTenant, "/recipe/multitenancy/connectionuridomain/v2"),
                requestBody, 1000, 2500, null,
                SemVer.v5_1.get(), "multitenancy");

        assertEquals("OK", response.getAsJsonPrimitive("status").getAsString());

        return response;
    }

    private static JsonObject listConnectionUriDomains(TenantIdentifier sourceTenant, Main main)
            throws HttpResponseException, IOException {
        JsonObject response = HttpRequestForTesting.sendGETRequest(main, "",
                HttpRequestForTesting.getMultitenantUrl(sourceTenant, "/recipe/multitenancy/connectionuridomain/list" +
                        "/v2"),
                null, 1000, 1000, null,
                SemVer.v5_1.get(), "multitenancy");

        assertEquals("OK", response.getAsJsonPrimitive("status").getAsString());
        return response;
    }

    private static JsonObject deleteConnectionUriDomain(TenantIdentifier sourceTenant, String connectionUriDomain,
                                                        Main main)
            throws HttpResponseException, IOException {
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("connectionUriDomain", connectionUriDomain);

        JsonObject response = HttpRequestForTesting.sendJsonPOSTRequest(main, "",
                HttpRequestForTesting.getMultitenantUrl(sourceTenant,
                        "/recipe/multitenancy/connectionuridomain/remove"),
                requestBody, 1000, 2500, null,
                SemVer.v5_1.get(), "multitenancy");

        assertEquals("OK", response.getAsJsonPrimitive("status").getAsString());
        return response;
    }

    private static JsonObject getTenant(TenantIdentifier tenantIdentifier, Main main)
            throws HttpResponseException, IOException {
        JsonObject response = HttpRequestForTesting.sendGETRequest(main, "",
                HttpRequestForTesting.getMultitenantUrl(tenantIdentifier, "/recipe/multitenancy/tenant/v2"),
                null, 1000, 1000, null,
                SemVer.v5_1.get(), "multitenancy");

        assertEquals("OK", response.getAsJsonPrimitive("status").getAsString());
        return response;
    }
}
