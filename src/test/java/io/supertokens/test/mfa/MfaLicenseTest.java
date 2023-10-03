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
import io.supertokens.featureflag.exceptions.FeatureNotEnabledException;
import io.supertokens.mfa.Mfa;
import io.supertokens.pluginInterface.mfa.MfaStorage;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifierWithStorage;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.test.httpRequest.HttpResponseException;
import org.junit.Test;

import java.util.HashMap;

import static org.junit.Assert.assertThrows;

public class MfaLicenseTest extends MfaTestBase {
    @Test
    public void testTotpWithoutLicense() throws Exception {
        TestSetupResult result = initSteps(false);
        if (result == null) {
            return;
        }

        if (StorageLayer.isInMemDb(result.process.getProcess())) {
            return;
        }

        Main main = result.process.getProcess();
        MfaStorage storage = result.storage;
        TenantIdentifierWithStorage tid = new TenantIdentifierWithStorage(null, null, null, storage);

        // Enable factor
        assertThrows(FeatureNotEnabledException.class, () -> {
            Mfa.enableFactor(tid, main, "userId", "f1");
        });
        // List factors
        assertThrows(FeatureNotEnabledException.class, () -> {
            Mfa.listFactors(tid, main, "user");
        });
        // Disable factor
        assertThrows(FeatureNotEnabledException.class, () -> {
            Mfa.disableFactor(tid, main, "userId", "f1");
        });

        // Try to create device via API:
        JsonObject body = new JsonObject();
        body.addProperty("userId", "user-id");
        body.addProperty("factor", "f1");

        HttpResponseException e = assertThrows(
                HttpResponseException.class,
                () -> {
                    enableFactorRequest(result.process, body);
                }
        );
        assert e.statusCode == 402;
        assert e.getMessage().contains("MFA feature is not enabled");


        // Try to list devices via API:
        HashMap<String, String> params = new HashMap<>();
        params.put("userId", "user-id");

        HttpResponseException e2 = assertThrows(
                HttpResponseException.class,
                () -> {
                    listFactorsRequest(result.process, params);
                }
        );
        assert e2.statusCode == 402;
        assert e2.getMessage().contains("MFA feature is not enabled");

        // Try to disable factor via API:
        body.addProperty("userId", "user-id");
        body.addProperty("factor", "f1");

        HttpResponseException e3 = assertThrows(
                HttpResponseException.class,
                () -> {
                    disableFactorRequest(result.process, body);
                }
        );
        assert e3.statusCode == 402;
        assert e3.getMessage().contains("MFA feature is not enabled");
    }


    @Test
    public void testTotpWithLicense() throws Exception {
        TestSetupResult result = initSteps(true);
        if (result == null) {
            return;
        }
        Main main = result.process.getProcess();
        MfaStorage storage = result.storage;
        TenantIdentifierWithStorage tid = new TenantIdentifierWithStorage(null, null, null, storage);

        // Enable factor
        boolean insertedFactor = Mfa.enableFactor(tid, main, "userId", "f1");
        assert insertedFactor;
        // List factors
        String[] factors = Mfa.listFactors(tid, main, "userId");
        assert factors.length == 1;
        assert factors[0].equals("f1");
        // Disable factor
        boolean removedFactor = Mfa.disableFactor(tid, main, "userId", "f1");
        assert removedFactor;

        // Try to enable factor via API:
        JsonObject body = new JsonObject();
        body.addProperty("userId", "user-id");
        body.addProperty("factor", "f1");

        JsonObject res = enableFactorRequest(result.process, body);
        assert res.get("status").getAsString().equals("OK");
        assert res.get("didExist").getAsBoolean() == false;

        // Try to list factors via API:
        HashMap<String, String> params = new HashMap<>();
        params.put("userId", "user-id");

        JsonObject res2 = listFactorsRequest(result.process, params);
        assert res2.get("status").getAsString().equals("OK");
        assert res2.get("factors").getAsJsonArray().size() == 1;
        assert res2.get("factors").getAsJsonArray().get(0).getAsString().equals("f1");

        // Try to disable factor via API:
        body.addProperty("userId", "user-id");
        body.addProperty("factor", "f1");

        JsonObject res3 = disableFactorRequest(result.process, body);
        assert res3.get("status").getAsString().equals("OK");
        assert res3.get("didExist").getAsBoolean() == true;
    }
}
