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

package io.supertokens.test.passwordless;

import io.supertokens.ProcessState;
import io.supertokens.passwordless.Passwordless;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifier;
import io.supertokens.pluginInterface.passwordless.PasswordlessCode;
import io.supertokens.pluginInterface.passwordless.PasswordlessDevice;
import io.supertokens.pluginInterface.passwordless.PasswordlessStorage;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.test.TestingProcessManager;
import io.supertokens.test.Utils;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import static io.supertokens.test.passwordless.PasswordlessUtility.EMAIL;
import static io.supertokens.test.passwordless.PasswordlessUtility.PHONE_NUMBER;
import static org.junit.Assert.*;

/**
 * This UT encompasses all tests related to remove code
 */

public class PasswordlessRemoveCodeTest {

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

    /**
     * deletes code and leaves device if other code exists
     *
     * @throws Exception
     */
    @Test
    public void deleteCodeAndLeaveDevices() throws Exception {

        int NUMBER_OF_CODES_TO_GENERATE = 5;

        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        PasswordlessStorage storage = (PasswordlessStorage) StorageLayer.getStorage(process.getProcess());

        Passwordless.CreateCodeResponse createCodeResponse = Passwordless.createCode(process.getProcess(), null,
                PHONE_NUMBER, null, null);
        assertNotNull(createCodeResponse);

        // create multiple codes with this device
        for (int counter = 0; counter < NUMBER_OF_CODES_TO_GENERATE; counter++) {

            createCodeResponse = Passwordless.createCode(process.getProcess(), null, PHONE_NUMBER,
                    createCodeResponse.deviceId, null);
            assertNotNull(createCodeResponse);

        }

        Passwordless.removeCode(process.getProcess(), createCodeResponse.codeId);

        PasswordlessDevice[] devices = storage.getDevicesByPhoneNumber(new TenantIdentifier(null, null, null),
                PHONE_NUMBER);

        for (int counter = 0; counter < devices.length; counter++) {
            PasswordlessDevice device = devices[counter];

            // verify device retrieved
            assertNotNull(device);
            assertEquals(createCodeResponse.deviceIdHash, device.deviceIdHash);
            assertNull(device.email);
            assertEquals(PHONE_NUMBER, device.phoneNumber);
            assertEquals(0, device.failedAttempts);

            PasswordlessCode[] codes = storage.getCodesOfDevice(new TenantIdentifier(null, null, null),
                    device.deviceIdHash);
            assertEquals(NUMBER_OF_CODES_TO_GENERATE, codes.length);

            for (int counter_code = 0; counter_code < codes.length; counter_code++) {

                PasswordlessCode code = codes[counter];
                assertEquals(device.deviceIdHash, code.deviceIdHash);

            }
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));

    }

    /**
     * deletes code and cleans device if no other code exists
     *
     * @throws Exception
     */
    @Test
    public void deleteCodeAndCleansDevice() throws Exception {

        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        PasswordlessStorage storage = (PasswordlessStorage) StorageLayer.getStorage(process.getProcess());

        Passwordless.CreateCodeResponse createCodeResponse = Passwordless.createCode(process.getProcess(), null,
                PHONE_NUMBER, null, null);
        assertNotNull(createCodeResponse);

        Passwordless.removeCode(process.getProcess(), createCodeResponse.codeId);

        PasswordlessDevice[] devices = storage.getDevicesByPhoneNumber(new TenantIdentifier(null, null, null),
                PHONE_NUMBER);
        assertEquals(0, devices.length);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));

    }

    /**
     * no op if code doesn't exist
     *
     * @throws Exception
     */
    @Test
    public void doNothingIfCodeDoesNotExist() throws Exception {

        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        PasswordlessStorage storage = (PasswordlessStorage) StorageLayer.getStorage(process.getProcess());

        Passwordless.CreateCodeResponse createCodeResponse = Passwordless.createCode(process.getProcess(), null,
                PHONE_NUMBER, null, null);
        assertNotNull(createCodeResponse);

        Passwordless.removeCode(process.getProcess(), "1234");

        PasswordlessDevice[] devices = storage.getDevicesByPhoneNumber(new TenantIdentifier(null, null, null),
                PHONE_NUMBER);
        assertEquals(1, devices.length);

        PasswordlessCode[] codes = storage.getCodesOfDevice(new TenantIdentifier(null, null, null),
                createCodeResponse.deviceIdHash);
        assertEquals(1, codes.length);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));

    }

    /**
     * removeCodesByEmail
     * removes devices with matching email (leaving others)
     *
     * @throws Exception
     */
    @Test
    public void removeDevicesFromEmail() throws Exception {

        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        PasswordlessStorage storage = (PasswordlessStorage) StorageLayer.getStorage(process.getProcess());

        String alternate_email = "alternateTest@example.com";

        Passwordless.CreateCodeResponse createCodeResponse = Passwordless.createCode(process.getProcess(), EMAIL, null,
                null, null);
        assertNotNull(createCodeResponse);

        createCodeResponse = Passwordless.createCode(process.getProcess(), EMAIL, null, null, null);
        assertNotNull(createCodeResponse);

        createCodeResponse = Passwordless.createCode(process.getProcess(), alternate_email, null, null, null);
        assertNotNull(createCodeResponse);

        Passwordless.removeCodesByEmail(process.getProcess(), EMAIL);

        PasswordlessDevice[] devices = storage.getDevicesByEmail(new TenantIdentifier(null, null, null), EMAIL);
        assertEquals(0, devices.length);

        devices = storage.getDevicesByEmail(new TenantIdentifier(null, null, null), alternate_email);
        assertEquals(1, devices.length);

        PasswordlessCode[] codes = storage.getCodesOfDevice(new TenantIdentifier(null, null, null),
                devices[0].deviceIdHash);
        assertEquals(1, codes.length);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));

    }

    /**
     * removeCodesByPhoneNumber
     * removes devices with matching phone number (leaving others)
     *
     * @throws Exception
     */
    @Test
    public void removeDevicesFromPhoneNumber() throws Exception {

        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        PasswordlessStorage storage = (PasswordlessStorage) StorageLayer.getStorage(process.getProcess());

        String alternate_phoneNumber = "+442071838751";

        Passwordless.CreateCodeResponse createCodeResponse = Passwordless.createCode(process.getProcess(), null,
                PHONE_NUMBER, null, null);
        assertNotNull(createCodeResponse);

        createCodeResponse = Passwordless.createCode(process.getProcess(), null, PHONE_NUMBER, null, null);
        assertNotNull(createCodeResponse);

        createCodeResponse = Passwordless.createCode(process.getProcess(), null, alternate_phoneNumber, null, null);
        assertNotNull(createCodeResponse);

        Passwordless.removeCodesByPhoneNumber(process.getProcess(), PHONE_NUMBER);

        PasswordlessDevice[] devices = storage.getDevicesByPhoneNumber(new TenantIdentifier(null, null, null),
                PHONE_NUMBER);
        assertEquals(0, devices.length);

        devices = storage.getDevicesByPhoneNumber(new TenantIdentifier(null, null, null), alternate_phoneNumber);
        assertEquals(1, devices.length);

        PasswordlessCode[] codes = storage.getCodesOfDevice(new TenantIdentifier(null, null, null),
                devices[0].deviceIdHash);
        assertEquals(1, codes.length);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));

    }
}
