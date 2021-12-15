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

import io.supertokens.passwordless.Passwordless;
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
     * returns created device (with multiple codes)
     *
     * @throws Exception
     */
    @Test
    public void getDevicesFromHashIDWithCodes() throws Exception {

        int NUMBER_OF_CODES_TO_GENERATE = 5;

        TestingProcessManager.TestingProcess process = startApplicationWithDefaultArgs();

        PasswordlessStorage storage = StorageLayer.getPasswordlessStorage(process.getProcess());

        Passwordless.CreateCodeResponse createCodeResponse = Passwordless.createCode(process.getProcess(), EMAIL, null,
                null, null);
        assertNotNull(createCodeResponse);

        // create multiple codes with this device
        for (int counter = 0; counter < NUMBER_OF_CODES_TO_GENERATE; counter++) {

            createCodeResponse = Passwordless.createCode(process.getProcess(), null, null, createCodeResponse.deviceId,
                    null);
            assertNotNull(createCodeResponse);

        }
        PasswordlessDevice device = storage.getDevice(createCodeResponse.deviceIdHash);

        // verify device retrieved
        assertNotNull(device);
        assertEquals(createCodeResponse.deviceIdHash, device.deviceIdHash);
        assertEquals(EMAIL, device.email);
        assertEquals(null, device.phoneNumber);
        assertEquals(0, device.failedAttempts);

        PasswordlessCode[] codes = storage.getCodesOfDevice(device.deviceIdHash);
        assertEquals(NUMBER_OF_CODES_TO_GENERATE + 1, codes.length);

        for (int counter = 0; counter < codes.length; counter++) {

            PasswordlessCode code = codes[counter];
            assertEquals(device.deviceIdHash, code.deviceIdHash);

        }

        killApplication(process);

    }

    /**
     * returns null if no device exists
     *
     * @throws Exception
     */
    @Test
    public void getDevicesFromInvalidHashID() throws Exception {

        TestingProcessManager.TestingProcess process = startApplicationWithDefaultArgs();

        PasswordlessStorage storage = StorageLayer.getPasswordlessStorage(process.getProcess());

        PasswordlessDevice device = storage.getDevice("32");

        // verify null device retrieved
        assertNull(device);
        killApplication(process);

    }

    /**
     * getDevicesWithCodesByEmail
     */

    /**
     * returns created device (with multiple codes)
     *
     * @throws Exception
     */
    @Test
    public void getDevicesFromIDWithCodes() throws Exception {

        int NUMBER_OF_CODES_TO_GENERATE = 5;

        TestingProcessManager.TestingProcess process = startApplicationWithDefaultArgs();

        PasswordlessStorage storage = StorageLayer.getPasswordlessStorage(process.getProcess());

        Passwordless.CreateCodeResponse createCodeResponse = Passwordless.createCode(process.getProcess(), EMAIL, null,
                null, null);
        assertNotNull(createCodeResponse);

        // create multiple codes with this device
        for (int counter = 0; counter < NUMBER_OF_CODES_TO_GENERATE; counter++) {

            createCodeResponse = Passwordless.createCode(process.getProcess(), EMAIL, null, createCodeResponse.deviceId,
                    null);
            assertNotNull(createCodeResponse);

        }
        PasswordlessDevice[] devices = storage.getDevicesByEmail(EMAIL);

        for (int counter = 0; counter < devices.length; counter++) {
            PasswordlessDevice device = devices[counter];

            // verify device retrieved
            assertNotNull(device);
            assertEquals(createCodeResponse.deviceIdHash, device.deviceIdHash);
            assertEquals(EMAIL, device.email);
            assertEquals(null, device.phoneNumber);
            assertEquals(0, device.failedAttempts);

            PasswordlessCode[] codes = storage.getCodesOfDevice(device.deviceIdHash);
            assertEquals(NUMBER_OF_CODES_TO_GENERATE + 1, codes.length);

            for (int counter_code = 0; counter_code < codes.length; counter_code++) {

                PasswordlessCode code = codes[counter];
                assertEquals(device.deviceIdHash, code.deviceIdHash);

            }
        }

        killApplication(process);

    }

    /**
     * returns null if no device exists with email
     *
     * @throws Exception
     */
    @Test
    public void getDevicesFromInvalidEmail() throws Exception {

        TestingProcessManager.TestingProcess process = startApplicationWithDefaultArgs();

        PasswordlessStorage storage = StorageLayer.getPasswordlessStorage(process.getProcess());

        PasswordlessDevice[] devices = storage.getDevicesByEmail("aa@aa.com");

        // verify null device retrieved
        assertEquals(0, devices.length);
        killApplication(process);

    }

    /**
     * getDevicesWithCodesByPhoneNumber
     */

    /**
     * returns created device (with multiple codes)
     *
     * @throws Exception
     */
    @Test
    public void getDevicesFromPhoneNumberWithCodes() throws Exception {

        int NUMBER_OF_CODES_TO_GENERATE = 5;

        TestingProcessManager.TestingProcess process = startApplicationWithDefaultArgs();

        PasswordlessStorage storage = StorageLayer.getPasswordlessStorage(process.getProcess());

        Passwordless.CreateCodeResponse createCodeResponse = Passwordless.createCode(process.getProcess(), null,
                PHONE_NUMBER, null, null);
        assertNotNull(createCodeResponse);

        // create multiple codes with this device
        for (int counter = 0; counter < NUMBER_OF_CODES_TO_GENERATE; counter++) {

            createCodeResponse = Passwordless.createCode(process.getProcess(), null, PHONE_NUMBER,
                    createCodeResponse.deviceId, null);
            assertNotNull(createCodeResponse);

        }
        PasswordlessDevice[] devices = storage.getDevicesByPhoneNumber(PHONE_NUMBER);

        for (int counter = 0; counter < devices.length; counter++) {
            PasswordlessDevice device = devices[counter];

            // verify device retrieved
            assertNotNull(device);
            assertEquals(createCodeResponse.deviceIdHash, device.deviceIdHash);
            assertEquals(null, device.email);
            assertEquals(PHONE_NUMBER, device.phoneNumber);
            assertEquals(0, device.failedAttempts);

            PasswordlessCode[] codes = storage.getCodesOfDevice(device.deviceIdHash);
            assertEquals(NUMBER_OF_CODES_TO_GENERATE + 1, codes.length);

            for (int counter_code = 0; counter_code < codes.length; counter_code++) {

                PasswordlessCode code = codes[counter];
                assertEquals(device.deviceIdHash, code.deviceIdHash);

            }
        }

        killApplication(process);

    }

    /**
     * returns null if no device exists with phone
     *
     * @throws Exception
     */
    @Test
    public void getDevicesFromInvalidPhoneNumber() throws Exception {

        TestingProcessManager.TestingProcess process = startApplicationWithDefaultArgs();

        PasswordlessStorage storage = StorageLayer.getPasswordlessStorage(process.getProcess());

        PasswordlessDevice[] devices = storage.getDevicesByPhoneNumber("9231");

        // verify null device retrieved
        assertEquals(0, devices.length);
        killApplication(process);

    }

}
