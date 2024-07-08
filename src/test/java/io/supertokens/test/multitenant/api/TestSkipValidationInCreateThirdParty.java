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

import com.google.gson.JsonObject;
import io.supertokens.ProcessState;
import io.supertokens.featureflag.EE_FEATURES;
import io.supertokens.featureflag.FeatureFlagTestContent;
import io.supertokens.multitenancy.Multitenancy;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.pluginInterface.multitenancy.*;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.test.TestingProcessManager;
import io.supertokens.test.Utils;
import io.supertokens.test.httpRequest.HttpResponseException;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import static org.junit.Assert.*;

public class TestSkipValidationInCreateThirdParty {
    @Rule
    public TestRule watchman = Utils.getOnFailure();

    @AfterClass
    public static void afterTesting() {
        Utils.afterTesting();
    }

    @Before
    public void beforeEach() {
        Utils.reset();
    }

    @Test
    public void testSkipValidation() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args, false);
        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{EE_FEATURES.MULTI_TENANCY});
        process.startProcess();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        Multitenancy.addNewOrUpdateAppOrTenant(process.getProcess(), new TenantConfig(
                new TenantIdentifier(null, "a1", null),
                new EmailPasswordConfig(true),
                new ThirdPartyConfig(true, null),
                new PasswordlessConfig(true),
                null, null, new JsonObject()
        ), false);

        try {
            JsonObject additionalConfig = new JsonObject();
            additionalConfig.addProperty("boxyURL", "");
            TestMultitenancyAPIHelper.addOrUpdateThirdPartyProviderConfig(new TenantIdentifier(null, "a1", null),
                    new ThirdPartyConfig.Provider(
                            "boxy-saml", "Boxy SAML", new ThirdPartyConfig.ProviderClient[]{
                            new ThirdPartyConfig.ProviderClient("web", "clientid", "clientsecret", null, null,
                                    additionalConfig)
                    }, null, null, null,
                            null, null, null, null, null, null, null,
                            null
                    ), process.getProcess());
            fail();
        } catch (HttpResponseException e) {
            assertTrue(e.getMessage().contains(
                    "a non empty string value must be specified for boxyURL in the additionalConfig for Boxy SAML " +
                            "provider"));
        }

        TestMultitenancyAPIHelper.addOrUpdateThirdPartyProviderConfig(new TenantIdentifier(null, "a1", null),
                new ThirdPartyConfig.Provider(
                        "boxy-saml", "Boxy SAML", new ThirdPartyConfig.ProviderClient[]{
                        new ThirdPartyConfig.ProviderClient("web", "clientid", "clientsecret", null, null, null)
                }, null, null, null,
                        null, null, null, null, null, null, null,
                        null
                ), true, process.getProcess()); // This must succeed

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }
}
