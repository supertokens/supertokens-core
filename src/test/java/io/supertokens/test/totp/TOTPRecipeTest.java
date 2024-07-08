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

import com.eatthepath.otp.TimeBasedOneTimePasswordGenerator;
import io.supertokens.Main;
import io.supertokens.ProcessState;
import io.supertokens.config.Config;
import io.supertokens.cronjobs.deleteExpiredTotpTokens.DeleteExpiredTotpTokens;
import io.supertokens.featureflag.EE_FEATURES;
import io.supertokens.featureflag.FeatureFlagTestContent;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.exceptions.StorageTransactionLogicException;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifier;
import io.supertokens.pluginInterface.totp.TOTPDevice;
import io.supertokens.pluginInterface.totp.TOTPStorage;
import io.supertokens.pluginInterface.totp.TOTPUsedCode;
import io.supertokens.pluginInterface.totp.exception.DeviceAlreadyExistsException;
import io.supertokens.pluginInterface.totp.exception.UnknownDeviceException;
import io.supertokens.pluginInterface.totp.exception.UnknownTotpUserIdException;
import io.supertokens.pluginInterface.totp.sqlStorage.TOTPSQLStorage;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.test.TestingProcessManager;
import io.supertokens.test.Utils;
import io.supertokens.totp.Totp;
import io.supertokens.totp.exceptions.InvalidTotpException;
import io.supertokens.totp.exceptions.LimitReachedException;
import org.apache.commons.codec.binary.Base32;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.Key;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

import static org.junit.Assert.*;

