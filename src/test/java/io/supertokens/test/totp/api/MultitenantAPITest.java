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

package io.supertokens.test.totp.api;

import com.google.gson.JsonObject;
import io.supertokens.ProcessState;
import io.supertokens.emailpassword.EmailPassword;
import io.supertokens.featureflag.EE_FEATURES;
import io.supertokens.featureflag.FeatureFlagTestContent;
import io.supertokens.featureflag.exceptions.FeatureNotEnabledException;
import io.supertokens.multitenancy.Multitenancy;
import io.supertokens.multitenancy.exception.BadPermissionException;
import io.supertokens.multitenancy.exception.CannotModifyBaseConfigException;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.pluginInterface.authRecipe.AuthRecipeUserInfo;
import io.supertokens.pluginInterface.exceptions.InvalidConfigException;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.multitenancy.*;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.pluginInterface.multitenancy.TenantConfig;
import io.supertokens.pluginInterface.totp.TOTPDevice;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.test.TestingProcessManager;
import io.supertokens.test.Utils;
import io.supertokens.test.httpRequest.HttpRequestForTesting;
import io.supertokens.test.httpRequest.HttpResponseException;
import io.supertokens.test.totp.TOTPRecipeTest;
import io.supertokens.thirdparty.InvalidProviderConfigException;
import io.supertokens.totp.Totp;
import io.supertokens.utils.SemVer;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;

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
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{
                        EE_FEATURES.MULTI_TENANCY, EE_FEATURES.MFA});
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
                            new ThirdPartyConfig(false, null),
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
                            new ThirdPartyConfig(false, null),
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
                            new ThirdPartyConfig(false, null),
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

    private JsonObject createDevice(TenantIdentifier tenantIdentifier, String userId)
            throws HttpResponseException, IOException {
        return createDevice(tenantIdentifier, userId, SemVer.v3_0);
    }

    private JsonObject createDevice(TenantIdentifier tenantIdentifier, String userId, SemVer version)
            throws HttpResponseException, IOException {
        JsonObject body = new JsonObject();
        body.addProperty("userId", userId);
        body.addProperty("deviceName", "d1");
        body.addProperty("skew", 1);
        body.addProperty("period", 2);

        JsonObject res = HttpRequestForTesting.sendJsonPOSTRequest(
                process.getProcess(),
                "",
                HttpRequestForTesting.getMultitenantUrl(tenantIdentifier, "/recipe/totp/device"),
                body,
                1000,
                1000,
                null,
                version.get(),
                "totp");
        assertEquals("OK", res.get("status").getAsString());
        return res;
    }

    private void createDeviceAlreadyExists(TenantIdentifier tenantIdentifier, String userId)
            throws HttpResponseException, IOException {
        JsonObject body = new JsonObject();
        body.addProperty("userId", userId);
        body.addProperty("deviceName", "d1");
        body.addProperty("skew", 1);
        body.addProperty("period", 30);

        JsonObject res = HttpRequestForTesting.sendJsonPOSTRequest(
                process.getProcess(),
                "",
                HttpRequestForTesting.getMultitenantUrl(tenantIdentifier, "/recipe/totp/device"),
                body,
                1000,
                1000,
                null,
                SemVer.v3_0.get(),
                "totp");
        assertEquals("DEVICE_ALREADY_EXISTS_ERROR", res.get("status").getAsString());
    }

    private void verifyDevice(TenantIdentifier tenantIdentifier, String userId, String totp)
            throws HttpResponseException, IOException {
        JsonObject body = new JsonObject();
        body.addProperty("userId", userId);
        body.addProperty("deviceName", "d1");
        body.addProperty("totp", totp);
        JsonObject res = HttpRequestForTesting.sendJsonPOSTRequest(
                process.getProcess(),
                "",
                HttpRequestForTesting.getMultitenantUrl(tenantIdentifier, "/recipe/totp/device/verify"),
                body,
                1000,
                1000,
                null,
                SemVer.v3_0.get(),
                "totp");
        assert res.get("status").getAsString().equals("OK");
    }

    private void validateTotp(TenantIdentifier tenantIdentifier, String userId, String totp)
            throws HttpResponseException, IOException {
        JsonObject body = new JsonObject();
        body.addProperty("userId", userId);
        body.addProperty("allowUnverifiedDevices", true);
        body.addProperty("totp", totp);

        JsonObject res = HttpRequestForTesting.sendJsonPOSTRequest(
                process.getProcess(),
                "",
                HttpRequestForTesting.getMultitenantUrl(tenantIdentifier, "/recipe/totp/verify"),
                body,
                1000,
                1000,
                null,
                SemVer.v3_0.get(),
                "totp");
        assertEquals("OK", res.get("status").getAsString());
    }

    @Test
    public void testCreateDeviceWorksFromAllTenants() throws Exception {
        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        int userCount = 1;

        TenantIdentifier[] tenants = new TenantIdentifier[]{t1, t2, t3};
        for (TenantIdentifier tenantId : tenants) {
            createDevice(tenantId, "user" + userCount);

            userCount++;
        }
    }

    @Test
    public void testCreateDeviceWorksFromPublicTenantOnly_v5() throws Exception {
        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        int userCount = 1;

        createDevice(t1, "user" + userCount);
        TOTPDevice device = Totp.getDevices(t1.toAppIdentifier(), (StorageLayer.getStorage(t1, process.getProcess())),
                "user" + userCount)[0];
        String validTotp = TOTPRecipeTest.generateTotpCode(process.getProcess(), device);
        verifyDevice(t1, "user" + userCount, validTotp);

        userCount++;

        try {
            createDevice(t2, "user" + userCount, SemVer.v5_0);
            fail();
        } catch (HttpResponseException e) {
            assertEquals(403, e.statusCode);
        }
    }

    @Test
    public void testSameCodeUsedOnDifferentTenantsIsAllowed() throws Exception {
        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        TenantIdentifier[] tenants = new TenantIdentifier[]{t2, t3};
        int userCount = 1;
        for (TenantIdentifier tenant1 : tenants) {
            AuthRecipeUserInfo user = EmailPassword.signUp(
                    tenant1, (StorageLayer.getStorage(tenant1, process.getProcess())), process.getProcess(),
                    "test@example.com", "password1");
            String userId = user.getSupertokensUserId();

            JsonObject deviceResponse = createDevice(t1, userId);
            String secretKey = deviceResponse.get("secret").getAsString();
            TOTPDevice device = new TOTPDevice("user" + userCount, "d1", secretKey, 2, 1, true,
                    System.currentTimeMillis());
            String validTotp = TOTPRecipeTest.generateTotpCode(process.getProcess(), device);
            verifyDevice(tenant1, userId, validTotp);

            Thread.sleep(2500); // Wait for a new TOTP
            String validTotp2 = TOTPRecipeTest.generateTotpCode(process.getProcess(), device);
            for (TenantIdentifier tenant2 : tenants) {
                validateTotp(tenant2, userId, validTotp2);
            }

            userCount++;
        }
    }
}
