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

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;

import java.io.IOException;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import io.supertokens.test.Utils;
import io.supertokens.Main;
import io.supertokens.ProcessState;
import io.supertokens.config.Config;
import io.supertokens.config.CoreConfig;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.test.TestingProcessManager;

import io.supertokens.totp.Totp;
import io.supertokens.totp.exceptions.InvalidTotpException;
import io.supertokens.totp.exceptions.LimitReachedException;
import io.supertokens.pluginInterface.totp.TOTPDevice;
import io.supertokens.pluginInterface.totp.TOTPStorage;
import io.supertokens.pluginInterface.totp.TOTPUsedCode;
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

    public TestSetupResult setup() throws InterruptedException, IOException {
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
        String secret = Totp.registerDevice(main, "user", "device1", 1, 30);
        assert secret != "";

        // Create same device again (should fail)
        assertThrows(DeviceAlreadyExistsException.class,
                () -> Totp.registerDevice(main, "user", "device1", 1, 30));
    }

    @Test
    public void createDeviceAndVerifyCode() throws Exception {
        TestSetupResult result = setup();
        Main main = result.process.getProcess();

        // Create device
        String secret = Totp.registerDevice(main, "user", "device", 1, 30);

        // Try login with non-existent user:
        assertThrows(TotpNotEnabledException.class,
                () -> Totp.verifyCode(main, "non-existent-user", "any-code", true));

        // {Code: [INVALID, VALID]} * {Devices: [VERIFIED_ONLY, ALL]}

        // Invalid code & allowUnverifiedDevice = true:
        assertThrows(InvalidTotpException.class,
                () -> Totp.verifyCode(main, "user", "invalid-code", true));

        // Invalid code & allowUnverifiedDevice = false:
        assertThrows(InvalidTotpException.class,
                () -> Totp.verifyCode(main, "user", "invalid-code", false));

        // Valid code & allowUnverifiedDevice = false:
        assertThrows(
                InvalidTotpException.class,
                () -> Totp.verifyCode(main, "user", Totp.generateTotpCode(main, "user", "device"), false));

        // Valid code & allowUnverifiedDevice = true (Success):
        String validCode = Totp.generateTotpCode(main, "user", "device");
        Totp.verifyCode(main, "user", validCode, true);

        // Now try again with same code:
        assertThrows(
                InvalidTotpException.class,
                () -> Totp.verifyCode(main, "user", validCode, true));

        // Use a new valid code:
        String newValidCode = Totp.generateTotpCode(main, "user", "device");
        Totp.verifyCode(main, "user", newValidCode, true);
    }

    public void triggerAndCheckRateLimit(Main main, String userId, String deviceName) throws Exception {
        int N = Config.getConfig(main).getTotpMaxAttempts();
        // First N attempts should fail with invalid code:
        // This is to trigger rate limiting
        for (int i = 0; i < N; i++) {
            String code = "invalid-code-" + i;
            assertThrows(
                    InvalidTotpException.class,
                    () -> Totp.verifyCode(main, "user", code, true));
        }

        // Any kind of attempt after this should fail with rate limiting error.
        // This should happen until rate limiting cooldown happens:
        assertThrows(
                LimitReachedException.class,
                () -> Totp.verifyCode(main, "user", "invalid-code-N+1", true));
        assertThrows(
                LimitReachedException.class,
                () -> Totp.verifyCode(main, "user", Totp.generateTotpCode(main, userId, deviceName), true));
        assertThrows(
                LimitReachedException.class,
                () -> Totp.verifyCode(main, "user", "invalid-code-N+2", true));
    }

    @Test
    public void rateLimitCooldownTest() throws Exception {
        String[] args = { "../" };

        // set rate limiting cooldown time to 1s
        Utils.setValueInConfig("totp_rate_limit_cooldown_time", "1");

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            assert (false);
        }
        TOTPStorage storage = StorageLayer.getTOTPStorage(process.getProcess());

        Main main = process.getProcess();

        // Create device
        String secret = Totp.registerDevice(main, "user", "deviceName", 1, 30);

        // Trigger rate limiting and fix it with a correct code after some time:
        triggerAndCheckRateLimit(main, "user", "deviceName");
        // Wait for 1 second (Should cool down rate limiting):
        Thread.sleep(1000);
        // But again try with invalid code:
        Totp.verifyCode(main, "user", "yet-another-invalid-code", true);
        // Wait for 1 second (Should cool down rate limiting):
        Thread.sleep(1000);
        // Now try with valid code:
        Totp.verifyCode(main, "user", Totp.generateTotpCode(main, "user", "deviceName"), true);
    }

    @Test
    public void removeExpiredCodesCronDuringRateLimitTest() throws Exception {
        TestSetupResult result = setup();
        Main main = result.process.getProcess();

        // Create device
        String secret = Totp.registerDevice(main, "user", "deviceName", 1, 30);

        // Trigger rate limiting and fix it with cronjob (runs every 1 hour)
        triggerAndCheckRateLimit(main, "user", "deviceName");
        // FIXME: Run cronjob at higher frequency:
        assert false;
        // Totp.runCron(main);
        Totp.verifyCode(main, "user", "XXXX-code", true);
    }

    @Test
    public void createAndVerifyDevice() throws Exception {
        TestSetupResult result = setup();
        Main main = result.process.getProcess();

        // Create device
        String secret = Totp.registerDevice(main, "user", "deviceName", 1, 30);

        // Try verify non-existent user:
        assertThrows(TotpNotEnabledException.class,
                () -> Totp.verifyDevice(main, "non-existent-user", "deviceName", "XXXX"));

        // Try verify non-existent device
        assertThrows(UnknownDeviceException.class,
                () -> Totp.verifyDevice(main, "user", "non-existent-device", "XXXX"));

        // Verify device with wrong code
        assertThrows(InvalidTotpException.class, () -> Totp.verifyDevice(main, "user", "deviceName", "wrong-code"));

        // Verify device with correct code
        String validCode = Totp.generateTotpCode(main, "user", "deviceName");
        boolean justVerfied = Totp.verifyDevice(main, "user", "deviceName", validCode);
        assert justVerfied;

        // Verify again with same correct code:
        justVerfied = Totp.verifyDevice(main, "user", "deviceName", validCode);
        assert !justVerfied;

        // Verify again with new correct code:
        String newValidCode = Totp.generateTotpCode(main, "user", "deviceName");
        justVerfied = Totp.verifyDevice(main, "user", "deviceName", newValidCode);
        assert !justVerfied;

        // Verify again with wrong code:
        justVerfied = Totp.verifyDevice(main, "user", "deviceName", "wrong-code");
        assert !justVerfied;

        result.process.kill();
        assertNotNull(result.process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void deleteDevice() throws Exception {

        // Deleting the last device of a user should delete all related codes:
        TestSetupResult result = setup();
        Main main = result.process.getProcess();

        // Create device
        String secret = Totp.registerDevice(main, "user", "device", 1, 30);

        long nextDay = System.currentTimeMillis() + 1000 * 60 * 60 * 24; // 1 day
        long now = System.currentTimeMillis();
        {
            Totp.verifyCode(main, "user", "invalid-code", true);
            Totp.verifyCode(main, "user", Totp.generateTotpCode(main, "user", "device"), true);

            // delete device2 as well
            // storage.startTransaction(con -> {
            // storage.deleteDevice_Transaction(con, "user", "device2");
            // storage.commitTransaction(con);
            // return null;
            // });

            TOTPDevice[] devices = Totp.getDevices(main, "user");
            assert (devices.length == 0);
        }
    }

    @Test
    public void deleteUser() throws Exception {
        // Deleting a user should delete all related devices and codes:

    }

}
