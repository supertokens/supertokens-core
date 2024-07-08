/*
 *    Copyright (c) 2024, VRAI Labs and/or its affiliates. All rights reserved.
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
import com.google.gson.JsonArray;
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
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.*;

public class TestTenantCreationAndRecipeEnabledChecksAcrossVersions {
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
    public void testDefaultBooleanBehavioursWithTenantCreatedIn3_0() throws Exception {
        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        createApp_3_0(process.getProcess(), TenantIdentifier.BASE_TENANT, "a1", null, null, null, new JsonObject());
        assertTrue(checkEmailPasswordSignIn(new TenantIdentifier(null, "a1", null), SemVer.v3_0));
        assertTrue(checkPasswordlessSignIn(new TenantIdentifier(null, "a1", null), SemVer.v3_0));
        assertTrue(checkThirdPartySignInUp(new TenantIdentifier(null, "a1", null), SemVer.v3_0));

        assertTrue(checkEmailPasswordSignIn(new TenantIdentifier(null, "a1", null), SemVer.v5_0));
        assertTrue(checkPasswordlessSignIn(new TenantIdentifier(null, "a1", null), SemVer.v5_0));
        assertTrue(checkThirdPartySignInUp(new TenantIdentifier(null, "a1", null), SemVer.v5_0));

        assertTrue(checkEmailPasswordSignIn(new TenantIdentifier(null, "a1", null), SemVer.v5_1));
        assertTrue(checkPasswordlessSignIn(new TenantIdentifier(null, "a1", null), SemVer.v5_1));
        assertTrue(checkThirdPartySignInUp(new TenantIdentifier(null, "a1", null), SemVer.v5_1));

        createTenant_3_0(process.getProcess(), new TenantIdentifier(null, "a1", null), "t1", null, null, null,
                new JsonObject());
        assertFalse(checkEmailPasswordSignIn(new TenantIdentifier(null, "a1", "t1"), SemVer.v3_0));
        assertFalse(checkPasswordlessSignIn(new TenantIdentifier(null, "a1", "t1"), SemVer.v3_0));
        assertFalse(checkThirdPartySignInUp(new TenantIdentifier(null, "a1", "t1"), SemVer.v3_0));

        assertFalse(checkEmailPasswordSignIn(new TenantIdentifier(null, "a1", "t1"), SemVer.v5_0));
        assertFalse(checkPasswordlessSignIn(new TenantIdentifier(null, "a1", "t1"), SemVer.v5_0));
        assertFalse(checkThirdPartySignInUp(new TenantIdentifier(null, "a1", "t1"), SemVer.v5_0));

        assertTrue(checkEmailPasswordSignIn(new TenantIdentifier(null, "a1", "t1"), SemVer.v5_1));
        assertTrue(checkPasswordlessSignIn(new TenantIdentifier(null, "a1", "t1"), SemVer.v5_1));
        assertTrue(checkThirdPartySignInUp(new TenantIdentifier(null, "a1", "t1"), SemVer.v5_1));
    }

    @Test
    public void testDefaultBooleanBehavioursWithTenantCreatedIn5_0() throws Exception {
        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        createApp_5_0(process.getProcess(), TenantIdentifier.BASE_TENANT, "a1", null, null, null, false, null, false,
                null, new JsonObject());
        assertTrue(checkEmailPasswordSignIn(new TenantIdentifier(null, "a1", null), SemVer.v3_0));
        assertTrue(checkPasswordlessSignIn(new TenantIdentifier(null, "a1", null), SemVer.v3_0));
        assertTrue(checkThirdPartySignInUp(new TenantIdentifier(null, "a1", null), SemVer.v3_0));

        assertTrue(checkEmailPasswordSignIn(new TenantIdentifier(null, "a1", null), SemVer.v5_0));
        assertTrue(checkPasswordlessSignIn(new TenantIdentifier(null, "a1", null), SemVer.v5_0));
        assertTrue(checkThirdPartySignInUp(new TenantIdentifier(null, "a1", null), SemVer.v5_0));

        assertTrue(checkEmailPasswordSignIn(new TenantIdentifier(null, "a1", null), SemVer.v5_1));
        assertTrue(checkPasswordlessSignIn(new TenantIdentifier(null, "a1", null), SemVer.v5_1));
        assertTrue(checkThirdPartySignInUp(new TenantIdentifier(null, "a1", null), SemVer.v5_1));

        createTenant_5_0(process.getProcess(), new TenantIdentifier(null, "a1", null), "t1", null, null, null, false,
                null, false, null, new JsonObject());
        assertFalse(checkEmailPasswordSignIn(new TenantIdentifier(null, "a1", "t1"), SemVer.v3_0));
        assertFalse(checkPasswordlessSignIn(new TenantIdentifier(null, "a1", "t1"), SemVer.v3_0));
        assertFalse(checkThirdPartySignInUp(new TenantIdentifier(null, "a1", "t1"), SemVer.v3_0));

        assertFalse(checkEmailPasswordSignIn(new TenantIdentifier(null, "a1", "t1"), SemVer.v5_0));
        assertFalse(checkPasswordlessSignIn(new TenantIdentifier(null, "a1", "t1"), SemVer.v5_0));
        assertFalse(checkThirdPartySignInUp(new TenantIdentifier(null, "a1", "t1"), SemVer.v5_0));

        assertTrue(checkEmailPasswordSignIn(new TenantIdentifier(null, "a1", "t1"), SemVer.v5_1));
        assertTrue(checkPasswordlessSignIn(new TenantIdentifier(null, "a1", "t1"), SemVer.v5_1));
        assertTrue(checkThirdPartySignInUp(new TenantIdentifier(null, "a1", "t1"), SemVer.v5_1));
    }

    @Test
    public void testDefaultBooleanBehavioursWithTenantCreatedIn5_1() throws Exception {
        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        createApp_5_1(process.getProcess(), TenantIdentifier.BASE_TENANT, "a1", false, null, false, null,
                new JsonObject());
        assertTrue(checkEmailPasswordSignIn(new TenantIdentifier(null, "a1", null), SemVer.v3_0));
        assertTrue(checkPasswordlessSignIn(new TenantIdentifier(null, "a1", null), SemVer.v3_0));
        assertTrue(checkThirdPartySignInUp(new TenantIdentifier(null, "a1", null), SemVer.v3_0));

        assertTrue(checkEmailPasswordSignIn(new TenantIdentifier(null, "a1", null), SemVer.v5_0));
        assertTrue(checkPasswordlessSignIn(new TenantIdentifier(null, "a1", null), SemVer.v5_0));
        assertTrue(checkThirdPartySignInUp(new TenantIdentifier(null, "a1", null), SemVer.v5_0));

        assertTrue(checkEmailPasswordSignIn(new TenantIdentifier(null, "a1", null), SemVer.v5_1));
        assertTrue(checkPasswordlessSignIn(new TenantIdentifier(null, "a1", null), SemVer.v5_1));
        assertTrue(checkThirdPartySignInUp(new TenantIdentifier(null, "a1", null), SemVer.v5_1));

        createTenant_5_1(process.getProcess(), new TenantIdentifier(null, "a1", null), "t1", false, null, false, null,
                new JsonObject());
        assertFalse(checkEmailPasswordSignIn(new TenantIdentifier(null, "a1", "t1"), SemVer.v3_0));
        assertFalse(checkPasswordlessSignIn(new TenantIdentifier(null, "a1", "t1"), SemVer.v3_0));
        assertFalse(checkThirdPartySignInUp(new TenantIdentifier(null, "a1", "t1"), SemVer.v3_0));

        assertFalse(checkEmailPasswordSignIn(new TenantIdentifier(null, "a1", "t1"), SemVer.v5_0));
        assertFalse(checkPasswordlessSignIn(new TenantIdentifier(null, "a1", "t1"), SemVer.v5_0));
        assertFalse(checkThirdPartySignInUp(new TenantIdentifier(null, "a1", "t1"), SemVer.v5_0));

        assertTrue(checkEmailPasswordSignIn(new TenantIdentifier(null, "a1", "t1"), SemVer.v5_1));
        assertTrue(checkPasswordlessSignIn(new TenantIdentifier(null, "a1", "t1"), SemVer.v5_1));
        assertTrue(checkThirdPartySignInUp(new TenantIdentifier(null, "a1", "t1"), SemVer.v5_1));
    }

    @Test
    public void testDisableRecipeWithTenantCreatedIn3_0() throws Exception {
        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        createApp_3_0(process.getProcess(), TenantIdentifier.BASE_TENANT, "a1", false, null, null, new JsonObject());
        assertFalse(checkEmailPasswordSignIn(new TenantIdentifier(null, "a1", null), SemVer.v3_0));
        assertTrue(checkPasswordlessSignIn(new TenantIdentifier(null, "a1", null), SemVer.v3_0));
        assertTrue(checkThirdPartySignInUp(new TenantIdentifier(null, "a1", null), SemVer.v3_0));

        assertTrue(checkEmailPasswordSignIn(new TenantIdentifier(null, "a1", null), SemVer.v5_0));
        assertTrue(checkPasswordlessSignIn(new TenantIdentifier(null, "a1", null), SemVer.v5_0));
        assertTrue(checkThirdPartySignInUp(new TenantIdentifier(null, "a1", null), SemVer.v5_0));

        assertTrue(checkEmailPasswordSignIn(new TenantIdentifier(null, "a1", null), SemVer.v5_1));
        assertTrue(checkPasswordlessSignIn(new TenantIdentifier(null, "a1", null), SemVer.v5_1));
        assertTrue(checkThirdPartySignInUp(new TenantIdentifier(null, "a1", null), SemVer.v5_1));

        createApp_3_0(process.getProcess(), TenantIdentifier.BASE_TENANT, "a2", null, false, null, new JsonObject());
        assertTrue(checkEmailPasswordSignIn(new TenantIdentifier(null, "a2", null), SemVer.v3_0));
        assertTrue(checkPasswordlessSignIn(new TenantIdentifier(null, "a2", null), SemVer.v3_0));
        assertFalse(checkThirdPartySignInUp(new TenantIdentifier(null, "a2", null), SemVer.v3_0));

        assertTrue(checkEmailPasswordSignIn(new TenantIdentifier(null, "a2", null), SemVer.v5_0));
        assertTrue(checkPasswordlessSignIn(new TenantIdentifier(null, "a2", null), SemVer.v5_0));
        assertTrue(checkThirdPartySignInUp(new TenantIdentifier(null, "a2", null), SemVer.v5_0));

        assertTrue(checkEmailPasswordSignIn(new TenantIdentifier(null, "a2", null), SemVer.v5_1));
        assertTrue(checkPasswordlessSignIn(new TenantIdentifier(null, "a2", null), SemVer.v5_1));
        assertTrue(checkThirdPartySignInUp(new TenantIdentifier(null, "a2", null), SemVer.v5_1));

        createApp_3_0(process.getProcess(), TenantIdentifier.BASE_TENANT, "a3", null, null, false, new JsonObject());
        assertTrue(checkEmailPasswordSignIn(new TenantIdentifier(null, "a3", null), SemVer.v3_0));
        assertFalse(checkPasswordlessSignIn(new TenantIdentifier(null, "a3", null), SemVer.v3_0));
        assertTrue(checkThirdPartySignInUp(new TenantIdentifier(null, "a3", null), SemVer.v3_0));

        assertTrue(checkEmailPasswordSignIn(new TenantIdentifier(null, "a3", null), SemVer.v5_0));
        assertTrue(checkPasswordlessSignIn(new TenantIdentifier(null, "a3", null), SemVer.v5_0));
        assertTrue(checkThirdPartySignInUp(new TenantIdentifier(null, "a3", null), SemVer.v5_0));

        assertTrue(checkEmailPasswordSignIn(new TenantIdentifier(null, "a3", null), SemVer.v5_1));
        assertTrue(checkPasswordlessSignIn(new TenantIdentifier(null, "a3", null), SemVer.v5_1));
        assertTrue(checkThirdPartySignInUp(new TenantIdentifier(null, "a3", null), SemVer.v5_1));

    }

    @Test
    public void testDisableRecipeWithTenantCreatedIn5_0() throws Exception {
        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        createApp_5_0(process.getProcess(), TenantIdentifier.BASE_TENANT, "a1", false, null, null, false, null, false,
                null, new JsonObject());
        assertFalse(checkEmailPasswordSignIn(new TenantIdentifier(null, "a1", null), SemVer.v3_0));
        assertTrue(checkPasswordlessSignIn(new TenantIdentifier(null, "a1", null), SemVer.v3_0));
        assertTrue(checkThirdPartySignInUp(new TenantIdentifier(null, "a1", null), SemVer.v3_0));

        assertFalse(checkEmailPasswordSignIn(new TenantIdentifier(null, "a1", null), SemVer.v5_0));
        assertTrue(checkPasswordlessSignIn(new TenantIdentifier(null, "a1", null), SemVer.v5_0));
        assertTrue(checkThirdPartySignInUp(new TenantIdentifier(null, "a1", null), SemVer.v5_0));

        assertTrue(checkEmailPasswordSignIn(new TenantIdentifier(null, "a1", null), SemVer.v5_1));
        assertTrue(checkPasswordlessSignIn(new TenantIdentifier(null, "a1", null), SemVer.v5_1));
        assertTrue(checkThirdPartySignInUp(new TenantIdentifier(null, "a1", null), SemVer.v5_1));

        createApp_5_0(process.getProcess(), TenantIdentifier.BASE_TENANT, "a2", null, false, null, false, null, false,
                null, new JsonObject());
        assertTrue(checkEmailPasswordSignIn(new TenantIdentifier(null, "a2", null), SemVer.v3_0));
        assertTrue(checkPasswordlessSignIn(new TenantIdentifier(null, "a2", null), SemVer.v3_0));
        assertFalse(checkThirdPartySignInUp(new TenantIdentifier(null, "a2", null), SemVer.v3_0));

        assertTrue(checkEmailPasswordSignIn(new TenantIdentifier(null, "a2", null), SemVer.v5_0));
        assertTrue(checkPasswordlessSignIn(new TenantIdentifier(null, "a2", null), SemVer.v5_0));
        assertFalse(checkThirdPartySignInUp(new TenantIdentifier(null, "a2", null), SemVer.v5_0));

        assertTrue(checkEmailPasswordSignIn(new TenantIdentifier(null, "a2", null), SemVer.v5_1));
        assertTrue(checkPasswordlessSignIn(new TenantIdentifier(null, "a2", null), SemVer.v5_1));
        assertTrue(checkThirdPartySignInUp(new TenantIdentifier(null, "a2", null), SemVer.v5_1));

        createApp_5_0(process.getProcess(), TenantIdentifier.BASE_TENANT, "a3", null, null, false, false, null, false,
                null, new JsonObject());
        assertTrue(checkEmailPasswordSignIn(new TenantIdentifier(null, "a3", null), SemVer.v3_0));
        assertFalse(checkPasswordlessSignIn(new TenantIdentifier(null, "a3", null), SemVer.v3_0));
        assertTrue(checkThirdPartySignInUp(new TenantIdentifier(null, "a3", null), SemVer.v3_0));

        assertTrue(checkEmailPasswordSignIn(new TenantIdentifier(null, "a3", null), SemVer.v5_0));
        assertFalse(checkPasswordlessSignIn(new TenantIdentifier(null, "a3", null), SemVer.v5_0));
        assertTrue(checkThirdPartySignInUp(new TenantIdentifier(null, "a3", null), SemVer.v5_0));

        assertTrue(checkEmailPasswordSignIn(new TenantIdentifier(null, "a3", null), SemVer.v5_1));
        assertTrue(checkPasswordlessSignIn(new TenantIdentifier(null, "a3", null), SemVer.v5_1));
        assertTrue(checkThirdPartySignInUp(new TenantIdentifier(null, "a3", null), SemVer.v5_1));

    }

    @Test
    public void testFirstFactorsAffectsBooleansInCDI3_0() throws Exception {
        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        createApp_5_0(process.getProcess(), TenantIdentifier.BASE_TENANT, "a1", null, null, null, true,
                new String[]{"emailpassword"}, false, null, new JsonObject());
        assertTrue(checkEmailPasswordSignIn(new TenantIdentifier(null, "a1", null), SemVer.v3_0));
        assertFalse(checkPasswordlessSignIn(new TenantIdentifier(null, "a1", null), SemVer.v3_0));
        assertFalse(checkThirdPartySignInUp(new TenantIdentifier(null, "a1", null), SemVer.v3_0));

        createApp_5_0(process.getProcess(), TenantIdentifier.BASE_TENANT, "a2", null, null, null, true,
                new String[]{"thirdparty"}, false, null, new JsonObject());
        assertFalse(checkEmailPasswordSignIn(new TenantIdentifier(null, "a2", null), SemVer.v3_0));
        assertFalse(checkPasswordlessSignIn(new TenantIdentifier(null, "a2", null), SemVer.v3_0));
        assertTrue(checkThirdPartySignInUp(new TenantIdentifier(null, "a2", null), SemVer.v3_0));

        createApp_5_0(process.getProcess(), TenantIdentifier.BASE_TENANT, "a3", null, null, null, true,
                new String[]{"otp-email"}, false, null, new JsonObject());
        assertFalse(checkEmailPasswordSignIn(new TenantIdentifier(null, "a3", null), SemVer.v3_0));
        assertTrue(checkPasswordlessSignIn(new TenantIdentifier(null, "a3", null), SemVer.v3_0));
        assertFalse(checkThirdPartySignInUp(new TenantIdentifier(null, "a3", null), SemVer.v3_0));
    }

    private static int userCount = 0;

    private boolean checkEmailPasswordSignIn(TenantIdentifier tenantIdentifier, SemVer version) throws IOException {
        try {
            TestMultitenancyAPIHelper.epSignUpAndGetResponse(tenantIdentifier, "user" + (userCount++) + "@example.com",
                    "password123!", process.getProcess(), version);
        } catch (HttpResponseException e) {
            if (e.statusCode == 403) {
                return false;
            }
            throw new IllegalStateException(e);
        }
        return true;
    }

    private boolean checkPasswordlessSignIn(TenantIdentifier tenantIdentifier, SemVer version) throws IOException {
        try {
            TestMultitenancyAPIHelper.plSignInUpWithEmailOTP(tenantIdentifier, "user" + (userCount++) + "@example.com",
                    process.getProcess(), version);
        } catch (HttpResponseException e) {
            if (e.statusCode == 403) {
                return false;
            }
            throw new IllegalStateException(e);
        }
        return true;
    }

    private boolean checkThirdPartySignInUp(TenantIdentifier tenantIdentifier, SemVer version) throws IOException {
        try {
            TestMultitenancyAPIHelper.tpSignInUpAndGetResponse(tenantIdentifier, "google", "googleid" + (userCount),
                    "user" + userCount + "@example.com", process.getProcess(), version);
            userCount++;
        } catch (HttpResponseException e) {
            if (e.statusCode == 403) {
                return false;
            }
            throw new IllegalStateException(e);
        }
        return true;
    }

    private static JsonObject createApp_3_0(Main main, TenantIdentifier sourceTenant, String appId,
                                            Boolean emailPasswordEnabled,
                                            Boolean thirdPartyEnabled, Boolean passwordlessEnabled,
                                            JsonObject coreConfig) throws HttpResponseException, IOException {
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("appId", appId);
        if (emailPasswordEnabled != null) {
            requestBody.addProperty("emailPasswordEnabled", emailPasswordEnabled);
        }
        if (thirdPartyEnabled != null) {
            requestBody.addProperty("thirdPartyEnabled", thirdPartyEnabled);
        }
        if (passwordlessEnabled != null) {
            requestBody.addProperty("passwordlessEnabled", passwordlessEnabled);
        }
        requestBody.add("coreConfig", coreConfig);

        JsonObject response = HttpRequestForTesting.sendJsonPUTRequest(main, "",
                HttpRequestForTesting.getMultitenantUrl(sourceTenant, "/recipe/multitenancy/app"),
                requestBody, 1000, 2500, null,
                SemVer.v3_0.get(), "multitenancy");

        assertEquals("OK", response.getAsJsonPrimitive("status").getAsString());
        return response;
    }

    private static JsonObject createOrUpdateApp_3_0(Main main, TenantIdentifier sourceTenant, String appId,
                                                    JsonObject requestBody) throws HttpResponseException, IOException {
        requestBody.addProperty("appId", appId);

        JsonObject response = HttpRequestForTesting.sendJsonPUTRequest(main, "",
                HttpRequestForTesting.getMultitenantUrl(sourceTenant, "/recipe/multitenancy/app"),
                requestBody, 1000, 2500, null,
                SemVer.v3_0.get(), "multitenancy");

        assertEquals("OK", response.getAsJsonPrimitive("status").getAsString());
        return response;
    }

    private static JsonObject createApp_5_0(Main main, TenantIdentifier sourceTenant, String appId,
                                            Boolean emailPasswordEnabled,
                                            Boolean thirdPartyEnabled, Boolean passwordlessEnabled,
                                            boolean setFirstFactors, String[] firstFactors,
                                            boolean setRequiredSecondaryFactors, String[] requiredSecondaryFactors,
                                            JsonObject coreConfig) throws HttpResponseException, IOException {
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("appId", appId);
        if (emailPasswordEnabled != null) {
            requestBody.addProperty("emailPasswordEnabled", emailPasswordEnabled);
        }
        if (thirdPartyEnabled != null) {
            requestBody.addProperty("thirdPartyEnabled", thirdPartyEnabled);
        }
        if (passwordlessEnabled != null) {
            requestBody.addProperty("passwordlessEnabled", passwordlessEnabled);
        }
        if (setFirstFactors || firstFactors != null) {
            requestBody.add("firstFactors", new Gson().toJsonTree(firstFactors));
        }
        if (setRequiredSecondaryFactors || requiredSecondaryFactors != null) {
            requestBody.add("requiredSecondaryFactors", new Gson().toJsonTree(requiredSecondaryFactors));
        }
        requestBody.add("coreConfig", coreConfig);

        JsonObject response = HttpRequestForTesting.sendJsonPUTRequest(main, "",
                HttpRequestForTesting.getMultitenantUrl(sourceTenant, "/recipe/multitenancy/app"),
                requestBody, 1000, 2500, null,
                SemVer.v5_0.get(), "multitenancy");

        assertEquals("OK", response.getAsJsonPrimitive("status").getAsString());
        return response;
    }

    private static JsonObject createOrUpdateApp_5_0(Main main, TenantIdentifier sourceTenant, String appId,
                                                    JsonObject requestBody) throws HttpResponseException, IOException {
        requestBody.addProperty("appId", appId);

        JsonObject response = HttpRequestForTesting.sendJsonPUTRequest(main, "",
                HttpRequestForTesting.getMultitenantUrl(sourceTenant, "/recipe/multitenancy/app"),
                requestBody, 1000, 2500, null,
                SemVer.v5_0.get(), "multitenancy");

        assertEquals("OK", response.getAsJsonPrimitive("status").getAsString());
        return response;
    }

    private static JsonObject createApp_5_1(Main main, TenantIdentifier sourceTenant, String appId,
                                            boolean setFirstFactors, String[] firstFactors,
                                            boolean setRequiredSecondaryFactors, String[] requiredSecondaryFactors,
                                            JsonObject coreConfig) throws HttpResponseException, IOException {
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("appId", appId);
        if (setFirstFactors || firstFactors != null) {
            requestBody.add("firstFactors", new Gson().toJsonTree(firstFactors));
        }
        if (setRequiredSecondaryFactors || requiredSecondaryFactors != null) {
            requestBody.add("requiredSecondaryFactors", new Gson().toJsonTree(requiredSecondaryFactors));
        }
        requestBody.add("coreConfig", coreConfig);

        JsonObject response = HttpRequestForTesting.sendJsonPUTRequest(main, "",
                HttpRequestForTesting.getMultitenantUrl(sourceTenant, "/recipe/multitenancy/app/v2"),
                requestBody, 1000, 2500, null,
                SemVer.v5_1.get(), "multitenancy");

        assertEquals("OK", response.getAsJsonPrimitive("status").getAsString());
        return response;
    }

    private static JsonObject createOrUpdateApp_5_1(Main main, TenantIdentifier sourceTenant, String appId,
                                                    JsonObject requestBody) throws HttpResponseException, IOException {
        requestBody.addProperty("appId", appId);

        JsonObject response = HttpRequestForTesting.sendJsonPUTRequest(main, "",
                HttpRequestForTesting.getMultitenantUrl(sourceTenant, "/recipe/multitenancy/app/v2"),
                requestBody, 1000, 2500, null,
                SemVer.v5_1.get(), "multitenancy");

        assertEquals("OK", response.getAsJsonPrimitive("status").getAsString());
        return response;
    }

    private static JsonObject getTenant_3_0(TenantIdentifier tenantIdentifier, Main main)
            throws HttpResponseException, IOException {

        JsonObject response = HttpRequestForTesting.sendGETRequest(main, "",
                HttpRequestForTesting.getMultitenantUrl(tenantIdentifier, "/recipe/multitenancy/tenant"),
                null, 1000, 1000, null,
                SemVer.v3_0.get(), "multitenancy");

        assertEquals("OK", response.getAsJsonPrimitive("status").getAsString());
        return response;
    }

    private static JsonObject getTenant_5_0(TenantIdentifier tenantIdentifier, Main main)
            throws HttpResponseException, IOException {

        JsonObject response = HttpRequestForTesting.sendGETRequest(main, "",
                HttpRequestForTesting.getMultitenantUrl(tenantIdentifier, "/recipe/multitenancy/tenant"),
                null, 1000, 1000, null,
                SemVer.v5_0.get(), "multitenancy");

        assertEquals("OK", response.getAsJsonPrimitive("status").getAsString());
        return response;
    }

    private static JsonObject getTenant_5_1(TenantIdentifier tenantIdentifier, Main main)
            throws HttpResponseException, IOException {

        JsonObject response = HttpRequestForTesting.sendGETRequest(main, "",
                HttpRequestForTesting.getMultitenantUrl(tenantIdentifier, "/recipe/multitenancy/tenant/v2"),
                null, 1000, 1000, null,
                SemVer.v5_1.get(), "multitenancy");

        assertEquals("OK", response.getAsJsonPrimitive("status").getAsString());
        return response;
    }

    private static JsonObject listApps(TenantIdentifier sourceTenant, Main main)
            throws HttpResponseException, IOException {
        JsonObject response = HttpRequestForTesting.sendGETRequest(main, "",
                HttpRequestForTesting.getMultitenantUrl(sourceTenant, "/recipe/multitenancy/app/list"),
                null, 1000, 1000, null,
                SemVer.v5_1.get(), "multitenancy");

        assertEquals("OK", response.getAsJsonPrimitive("status").getAsString());
        return response;
    }

    private static JsonObject deleteApp(TenantIdentifier sourceTenant, String appId, Main main)
            throws HttpResponseException, IOException {
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("appId", appId);

        JsonObject response = HttpRequestForTesting.sendJsonPOSTRequest(main, "",
                HttpRequestForTesting.getMultitenantUrl(sourceTenant, "/recipe/multitenancy/app/remove"),
                requestBody, 1000, 2500, null,
                SemVer.v5_1.get(), "multitenancy");

        assertEquals("OK", response.getAsJsonPrimitive("status").getAsString());
        return response;
    }

    private static JsonObject deleteTenant(TenantIdentifier sourceTenant, String tenantId, Main main)
            throws HttpResponseException, IOException {
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("tenantId", tenantId);

        JsonObject response = HttpRequestForTesting.sendJsonPOSTRequest(main, "",
                HttpRequestForTesting.getMultitenantUrl(sourceTenant, "/recipe/multitenancy/tenant/remove"),
                requestBody, 1000, 2500, null,
                SemVer.v5_1.get(), "multitenancy");

        assertEquals("OK", response.getAsJsonPrimitive("status").getAsString());
        return response;
    }

    private static JsonObject createTenant_3_0(Main main, TenantIdentifier sourceTenant, String tenantId,
                                               Boolean emailPasswordEnabled,
                                               Boolean thirdPartyEnabled, Boolean passwordlessEnabled,
                                               JsonObject coreConfig) throws HttpResponseException, IOException {
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("tenantId", tenantId);
        if (emailPasswordEnabled != null) {
            requestBody.addProperty("emailPasswordEnabled", emailPasswordEnabled);
        }
        if (thirdPartyEnabled != null) {
            requestBody.addProperty("thirdPartyEnabled", thirdPartyEnabled);
        }
        if (passwordlessEnabled != null) {
            requestBody.addProperty("passwordlessEnabled", passwordlessEnabled);
        }

        requestBody.add("coreConfig", coreConfig);

        JsonObject response = HttpRequestForTesting.sendJsonPUTRequest(main, "",
                HttpRequestForTesting.getMultitenantUrl(sourceTenant, "/recipe/multitenancy/tenant"),
                requestBody, 1000, 2500, null,
                SemVer.v3_0.get(), "multitenancy");

        assertEquals("OK", response.getAsJsonPrimitive("status").getAsString());
        return response;
    }

    private static JsonObject createOrUpdateTenant_3_0(Main main, TenantIdentifier sourceTenant, String tenantId,
                                                       JsonObject requestBody)
            throws HttpResponseException, IOException {
        requestBody.addProperty("tenantId", tenantId);

        JsonObject response = HttpRequestForTesting.sendJsonPUTRequest(main, "",
                HttpRequestForTesting.getMultitenantUrl(sourceTenant, "/recipe/multitenancy/tenant"),
                requestBody, 1000, 2500, null,
                SemVer.v3_0.get(), "multitenancy");

        assertEquals("OK", response.getAsJsonPrimitive("status").getAsString());
        return response;
    }

    private static JsonObject createTenant_5_0(Main main, TenantIdentifier sourceTenant, String tenantId,
                                               Boolean emailPasswordEnabled,
                                               Boolean thirdPartyEnabled, Boolean passwordlessEnabled,
                                               boolean setFirstFactors, String[] firstFactors,
                                               boolean setRequiredSecondaryFactors, String[] requiredSecondaryFactors,
                                               JsonObject coreConfig) throws HttpResponseException, IOException {
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("tenantId", tenantId);
        if (emailPasswordEnabled != null) {
            requestBody.addProperty("emailPasswordEnabled", emailPasswordEnabled);
        }
        if (thirdPartyEnabled != null) {
            requestBody.addProperty("thirdPartyEnabled", thirdPartyEnabled);
        }
        if (passwordlessEnabled != null) {
            requestBody.addProperty("passwordlessEnabled", passwordlessEnabled);
        }
        if (setFirstFactors || firstFactors != null) {
            requestBody.add("firstFactors", new Gson().toJsonTree(firstFactors));
        }
        if (setRequiredSecondaryFactors || requiredSecondaryFactors != null) {
            requestBody.add("requiredSecondaryFactors", new Gson().toJsonTree(requiredSecondaryFactors));
        }

        requestBody.add("coreConfig", coreConfig);

        JsonObject response = HttpRequestForTesting.sendJsonPUTRequest(main, "",
                HttpRequestForTesting.getMultitenantUrl(sourceTenant, "/recipe/multitenancy/tenant"),
                requestBody, 1000, 2500, null,
                SemVer.v5_0.get(), "multitenancy");

        assertEquals("OK", response.getAsJsonPrimitive("status").getAsString());
        return response;
    }

    private static JsonObject createOrUpdateTenant_5_0(Main main, TenantIdentifier sourceTenant, String tenantId,
                                                       JsonObject requestBody)
            throws HttpResponseException, IOException {
        requestBody.addProperty("tenantId", tenantId);

        JsonObject response = HttpRequestForTesting.sendJsonPUTRequest(main, "",
                HttpRequestForTesting.getMultitenantUrl(sourceTenant, "/recipe/multitenancy/tenant"),
                requestBody, 1000, 2500, null,
                SemVer.v5_0.get(), "multitenancy");

        assertEquals("OK", response.getAsJsonPrimitive("status").getAsString());
        return response;
    }

    private static JsonObject createTenant_5_1(Main main, TenantIdentifier sourceTenant, String tenantId,
                                               boolean setFirstFactors, String[] firstFactors,
                                               boolean setRequiredSecondaryFactors, String[] requiredSecondaryFactors,
                                               JsonObject coreConfig) throws HttpResponseException, IOException {
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("tenantId", tenantId);
        if (setFirstFactors || firstFactors != null) {
            requestBody.add("firstFactors", new Gson().toJsonTree(firstFactors));
        }
        if (setRequiredSecondaryFactors || requiredSecondaryFactors != null) {
            requestBody.add("requiredSecondaryFactors", new Gson().toJsonTree(requiredSecondaryFactors));
        }

        requestBody.add("coreConfig", coreConfig);

        JsonObject response = HttpRequestForTesting.sendJsonPUTRequest(main, "",
                HttpRequestForTesting.getMultitenantUrl(sourceTenant, "/recipe/multitenancy/tenant/v2"),
                requestBody, 1000, 2500, null,
                SemVer.v5_1.get(), "multitenancy");

        assertEquals("OK", response.getAsJsonPrimitive("status").getAsString());
        return response;
    }

    private static JsonObject createOrUpdateTenant_5_1(Main main, TenantIdentifier sourceTenant, String tenantId,
                                                       JsonObject requestBody)
            throws HttpResponseException, IOException {
        requestBody.addProperty("tenantId", tenantId);

        JsonObject response = HttpRequestForTesting.sendJsonPUTRequest(main, "",
                HttpRequestForTesting.getMultitenantUrl(sourceTenant, "/recipe/multitenancy/tenant/v2"),
                requestBody, 1000, 2500, null,
                SemVer.v5_1.get(), "multitenancy");

        assertEquals("OK", response.getAsJsonPrimitive("status").getAsString());
        return response;
    }

    private static class CrossVersionTestCase {
        CrossVersionTestCaseStep[] steps;

        public CrossVersionTestCase(CrossVersionTestCaseStep[] steps) {
            this.steps = steps;
        }

        public void perform(Main main) throws HttpResponseException, IOException {
            for (CrossVersionTestCaseStep step : steps) {
                step.perform(main);
            }
        }
    }

    private static class CrossVersionTestCaseStep {
        private static enum OperationType {
            CREATE_APP, CREATE_TENANT, UPDATE_APP, UPDATE_TENANT
        }

        SemVer version;
        OperationType operation;
        JsonObject body;

        public CrossVersionTestCaseStep(SemVer version, OperationType operation, JsonObject body) {
            this.version = version;
            this.operation = operation;
            this.body = body;
        }

        public void perform(Main main) throws HttpResponseException, IOException {
            if (version.equals(SemVer.v3_0)) {
                if (operation == OperationType.CREATE_APP) {
                    deleteApp(TenantIdentifier.BASE_TENANT, "a1", main);
                    createOrUpdateApp_3_0(main, TenantIdentifier.BASE_TENANT, "a1", body);
                } else if (operation == OperationType.CREATE_TENANT) {
                    deleteTenant(TenantIdentifier.BASE_TENANT, "t1", main);
                    createOrUpdateTenant_3_0(main, TenantIdentifier.BASE_TENANT, "t1", body);
                } else if (operation == OperationType.UPDATE_APP) {
                    createOrUpdateApp_3_0(main, TenantIdentifier.BASE_TENANT, "a1", body);
                } else if (operation == OperationType.UPDATE_TENANT) {
                    createOrUpdateTenant_3_0(main, TenantIdentifier.BASE_TENANT, "t1", body);
                } else {
                    throw new RuntimeException("Should never come here");
                }
            }
        }
    }
}
