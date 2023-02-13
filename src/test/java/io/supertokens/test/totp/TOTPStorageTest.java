package io.supertokens.test.totp;

import static org.junit.Assert.assertNotNull; // Not sure about this

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

        TOTPDevice device1 = new TOTPDevice("d1", "user", "secretKey", 30, 1, false);
        TOTPDevice device2 = new TOTPDevice("d2", "user", "secretKey", 30, 1, false);
        TOTPDevice device2Duplicate = new TOTPDevice("d2", "user", "secretKey", 30, 1, false);

        storage.createDevice(device1);

        TOTPDevice[] storedDevices = storage.getDevices("user");
        TOTPDevice storedDevice = storedDevices[0];
        assert (storedDevices.length == 1);
        assert (storedDevice.deviceName.equals("d1"));
        assert (storedDevice.userId.equals("user"));
        assert (storedDevice.secretKey.equals("secretKey"));
        assert (storedDevice.period == 30);
        assert (storedDevice.skew == 1);
        assert (storedDevice.verified == false);

        storage.createDevice(device2);
        storedDevices = storage.getDevices("user");
        assert (storedDevices.length == 2);

        try {
            storage.createDevice(device2Duplicate);
            assert (false);
        } catch (DeviceAlreadyExistsException e) {
            assert (true);
        }

        result.process.kill();
        assertNotNull(result.process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void verifyDeviceTests() throws Exception {
        TestSetupResult result = setup();
        TOTPStorage storage = result.storage;

        TOTPDevice device = new TOTPDevice("device", "user", "secretKey", 30, 1, false);
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
        try {
            storage.markDeviceAsVerified("user", "non-existent-device");
            assert (false);
        } catch (Exception e) {
            assert (true);
        }
    }

    @Test
    public void deleteDeviceTests() throws Exception {
        TestSetupResult result = setup();
        TOTPStorage storage = result.storage;

        TOTPDevice device = new TOTPDevice("device", "user", "secretKey", 30, 1, false);
        storage.createDevice(device);

        TOTPDevice[] storedDevices = storage.getDevices("user");
        assert (storedDevices.length == 1);

        // Try to delete a device that doesn't exist:
        try {
            storage.deleteDevice("user", "non-existent-device");
            assert (false);
        } catch (Exception e) {
            assert (true);
        }

        // Delete the device:
        storage.deleteDevice("user", "device");

        storedDevices = storage.getDevices("user");
        assert (storedDevices.length == 0);
    }

    @Test
    public void updateDeviceNametests() throws Exception {
        TestSetupResult result = setup();
        TOTPStorage storage = result.storage;

        TOTPDevice device = new TOTPDevice("device", "user", "secretKey", 30, 1, false);
        storage.createDevice(device);

        TOTPDevice[] storedDevices = storage.getDevices("user");
        assert (storedDevices.length == 1);
        assert (storedDevices[0].deviceName.equals("device"));

        // Try to update a device that doesn't exist:
        try {
            storage.updateDeviceName("user", "non-existent-device", "new-device-name");
            assert (false);
        } catch (Exception e) {
            assert (true);
        }

        // Update the device name:
        storage.updateDeviceName("user", "device", "new-device-name");

        storedDevices = storage.getDevices("user");
        assert (storedDevices.length == 1);
        assert (storedDevices[0].deviceName.equals("new-device-name"));
    }

    @Test
    public void getDevicesTest() throws Exception {
        TestSetupResult result = setup();
        TOTPStorage storage = result.storage;

        TOTPDevice device1 = new TOTPDevice("d1", "user", "secretKey", 30, 1, false);
        TOTPDevice device2 = new TOTPDevice("d2", "user", "secretKey", 30, 1, false);

        storage.createDevice(device1);
        storage.createDevice(device2);

        TOTPDevice[] storedDevices = storage.getDevices("user");

        assert (storedDevices.length == 2);
        assert (storedDevices[0].deviceName.equals("d1"));
        assert (storedDevices[1].deviceName.equals("d2"));
    }

    @Test
    public void insertUsedCodeTest() throws Exception {
        TestSetupResult result = setup();
        TOTPStorage storage = result.storage;

        TOTPDevice device = new TOTPDevice("device", "user", "secretKey", 30, 1, false);
        TOTPUsedCode code = new TOTPUsedCode("user", "1234", true, 1);

        storage.createDevice(device);
        boolean isInserted = storage.insertUsedCode(code);
        TOTPUsedCode[] usedCodes = storage.getUsedCodes("user");

        assert (isInserted);
        assert (usedCodes.length == 1);
        assert (usedCodes[0].userId.equals("user"));
        assert (usedCodes[0].code.equals("1234"));
        assert (usedCodes[0].isValidCode);
        assert (usedCodes[0].expiryTime == 1);

        // FIXME: Next two features aren't working because foreign key constraint is not
        // working in tests:

        // Deleting the device should delete the used codes:
        storage.deleteDevice("user", "device");
        usedCodes = storage.getUsedCodes("user");
        assert (usedCodes.length == 0);

        // Try to insert code when device (userId) doesn't exist:
        try {
            // Need to run `PRAGMA foreign_keys = ON;` then only will throws exception. But
            // unable to setup that also in tests.
            storage.insertUsedCode(new TOTPUsedCode("non-existent-user", "1234", true, 1));
            assert (false);
        } catch (Exception e) {
            assert (true);
        }
    }

    @Test
    public void getUsedCodesTest() throws Exception {
        TestSetupResult result = setup();
        TOTPStorage storage = result.storage;

        TOTPDevice device = new TOTPDevice("device", "user", "secretKey", 30, 1, false);
        TOTPUsedCode code1 = new TOTPUsedCode("user", "code1", true, 1);
        TOTPUsedCode code2 = new TOTPUsedCode("user", "code2", false, 1);

        storage.createDevice(device);
        storage.insertUsedCode(code1);
        storage.insertUsedCode(code2);

        TOTPUsedCode[] usedCodes = storage.getUsedCodes("user");
        assert (usedCodes.length == 2);
        assert (usedCodes[0].code.equals("code1"));
        assert (usedCodes[0].isValidCode);
        assert (usedCodes[1].code.equals("code2"));
        assert (!usedCodes[1].isValidCode);
    }

    @Test
    public void removeExpiredCodesTest() throws Exception {
        TestSetupResult result = setup();
        TOTPStorage storage = result.storage;

        TOTPDevice device = new TOTPDevice("device", "user", "secretKey", 30, 1, false);
        TOTPUsedCode codeToDelete = new TOTPUsedCode("user", "codeToDelete", true, 1);
        TOTPUsedCode codeToKeep = new TOTPUsedCode("user", "codeToKeep", false, 1);

        storage.createDevice(device);
        storage.insertUsedCode(codeToDelete);
        storage.insertUsedCode(codeToKeep);

        TOTPUsedCode[] usedCodes = storage.getUsedCodes("user");
        assert (usedCodes.length == 2);

        storage.removeExpiredCodes();

        usedCodes = storage.getUsedCodes("user");
        assert (usedCodes.length == 1);
        assert (usedCodes[0].code.equals("codeToKeep"));
    }
}
