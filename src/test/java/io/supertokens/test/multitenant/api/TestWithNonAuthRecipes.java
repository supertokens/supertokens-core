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

import com.google.gson.JsonObject;
import io.supertokens.ProcessState;
import io.supertokens.emailpassword.EmailPassword;
import io.supertokens.featureflag.EE_FEATURES;
import io.supertokens.featureflag.FeatureFlagTestContent;
import io.supertokens.featureflag.exceptions.FeatureNotEnabledException;
import io.supertokens.multitenancy.exception.BadPermissionException;
import io.supertokens.multitenancy.exception.CannotModifyBaseConfigException;
import io.supertokens.pluginInterface.Storage;
import io.supertokens.pluginInterface.StorageUtils;
import io.supertokens.pluginInterface.authRecipe.AuthRecipeUserInfo;
import io.supertokens.pluginInterface.exceptions.InvalidConfigException;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifier;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.pluginInterface.usermetadata.sqlStorage.UserMetadataSQLStorage;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.test.TestingProcessManager;
import io.supertokens.test.Utils;
import io.supertokens.test.httpRequest.HttpResponseException;
import io.supertokens.thirdparty.InvalidProviderConfigException;
import io.supertokens.useridmapping.UserIdMapping;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.*;

public class TestWithNonAuthRecipes {
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
            InvalidConfigException, CannotModifyBaseConfigException, BadPermissionException, HttpResponseException {
        Utils.reset();

        String[] args = {"../"};

        this.process = TestingProcessManager.start(args);
        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{EE_FEATURES.MULTI_TENANCY});
        process.startProcess();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        JsonObject config = new JsonObject();
        StorageLayer.getBaseStorage(process.getProcess()).modifyConfigToAddANewUserPoolForTesting(config, 1);
        TestMultitenancyAPIHelper.createTenant(process.getProcess(), TenantIdentifier.BASE_TENANT, "t1", true, true,
                true, config);
        StorageLayer.getBaseStorage(process.getProcess()).modifyConfigToAddANewUserPoolForTesting(config, 2);
        TestMultitenancyAPIHelper.createTenant(process.getProcess(), TenantIdentifier.BASE_TENANT, "t2", true, true,
                true, config);
    }

    @Test
    public void testThatUserMetadataIsSavedInTheStorageWhereUserExists() throws Exception {
        TenantIdentifier t0 = new TenantIdentifier(null, null, null);
        Storage t0WithStorage = (StorageLayer.getStorage(t0, process.getProcess()));

        TenantIdentifier t1 = new TenantIdentifier(null, null, "t1");
        Storage t1WithStorage = (StorageLayer.getStorage(t1, process.getProcess()));

        // Create users
        AuthRecipeUserInfo user1 = EmailPassword.signUp(t0, t0WithStorage, process.getProcess(), "test@example.com", "password123");
        AuthRecipeUserInfo user2 = EmailPassword.signUp(t1, t1WithStorage, process.getProcess(), "test@example.com", "password123");

        UserIdMapping.populateExternalUserIdForUsers(t0WithStorage, new AuthRecipeUserInfo[]{user1});
        UserIdMapping.populateExternalUserIdForUsers(t1WithStorage, new AuthRecipeUserInfo[]{user2});

        // Check that get user by ID works fine
        JsonObject jsonUser1 = TestMultitenancyAPIHelper.getUserById(t0, user1.getSupertokensUserId(), process.getProcess());
        assertEquals(user1.toJson(), jsonUser1.get("user").getAsJsonObject());

        JsonObject jsonUser2 = TestMultitenancyAPIHelper.getUserById(t0, user2.getSupertokensUserId(), process.getProcess());
        assertEquals(user2.toJson(), jsonUser2.get("user").getAsJsonObject());

        JsonObject metadata = new JsonObject();
        metadata.addProperty("key", "value");

        {
            // Add metadata for user2 using t0 and ensure get user works fine
            TestMultitenancyAPIHelper.updateUserMetadata(t0, user2.getSupertokensUserId(), metadata, process.getProcess());

            jsonUser2 = TestMultitenancyAPIHelper.getUserById(t0, user2.getSupertokensUserId(), process.getProcess());
            assertEquals(user2.toJson(), jsonUser2.get("user").getAsJsonObject());

            try {
                TestMultitenancyAPIHelper.getUserById(t1, user2.getSupertokensUserId(),
                        process.getProcess());
                fail();
            } catch (HttpResponseException e) {
                assertEquals(403, e.statusCode);
            }
        }

        { // Add metadata using t1 results in 403
            try {
                TestMultitenancyAPIHelper.updateUserMetadata(t1, user1.getSupertokensUserId(), metadata, process.getProcess());
                fail();
            } catch (HttpResponseException e) {
                assertEquals(403, e.statusCode);
            }
        }

        {
            // Add metadata for user1 using t0 and ensure get user works fine
            TestMultitenancyAPIHelper.updateUserMetadata(t0, user1.getSupertokensUserId(), metadata, process.getProcess());

            jsonUser1 = TestMultitenancyAPIHelper.getUserById(t0, user1.getSupertokensUserId(), process.getProcess());
            assertEquals(user1.toJson(), jsonUser1.get("user").getAsJsonObject());

            try {
                TestMultitenancyAPIHelper.getUserById(t1, user1.getSupertokensUserId(), process.getProcess());
                fail();
            } catch (HttpResponseException e) {
                assertEquals(403, e.statusCode);
            }
        }

        UserMetadataSQLStorage t0UserMetadataStorage = StorageUtils.getUserMetadataStorage(t0WithStorage);
        UserMetadataSQLStorage t1UserMetadataStorage = StorageUtils.getUserMetadataStorage(t1WithStorage);

        // Ensure that the metadata is saved in the correct storage
        assertNotNull(t0UserMetadataStorage.getUserMetadata(t0.toAppIdentifier(), user1.getSupertokensUserId())); // ensure t0 storage does not have user2's metadata
        assertNotNull(t1UserMetadataStorage.getUserMetadata(t0.toAppIdentifier(), user2.getSupertokensUserId())); // ensure t1 storage does not have user1's metadata

        // Ensure that the metadata is not stored in the wrong storage
        assertNull(t0UserMetadataStorage.getUserMetadata(t0.toAppIdentifier(), user2.getSupertokensUserId())); // ensure t0 storage does not have user2's metadata
        assertNull(t1UserMetadataStorage.getUserMetadata(t0.toAppIdentifier(), user1.getSupertokensUserId())); // ensure t1 storage does not have user1's metadata

        // Try deleting metadata
        try {
            TestMultitenancyAPIHelper.removeMetadata(t1, user1.getSupertokensUserId(), process.getProcess());
            fail();
        } catch (HttpResponseException e) {
            assertEquals(403, e.statusCode);
        }
        TestMultitenancyAPIHelper.removeMetadata(t0, user1.getSupertokensUserId(), process.getProcess());
        TestMultitenancyAPIHelper.removeMetadata(t0, user2.getSupertokensUserId(), process.getProcess());
        assertNull(t0UserMetadataStorage.getUserMetadata(t0.toAppIdentifier(), user1.getSupertokensUserId())); // ensure t0 storage does not have user2's metadata
        assertNull(t1UserMetadataStorage.getUserMetadata(t0.toAppIdentifier(), user2.getSupertokensUserId())); // ensure t1 storage does not have user1's metadata
    }

    @Test
    public void testThatUserRolesWorkWithDifferentTenantsOnDifferentStorages() throws Exception {
        TenantIdentifier t0 = new TenantIdentifier(null, null, null);
        Storage t0WithStorage = (StorageLayer.getStorage(t0, process.getProcess()));

        TenantIdentifier t1 = new TenantIdentifier(null, null, "t1");
        Storage t1WithStorage = (StorageLayer.getStorage(t1, process.getProcess()));

        // Create users
        AuthRecipeUserInfo user1 = EmailPassword.signUp(t0, t0WithStorage, process.getProcess(), "test@example.com", "password123");
        AuthRecipeUserInfo user2 = EmailPassword.signUp(t1, t1WithStorage, process.getProcess(), "test@example.com", "password123");

        UserIdMapping.populateExternalUserIdForUsers(t0WithStorage, new AuthRecipeUserInfo[]{user1});
        UserIdMapping.populateExternalUserIdForUsers(t1WithStorage, new AuthRecipeUserInfo[]{user2});


    }
}
