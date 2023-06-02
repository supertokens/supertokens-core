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

/**
 * This test class encompasses tests for create code
 */

package io.supertokens.test.passwordless;

import io.supertokens.ProcessState;
import io.supertokens.passwordless.Passwordless;
import io.supertokens.passwordless.exceptions.RestartFlowException;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifier;
import io.supertokens.pluginInterface.passwordless.PasswordlessCode;
import io.supertokens.pluginInterface.passwordless.PasswordlessDevice;
import io.supertokens.pluginInterface.passwordless.PasswordlessStorage;
import io.supertokens.pluginInterface.passwordless.exception.DuplicateLinkCodeHashException;
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

public class PasswordlessCreateCodeTest {

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
     * Create single device using single email
     *
     * @throws Exception
     */
    @Test
    public void testCreateCodeWithEmail() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        PasswordlessStorage storage = (PasswordlessStorage) StorageLayer.getStorage(process.getProcess());

        // create code
        Passwordless.CreateCodeResponse createCodeResponse = Passwordless.createCode(process.getProcess(), EMAIL, null,
                null, null);
        assertNotNull(createCodeResponse);

        // verify single device created
        PasswordlessDevice[] devices = storage.getDevicesByEmail(new TenantIdentifier(null, null, null), EMAIL);
        assertEquals(1, devices.length);

        // device assertions
        PasswordlessDevice device = devices[0];
        assertEquals(createCodeResponse.deviceIdHash, device.deviceIdHash);
        assertEquals(EMAIL, device.email);
        assertNull(device.phoneNumber);
        assertEquals(0, device.failedAttempts);

        // code assertions
        PasswordlessCode[] codes = storage.getCodesOfDevice(new TenantIdentifier(null, null, null),
                device.deviceIdHash);
        assertEquals(1, codes.length);

        PasswordlessCode code = codes[0];
        assertEquals(device.deviceIdHash, code.deviceIdHash);
        assertEquals(createCodeResponse.codeId, code.id);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    /**
     * Create multiple devices using a single email
     *
     * @throws Exception
     */
    @Test
    public void testCreateCodeForMultipleDevicesWithSingleEmail() throws Exception {
        final int NUMBER_OF_DEVICES_TO_CREATE = 5;

        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }
        PasswordlessStorage storage = (PasswordlessStorage) StorageLayer.getStorage(process.getProcess());

