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

package io.supertokens.test.totp;

import com.google.gson.JsonObject;
import io.supertokens.Main;
import io.supertokens.ProcessState;
import io.supertokens.featureflag.EE_FEATURES;
import io.supertokens.featureflag.FeatureFlagTestContent;
import io.supertokens.featureflag.exceptions.FeatureNotEnabledException;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.pluginInterface.totp.TOTPDevice;
import io.supertokens.pluginInterface.totp.TOTPStorage;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.test.TestingProcessManager;
import io.supertokens.test.Utils;
import io.supertokens.test.httpRequest.HttpRequestForTesting;
import io.supertokens.test.httpRequest.HttpResponseException;
import io.supertokens.totp.Totp;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import static io.supertokens.test.totp.TOTPRecipeTest.generateTotpCode;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;

public class TotpLicenseTest {
    public final static String OPAQUE_KEY_WITH_MFA_FEATURE = "Qk8olVa=v-9PU" +
            "=snnUFMF4ihMCx4zVBOO6Jd7Nrg6Cg5YyFliEj252ADgpwEpDLfFowA0U5OyVo3XL" +
            "=U4FMft2HDHCDGg9hWD4iwQQiyjMRi6Mu03CVbAxIkNGaXtJ53";

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

    public class TestSetupResult {
        public TOTPStorage storage;
        public TestingProcessManager.TestingProcess process;

        public TestSetupResult(TOTPStorage storage, TestingProcessManager.TestingProcess process) {
            this.storage = storage;
            this.process = process;
        }
    }

    public TestSetupResult defaultInit() throws InterruptedException {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return null;
        }
        TOTPStorage storage = (TOTPStorage) StorageLayer.getStorage(process.getProcess());

        return new TestSetupResult(storage, process);
    }

    @Test
    public void testTotpWithoutLicense() throws Exception {
        TestSetupResult result = defaultInit();
        if (result == null) {
            return;
        }
        if (StorageLayer.isInMemDb(result.process.getProcess())) {
            return;
        }
        Main main = result.process.getProcess();

        // Create device
        assertThrows(FeatureNotEnabledException.class, () -> {
            Totp.registerDevice(main, "user", "device1", 1, 30);
        });
        // Verify code
        assertThrows(FeatureNotEnabledException.class, () -> {
            Totp.verifyCode(main, "user", "device1");
        });

        // Try to create device via API:
        JsonObject body = new JsonObject();
        body.addProperty("userId", "user-id");
        body.addProperty("deviceName", "d1");
        body.addProperty("skew", 0);
        body.addProperty("period", 30);


        HttpResponseException e = assertThrows(
                HttpResponseException.class,
                () -> {
                    HttpRequestForTesting.sendJsonPOSTRequest(
                            result.process.getProcess(),
                            "",
                            "http://localhost:3567/recipe/totp/device",
                            body,
                            1000,
                            1000,
                            null,
                            Utils.getCdiVersionStringLatestForTests(),
                            "totp");
                }
        );
        assert e.statusCode == 402;
        assert e.getMessage().contains("MFA feature is not enabled");


        // Try to verify code via API:
        JsonObject body2 = new JsonObject();
        body2.addProperty("userId", "user-id");
        body2.addProperty("totp", "123456");


        HttpResponseException e2 = assertThrows(
                HttpResponseException.class,
                () -> {
                    HttpRequestForTesting.sendJsonPOSTRequest(
                            result.process.getProcess(),
                            "",
                            "http://localhost:3567/recipe/totp/verify",
                            body2,
                            1000,
                            1000,
                            null,
                            Utils.getCdiVersionStringLatestForTests(),
                            "totp");
                }
        );
        assert e2.statusCode == 402;
        assert e2.getMessage().contains("MFA feature is not enabled");
    }


    @Test
    public void testTotpWithLicense() throws Exception {
        TestSetupResult result = defaultInit();
        if (result == null) {
            return;
        }
        FeatureFlagTestContent.getInstance(result.process.main)
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{EE_FEATURES.MFA});

        Main main = result.process.getProcess();

        // Create device
        TOTPDevice device = Totp.registerDevice(main, "user", "device1", 1, 30);
        // Verify device
        String code = generateTotpCode(main, device, 0);
        Totp.verifyDevice(main, device.userId, device.deviceName, code);
        // Verify code
        Thread.sleep(1);
        String nextCode = generateTotpCode(main, device, 1);
        Totp.verifyCode(main, "user", nextCode);
    }


}
