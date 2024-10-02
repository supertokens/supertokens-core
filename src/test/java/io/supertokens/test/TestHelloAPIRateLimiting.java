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

package io.supertokens.test;

import com.google.gson.JsonObject;
import io.supertokens.Main;
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
import io.supertokens.test.httpRequest.HttpRequestForTesting;
import io.supertokens.test.httpRequest.HttpResponseException;
import io.supertokens.thirdparty.InvalidProviderConfigException;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import java.io.IOException;

import static org.junit.Assert.*;

public class TestHelloAPIRateLimiting {
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

    private void createApps(TestingProcessManager.TestingProcess process)
            throws StorageQueryException, TenantOrAppNotFoundException, InvalidProviderConfigException,
            FeatureNotEnabledException, IOException, InvalidConfigException,
            CannotModifyBaseConfigException, BadPermissionException {

        { // app 1
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
                            null, null,
                            config
                    )
            );
        }

        { // app 2
            JsonObject config = new JsonObject();
            TenantIdentifier tenantIdentifier = new TenantIdentifier(null, "a2", null);

            StorageLayer.getStorage(new TenantIdentifier(null, null, null), process.getProcess())
                    .modifyConfigToAddANewUserPoolForTesting(config, 2);

            Multitenancy.addNewOrUpdateAppOrTenant(
                    process.getProcess(),
                    new TenantIdentifier(null, null, null),
                    new TenantConfig(
                            tenantIdentifier,
                            new EmailPasswordConfig(true),
                            new ThirdPartyConfig(false, null),
                            new PasswordlessConfig(false),
                            null, null,
                            config
                    )
            );
        }

        { // app 3
            JsonObject config = new JsonObject();
            TenantIdentifier tenantIdentifier = new TenantIdentifier(null, "a3", null);

            StorageLayer.getStorage(new TenantIdentifier(null, null, null), process.getProcess())
                    .modifyConfigToAddANewUserPoolForTesting(config, 3);

            Multitenancy.addNewOrUpdateAppOrTenant(
                    process.getProcess(),
                    new TenantIdentifier(null, null, null),
                    new TenantConfig(
                            tenantIdentifier,
                            new EmailPasswordConfig(true),
                            new ThirdPartyConfig(false, null),
                            new PasswordlessConfig(false),
                            null, null,
                            config
                    )
            );
        }

    }

    private boolean callHello(TenantIdentifier tenantIdentifier, Main main)
            throws HttpResponseException, IOException {
        String response = HttpRequestForTesting.sendGETRequest(main, "",
                HttpRequestForTesting.getMultitenantUrl(tenantIdentifier, "/hello"),
                null, 1000, 1000, null,
                Utils.getCdiVersionStringLatestForTests(), null);
        return "Hello".equals(response);
    }

    private boolean callHello2(TenantIdentifier tenantIdentifier, Main main)
            throws HttpResponseException, IOException {
        String response = HttpRequestForTesting.sendGETRequest(main, "",
                HttpRequestForTesting.getMultitenantUrl(tenantIdentifier, "/"),
                null, 1000, 1000, null,
                Utils.getCdiVersionStringLatestForTests(), null);
        return "Hello".equals(response);
    }

    @Test
    public void testThatTheHelloAPIisRateLimited() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{EE_FEATURES.MULTI_TENANCY});
        process.startProcess();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        createApps(process);

        // Call 5 requests rapidly
        for (int i = 0; i < 5; i++) {
            assertTrue(callHello(new TenantIdentifier(null, "a1", null), process.getProcess()));
        }

        // 6th request fails
        assertFalse(callHello(new TenantIdentifier(null, "a1", null), process.getProcess()));

        // Should be able to call after pausing for 200ms
        Thread.sleep(201);
        assertTrue(callHello(new TenantIdentifier(null, "a1", null), process.getProcess()));

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testThatTheHelloAPIisRateLimitedPerApp() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{EE_FEATURES.MULTI_TENANCY});
        process.startProcess();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        createApps(process);

        // Call 5 requests rapidly
        for (int i = 0; i < 5; i++) {
            assertTrue(callHello(new TenantIdentifier(null, "a1", null), process.getProcess()));
        }

        // 6th request fails
        assertFalse(callHello(new TenantIdentifier(null, "a1", null), process.getProcess()));

        // But it should work for a different app
        for (int i = 0; i < 5; i++) {
            assertTrue(callHello(new TenantIdentifier(null, "a2", null), process.getProcess()));
            assertTrue(callHello(new TenantIdentifier(null, "a3", null), process.getProcess()));
        }

        // Now you are ratelimited on a2 and a3 apps
        assertFalse(callHello(new TenantIdentifier(null, "a2", null), process.getProcess()));
        assertFalse(callHello(new TenantIdentifier(null, "a3", null), process.getProcess()));

        // Should be able to call after pausing for 300ms
        Thread.sleep(300);
        assertTrue(callHello(new TenantIdentifier(null, "a1", null), process.getProcess()));
        assertTrue(callHello(new TenantIdentifier(null, "a2", null), process.getProcess()));
        assertTrue(callHello(new TenantIdentifier(null, "a3", null), process.getProcess()));

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testThatTheHelloAPIisRateLimited2() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{EE_FEATURES.MULTI_TENANCY});
        process.startProcess();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        createApps(process);

        // Call 5 requests rapidly
        for (int i = 0; i < 5; i++) {
            assertTrue(callHello2(new TenantIdentifier(null, null, null), process.getProcess()));
        }

        // 6th request fails
        assertFalse(callHello2(new TenantIdentifier(null, null, null), process.getProcess()));

        // Should be able to call after pausing for 200ms
        Thread.sleep(201);
        assertTrue(callHello2(new TenantIdentifier(null, null, null), process.getProcess()));

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }
}
