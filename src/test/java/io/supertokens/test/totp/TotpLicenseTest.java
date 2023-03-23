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

import io.supertokens.Main;
import io.supertokens.ProcessState;
import io.supertokens.featureflag.FeatureFlag;
import io.supertokens.featureflag.exceptions.FeatureNotEnabledException;
import io.supertokens.featureflag.exceptions.InvalidLicenseKeyException;
import io.supertokens.httpRequest.HttpResponseException;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.totp.TOTPDevice;
import io.supertokens.pluginInterface.totp.TOTPStorage;
import io.supertokens.pluginInterface.totp.exception.DeviceAlreadyExistsException;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.test.TestingProcessManager;
import io.supertokens.test.Utils;
import io.supertokens.totp.Totp;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import java.io.IOException;

import static io.supertokens.test.totp.TOTPRecipeTest.generateTotpCode;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;

public class TotpLicenseTest {
    public final static String OPAQUE_KEY_WITH_TOTP_FEATURE = "pXhNK=nYiEsb6gJEOYP2kIR6M0kn4XLvNqcwT1XbX8xHtm44K-lQfGCbaeN0Ieeza39fxkXr=tiiUU=DXxDH40Y=4FLT4CE-rG1ETjkXxO4yucLpJvw3uSegPayoISGL";

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
        String[] args = { "../" };

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            assert (false);
        }
        TOTPStorage storage = StorageLayer.getTOTPStorage(process.getProcess());

        return new TestSetupResult(storage, process);
    }

    @Test
    public void testTotpWithoutLicense() throws Exception {
        TestSetupResult result = defaultInit();
        Main main = result.process.getProcess();

        // Create device
        assertThrows(FeatureNotEnabledException.class, () -> {
            Totp.registerDevice(main, "user", "device1", 1, 30);
        });
        // Verify code
        assertThrows(FeatureNotEnabledException.class, () -> {
            Totp.verifyCode(main, "user", "device1", true);
        });
    }

    @Test
    public void testTotpWithLicense() throws Exception {
        TestSetupResult result = defaultInit();
        FeatureFlag.getInstance(result.process.main).setLicenseKeyAndSyncFeatures(TotpLicenseTest.OPAQUE_KEY_WITH_TOTP_FEATURE);
        Main main = result.process.getProcess();

        // Create device
        TOTPDevice device = Totp.registerDevice(main, "user", "device1", 1, 30);
        // Verify code
        String code = generateTotpCode(main, device);
        Totp.verifyCode(main, "user", code, true);
    }


}