// TODO: Add test for UsedCodeAlreadyExistsException once we implement time mocking

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

    public TestSetupResult defaultInit()
            throws InterruptedException {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return null;
        }
        TOTPStorage storage = (TOTPStorage) StorageLayer.getStorage(process.getProcess());

        FeatureFlagTestContent.getInstance(process.main)
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{EE_FEATURES.MFA});

        return new TestSetupResult(storage, process);
    }

    public static String generateTotpCode(Main main, TOTPDevice device)
            throws InvalidKeyException, StorageQueryException {
        return generateTotpCode(main, device, 0);
    }

    /**
     * Generates TOTP code similar to apps like Google Authenticator and Authy
     */
    public static String generateTotpCode(Main main, TOTPDevice device, int step)
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
            TOTPUsedCode[] usedCodes = sqlStorage.getAllUsedCodesDescOrder_Transaction(con,
                    new TenantIdentifier(null, null, null), userId);
            sqlStorage.commitTransaction(con);
            return usedCodes;
        });
    }

    @Test
    public void createDeviceTest() throws Exception {
        TestSetupResult result = defaultInit();
        if (result == null) {
            return;
        }
        Main main = result.process.getProcess();

        // Create device
        TOTPDevice device = Totp.registerDevice(main, "user", "device1", 1, 30);
        assert !Objects.equals(device.secretKey, "");

        // Verify device
        String validTotp = TOTPRecipeTest.generateTotpCode(main, device);
        Totp.verifyDevice(main, "user", "device1", validTotp);

        // Create same device again (should fail)
        assertThrows(DeviceAlreadyExistsException.class,
                () -> Totp.registerDevice(main, "user", "device1", 1, 30));
    }

    @Test
    public void createDeviceAndVerifyCodeTest() throws Exception {
        TestSetupResult result = defaultInit();
        if (result == null) {
            return;
        }
        Main main = result.process.getProcess();

        // Create devices
        TOTPDevice device = Totp.registerDevice(main, "user", "device1", 1, 1);
        TOTPDevice unverifiedDevice = Totp.registerDevice(main, "user", "unverified-device", 1, 1);

        // Verify device:
        Totp.verifyDevice(main, "user", device.deviceName, generateTotpCode(main, device, -1));

        // Try login with non-existent user:
        assertThrows(UnknownTotpUserIdException.class,
                () -> Totp.verifyCode(main, "non-existent-user", "any-code"));

        // {Code: [INVALID, VALID]} * {Devices: [verified, unverfied]}

        // Invalid code & unverified device:
        assertThrows(InvalidTotpException.class,
                () -> Totp.verifyCode(main, "user", "invalid"));

        // Invalid code & verified device:
        assertThrows(InvalidTotpException.class,
                () -> Totp.verifyCode(main, "user", "invalid"));

        // Valid code & unverified device:
        assertThrows(
                InvalidTotpException.class,
                () -> Totp.verifyCode(main, "user", generateTotpCode(main, unverifiedDevice)));

        Thread.sleep(1000 - System.currentTimeMillis() % 1000 + 10);

        // Valid code & verified device (Success)
        String validCode = generateTotpCode(main, device);
        Totp.verifyCode(main, "user", validCode);

        // Now try again with same code:
        assertThrows(
                InvalidTotpException.class,
                () -> Totp.verifyCode(main, "user", validCode));

        // Sleep for 1s so that code changes.
        Thread.sleep(1000 - System.currentTimeMillis() % 1000 + 10);

        // Use a new valid code:
        String newValidCode = generateTotpCode(main, device);
        Totp.verifyCode(main, "user", newValidCode);

        // Reuse the same code and use it again (should fail):
        assertThrows(InvalidTotpException.class,
                () -> Totp.verifyCode(main, "user", newValidCode));

        // Use a code from next period:
        Thread.sleep(1);
        String nextValidCode = generateTotpCode(main, device, 1);
        Totp.verifyCode(main, "user", nextValidCode);

        // Use previous period code (should fail coz validCode has been used):
        Thread.sleep(1);
        String previousCode = generateTotpCode(main, device, -1);
        assert previousCode.equals(validCode);
        assertThrows(InvalidTotpException.class, () -> Totp.verifyCode(main, "user", previousCode));

        // Create device with skew = 0, check that it only works with the current code
        Thread.sleep(1);
        TOTPDevice device2 = Totp.registerDevice(main, "user", "device2", 0, 1);
        assert !Objects.equals(device2.secretKey, device.secretKey);
        Totp.verifyDevice(main, "user", device2.deviceName, generateTotpCode(main, device2));

        // Sleep because code was used for verifying the device
        Thread.sleep(1000);

        String nextValidCode2 = generateTotpCode(main, device2, 1);
        assertThrows(InvalidTotpException.class,
                () -> Totp.verifyCode(main, "user", nextValidCode2));

        String previousValidCode2 = generateTotpCode(main, device2, -1);
        Thread.sleep(1);
        assertThrows(InvalidTotpException.class,
                () -> Totp.verifyCode(main, "user", previousValidCode2));

        Thread.sleep(1);
        String currentValidCode2 = generateTotpCode(main, device2);
        Totp.verifyCode(main, "user", currentValidCode2);

        // Submit invalid code and check that it's expiry time is correct
        // created - expiryTime = max of ((2 * skew + 1) * period) for all devices
        Thread.sleep(1);
        assertThrows(InvalidTotpException.class,
                () -> Totp.verifyCode(main, "user", "invalid"));

        TOTPUsedCode[] usedCodes = getAllUsedCodesUtil(result.storage, "user");
        TOTPUsedCode latestCode = usedCodes[0];
        assert !latestCode.isValid;
        assert latestCode.expiryTime - latestCode.createdTime ==
                3000; // it should be 3s because of device1 (i.e. max(device1Exp, device2Exp))

        // Sleep for 1s so that code changes.
        Thread.sleep(1000);

        // Now verify device2:
        Totp.verifyDevice(main, "user", device2.deviceName, generateTotpCode(main, device2));

        // device1: unverified, device2: verified
        // Valid code & verified device:
        Totp.verifyCode(main, "user", generateTotpCode(main, device));

        Thread.sleep(1000);

        Totp.verifyCode(main, "user", generateTotpCode(main, device2));

        // Valid code & allowUnverifiedDevice = true:
        Thread.sleep(1000);
        Totp.verifyCode(main, "user", generateTotpCode(main, device));
        Thread.sleep(1000);
        Totp.verifyCode(main, "user", generateTotpCode(main, device2));
    }

    /*
     * Triggers rate limiting and checks that it works.
     * It returns the number of attempts that were made before rate limiting was
     * triggered.
     */
    public int triggerAndCheckRateLimit(Main main, TOTPDevice device) throws Exception {
        int N = Config.getConfig(main).getTotpMaxAttempts();

        // Sleep until we finish the current second so that TOTP verification won't change in the time limit
        Thread.sleep(1000 - System.currentTimeMillis() % 1000 + 10);
        Thread.sleep(1000); // sleep another second so that the rate limit state is kind of reset

        // First N attempts should fail with invalid code:
        // This is to trigger rate limiting
        for (int i = 0; i < N; i++) {
            String code = "ic-" + i; // ic = invalid code
            Thread.sleep(1);
            assertThrows(
                    InvalidTotpException.class,
                    () -> Totp.verifyCode(main, "user", code));
        }

        // Any kind of attempt after this should fail with rate limiting error.
        // This should happen until rate limiting cooldown happens:
        Thread.sleep(1);
        assertThrows(
                LimitReachedException.class,
                () -> Totp.verifyCode(main, "user", "icN+1"));
        Thread.sleep(1);
        assertThrows(
                LimitReachedException.class,
                () -> Totp.verifyCode(main, "user", generateTotpCode(main, device)));
        Thread.sleep(1);
        assertThrows(
                LimitReachedException.class,
                () -> Totp.verifyCode(main, "user", "icN+2"));

        return N;
    }

    @Test
    public void rateLimitCooldownTest() throws Exception {
        String[] args = {"../"};

        // set rate limiting cooldown time to 1s
        Utils.setValueInConfig("totp_rate_limit_cooldown_sec", "1");
        // set max attempts to 3
        Utils.setValueInConfig("totp_max_attempts", "3");

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        FeatureFlagTestContent.getInstance(process.main)
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{EE_FEATURES.MFA});

        Main main = process.getProcess();

        // Create device
        TOTPDevice device = Totp.registerDevice(main, "user", "deviceName", 1, 1);
        Totp.verifyDevice(main, "user", device.deviceName, generateTotpCode(main, device, -1));

        // Trigger rate limiting and fix it with a correct code after some time:
        int attemptsRequired = triggerAndCheckRateLimit(main, device);
        assert attemptsRequired == 3;
        // Wait for 1 second (Should cool down rate limiting):
        Thread.sleep(1000);
        // But again try with invalid code:
        InvalidTotpException invalidTotpException = assertThrows(InvalidTotpException.class,
                () -> Totp.verifyCode(main, "user", "invalid0"));
        assertEquals(1, invalidTotpException.currentAttempts);
        invalidTotpException = assertThrows(InvalidTotpException.class,
                () -> Totp.verifyCode(main, "user", "invalid0"));
        assertEquals(2, invalidTotpException.currentAttempts);
        invalidTotpException = assertThrows(InvalidTotpException.class,
                () -> Totp.verifyCode(main, "user", "invalid0"));
        assertEquals(3, invalidTotpException.currentAttempts);

        // This triggered rate limiting again. So even valid codes will fail for
        // another cooldown period:
        LimitReachedException limitReachedException = assertThrows(LimitReachedException.class,
                () -> Totp.verifyCode(main, "user", generateTotpCode(main, device)));
        assertEquals(3, limitReachedException.currentAttempts);
        // Wait for 1 second (Should cool down rate limiting):
        Thread.sleep(1000);

        // test that after cool down, we can retry invalid codes N times again
        invalidTotpException = assertThrows(InvalidTotpException.class,
                () -> Totp.verifyCode(main, "user", "invalid0"));
        assertEquals(1, invalidTotpException.currentAttempts);
        invalidTotpException = assertThrows(InvalidTotpException.class,
                () -> Totp.verifyCode(main, "user", "invalid0"));
        assertEquals(2, invalidTotpException.currentAttempts);
        invalidTotpException = assertThrows(InvalidTotpException.class,
                () -> Totp.verifyCode(main, "user", "invalid0"));
        assertEquals(3, invalidTotpException.currentAttempts);

        Thread.sleep(1100);

        // Now try with valid code:
        Totp.verifyCode(main, "user", generateTotpCode(main, device));
        // Now invalid code shouldn't trigger rate limiting. Unless you do it N times:
        assertThrows(InvalidTotpException.class, () -> Totp.verifyCode(main, "user", "invaldd"));
    }

    @Test
    public void cronRemovesCodesDuringRateLimitTest() throws Exception {
        // This test is flaky because of time.
        TestSetupResult result = defaultInit();
        if (result == null) {
            return;
        }
        Main main = result.process.getProcess();

        // Create device
        TOTPDevice device = Totp.registerDevice(main, "user", "deviceName", 0, 1);

        // Trigger rate limiting and fix it with cronjob (manually run cronjob):
        int attemptsRequired = triggerAndCheckRateLimit(main, device);
        assert attemptsRequired == 5;
        // Wait for 1 second so that all the codes expire:
        Thread.sleep(1100);
        // Manually run cronjob to delete all the codes after their
        // expiry time + rate limiting period is over:
        DeleteExpiredTotpTokens.getInstance(main).run();

        // This removal shouldn't affect rate limiting. User must remain rate limited.
        assertThrows(LimitReachedException.class,
                () -> Totp.verifyCode(main, "user", generateTotpCode(main, device)));
        assertThrows(LimitReachedException.class,
                () -> Totp.verifyCode(main, "user", "yet-ic"));
    }

    @Test
    public void createAndVerifyDeviceTest() throws Exception {
        TestSetupResult result = defaultInit();
        if (result == null) {
            return;
        }
        Main main = result.process.getProcess();

        // Create device
        TOTPDevice device = Totp.registerDevice(main, "user", "deviceName", 1, 30);

        // Try verify non-existent user:
        assertThrows(UnknownDeviceException.class,
                () -> Totp.verifyDevice(main, "non-existent-user", "deviceName", "XXXX"));

        // Try verify non-existent device
        assertThrows(UnknownDeviceException.class,
                () -> Totp.verifyDevice(main, "user", "non-existent-device", "XXXX"));

        // Verify device with wrong code
        assertThrows(InvalidTotpException.class, () -> Totp.verifyDevice(main, "user", "deviceName", "ic0"));


        // Verify device with correct code
        Thread.sleep(1);
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
        justVerfied = Totp.verifyDevice(main, "user", "deviceName", "ic1");
        assert !justVerfied;

        result.process.kill();
        assertNotNull(result.process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void removeDeviceTest() throws Exception {
        // Flaky test.
        TestSetupResult result = defaultInit();
        if (result == null) {
            return;
        }
        Main main = result.process.getProcess();
        TOTPStorage storage = result.storage;

        // Create devices
        TOTPDevice device1 = Totp.registerDevice(main, "user", "device1", 1, 30);
        TOTPDevice device2 = Totp.registerDevice(main, "user", "device2", 1, 30);

        Thread.sleep(1);
        Totp.verifyDevice(main, "user", "device1", generateTotpCode(main, device1, -1));
        Thread.sleep(1);
        Totp.verifyDevice(main, "user", "device2", generateTotpCode(main, device2, -1));

        TOTPDevice[] devices = Totp.getDevices(main, "user");
        assert (devices.length == 2);

        // Try to delete device for non-existent user:
        assertThrows(UnknownDeviceException.class, () -> Totp.removeDevice(main, "non-existent-user", "device1"));

        // Try to delete non-existent device:
        assertThrows(UnknownDeviceException.class, () -> Totp.removeDevice(main, "user", "non-existent-device"));

        // Delete one of the devices
        {
            assertThrows(InvalidTotpException.class, () -> Totp.verifyCode(main, "user", "ic0"));

            Thread.sleep(1000 - System.currentTimeMillis() % 1000 + 10);

            Thread.sleep(1);
            Totp.verifyCode(main, "user", generateTotpCode(main, device1));
            Thread.sleep(1);
            Totp.verifyCode(main, "user", generateTotpCode(main, device2));

            // Delete device1
            Totp.removeDevice(main, "user", "device1");

            devices = Totp.getDevices(main, "user");
            assert (devices.length == 1);

            // 1 device still remain so all codes should still be still there:
            TOTPUsedCode[] usedCodes = getAllUsedCodesUtil(storage, "user");
            assert (usedCodes.length == 5); // 2 for device verification and 3 for code verification
        }

        // Deleting the last device of a user should delete all related codes:
        // Delete the 2nd (and the last) device
        {

            // Create another user to test that other users aren't affected:
            TOTPDevice otherUserDevice = Totp.registerDevice(main, "other-user", "device", 1, 30);
            Totp.verifyDevice(main, "other-user", "device", generateTotpCode(main, otherUserDevice, -1));
            Thread.sleep(1);
            Totp.verifyCode(main, "other-user", generateTotpCode(main, otherUserDevice));
            assertThrows(InvalidTotpException.class, () -> Totp.verifyCode(main, "other-user", "ic1"));

            // Delete device2
            Totp.removeDevice(main, "user", "device2");

            // No more devices are left for the user:
            assert (Totp.getDevices(main, "user").length == 0);

            // No device left so all codes of the user should be deleted:
            TOTPUsedCode[] usedCodes = getAllUsedCodesUtil(storage, "user");
            assert (usedCodes.length == 0);

            // But for other users things should still be there:
            TOTPDevice[] otherUserDevices = Totp.getDevices(main, "other-user");
            assert (otherUserDevices.length == 1);

            usedCodes = getAllUsedCodesUtil(storage, "other-user");
            assert (usedCodes.length == 3); // 1 for device verification and 2 for code verification
        }
    }

    @Test
    public void updateDeviceNameTest() throws Exception {
        TestSetupResult result = defaultInit();
        if (result == null) {
            return;
        }
        Main main = result.process.getProcess();

        Totp.registerDevice(main, "user", "device1", 1, 30);
        Totp.registerDevice(main, "user", "device2", 1, 30);

        // Try update non-existent user:
        assertThrows(UnknownDeviceException.class,
                () -> Totp.updateDeviceName(main, "non-existent-user", "device1", "new-device-name"));

        // Try update non-existent device:
        assertThrows(UnknownDeviceException.class,
                () -> Totp.updateDeviceName(main, "user", "non-existent-device", "new-device-name"));

        // Update device name (should work)
        Totp.updateDeviceName(main, "user", "device1", "new-device-name");

        // Verify that the device name has been updated:
        TOTPDevice[] devices = Totp.getDevices(main, "user");
        assert (devices.length == 2);
        assert (devices[0].deviceName.equals("device2") && devices[1].deviceName.equals("new-device-name")
                || devices[0].deviceName.equals("new-device-name") && devices[1].deviceName.equals("device2"));

        // Verify that TOTP verification still works:
        Totp.verifyDevice(main, "user", devices[0].deviceName, generateTotpCode(main, devices[0]));
        Totp.verifyDevice(main, "user", devices[0].deviceName, generateTotpCode(main, devices[1]));

        // Try update device name to an already existing device name:
        assertThrows(DeviceAlreadyExistsException.class,
                () -> Totp.updateDeviceName(main, "user", "device2", "new-device-name"));
        // Try to rename to the same name: (Should work)
        Totp.updateDeviceName(main, "user", "device2", "device2");
    }

    @Test
    public void getDevicesTest() throws Exception {
        TestSetupResult result = defaultInit();
        if (result == null) {
            return;
        }
        Main main = result.process.getProcess();

        // Try get devices for non-existent user:
        assert (Totp.getDevices(main, "non-existent-user").length == 0);

        TOTPDevice device1 = Totp.registerDevice(main, "user", "device1", 2, 30);
        TOTPDevice device2 = Totp.registerDevice(main, "user", "device2", 1, 10);

        TOTPDevice[] devices = Totp.getDevices(main, "user");
        assert (devices.length == 2);
        assert (devices[0].equals(device1) && devices[1].equals(device2)) ||
                (devices[1].equals(device1) && devices[0].equals(device2));
    }

    @Test
    public void deleteExpiredTokensCronIntervalTest() throws Exception {
        TestSetupResult result = defaultInit();
        if (result == null) {
            return;
        }
        Main main = result.process.getProcess();

        // Ensure that delete expired tokens cron runs every hour:
        assert DeleteExpiredTotpTokens.getInstance(main).getIntervalTimeSeconds() == 60 * 60;
    }

    @Test
    public void testRegisterDeviceWithSameNameAsAnUnverifiedDevice() throws Exception {
        TestSetupResult result = defaultInit();
        if (result == null) {
            return;
        }
        Main main = result.process.getProcess();

        Totp.registerDevice(main, "user", "device1", 1, 30);
        Totp.registerDevice(main, "user", "device1", 1, 30);
    }

    @Test
    public void testCurrentAndMaxAttemptsInExceptions() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        FeatureFlagTestContent.getInstance(process.main)
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{EE_FEATURES.MFA});

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        TOTPDevice device = Totp.registerDevice(process.getProcess(), "userId", "deviceName", 1, 30);
        try {
            Totp.verifyDevice(process.getProcess(), "userId", "deviceName", "123456");
            fail();
        } catch (InvalidTotpException e) {
            assertEquals(1, e.currentAttempts);
            assertEquals(5, e.maxAttempts);
        }
        Thread.sleep(1);
        try {
            Totp.verifyDevice(process.getProcess(), "userId", "deviceName", "223456");
            fail();
        } catch (InvalidTotpException e) {
            assertEquals(2, e.currentAttempts);
            assertEquals(5, e.maxAttempts);
        }
        Thread.sleep(1);

        try {
            Totp.verifyDevice(process.getProcess(), "userId", "deviceName", "323456");
            fail();
        } catch (InvalidTotpException e) {
            assertEquals(3, e.currentAttempts);
            assertEquals(5, e.maxAttempts);
        }
        Thread.sleep(1);

        try {
            Totp.verifyDevice(process.getProcess(), "userId", "deviceName", "423456");
            fail();
        } catch (InvalidTotpException e) {
            assertEquals(4, e.currentAttempts);
            assertEquals(5, e.maxAttempts);
        }
        Thread.sleep(1);

        try {
            Totp.verifyDevice(process.getProcess(), "userId", "deviceName", "523456");
            fail();
        } catch (InvalidTotpException e) {
            assertEquals(5, e.currentAttempts);
            assertEquals(5, e.maxAttempts);
        }
        Thread.sleep(1);

        try {
            Totp.verifyDevice(process.getProcess(), "userId", "deviceName", "623456");
            fail();
        } catch (LimitReachedException e) {
            assertEquals(5, e.currentAttempts);
            assertEquals(5, e.maxAttempts);
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }
}
