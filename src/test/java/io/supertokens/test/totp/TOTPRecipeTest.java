/*
 *    Copyright (c) 2021, VRAI Labs and/or its affiliates. All rights reserved.
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

import static org.junit.Assert.assertNotNull; // Not sure about this
import static org.junit.Assert.assertThrows;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import io.supertokens.test.Utils;
import io.supertokens.Main;
import io.supertokens.ProcessState;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.test.TestingProcessManager;

import io.supertokens.totp.Totp;
import io.supertokens.totp.exceptions.InvalidTotpException;
import io.supertokens.pluginInterface.totp.TOTPStorage;
import io.supertokens.pluginInterface.totp.exception.DeviceAlreadyExistsException;
import io.supertokens.pluginInterface.totp.exception.TotpNotEnabledException;
import io.supertokens.pluginInterface.totp.exception.UnknownDeviceException;

public class TOTPRecipeTest {

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

    public TestSetupResult setup() throws InterruptedException {
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
    public void createDevice() throws Exception {
        TestSetupResult result = setup();
        Main main = result.process.getProcess();

        // Create device
        String secret = Totp.createDevice(main, "userId", "deviceName", 1, 30);
        assert secret != "";

        // Create same device again (should fail)
        assertThrows(DeviceAlreadyExistsException.class, () -> Totp.createDevice(main, "userId", "deviceName", 1, 30));
    }

    public void triggerRateLimit(Main main) throws Exception {
        for (int i = 0; i < 4; i++) {
            assertThrows(
                    InvalidTotpException.class,
                    () -> Totp.verifyCode(main, "user", "wrong-code", true));
        }

        // 5th attempt should fail with rate limiting error:
        assertThrows(
                InvalidTotpException.class,
                () -> Totp.verifyCode(main, "user", "XXXX-code", true));
    }

    @Test
    public void createDeviceAndVerifyCode() throws Exception {
        TestSetupResult result = setup();
        Main main = result.process.getProcess();

        // Create device
        String secret = Totp.createDevice(main, "userId", "deviceName", 1, 30);

        // Try login with non-existent user:
        assertThrows(TotpNotEnabledException.class,
                () -> Totp.verifyCode(main, "non-existent-user", "XXXX-code", true));

        // Try login with invalid code:
        assertThrows(InvalidTotpException.class,
                () -> Totp.verifyCode(main, "user", "invalid-code", true));

        // Try login with with unverified device:
        assertThrows(InvalidTotpException.class,
                () -> Totp.verifyCode(main, "user", "XXXX-code", false));

        // Successfully login:
        Totp.verifyCode(main, "user", "XXXX-code", true);
        // Now try again with same code:
        assertThrows(
                InvalidTotpException.class,
                () -> Totp.verifyCode(main, "user", "XXXX-code", true));

        // Trigger rate limiting and fix it with a correct code:
        {
            triggerRateLimit(main);
            // Using a correct code should fix the rate limiting:
            Totp.verifyCode(main, "user", "XXXX-code", true);
        }

        // Trigger rate limiting and fix it with cronjob (runs every 1 hour)
        {
            triggerRateLimit(main);
            // Run cronjob:
            // Totp.runCron(main);
            Totp.verifyCode(main, "user", "XXXX-code", true);
        }
    }

    @Test
    public void createAndVerifyDevice() throws Exception {
        TestSetupResult result = setup();
        Main main = result.process.getProcess();

        // Create device
        // FIXME: Use secret to generate actual TOTP code
        String secret = Totp.createDevice(main, "userId", "deviceName", 1, 30);

        // Try verify non-existent user:
        assertThrows(TotpNotEnabledException.class,
                () -> Totp.verifyDevice(main, "non-existent-user", "deviceName", "XXXX"));

        // Try verify non-existent device
        assertThrows(UnknownDeviceException.class,
                () -> Totp.verifyDevice(main, "userId", "non-existent-device", "XXXX"));

        // Verify device with wrong code
        assertThrows(InvalidTotpException.class, () -> Totp.verifyDevice(main, "userId", "deviceName", "wrong-code"));

        // Verify device with correct code
        boolean deviceAlreadyVerified = Totp.verifyDevice(main, "userId", "deviceName", "XXXX");
        assert !deviceAlreadyVerified;

        // Verify again with same correct code:
        assertThrows(InvalidTotpException.class, () -> Totp.verifyDevice(main, "userId", "deviceName", "XXXX"));

        // Verify again with new correct code:
        deviceAlreadyVerified = Totp.verifyDevice(main, "userId", "deviceName", "XXXX-new");
        assert deviceAlreadyVerified;

        // Verify again with wrong code
        assertThrows(InvalidTotpException.class, () -> Totp.verifyDevice(main, "userId", "deviceName", "wrong-code"));

        result.process.kill();
        assertNotNull(result.process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

}
