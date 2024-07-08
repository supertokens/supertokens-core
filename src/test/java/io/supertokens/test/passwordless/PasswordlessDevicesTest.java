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
import io.supertokens.pluginInterface.passwordless.PasswordlessCode;
import io.supertokens.pluginInterface.passwordless.PasswordlessDevice;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.test.TestingProcessManager;
import io.supertokens.test.Utils;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import java.util.Iterator;
import java.util.List;

import static io.supertokens.test.passwordless.PasswordlessUtility.*;
import static org.junit.Assert.*;

/**
 * This unit test encompasses tests related to devices
 */
public class PasswordlessDevicesTest {

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
     * getDeviceWithCodesById
     * returns created device (with multiple codes)
     *
     * @throws Exception
     */
    @Test
    public void getDeviceWithCodesByValidId() throws Exception {

        int NUMBER_OF_CODES_TO_GENERATE = 5;

        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        Passwordless.CreateCodeResponse createCodeResponse = Passwordless.createCode(process.getProcess(), EMAIL, null,
                null, null);
        assertNotNull(createCodeResponse);

        // create multiple codes with this device
        for (int counter = 0; counter < NUMBER_OF_CODES_TO_GENERATE; counter++) {

            createCodeResponse = Passwordless.createCode(process.getProcess(), null, null, createCodeResponse.deviceId,
                    null);
            assertNotNull(createCodeResponse);

        }
        Passwordless.DeviceWithCodes deviceWithCodes = Passwordless.getDeviceWithCodesById(process.getProcess(),
                createCodeResponse.deviceId);
        PasswordlessDevice device = deviceWithCodes.device;

        // verify device retrieved
        assertNotNull(device);
        assertEquals(createCodeResponse.deviceIdHash, device.deviceIdHash);
        assertEquals(EMAIL, device.email);
        assertNull(device.phoneNumber);
        assertEquals(0, device.failedAttempts);

        PasswordlessCode[] codes = deviceWithCodes.codes;
        assertEquals(NUMBER_OF_CODES_TO_GENERATE + 1, codes.length);

        for (PasswordlessCode code : codes) {
            assertEquals(device.deviceIdHash, code.deviceIdHash);
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));

    }

    /**
     * returns null if no device exists
     *
     * @throws Exception
     */
    @Test
    public void getDeviceWithCodesByInvalidId() throws Exception {

        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        Passwordless.DeviceWithCodes deviceWithCodes = Passwordless.getDeviceWithCodesById(process.getProcess(),
                "KfW2w5otf+/hEpOsfzinNmD6BdlodqlaMegeJ6dbAwA=");
        // verify null device retrieved
        assertNull(deviceWithCodes);
        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));

    }

    /**
     * getDeviceWithCodesByIdHash
     * returns created device (with multiple codes)
     *
     * @throws Exception
     */
    @Test
    public void getDevicesFromHashIDWithCodes() throws Exception {

        int NUMBER_OF_CODES_TO_GENERATE = 5;

        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        Passwordless.CreateCodeResponse createCodeResponse = Passwordless.createCode(process.getProcess(), EMAIL, null,
                null, null);
        assertNotNull(createCodeResponse);

        // create multiple codes with this device
        for (int counter = 0; counter < NUMBER_OF_CODES_TO_GENERATE; counter++) {

            createCodeResponse = Passwordless.createCode(process.getProcess(), EMAIL, null, createCodeResponse.deviceId,
                    null);
            assertNotNull(createCodeResponse);

        }
        Passwordless.DeviceWithCodes deviceWithCodes = Passwordless.getDeviceWithCodesByIdHash(process.getProcess(),
                createCodeResponse.deviceIdHash);
        PasswordlessDevice device = deviceWithCodes.device;

        // verify device retrieved
        assertNotNull(device);
        assertEquals(createCodeResponse.deviceIdHash, device.deviceIdHash);
        assertEquals(EMAIL, device.email);
        assertNull(device.phoneNumber);
        assertEquals(0, device.failedAttempts);

        PasswordlessCode[] codes = deviceWithCodes.codes;
        assertEquals(NUMBER_OF_CODES_TO_GENERATE + 1, codes.length);

        for (PasswordlessCode code : codes) {
            assertEquals(device.deviceIdHash, code.deviceIdHash);
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));

    }

    /**
     * returns null if no device exists
     *
     * @throws Exception
     */
    @Test
    public void getDevicesFromInvalidHashID() throws Exception {

        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        Passwordless.DeviceWithCodes deviceWithCodes = Passwordless.getDeviceWithCodesByIdHash(process.getProcess(),
                "32");

        // verify null device retrieved
        assertNull(deviceWithCodes);
        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));

    }

    /**
     * getDevicesWithCodesByEmail
     * returns created device (with multiple codes)
     *
     * @throws Exception
     */
    @Test
    public void getDevicesFromEmailWithCodes() throws Exception {

        int NUMBER_OF_CODES_TO_GENERATE = 5;

        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        Passwordless.CreateCodeResponse createCodeResponse = Passwordless.createCode(process.getProcess(), EMAIL, null,
                null, null);
        assertNotNull(createCodeResponse);

        // create multiple codes with this device
        for (int counter = 0; counter < NUMBER_OF_CODES_TO_GENERATE; counter++) {

            createCodeResponse = Passwordless.createCode(process.getProcess(), EMAIL, null, null, null);
            assertNotNull(createCodeResponse);

        }
        List<Passwordless.DeviceWithCodes> list = Passwordless.getDevicesWithCodesByEmail(process.getProcess(), EMAIL);

        assertEquals(NUMBER_OF_CODES_TO_GENERATE + 1, list.size());
        Iterator<Passwordless.DeviceWithCodes> iterator = list.iterator();

        while (iterator.hasNext()) {
            Passwordless.DeviceWithCodes deviceWithCodes = iterator.next();

            PasswordlessDevice device = deviceWithCodes.device;

            // verify device retrieved
            assertNotNull(device);
            assertEquals(EMAIL, device.email);
            assertNull(device.phoneNumber);
            assertEquals(0, device.failedAttempts);

            PasswordlessCode[] codes = deviceWithCodes.codes;
            assertEquals(1, codes.length);
        }
        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));

    }

    /**
     * returns null if no device exists with email
     *
     * @throws Exception
     */
    @Test
    public void getDevicesFromInvalidEmail() throws Exception {

        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        List<Passwordless.DeviceWithCodes> list = Passwordless.getDevicesWithCodesByEmail(process.getProcess(),
                "aa@gmail.com");

        // verify null device retrieved
        assertEquals(0, list.size());
        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));

    }

    /**
     * getDevicesWithCodesByPhoneNumber
     * returns created device (with multiple codes)
     *
     * @throws Exception
     */
    @Test
    public void getDevicesFromPhoneNumberWithCodes() throws Exception {

        int NUMBER_OF_CODES_TO_GENERATE = 5;

        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        Passwordless.CreateCodeResponse createCodeResponse = Passwordless.createCode(process.getProcess(), null,
                PHONE_NUMBER, null, null);
        assertNotNull(createCodeResponse);

        // create multiple codes with this device
        for (int counter = 0; counter < NUMBER_OF_CODES_TO_GENERATE; counter++) {

            createCodeResponse = Passwordless.createCode(process.getProcess(), null, PHONE_NUMBER, null, null);
            assertNotNull(createCodeResponse);

        }
        List<Passwordless.DeviceWithCodes> list = Passwordless.getDevicesWithCodesByPhoneNumber(process.getProcess(),
                PHONE_NUMBER);

        assertEquals(NUMBER_OF_CODES_TO_GENERATE + 1, list.size());
        Iterator<Passwordless.DeviceWithCodes> iterator = list.iterator();

        while (iterator.hasNext()) {
            Passwordless.DeviceWithCodes deviceWithCodes = iterator.next();

            PasswordlessDevice device = deviceWithCodes.device;

            // verify device retrieved
            assertNotNull(device);
            assertNull(device.email);
            assertEquals(PHONE_NUMBER, device.phoneNumber);
            assertEquals(0, device.failedAttempts);

            PasswordlessCode[] codes = deviceWithCodes.codes;
            assertEquals(1, codes.length);
        }
        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));

    }

    /**
     * returns null if no device exists with phone
     *
     * @throws Exception
     */
    @Test
    public void getDevicesFromInvalidPhoneNumber() throws Exception {

        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        List<Passwordless.DeviceWithCodes> list = Passwordless.getDevicesWithCodesByPhoneNumber(process.getProcess(),
                "9231");

        // verify null device retrieved
        assertEquals(0, list.size());
        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));

    }

}
