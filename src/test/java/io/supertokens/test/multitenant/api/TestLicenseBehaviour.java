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
import io.supertokens.test.httpRequest.HttpResponseException;
import io.supertokens.thirdparty.InvalidProviderConfigException;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.*;

public class TestLicenseBehaviour {
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
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));
    }

    private final String OPAQUE_KEY_WITH_MULTITENANCY_FEATURE = "ijaleljUd2kU9XXWLiqFYv5br8nutTxbyBqWypQdv2N-" +
            "BocoNriPrnYQd0NXPm8rVkeEocN9ayq0B7c3Pv-BTBIhAZSclXMlgyfXtlwAOJk=9BfESEleW6LyTov47dXu";

    @Test
    public void testAllowLicenseRemovalForCoreWithMultitenancy() throws Exception {
        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        TestMultitenancyAPIHelper.addLicense(OPAQUE_KEY_WITH_MULTITENANCY_FEATURE, process.getProcess());

        JsonObject coreConfig = new JsonObject();
        StorageLayer.getStorage(new TenantIdentifier(null, null, null), process.getProcess())
                .modifyConfigToAddANewUserPoolForTesting(coreConfig, 1);

        TestMultitenancyAPIHelper.createApp(
                process.getProcess(),
                new TenantIdentifier(null, null, null),
                "a1", true, true, true,
                coreConfig);

        TestMultitenancyAPIHelper.createTenant(
                process.getProcess(),
                new TenantIdentifier(null, "a1", null),
                "t1", true, true, true,
                coreConfig);

        TestMultitenancyAPIHelper.removeLicense(process.getProcess());

        // Sign up and get user info
        JsonObject userInfo = TestMultitenancyAPIHelper.epSignUp(new TenantIdentifier(null, "a1", "t1"),
                "user@example.com", "password", process.getProcess());
        JsonObject userInfo2 = TestMultitenancyAPIHelper.getEpUserById(new TenantIdentifier(null, "a1", "t1"),
                userInfo.get("id").getAsString(), process.getProcess());
        assertEquals(userInfo, userInfo2);
    }

    @Test
    public void testThatCreationOfNewTenantIsNotAllowedAfterLicenseRemoval() throws Exception {
        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL ||
                StorageLayer.isInMemDb(process.getProcess())) {
            return;
        }

        TestMultitenancyAPIHelper.addLicense(OPAQUE_KEY_WITH_MULTITENANCY_FEATURE, process.getProcess());

        JsonObject coreConfig = new JsonObject();
        StorageLayer.getStorage(new TenantIdentifier(null, null, null), process.getProcess())
                .modifyConfigToAddANewUserPoolForTesting(coreConfig, 1);

        TestMultitenancyAPIHelper.createApp(
                process.getProcess(),
                new TenantIdentifier(null, null, null),
                "a1", true, true, true,
                coreConfig);

        TestMultitenancyAPIHelper.createTenant(
                process.getProcess(),
                new TenantIdentifier(null, "a1", null),
                "t1", true, true, true,
                coreConfig);

        TestMultitenancyAPIHelper.removeLicense(process.getProcess());

        try {
            TestMultitenancyAPIHelper.createTenant(
                    process.getProcess(),
                    new TenantIdentifier(null, "a1", null),
                    "t2", true, true, true,
                    coreConfig);
            fail();
        } catch (HttpResponseException e) {
            assertEquals(
                    "Http error. Status Code: 402. Message: Cannot use feature: multi_tenancy, because the license " +
                            "key is missing, or doesn't have this feature enabled.",
                    e.getMessage());
        }
    }

    @Test
    public void testThatCoreCanRestartWithAllTheTenantsWithoutLicenseKey() throws Exception {
        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        if (StorageLayer.isInMemDb(process.getProcess())) {
            return;
        }

        TestMultitenancyAPIHelper.addLicense(OPAQUE_KEY_WITH_MULTITENANCY_FEATURE, process.getProcess());

        JsonObject coreConfig = new JsonObject();
        StorageLayer.getStorage(new TenantIdentifier(null, null, null), process.getProcess())
                .modifyConfigToAddANewUserPoolForTesting(coreConfig, 1);

        TestMultitenancyAPIHelper.createApp(
                process.getProcess(),
                new TenantIdentifier(null, null, null),
                "a1", true, true, true,
                coreConfig);

        TestMultitenancyAPIHelper.createTenant(
                process.getProcess(),
                new TenantIdentifier(null, "a1", null),
                "t1", true, true, true,
                coreConfig);

        JsonObject tenants = TestMultitenancyAPIHelper.listConnectionUriDomains(new TenantIdentifier(null, null, null),
                process.getProcess());

        TestMultitenancyAPIHelper.removeLicense(process.getProcess());

        // Restart the core
        process.kill(false);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));

        String[] args = {"../"};
        this.process = TestingProcessManager.start(args);
        process.startProcess();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        JsonObject tenants2 = TestMultitenancyAPIHelper.listConnectionUriDomains(new TenantIdentifier(null, null, null),
                process.getProcess());

        // Ensure all tenants are loaded back correctly
        assertEquals(tenants, tenants2);

        try {
            TestMultitenancyAPIHelper.createTenant(
                    process.getProcess(),
                    new TenantIdentifier(null, "a1", null),
                    "t2", true, true, true,
                    coreConfig);
            fail();
        } catch (HttpResponseException e) {
            assertEquals(
                    "Http error. Status Code: 402. Message: Cannot use feature: multi_tenancy, because the license " +
                            "key is missing, or doesn't have this feature enabled.",
                    e.getMessage());
        }
    }

    @Test
    public void testThatAddingThirdPartyConfigIsNotAllowedAfterLicenseRemoval() throws Exception {
        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL ||
                StorageLayer.isInMemDb(process.getProcess())) {
            return;
        }

        TestMultitenancyAPIHelper.addLicense(OPAQUE_KEY_WITH_MULTITENANCY_FEATURE, process.getProcess());

        JsonObject coreConfig = new JsonObject();
        StorageLayer.getStorage(new TenantIdentifier(null, null, null), process.getProcess())
                .modifyConfigToAddANewUserPoolForTesting(coreConfig, 1);

        TestMultitenancyAPIHelper.createApp(
                process.getProcess(),
                new TenantIdentifier(null, null, null),
                "a1", true, true, true,
                coreConfig);

        TestMultitenancyAPIHelper.createTenant(
                process.getProcess(),
                new TenantIdentifier(null, "a1", null),
                "t1", true, true, true,
                coreConfig);

        TestMultitenancyAPIHelper.removeLicense(process.getProcess());

        try {
            TestMultitenancyAPIHelper.addOrUpdateThirdPartyProviderConfig(
                    new TenantIdentifier(null, "a1", "t1"),
                    new ThirdPartyConfig.Provider(
                            "google", "Google", null, null, null, null, null, null, null, null, null, null, null, null
                    ),
                    process.getProcess());
            fail();
        } catch (HttpResponseException e) {
            assertEquals(
                    "Http error. Status Code: 402. Message: Cannot use feature: multi_tenancy, because the license " +
                            "key is missing, or doesn't have this feature enabled.",
                    e.getMessage());
        }
    }

    @Test
    public void testThatAssociationOfUserWithAnotherTenantIsNotAllowedAfterLicenseRemoval() throws Exception {
        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL ||
                StorageLayer.isInMemDb(process.getProcess())) {
            return;
        }

        TestMultitenancyAPIHelper.addLicense(OPAQUE_KEY_WITH_MULTITENANCY_FEATURE, process.getProcess());

        JsonObject coreConfig = new JsonObject();
        StorageLayer.getStorage(new TenantIdentifier(null, null, null), process.getProcess())
                .modifyConfigToAddANewUserPoolForTesting(coreConfig, 1);

        TestMultitenancyAPIHelper.createApp(
                process.getProcess(),
                new TenantIdentifier(null, null, null),
                "a1", true, true, true,
                coreConfig);

        TestMultitenancyAPIHelper.createTenant(
                process.getProcess(),
                new TenantIdentifier(null, "a1", null),
                "t1", true, true, true,
                coreConfig);

        TestMultitenancyAPIHelper.createTenant(
                process.getProcess(),
                new TenantIdentifier(null, "a1", null),
                "t2", true, true, true,
                coreConfig);

        TestMultitenancyAPIHelper.removeLicense(process.getProcess());

        JsonObject userInfo = TestMultitenancyAPIHelper.epSignUp(new TenantIdentifier(null, "a1", "t1"),
                "user@example.com", "password", process.getProcess());

        try {
            TestMultitenancyAPIHelper.associateUserToTenant(new TenantIdentifier(null, "a1", "t2"),
                    userInfo.get("id").getAsString(), process.getProcess());
            fail();
        } catch (HttpResponseException e) {
            assertEquals(
                    "Http error. Status Code: 402. Message: Cannot use feature: multi_tenancy, because the license " +
                            "key is missing, or doesn't have this feature enabled.",
                    e.getMessage());
        }
    }

    @Test
    public void testUpdationOfBaseTenantIsAllowedWithoutLicense() throws Exception {
        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        TestMultitenancyAPIHelper.addLicense(OPAQUE_KEY_WITH_MULTITENANCY_FEATURE, process.getProcess());

        JsonObject coreConfig = new JsonObject();
        StorageLayer.getStorage(new TenantIdentifier(null, null, null), process.getProcess())
                .modifyConfigToAddANewUserPoolForTesting(coreConfig, 1);

        TestMultitenancyAPIHelper.createApp(
                process.getProcess(),
                new TenantIdentifier(null, null, null),
                "a1", true, true, true,
                coreConfig);

        TestMultitenancyAPIHelper.createTenant(
                process.getProcess(),
                new TenantIdentifier(null, "a1", null),
                "t1", true, true, true,
                coreConfig);

        TestMultitenancyAPIHelper.removeLicense(process.getProcess());

        JsonObject config = new JsonObject();
        TestMultitenancyAPIHelper.createConnectionUriDomain(process.main, new TenantIdentifier(null, null, null), null,
                true, true, true, new JsonObject());
        TestMultitenancyAPIHelper.addOrUpdateThirdPartyProviderConfig(
                new TenantIdentifier(null, null, null),
                new ThirdPartyConfig.Provider(
                        "google", "Google", null, null, null, null, null, null, null, null, null, null, null, null
                ),
                process.getProcess());
    }
}
