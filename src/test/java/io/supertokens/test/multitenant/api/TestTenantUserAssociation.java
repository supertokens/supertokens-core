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
import io.supertokens.featureflag.exceptions.FeatureNotEnabledException;
import io.supertokens.multitenancy.exception.BadPermissionException;
import io.supertokens.multitenancy.exception.CannotModifyBaseConfigException;
import io.supertokens.pluginInterface.exceptions.InvalidConfigException;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifier;
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

public class TestTenantUserAssociation {
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

    private void createTenants() throws TenantOrAppNotFoundException, HttpResponseException, IOException {
        {
            JsonObject coreConfig = new JsonObject();
            StorageLayer.getStorage(new TenantIdentifier(null, null, null), process.getProcess())
                    .modifyConfigToAddANewUserPoolForTesting(coreConfig, 1);

            TestMultitenancyAPIHelper.createApp(
                    process.getProcess(),
                    new TenantIdentifier(null, null, null),
                    "a1", true, true, true,
                    coreConfig);
        }
        {
            JsonObject coreConfig = new JsonObject();
            StorageLayer.getStorage(new TenantIdentifier(null, null, null), process.getProcess())
                    .modifyConfigToAddANewUserPoolForTesting(coreConfig, 2);

            TestMultitenancyAPIHelper.createTenant(
                    process.getProcess(),
                    new TenantIdentifier(null, "a1", null),
                    "t1", true, true, true,
                    coreConfig);
        }
        {
            JsonObject coreConfig = new JsonObject();
            StorageLayer.getStorage(new TenantIdentifier(null, null, null), process.getProcess())
                    .modifyConfigToAddANewUserPoolForTesting(coreConfig, 2);

            TestMultitenancyAPIHelper.createTenant(
                    process.getProcess(),
                    new TenantIdentifier(null, "a1", null),
                    "t2", true, true, true,
                    coreConfig);
        }
    }

    @Test
    public void testUserAssociationWorksForEmailPasswordUser() throws Exception {
        createTenants();
        JsonObject user = TestMultitenancyAPIHelper.epSignUp(new TenantIdentifier(null, "a1", "t1"), "user@example.com", "password", process.getProcess());
        String userId = user.get("id").getAsString();

        JsonObject response = TestMultitenancyAPIHelper.associateUserToTenant(new TenantIdentifier(null, "a1", "t2"), userId, process.getProcess());
        assertEquals("OK", response.getAsJsonPrimitive("status").getAsString());
        assertFalse(response.get("wasAlreadyAssociated").getAsBoolean());

        response = TestMultitenancyAPIHelper.associateUserToTenant(new TenantIdentifier(null, "a1", "t2"), userId, process.getProcess());
        assertEquals("OK", response.getAsJsonPrimitive("status").getAsString());
        assertTrue(response.get("wasAlreadyAssociated").getAsBoolean());
    }

    @Test
    public void testUserDisassociationWorks() throws Exception {
        createTenants();
        JsonObject user = TestMultitenancyAPIHelper.epSignUp(new TenantIdentifier(null, "a1", "t1"), "user@example.com", "password", process.getProcess());
        String userId = user.get("id").getAsString();

        JsonObject response = TestMultitenancyAPIHelper.associateUserToTenant(new TenantIdentifier(null, "a1", "t2"), userId, process.getProcess());
        assertEquals("OK", response.getAsJsonPrimitive("status").getAsString());
        assertFalse(response.get("wasAlreadyAssociated").getAsBoolean());

        response = TestMultitenancyAPIHelper.disassociateUserFromTenant(new TenantIdentifier(null, "a1", "t2"), userId, process.getProcess());
        assertEquals("OK", response.getAsJsonPrimitive("status").getAsString());
        assertTrue(response.get("wasAssociated").getAsBoolean());

        response = TestMultitenancyAPIHelper.disassociateUserFromTenant(new TenantIdentifier(null, "a1", "t2"), userId, process.getProcess());
        assertEquals("OK", response.getAsJsonPrimitive("status").getAsString());
        assertFalse(response.get("wasAssociated").getAsBoolean());

        response = TestMultitenancyAPIHelper.associateUserToTenant(new TenantIdentifier(null, "a1", "t2"), userId, process.getProcess());
        assertEquals("OK", response.getAsJsonPrimitive("status").getAsString());
        assertFalse(response.get("wasAlreadyAssociated").getAsBoolean());
    }

    @Test
    public void testDisassociateFromAllTenantsAndThenAssociateWithATenantWorks() throws Exception {
        createTenants();
        JsonObject user = TestMultitenancyAPIHelper.epSignUp(new TenantIdentifier(null, "a1", "t1"), "user@example.com", "password", process.getProcess());
        String userId = user.get("id").getAsString();

        JsonObject response = TestMultitenancyAPIHelper.disassociateUserFromTenant(new TenantIdentifier(null, "a1", "t1"), userId, process.getProcess());
        assertEquals("OK", response.getAsJsonPrimitive("status").getAsString());
        assertTrue(response.get("wasAssociated").getAsBoolean());

        response = TestMultitenancyAPIHelper.associateUserToTenant(new TenantIdentifier(null, "a1", "t2"), userId, process.getProcess());
        assertEquals("OK", response.getAsJsonPrimitive("status").getAsString());
        assertFalse(response.get("wasAlreadyAssociated").getAsBoolean());
    }

    @Test
    public void testAssociateOnDifferentStorageIsNotPossible() throws Exception {
        createTenants();
        JsonObject user = TestMultitenancyAPIHelper.epSignUp(new TenantIdentifier(null, "a1", "t1"), "user@example.com", "password", process.getProcess());
        String userId = user.get("id").getAsString();

        JsonObject response = TestMultitenancyAPIHelper.associateUserToTenant(new TenantIdentifier(null, "a1", null), userId, process.getProcess());
        assertEquals("UNKNOWN_USER_ID_ERROR", response.get("status").getAsString());
    }
}
