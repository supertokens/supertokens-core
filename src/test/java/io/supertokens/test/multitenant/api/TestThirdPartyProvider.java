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
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
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
import io.supertokens.pluginInterface.multitenancy.ThirdPartyConfig;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.test.TestingProcessManager;
import io.supertokens.test.Utils;
import io.supertokens.thirdparty.InvalidProviderConfigException;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;

public class TestThirdPartyProvider {
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
    public void testAddThirdPartyConfig() throws Exception {
        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        ThirdPartyConfig.Provider provider = new ThirdPartyConfig.Provider(
                "google",
                "Google",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
        JsonObject response = TestMultitenancyAPIHelper.addOrUpdateThirdPartyProviderConfig(
                new TenantIdentifier(null, null, null),
                provider,
                process.getProcess()
        );
        assertTrue(response.get("createdNew").getAsBoolean());

        response = TestMultitenancyAPIHelper.getTenant(new TenantIdentifier(null, null, null), process.getProcess());
        JsonObject resultProvider = response.get("thirdParty").getAsJsonObject().get("providers").getAsJsonArray()
                .get(0).getAsJsonObject();
        assertEquals(new Gson().toJsonTree(provider), resultProvider);
    }

    @Test
    public void testAddThirdPartyConfigWithNullValues() throws Exception {
        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        JsonObject objWithNulls = new JsonObject();
        objWithNulls.addProperty("key1", "value");
        objWithNulls.add("key2", null);

        ThirdPartyConfig.Provider provider = new ThirdPartyConfig.Provider(
                "google",
                "Google",
                null,
                null,
                objWithNulls,
                null,
                objWithNulls,
                null,
                objWithNulls,
                objWithNulls,
                null,
                null,
                null,
                null
        );
        JsonObject response = TestMultitenancyAPIHelper.addOrUpdateThirdPartyProviderConfig(
                new TenantIdentifier(null, null, null),
                provider,
                process.getProcess()
        );
        assertTrue(response.get("createdNew").getAsBoolean());

        response = TestMultitenancyAPIHelper.getTenant(new TenantIdentifier(null, null, null), process.getProcess());
        JsonObject resultProvider = response.get("thirdParty").getAsJsonObject().get("providers").getAsJsonArray()
                .get(0).getAsJsonObject();
        assertEquals(provider.toJson(), resultProvider);
    }

    @Test
    public void testUpdateThirdPartyConfig() throws Exception {
        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        ThirdPartyConfig.Provider provider = new ThirdPartyConfig.Provider(
                "google",
                "Google",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
        JsonObject response = TestMultitenancyAPIHelper.addOrUpdateThirdPartyProviderConfig(
                new TenantIdentifier(null, null, null),
                provider,
                process.getProcess()
        );
        assertTrue(response.get("createdNew").getAsBoolean());

        provider = new ThirdPartyConfig.Provider(
                "google",
                "Google",
                new ThirdPartyConfig.ProviderClient[]{
                        new ThirdPartyConfig.ProviderClient(
                                "client_id",
                                "client_secret",
                                "redirect_uri",
                                new String[]{"scope"},
                                null,
                                null
                        )
                },
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );

        response = TestMultitenancyAPIHelper.addOrUpdateThirdPartyProviderConfig(
                new TenantIdentifier(null, null, null),
                provider,
                process.getProcess()
        );
        assertFalse(response.get("createdNew").getAsBoolean());

        response = TestMultitenancyAPIHelper.getTenant(new TenantIdentifier(null, null, null), process.getProcess());
        JsonObject resultProvider = response.get("thirdParty").getAsJsonObject().get("providers").getAsJsonArray()
                .get(0).getAsJsonObject();
        assertEquals(new Gson().toJsonTree(provider), resultProvider);
    }

    @Test
    public void testDeleteThirdPartyConfig() throws Exception {
        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        ThirdPartyConfig.Provider provider = new ThirdPartyConfig.Provider(
                "google",
                "Google",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
        JsonObject response = TestMultitenancyAPIHelper.addOrUpdateThirdPartyProviderConfig(
                new TenantIdentifier(null, null, null),
                provider,
                process.getProcess()
        );
        assertTrue(response.get("createdNew").getAsBoolean());

        response = TestMultitenancyAPIHelper.deleteThirdPartyProvider(new TenantIdentifier(null, null, null), "google",
                process.getProcess());
        assertTrue(response.get("didConfigExist").getAsBoolean());

        // delete again
        response = TestMultitenancyAPIHelper.deleteThirdPartyProvider(new TenantIdentifier(null, null, null), "google",
                process.getProcess());
        assertFalse(response.get("didConfigExist").getAsBoolean());
    }
}
