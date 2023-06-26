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
import io.supertokens.Main;
import io.supertokens.emailpassword.EmailPassword;
import io.supertokens.featureflag.EE_FEATURES;
import io.supertokens.featureflag.FeatureFlagTestContent;
import io.supertokens.mfa.Mfa;
import io.supertokens.multitenancy.Multitenancy;
import io.supertokens.pluginInterface.mfa.MfaStorage;
import io.supertokens.pluginInterface.multitenancy.*;
import org.junit.Test;

import static org.junit.Assert.assertNotNull;

public class MfaRecipeTest extends MfaTestBase {
    @Test
    public void enableFactorTests() throws Exception {
        TestSetupResult result = initSteps();
        if (result == null) {
            return;
        }
        MfaStorage storage = result.storage;
        Main main = result.process.main;
        TenantIdentifierWithStorage tid = new TenantIdentifierWithStorage(null, null, null, storage);

        boolean insertedF1 = Mfa.enableFactor(tid, main, "userId", "f1");
        assert insertedF1;

        String[] factors = Mfa.listFactors(tid, main, "userId");

        assert factors != null;
        assert factors.length == 1;
        assert factors[0].equals("f1");

        boolean insertedF1Again = Mfa.enableFactor(tid, main, "userId", "f1");
        boolean insertedF2 = Mfa.enableFactor(tid, main, "userId", "f2");

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
        Main main = result.process.main;
        TenantIdentifierWithStorage tid = new TenantIdentifierWithStorage(null, null, null, storage);

        assert Mfa.enableFactor(tid, main, "userId", "f3") == true;
        assert Mfa.enableFactor(tid, main, "userId", "f1") == true;
        assert Mfa.enableFactor(tid, main, "userId", "f2") == true;

        assert Mfa.disableFactor(tid, main, "userId", "f2") == true;

        String[] factors = Mfa.listFactors(tid, main, "userId");

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
        Main main = result.process.main;
        TenantIdentifierWithStorage tid = new TenantIdentifierWithStorage(null, null, null, storage);

        assert Mfa.enableFactor(tid, main, "userId", "f1") == true;
        assert Mfa.enableFactor(tid, main, "userId", "f2") == true;

        assert Mfa.disableFactor(tid, main, "non-existent-userId", "f1") == false; // userId does not exist
        assert Mfa.disableFactor(tid, main, "userId", "f2") == true; // f2 was enabled
        assert Mfa.disableFactor(tid, main, "userId", "f3") == false; // f3 was never enabled

        String[] factors = storage.listFactors(tid, "userId");

        assert factors != null;
        assert factors.length == 1;
        assert factors[0].equals("f1");

        factors = Mfa.listFactors(tid, main, "non-existent-user");
        assert factors.length == 0;
    }
}
