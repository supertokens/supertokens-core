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
import java.security.InvalidKeyException;
import java.security.Key;
import java.time.Duration;
import java.time.Instant;

import javax.crypto.spec.SecretKeySpec;

import org.apache.commons.codec.binary.Base32;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import com.eatthepath.otp.TimeBasedOneTimePasswordGenerator;

import io.supertokens.test.Utils;
import io.supertokens.Main;
import io.supertokens.ProcessState;
import io.supertokens.config.Config;
import io.supertokens.cronjobs.deleteExpiredTotpTokens.DeleteExpiredTotpTokens;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.exceptions.StorageTransactionLogicException;
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
import io.supertokens.pluginInterface.totp.sqlStorage.TOTPSQLStorage;

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

    public TestSetupResult defaultInit() throws InterruptedException, IOException {
        String[] args = { "../" };

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            assert (false);
        }
        TOTPStorage storage = StorageLayer.getTOTPStorage(process.getProcess());

        return new TestSetupResult(storage, process);
    }

    private static String generateTotpCode(Main main, TOTPDevice device)
            throws InvalidKeyException, StorageQueryException {
        return generateTotpCode(main, device, 0);
    }

    /** Generates TOTP code similar to apps like Google Authenticator and Authy */
    private static String generateTotpCode(Main main, TOTPDevice device, int step)
            throws InvalidKeyException, StorageQueryException {
        final TimeBasedOneTimePasswordGenerator totp = new TimeBasedOneTimePasswordGenerator(
                Duration.ofSeconds(device.period));

        byte[] keyBytes = new Base32().decode(device.secretKey);
        Key key = new SecretKeySpec(keyBytes, "HmacSHA1");

        return totp.generateOneTimePasswordString(key, Instant.now().plusSeconds(step * device.period));
    }

    private static TOTPUsedCode[] getAllUsedCodesUtil(TOTPStorage storage, String userId)
            throws StorageQueryException, StorageTransactionLogicException {
        assert storage instanceof TOTPSQLStorage;
        TOTPSQLStorage sqlStorage = (TOTPSQLStorage) storage;

        return (TOTPUsedCode[]) sqlStorage.startTransaction(con -> {
            TOTPUsedCode[] usedCodes = sqlStorage.getAllUsedCodesDescOrderAndLockByUser_Transaction(con, userId);
            sqlStorage.commitTransaction(con);
            return usedCodes;
        });
    }

    @Test
    public void createDeviceTest() throws Exception {
        TestSetupResult result = defaultInit();
        Main main = result.process.getProcess();

        // Create device
        TOTPDevice device = Totp.registerDevice(main, "user", "device1", 1, 30);
        assert device.secretKey != "";

        // Create same device again (should fail)
        assertThrows(DeviceAlreadyExistsException.class,
                () -> Totp.registerDevice(main, "user", "device1", 1, 30));
    }

    @Test
    public void createDeviceAndVerifyCodeTest() throws Exception {
        TestSetupResult result = defaultInit();
        Main main = result.process.getProcess();

        // Create device
        TOTPDevice device = Totp.registerDevice(main, "user", "device", 1, 1);

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
                () -> Totp.verifyCode(main, "user", generateTotpCode(main, device), false));

        // Valid code & allowUnverifiedDevice = true (Success):
        String validCode = generateTotpCode(main, device);
        Totp.verifyCode(main, "user", validCode, true);

        // Now try again with same code:
        assertThrows(
                InvalidTotpException.class,
                () -> Totp.verifyCode(main, "user", validCode, true));

        // Sleep for 1s so that code changes.
        Thread.sleep(1500);

        // Use a new valid code:
        String newValidCode = generateTotpCode(main, device);
        Totp.verifyCode(main, "user", newValidCode, true);

        // Regenerate the same code and use it again (should fail):
        String newValidCodeCopy = generateTotpCode(main, device);
        assertThrows(InvalidTotpException.class,
                () -> Totp.verifyCode(main, "user", newValidCodeCopy, true));

        // Use a code from next period:
        String nextValidCode = generateTotpCode(main, device, 1);
        Totp.verifyCode(main, "user", nextValidCode, true);

        // Use previous period code (should fail coz validCode): // FIXME: This should
        // // fail
        // String previousCode = generateTotpCode(main, "user", "device", -1);
        // Totp.verifyCode(main, "user", previousCode, true);

        // TODO: Add isolated tests where we
        // - we try next and previous codes as well (try different skew values)
        // - change totp_max_attempts
        // - change totp_invalid_code_expiry_sec
    }

    public void triggerAndCheckRateLimit(Main main, TOTPDevice device) throws Exception {
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
                () -> Totp.verifyCode(main, "user", generateTotpCode(main, device), true));
        assertThrows(
                LimitReachedException.class,
                () -> Totp.verifyCode(main, "user", "invalid-code-N+2", true));
    }

    @Test
    public void rateLimitCooldownTest() throws Exception {
        String[] args = { "../" };

        // set rate limiting cooldown time to 1s
        Utils.setValueInConfig("totp_rate_limit_cooldown_sec", "1");

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            assert (false);
        }

        Main main = process.getProcess();

        // Create device
        TOTPDevice device = Totp.registerDevice(main, "user", "deviceName", 1, 1);

        // Trigger rate limiting and fix it with a correct code after some time:
        triggerAndCheckRateLimit(main, device);
        // Wait for 1 second (Should cool down rate limiting):
        Thread.sleep(1000);
        // But again try with invalid code:
        assertThrows(InvalidTotpException.class, () -> Totp.verifyCode(main, "user", "yet-another-invalid-code", true));
        // This triggered rate limiting again. So even valid codes will fail for
        // another cooldown period:
        assertThrows(LimitReachedException.class,
                () -> Totp.verifyCode(main, "user", generateTotpCode(main, device), true));
        // Wait for 1 second (Should cool down rate limiting):
        Thread.sleep(1000);
        // Now try with valid code:
        Totp.verifyCode(main, "user", generateTotpCode(main, device), true);
        // Now invalid code shouldn't trigger rate limiting. Unless you do it N times:
        assertThrows(InvalidTotpException.class, () -> Totp.verifyCode(main, "user", "some-invalid-code", true));
    }

    @Test
    public void cronRemovesAllCodesDuringRateLimitTest() throws Exception {
        // This test is flaky because of time.
        TestSetupResult result = defaultInit();
        Main main = result.process.getProcess();

        // Create device
        TOTPDevice device = Totp.registerDevice(main, "user", "deviceName", 0, 1);

        // Trigger rate limiting and fix it with cronjob (manually run cronjob):
        triggerAndCheckRateLimit(main, device);
        // Wait for 1 second so that all the codes expire:
        Thread.sleep(1500);
        // Manually run cronjob to delete all the codes after their
        // expiry time + rate limiting period is over:
        DeleteExpiredTotpTokens.getInstance(main).run();

        // This removal shouldn't affect rate limiting. User must remain rate limited.
        assertThrows(LimitReachedException.class,
                () -> Totp.verifyCode(main, "user", generateTotpCode(main, device), true));
        assertThrows(LimitReachedException.class,
                () -> Totp.verifyCode(main, "user", "again-wrong-code1", true));
    }

    @Test
    public void createAndVerifyDeviceTest() throws Exception {
        TestSetupResult result = defaultInit();
        Main main = result.process.getProcess();

        // Create device
        TOTPDevice device = Totp.registerDevice(main, "user", "deviceName", 1, 30);

        // Try verify non-existent user:
        assertThrows(TotpNotEnabledException.class,
                () -> Totp.verifyDevice(main, "non-existent-user", "deviceName", "XXXX"));

        // Try verify non-existent device
        assertThrows(UnknownDeviceException.class,
                () -> Totp.verifyDevice(main, "user", "non-existent-device", "XXXX"));

        // Verify device with wrong code
        assertThrows(InvalidTotpException.class, () -> Totp.verifyDevice(main, "user", "deviceName", "wrong-code"));

        // Verify device with correct code
        String validCode = generateTotpCode(main, device);
        boolean justVerfied = Totp.verifyDevice(main, "user", "deviceName", validCode);
        assert justVerfied;

        // Verify again with same correct code:
        justVerfied = Totp.verifyDevice(main, "user", "deviceName", validCode);
        assert !justVerfied;

        // Verify again with new correct code:
        String newValidCode = generateTotpCode(main, device);
        justVerfied = Totp.verifyDevice(main, "user", "deviceName", newValidCode);
        assert !justVerfied;

        // Verify again with wrong code:
        justVerfied = Totp.verifyDevice(main, "user", "deviceName", "wrong-code");
        assert !justVerfied;

        result.process.kill();
        assertNotNull(result.process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void removeDeviceTest() throws Exception {
        // Flaky test.
        TestSetupResult result = defaultInit();
        Main main = result.process.getProcess();
        TOTPStorage storage = result.storage;

        // Create devices
        TOTPDevice device1 = Totp.registerDevice(main, "user", "device1", 1, 30);
        TOTPDevice device2 = Totp.registerDevice(main, "user", "device2", 1, 30);

        TOTPDevice[] devices = Totp.getDevices(main, "user");
        assert (devices.length == 2);

        // Try to delete device for non-existent user:
        assertThrows(TotpNotEnabledException.class, () -> Totp.removeDevice(main, "non-existent-user", "device1"));

        // Try to delete non-existent device:
        assertThrows(UnknownDeviceException.class, () -> Totp.removeDevice(main, "user", "non-existent-device"));

        // Delete one of the devices
        {
            assertThrows(InvalidTotpException.class, () -> Totp.verifyCode(main, "user", "invalid-code", true));
            Totp.verifyCode(main, "user", generateTotpCode(main, device1), true);
            Totp.verifyCode(main, "user", generateTotpCode(main, device2), true);

            // Delete device1
            Totp.removeDevice(main, "user", "device1");

            devices = Totp.getDevices(main, "user");
            assert (devices.length == 1);

            // 1 device still remain so all codes should still be still there:
            TOTPUsedCode[] usedCodes = getAllUsedCodesUtil(storage, "user");
            assert (usedCodes.length == 3);
        }

        // Deleting the last device of a user should delete all related codes:
        // Delete the 2nd (and the last) device
        {

            // Create another user to test that other users aren't affected:
            TOTPDevice otherUserDevice = Totp.registerDevice(main, "other-user", "device", 1, 30);
            Totp.verifyCode(main, "other-user", generateTotpCode(main, otherUserDevice), true);
            assertThrows(InvalidTotpException.class, () -> Totp.verifyCode(main, "other-user", "invalid-code", true));

            // Delete device2
            Totp.removeDevice(main, "user", "device2");

            // TOTP has ben disabled for the user:
            assertThrows(TotpNotEnabledException.class, () -> Totp.getDevices(main, "user"));

            // No device left so all codes of the user should be deleted:
            TOTPUsedCode[] usedCodes = getAllUsedCodesUtil(storage, "user");
            assert (usedCodes.length == 0);

            // But for other users things should still be there:
            TOTPDevice[] otherUserDevices = Totp.getDevices(main, "other-user");
            assert (otherUserDevices.length == 1);

            usedCodes = getAllUsedCodesUtil(storage, "other-user");
            assert (usedCodes.length == 2);
        }
    }

    @Test
    public void updateDeviceNameTest() throws Exception {
        TestSetupResult result = defaultInit();
        Main main = result.process.getProcess();

        Totp.registerDevice(main, "user", "device1", 1, 30);
        Totp.registerDevice(main, "user", "device2", 1, 30);

        // Try update non-existent user:
        assertThrows(TotpNotEnabledException.class,
                () -> Totp.updateDeviceName(main, "non-existent-user", "device1", "new-device-name"));

        // Try update non-existent device:
        assertThrows(UnknownDeviceException.class,
                () -> Totp.updateDeviceName(main, "user", "non-existent-device", "new-device-name"));

        // Update device name (should work)
        Totp.updateDeviceName(main, "user", "device1", "new-device-name");

        // Verify that the device name has been updated:
        TOTPDevice[] devices = Totp.getDevices(main, "user");
        assert (devices.length == 2);
        assert (devices[0].deviceName.equals("device2"));
        assert (devices[1].deviceName.equals("new-device-name"));

        // Verify that TOTP verification still works:
        Totp.verifyDevice(main, "user", devices[0].deviceName, generateTotpCode(main, devices[0]));
        Totp.verifyDevice(main, "user", devices[0].deviceName, generateTotpCode(main, devices[1]));

        // Try update device name to an already existing device name:
        assertThrows(DeviceAlreadyExistsException.class,
                () -> Totp.updateDeviceName(main, "user", "device2", "new-device-name"));
    }

    @Test
    public void getDevicesTest() throws Exception {
        TestSetupResult result = defaultInit();
        Main main = result.process.getProcess();

        // Try get devices for non-existent user:
        assertThrows(TotpNotEnabledException.class, () -> Totp.getDevices(main, "non-existent-user"));

        TOTPDevice device1 = Totp.registerDevice(main, "user", "device1", 2, 30);
        TOTPDevice device2 = Totp.registerDevice(main, "user", "device2", 1, 10);

        TOTPDevice[] devices = Totp.getDevices(main, "user");
        assert (devices.length == 2);
        assert devices[0].equals(device1);
        assert devices[1].equals(device2);
    }

    @Test
    public void deleteExpiredTokensCronIntervalTest() throws Exception {
        TestSetupResult result = defaultInit();
        Main main = result.process.getProcess();

        // Ensure that delete expired tokens cron runs every hour:
        assert DeleteExpiredTotpTokens.getInstance(main).getIntervalTimeSeconds() == 60 * 60;
    }

}
