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

package io.supertokens.test.mfa;

import com.google.gson.JsonObject;
import io.supertokens.emailpassword.EmailPassword;
import io.supertokens.featureflag.EE_FEATURES;
import io.supertokens.featureflag.FeatureFlagTestContent;
import io.supertokens.multitenancy.Multitenancy;
import io.supertokens.pluginInterface.mfa.MfaStorage;
import io.supertokens.pluginInterface.multitenancy.*;
import org.junit.Test;

import static org.junit.Assert.assertNotNull;

public class MfaStorageTest extends MfaTestBase {

    @Test
    public void enableFactorTests() throws Exception {
        TestSetupResult result = initSteps();
        if (result == null) {
            return;
        }
        MfaStorage storage = result.storage;
        TenantIdentifier tid = new TenantIdentifier(null, null, null);

        boolean insertedF1 = storage.enableFactor(tid, "userId", "f1");
        assert insertedF1;

        String[] factors = storage.listFactors(tid, "userId");

        assert factors != null;
        assert factors.length == 1;
        assert factors[0].equals("f1");

        boolean insertedF1Again = storage.enableFactor(tid, "userId", "f1");
        boolean insertedF2 = storage.enableFactor(tid, "userId", "f2");

        assert !insertedF1Again;
        assert insertedF2;
    }

    @Test
    public void listFactorsTest() throws Exception {
        TestSetupResult result = initSteps();
        if (result == null) {
            return;
        }
        MfaStorage storage = result.storage;
        TenantIdentifier tid = new TenantIdentifier(null, null, null);

        assert storage.enableFactor(tid, "userId", "f1") == true;
        assert storage.enableFactor(tid, "userId", "f2") == true;
        assert storage.enableFactor(tid, "userId", "f3") == true;

        assert storage.disableFactor(tid, "userId", "f2") == true;

        String[] factors = storage.listFactors(tid, "userId");

        assert factors != null;
        assert factors.length == 2;
        assert factors[0].equals("f1");
        assert factors[1].equals("f3");
    }

    @Test
    public void disableFactorsTest() throws Exception {
        TestSetupResult result = initSteps();
        if (result == null) {
            return;
        }
        MfaStorage storage = result.storage;
        TenantIdentifier tid = new TenantIdentifier(null, null, null);

        assert storage.enableFactor(tid, "userId", "f1") == true;
        assert storage.enableFactor(tid, "userId", "f2") == true;

        assert storage.disableFactor(tid, "non-existent-userId", "f1") == false; // userId does not exist
        assert storage.disableFactor(tid, "userId", "f2") == true; // f2 was enabled
        assert storage.disableFactor(tid, "userId", "f3") == false; // f3 was never enabled

        String[] factors = storage.listFactors(tid, "userId");

        assert factors != null;
        assert factors.length == 1;
        assert factors[0].equals("f1");

        factors = storage.listFactors(tid, "non-existent-user");
        assert factors.length == 0;
    }


    @Test
    public void deleteUserTest() throws Exception {
        TestSetupResult result = initSteps();
        if (result == null) {
            return;
        }
        MfaStorage storage = result.storage;
        TenantIdentifier tid = new TenantIdentifier(null, null, null);

        assert storage.enableFactor(tid, "user1", "f1") == true;
        assert storage.enableFactor(tid, "user1", "f2") == true;

        assert storage.enableFactor(tid, "user2", "f1") == true;
        assert storage.enableFactor(tid, "user2", "f3") == true;

        assert storage.deleteMfaInfoForUser(tid.toAppIdentifier(), "non-existent-user") == false;
        assert storage.deleteMfaInfoForUser(tid.toAppIdentifier(), "user2") == true;

        String[] factors = storage.listFactors(tid, "user2");
        assert factors.length == 0;

        factors = storage.listFactors(tid, "user1");

        assert factors.length == 2;
        assert factors[0].equals("f1");
        assert factors[1].equals("f2");
    }


    @Test
    public void deleteUserFromTenantTest() throws Exception {
        TestSetupResult result = initSteps();
        if (result == null) {
            return;
        }

        FeatureFlagTestContent.getInstance(result.process.main)
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{EE_FEATURES.MFA, EE_FEATURES.MULTI_TENANCY});

        MfaStorage mfaStorage = result.storage;

        TenantIdentifierWithStorage publicTenant = new TenantIdentifierWithStorage(null, null, null, result.storage);
        TenantIdentifierWithStorage privateTenant = new TenantIdentifierWithStorage(null, null, "t1", result.storage);

        TenantConfig privateTenantConfig = new TenantConfig(privateTenant, new EmailPasswordConfig(true), new ThirdPartyConfig(true, null), new PasswordlessConfig(true), new JsonObject());

        Multitenancy.addNewOrUpdateAppOrTenant(
                result.process.main,
                privateTenantConfig,
                false
        );

        // we will use the same userId for both tenants
        String userId = EmailPassword.signUp(
                privateTenant,
                result.process.main,
                "user@example.com",
                "password"
        ).id;

        // Iterate over all both tenants and enable the same set of factors for the same user ID
        for (TenantIdentifierWithStorage tid : new TenantIdentifierWithStorage[]{publicTenant, privateTenant}) {
            assert mfaStorage.enableFactor(tid, userId, "f1") == true;
            assert mfaStorage.enableFactor(tid, userId, "f2") == true;
        }

        // Delete private tenant user
        assert mfaStorage.deleteMfaInfoForUser(privateTenant, userId) == true;

        // Deleting user from one tenant shouldn't affect others:
        assert mfaStorage.listFactors(privateTenant, userId).length == 0;
        assert mfaStorage.listFactors(publicTenant, userId).length == 2;

         String userEmail = EmailPassword.signIn(privateTenant, result.process.main, "user@example.com", "password").email;
         assert userEmail.equals("user@example.com"); // Use should still exist in the private tenant since we have only disabled MFA related info

        // Deleting from non existent user should return false:
        assert mfaStorage.deleteMfaInfoForUser(privateTenant, "non-existent-user") == false;
    }

}
