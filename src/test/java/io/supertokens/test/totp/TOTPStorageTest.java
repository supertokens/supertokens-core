package io.supertokens.test.totp;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import io.supertokens.test.Utils;
import io.supertokens.ProcessState;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.test.TestingProcessManager;

import io.supertokens.totp.Totp;
import io.supertokens.pluginInterface.totp.TOTPDevice;
import io.supertokens.pluginInterface.totp.TOTPStorage;
import io.supertokens.pluginInterface.totp.TOTPUsedCode;
import io.supertokens.pluginInterface.totp.exception.DeviceAlreadyExistsException;
import io.supertokens.pluginInterface.totp.exception.TotpNotEnabledException;
import io.supertokens.pluginInterface.totp.exception.UnknownDeviceException;

public class TOTPStorageTest {

    public class TestSetupResult {
        public TOTPStorage storage;
        public TestingProcessManager.TestingProcess process;

        public TestSetupResult(TOTPStorage storage, TestingProcessManager.TestingProcess process) {
            this.storage = storage;
            this.process = process;
        }
    }

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
    public void createDeviceTests() throws Exception {
        TestSetupResult result = setup();
        TOTPStorage storage = result.storage;

        TOTPDevice device1 = new TOTPDevice("user", "d1", "secretKey", 30, 1, false);
        TOTPDevice device2 = new TOTPDevice("user", "d2", "secretKey", 30, 1, false);
        TOTPDevice device2Duplicate = new TOTPDevice("user", "d2", "secretKey", 30, 1, false);

        storage.createDevice(device1);

        TOTPDevice[] storedDevices = storage.getDevices("user");
        assert (storedDevices.length == 1);
        assert storedDevices[0].equals(device1);

        storage.createDevice(device2);
        storedDevices = storage.getDevices("user");

        assert (storedDevices.length == 2);
        assert storedDevices[0].equals(device1);
        assert storedDevices[1].equals(device2);

        assertThrows(DeviceAlreadyExistsException.class, () -> storage.createDevice(device2Duplicate));

        result.process.kill();
        assertNotNull(result.process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void verifyDeviceTests() throws Exception {
        TestSetupResult result = setup();
        TOTPStorage storage = result.storage;

        TOTPDevice device = new TOTPDevice("user", "device", "secretKey", 30, 1, false);
        storage.createDevice(device);

        TOTPDevice[] storedDevices = storage.getDevices("user");
        assert (storedDevices.length == 1);
        assert (!storedDevices[0].verified);

        // Verify the device:
        storage.markDeviceAsVerified("user", "device");

        storedDevices = storage.getDevices("user");
        assert (storedDevices.length == 1);
        assert (storedDevices[0].verified);

        // Try to verify a device that doesn't exist:
        assertThrows(UnknownDeviceException.class, () -> storage.markDeviceAsVerified("user", "non-existent-device"));
    }

    @Test
    public void deleteDeviceTests() throws Exception {
        TestSetupResult result = setup();
        TOTPStorage storage = result.storage;

        TOTPDevice device1 = new TOTPDevice("user", "device1", "sk1", 30, 1, false);
        TOTPDevice device2 = new TOTPDevice("user", "device2", "sk2", 30, 1, false);

        storage.createDevice(device1);
        storage.createDevice(device2);

        TOTPDevice[] storedDevices = storage.getDevices("user");
        assert (storedDevices.length == 2);

        // Try to delete a device for a user that doesn't exist:
        assertThrows(UnknownDeviceException.class, () -> storage.deleteDevice("non-existent-user", "device1"));

        // Try to delete a device that doesn't exist:
        assertThrows(UnknownDeviceException.class, () -> storage.deleteDevice("user", "non-existent-device"));

        // Successfully delete device1:
        storage.deleteDevice("user", "device1");

        storedDevices = storage.getDevices("user");
        assert (storedDevices.length == 1); // device2 should still be there

        long nextDay = System.currentTimeMillis() + 1000 * 60 * 60 * 24; // 1 day from now
        long now = System.currentTimeMillis();
        // Deleting all devices of a user should delete all related codes:
        {
            TOTPUsedCode validCode = new TOTPUsedCode("user", "valid-code", true, nextDay, now);
            TOTPUsedCode invalidCode = new TOTPUsedCode("user", "invalid-code", false, nextDay, now);
            storage.insertUsedCode(validCode);
            storage.insertUsedCode(invalidCode);

            storage.deleteDevice("user", "device2"); // delete device2 as well

            TOTPUsedCode[] newUsedCodes = storage.getNonExpiredUsedCodes("user");
            assert (newUsedCodes.length == 0);
        }
    }

    @Test
    public void updateDeviceNameTests() throws Exception {
        TestSetupResult result = setup();
        TOTPStorage storage = result.storage;

        TOTPDevice device = new TOTPDevice("user", "device", "secretKey", 30, 1, false);
        storage.createDevice(device);

        TOTPDevice[] storedDevices = storage.getDevices("user");
        assert (storedDevices.length == 1);
        assert (storedDevices[0].deviceName.equals("device"));

        // Try to update a device that doesn't exist:
        assertThrows(UnknownDeviceException.class,
                () -> storage.updateDeviceName("user", "non-existent-device", "new-device-name"));

        // Update the device name:
        storage.updateDeviceName("user", "device", "updated-device-name");

        storedDevices = storage.getDevices("user");
        assert (storedDevices.length == 1);
        assert (storedDevices[0].deviceName.equals("updated-device-name"));

        // Try to create a new device and rename it to the same name as an existing
        // device:
        TOTPDevice newDevice = new TOTPDevice("user", "new-device", "secretKey", 30, 1, false);
        storage.createDevice(newDevice);

        assertThrows(DeviceAlreadyExistsException.class,
                () -> storage.updateDeviceName("user", "new-device", "updated-device-name"));

        // Try to rename the device the same name (Should work at database level):
        storage.updateDeviceName("user", "updated-device-name", "updated-device-name");
    }

    @Test
    public void getDevicesTest() throws Exception {
        TestSetupResult result = setup();
        TOTPStorage storage = result.storage;

        TOTPDevice device1 = new TOTPDevice("user", "d1", "secretKey", 30, 1, false);
        TOTPDevice device2 = new TOTPDevice("user", "d2", "secretKey", 30, 1, false);

        storage.createDevice(device1);
        storage.createDevice(device2);

        TOTPDevice[] storedDevices = storage.getDevices("user");

        assert (storedDevices.length == 2);
        assert (storedDevices[0].deviceName.equals("d1"));
        assert (storedDevices[1].deviceName.equals("d2"));

        storedDevices = storage.getDevices("non-existent-user");
        assert (storedDevices.length == 0);
    }

    @Test
    public void insertUsedCodeTest() throws Exception {
        TestSetupResult result = setup();
        TOTPStorage storage = result.storage;
        long nextDay = System.currentTimeMillis() + 1000 * 60 * 60 * 24; // 1 day from now

        // Insert a long lasting valid code and check that it's returned when queried:
        {
            TOTPDevice device = new TOTPDevice("user", "device", "secretKey", 30, 1, false);
            TOTPUsedCode code = new TOTPUsedCode("user", "1234", true, nextDay, System.currentTimeMillis());

            storage.createDevice(device);
            storage.insertUsedCode(code);
            TOTPUsedCode[] usedCodes = storage.getNonExpiredUsedCodes("user");

            assert (usedCodes.length == 1);
            assert usedCodes[0].equals(code);
        }

        // Try to insert code when user doesn't have any device (i.e. TOTP not enabled)
        {
            storage.deleteDevice("user", "device");
            assertThrows(TotpNotEnabledException.class,
                    () -> storage.insertUsedCode(
                            new TOTPUsedCode("user", "1234", true, nextDay, System.currentTimeMillis())));
        }

        // Try to insert code after user has atleast one device (i.e. TOTP enabled)
        {
            TOTPDevice newDevice = new TOTPDevice("user", "new-device", "secretKey", 30, 1, false);
            storage.createDevice(newDevice);
            storage.insertUsedCode(new TOTPUsedCode("user", "1234", true, nextDay, System.currentTimeMillis()));
        }

        // Try to insert code when user doesn't exist:
        assertThrows(TotpNotEnabledException.class,
                () -> storage.insertUsedCode(
                        new TOTPUsedCode("non-existent-user", "1234", true, nextDay, System.currentTimeMillis())));
    }

    @Test
    public void getNonExpiredUsedCodesTest() throws Exception {
        TestSetupResult result = setup();
        TOTPStorage storage = result.storage;

        TOTPUsedCode[] usedCodes = storage.getNonExpiredUsedCodes("non-existent-user");
        assert (usedCodes.length == 0);

        long nextDay = System.currentTimeMillis() + 1000 * 60 * 60 * 24; // 1 day from now
        long prevDay = System.currentTimeMillis() - 1000 * 60 * 60 * 24; // 1 day ago
        long now = System.currentTimeMillis();

        TOTPDevice device = new TOTPDevice("user", "device", "secretKey", 30, 1, false);
        TOTPUsedCode validCode = new TOTPUsedCode("user", "code1", true, nextDay, now);
        TOTPUsedCode invalidCode = new TOTPUsedCode("user", "code2", false, nextDay, now);
        TOTPUsedCode expiredCode = new TOTPUsedCode("user", "expired-code", true, prevDay, now);
        TOTPUsedCode expiredInvalidCode = new TOTPUsedCode("user", "expired-invalid-code", false, prevDay, now);

        storage.createDevice(device);
        storage.insertUsedCode(validCode);
        storage.insertUsedCode(invalidCode);
        storage.insertUsedCode(expiredCode);
        storage.insertUsedCode(expiredInvalidCode);

        usedCodes = storage.getNonExpiredUsedCodes("user");
        assert (usedCodes.length == 2);
        assert (usedCodes[0].equals(validCode));
        assert (usedCodes[1].equals(invalidCode));
    }

    @Test
    public void removeExpiredCodesTest() throws Exception {
        TestSetupResult result = setup();
        TOTPStorage storage = result.storage;

        long now = System.currentTimeMillis();
        long nextDay = System.currentTimeMillis() + 1000 * 60 * 60 * 24; // 1 day from now
        long halfSecond = System.currentTimeMillis() + 500; // 500ms from now

        TOTPDevice device = new TOTPDevice("user", "device", "secretKey", 30, 1, false);
        TOTPUsedCode validCodeToLive = new TOTPUsedCode("user", "valid-code", true, nextDay, now);
        TOTPUsedCode invalidCodeToLive = new TOTPUsedCode("user", "invalid-code", false, nextDay, now);
        TOTPUsedCode validCodeToExpire = new TOTPUsedCode("user", "valid-code", true, halfSecond, now);
        TOTPUsedCode invalidCodeToExpire = new TOTPUsedCode("user", "invalid-code", false, halfSecond, now);

        storage.createDevice(device);
        storage.insertUsedCode(validCodeToLive);
        storage.insertUsedCode(invalidCodeToLive);
        storage.insertUsedCode(validCodeToExpire);
        storage.insertUsedCode(invalidCodeToExpire);

        TOTPUsedCode[] usedCodes = storage.getNonExpiredUsedCodes("user");
        assert (usedCodes.length == 4);

        // After 500ms seconds pass:
        Thread.sleep(500);

        storage.removeExpiredCodes();

        usedCodes = storage.getNonExpiredUsedCodes("user");
        assert (usedCodes.length == 2);
        assert (usedCodes[0].equals(validCodeToLive));
        assert (usedCodes[1].equals(invalidCodeToLive));
    }

    @Test
    public void deleteAllDataForUserTest() throws Exception {
        TestSetupResult result = setup();
        TOTPStorage storage = result.storage;

        long now = System.currentTimeMillis();
        long nextDay = now + 1000 * 60 * 60 * 24; // 1 day from now

        TOTPDevice device1 = new TOTPDevice("user", "d1", "secretKey", 30, 1, false);
        TOTPDevice device2 = new TOTPDevice("user", "d2", "secretKey", 30, 1, false);
        TOTPUsedCode validCode = new TOTPUsedCode("user", "d1-valid", true, nextDay, now);
        TOTPUsedCode invalidCode = new TOTPUsedCode("user", "invalid-code", false, nextDay, now);

        storage.createDevice(device1);
        storage.createDevice(device2);
        storage.insertUsedCode(validCode);
        storage.insertUsedCode(invalidCode);

        TOTPDevice[] storedDevices = storage.getDevices("user");
        TOTPUsedCode[] usedCodes = storage.getNonExpiredUsedCodes("user");

        assert (storedDevices.length == 2);
        assert (usedCodes.length == 2);

        storage.deleteAllDataForUser("user");

        storedDevices = storage.getDevices("user");
        usedCodes = storage.getNonExpiredUsedCodes("user");

        assert (storedDevices.length == 0);
        assert (usedCodes.length == 0);
    }
}