        // create 'n' devices
        for (int counter = 1; counter <= NUMBER_OF_DEVICES_TO_CREATE; counter++) {

            Passwordless.CreateCodeResponse codeResponse = Passwordless.createCode(process.getProcess(), EMAIL, null,
                    null, null);
            assertNotNull(codeResponse);

        }
        PasswordlessDevice[] devices = storage.getDevicesByEmail(new TenantIdentifier(null, null, null), EMAIL);
        assertEquals(NUMBER_OF_DEVICES_TO_CREATE, devices.length);
        for (int counter = 0; counter < NUMBER_OF_DEVICES_TO_CREATE; counter++) {

            PasswordlessDevice device = devices[counter];
            assertEquals(EMAIL, device.email);
            assertNull(device.phoneNumber);
            assertEquals(0, device.failedAttempts);

            PasswordlessCode[] codes = storage.getCodesOfDevice(new TenantIdentifier(null, null, null),
                    device.deviceIdHash);
            assertEquals(1, codes.length);

            PasswordlessCode code = codes[0];
            assertEquals(device.deviceIdHash, code.deviceIdHash);
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    /**
     * Create single device code with single phone number
     *
     * @throws Exception
     */
    @Test
    public void testCreateCodeWithPhoneNumber() throws Exception {

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

        PasswordlessDevice[] devices = storage.getDevicesByPhoneNumber(new TenantIdentifier(null, null, null),
                PHONE_NUMBER);
        assertEquals(1, devices.length);

        PasswordlessDevice device = devices[0];
        assertEquals(createCodeResponse.deviceIdHash, device.deviceIdHash);
        assertNull(device.email);
        assertEquals(PHONE_NUMBER, device.phoneNumber);
        assertEquals(0, device.failedAttempts);

        PasswordlessCode[] codes = storage.getCodesOfDevice(new TenantIdentifier(null, null, null),
                device.deviceIdHash);
        assertEquals(1, codes.length);

        PasswordlessCode code = codes[0];
        assertEquals(device.deviceIdHash, code.deviceIdHash);
        assertEquals(createCodeResponse.codeId, code.id);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    /**
     * Create multiple devices using a single phone number
     *
     * @throws Exception
     */

    @Test
    public void testCreateCodeForMultipleDevicesWithSinglePhoneNumber() throws Exception {
        final int NUMBER_OF_DEVICES_TO_CREATE = 5;

        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }
        PasswordlessStorage storage = (PasswordlessStorage) StorageLayer.getStorage(process.getProcess());

        for (int counter = 1; counter <= NUMBER_OF_DEVICES_TO_CREATE; counter++) {

            Passwordless.CreateCodeResponse codeResponse = Passwordless.createCode(process.getProcess(), null,
                    PHONE_NUMBER, null, null);
            assertNotNull(codeResponse);
        }

        PasswordlessDevice[] devices = storage.getDevicesByPhoneNumber(new TenantIdentifier(null, null, null),
                PHONE_NUMBER);
        assertEquals(NUMBER_OF_DEVICES_TO_CREATE, devices.length);

        for (int counter = 0; counter < NUMBER_OF_DEVICES_TO_CREATE; counter++) {

            PasswordlessDevice device = devices[counter];
            assertNull(device.email);
            assertEquals(PHONE_NUMBER, device.phoneNumber);
            assertEquals(0, device.failedAttempts);

            PasswordlessCode[] codes = storage.getCodesOfDevice(new TenantIdentifier(null, null, null),
                    device.deviceIdHash);
            assertEquals(1, codes.length);

            PasswordlessCode code = codes[0];
            assertEquals(device.deviceIdHash, code.deviceIdHash);
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    /**
     * Create single code with device ID
     *
     * @throws Exception
     */
    @Test
    public void testCreateCodeWithDeviceId() throws Exception {

        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }
        PasswordlessStorage storage = (PasswordlessStorage) StorageLayer.getStorage(process.getProcess());

        Passwordless.CreateCodeResponse createCodeResponse = Passwordless.createCode(process.getProcess(), null,
                PHONE_NUMBER, null, null);
        Passwordless.CreateCodeResponse resendCodeResponse = Passwordless.createCode(process.getProcess(), null, null,
                createCodeResponse.deviceId, null);

        assertNotNull(resendCodeResponse);

        PasswordlessDevice[] devices = storage.getDevicesByPhoneNumber(new TenantIdentifier(null, null, null),
                PHONE_NUMBER);
        assertEquals(1, devices.length);

        PasswordlessDevice device = devices[0];
        assertEquals(resendCodeResponse.deviceIdHash, device.deviceIdHash);
        assertNull(device.email);
        assertEquals(PHONE_NUMBER, device.phoneNumber);
        assertEquals(0, device.failedAttempts);

        PasswordlessCode[] codes = storage.getCodesOfDevice(new TenantIdentifier(null, null, null),
                device.deviceIdHash);
        assertEquals(2, codes.length);

        for (PasswordlessCode code : codes) {
            assertEquals(device.deviceIdHash, code.deviceIdHash);
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    /**
     * create code with same deviceId+userInputCode twice -> DuplicateLinkCodeHashException
     *
     * @throws Exception
     */
    @Test
    public void testCreateCodeWithSameDeviceIdAndUserInputCode() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        Passwordless.CreateCodeResponse codeResponse = Passwordless.createCode(process.getProcess(), null, PHONE_NUMBER,
                null, null);
        Exception error = null;

        try {
            Passwordless.createCode(process.getProcess(), null, null, codeResponse.deviceId,
                    codeResponse.userInputCode);
        } catch (Exception e) {
            error = e;
        }
        assertNotNull(error);
        assert (error instanceof DuplicateLinkCodeHashException);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    /**
     * Create code for non existing device
     *
     * @throws Exception
     */
    @Test
    public void testCreateCodeResendWithNotExistingDeviceId() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        Exception error = null;

        try {
            Passwordless.createCode(process.getProcess(), null, null, "JWlE/V+Uz8qgaTyFkzOI4FfRrU6fBH85ve2GunoPpz0=",
                    null);
        } catch (Exception ex) {
            error = ex;
        }
        assertNotNull(error);
        assert (error instanceof RestartFlowException);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

}
