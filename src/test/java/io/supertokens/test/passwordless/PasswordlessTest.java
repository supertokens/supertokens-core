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

import io.supertokens.Main;
import io.supertokens.ProcessState;
import io.supertokens.config.Config;
import io.supertokens.passwordless.Passwordless;
import io.supertokens.passwordless.exceptions.ExpiredUserInputCodeException;
import io.supertokens.passwordless.exceptions.IncorrectUserInputCodeException;
import io.supertokens.passwordless.exceptions.RestartFlowException;
import io.supertokens.passwordless.Passwordless.ConsumeCodeResponse;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.pluginInterface.emailpassword.exceptions.DuplicateEmailException;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.passwordless.PasswordlessCode;
import io.supertokens.pluginInterface.passwordless.PasswordlessDevice;
import io.supertokens.pluginInterface.passwordless.PasswordlessStorage;
import io.supertokens.pluginInterface.passwordless.UserInfo;
import io.supertokens.pluginInterface.passwordless.exception.DuplicateLinkCodeHashException;
import io.supertokens.pluginInterface.passwordless.exception.DuplicatePhoneNumberException;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.test.TestingProcessManager;
import io.supertokens.test.Utils;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import java.util.Arrays;
import java.util.Iterator;

import static org.junit.Assert.*;

public class PasswordlessTest {

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

    // CONSTANTS
    final String EMAIL = "test@example.com";
    final String PHONE_NUMBER = "+442071838750";

    /**
     * CREATE CODE SECTION BEGINS
     */

    @Test
    public void testCreateCodeWithEmail() throws Exception {
        String[] args = { "../" };

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        PasswordlessStorage storage = StorageLayer.getPasswordlessStorage(process.getProcess());

        String email = "test@example.com";

        Passwordless.CreateCodeResponse createCodeResponse = Passwordless.createCode(process.getProcess(), email, null,
                null, null);
        assertNotNull(createCodeResponse);

        PasswordlessDevice[] devices = storage.getDevicesByEmail(email);
        assertEquals(1, devices.length);

        PasswordlessDevice device = devices[0];
        assertEquals(createCodeResponse.deviceIdHash, device.deviceIdHash);
        assertEquals(email, device.email);
        assertEquals(null, device.phoneNumber);
        assertEquals(0, device.failedAttempts);

        PasswordlessCode[] codes = storage.getCodesOfDevice(device.deviceIdHash);
        assertEquals(1, codes.length);

        PasswordlessCode code = codes[0];
        assertEquals(device.deviceIdHash, code.deviceIdHash);
        assertEquals(createCodeResponse.codeId, code.id);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    /**
     * Create multiple devices using a single email
     * TODO: review
     * 
     * @throws Exception
     */
    @Test
    public void testCreateCodeForMultipleDevicesWithSingleEmail() throws Exception {
        String[] args = { "../" };
        final int NUMBER_OF_DEVICES_TO_CREATE = 5;

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        PasswordlessStorage storage = StorageLayer.getPasswordlessStorage(process.getProcess());

        String email = "test@example.com";

        for (int counter = 1; counter < NUMBER_OF_DEVICES_TO_CREATE; counter++) {

            Passwordless.CreateCodeResponse codeResponse = Passwordless.createCode(process.getProcess(), email, null,
                    null, null);
            assertNotNull(codeResponse);

            PasswordlessDevice[] devices = storage.getDevicesByEmail(email);
            assertEquals(counter, devices.length);

            PasswordlessDevice device = devices[counter - 1];
            assertEquals(codeResponse.deviceIdHash, device.deviceIdHash);
            assertEquals(email, device.email);
            assertEquals(null, device.phoneNumber);
            assertEquals(0, device.failedAttempts);

            PasswordlessCode[] codes = storage.getCodesOfDevice(device.deviceIdHash);
            assertEquals(1, codes.length);

            PasswordlessCode code = codes[0];
            assertEquals(device.deviceIdHash, code.deviceIdHash);
            assertEquals(codeResponse.codeId, code.id);
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testCreateCodeWithPhoneNumber() throws Exception {
        String[] args = { "../" };

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        PasswordlessStorage storage = StorageLayer.getPasswordlessStorage(process.getProcess());

        String phoneNumber = "+442071838750";

        Passwordless.CreateCodeResponse createCodeResponse = Passwordless.createCode(process.getProcess(), null,
                phoneNumber, null, null);
        assertNotNull(createCodeResponse);

        PasswordlessDevice[] devices = storage.getDevicesByPhoneNumber(phoneNumber);
        assertEquals(1, devices.length);

        PasswordlessDevice device = devices[0];
        assertEquals(createCodeResponse.deviceIdHash, device.deviceIdHash);
        assertEquals(null, device.email);
        assertEquals(phoneNumber, device.phoneNumber);
        assertEquals(0, device.failedAttempts);

        PasswordlessCode[] codes = storage.getCodesOfDevice(device.deviceIdHash);
        assertEquals(1, codes.length);

        PasswordlessCode code = codes[0];
        assertEquals(device.deviceIdHash, code.deviceIdHash);
        assertEquals(createCodeResponse.codeId, code.id);
        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    /**
     * Create multiple devices using a single phone number
     * TODO: review
     * 
     * @throws Exception
     */

    @Test
    public void testCreateCodeForMultipleDevicesWithSinglePhoneNumber() throws Exception {
        String[] args = { "../" };
        final int NUMBER_OF_DEVICES_TO_CREATE = 5;

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        PasswordlessStorage storage = StorageLayer.getPasswordlessStorage(process.getProcess());

        String phoneNumber = "+442071838750";

        for (int counter = 1; counter < NUMBER_OF_DEVICES_TO_CREATE; counter++) {

            Passwordless.CreateCodeResponse codeResponse = Passwordless.createCode(process.getProcess(), null,
                    phoneNumber, null, null);
            assertNotNull(codeResponse);

            PasswordlessDevice[] devices = storage.getDevicesByPhoneNumber(phoneNumber);
            assertEquals(counter, devices.length);

            PasswordlessDevice device = devices[counter - 1];
            assertEquals(codeResponse.deviceIdHash, device.deviceIdHash);
            assertEquals(null, device.email);
            assertEquals(phoneNumber, device.phoneNumber);
            assertEquals(0, device.failedAttempts);

            PasswordlessCode[] codes = storage.getCodesOfDevice(device.deviceIdHash);
            assertEquals(1, codes.length);

            PasswordlessCode code = codes[0];
            assertEquals(device.deviceIdHash, code.deviceIdHash);
            assertEquals(codeResponse.codeId, code.id);
        }
        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testCreateCodeWithDeviceId() throws Exception {
        String[] args = { "../" };

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        PasswordlessStorage storage = StorageLayer.getPasswordlessStorage(process.getProcess());

        String phoneNumber = "+442071838750";

        Passwordless.CreateCodeResponse createCodeResponse = Passwordless.createCode(process.getProcess(), null,
                phoneNumber, null, null);
        Passwordless.CreateCodeResponse resendCodeResponse = Passwordless.createCode(process.getProcess(), null, null,
                createCodeResponse.deviceId, null);

        assertNotNull(resendCodeResponse);

        PasswordlessDevice[] devices = storage.getDevicesByPhoneNumber(phoneNumber);
        assertEquals(1, devices.length);

        PasswordlessDevice device = devices[0];
        assertEquals(resendCodeResponse.deviceIdHash, device.deviceIdHash);
        assertEquals(null, device.email);
        assertEquals(phoneNumber, device.phoneNumber);
        assertEquals(0, device.failedAttempts);

        PasswordlessCode[] codes = storage.getCodesOfDevice(device.deviceIdHash);
        assertEquals(2, codes.length);

        for (PasswordlessCode code : codes) {
            assertEquals(device.deviceIdHash, code.deviceIdHash);
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    /**
     * create code with same deviceId+userInputCode twice -> DuplicateLinkCodeHashException
     * TODO: review
     * 
     * @throws Exception
     */
    @Test
    public void testCreateCodeWithSameDeviceIdAndUserInputCode() throws Exception {
        String[] args = { "../" };

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        PasswordlessStorage storage = StorageLayer.getPasswordlessStorage(process.getProcess());

        String phoneNumber = "+442071838750";

        Passwordless.CreateCodeResponse codeResponse = Passwordless.createCode(process.getProcess(), null, phoneNumber,
                null, null);
        Exception error = null;

        try {
            Passwordless.CreateCodeResponse resendCodeResponse = Passwordless.createCode(process.getProcess(), null,
                    null, codeResponse.deviceId, codeResponse.userInputCode);
        } catch (Exception e) {
            error = e;
        }
        assertNotNull(error);
        assert (error instanceof DuplicateLinkCodeHashException);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testCreateCodeResendWithNotExistingDeviceId() throws Exception {
        String[] args = { "../" };

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        PasswordlessStorage storage = StorageLayer.getPasswordlessStorage(process.getProcess());

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

    /**
     * CONSUME CODE SECTION BEGINS
     */

    /**
     * success without existing user - link code
     * 
     * @throws Exception
     */
    @Test
    public void testConsumeLinkCode() throws Exception {
        String[] args = { "../" };

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        PasswordlessStorage storage = StorageLayer.getPasswordlessStorage(process.getProcess());

        String email = "test@example.com";

        Passwordless.CreateCodeResponse createCodeResponse = Passwordless.createCode(process.getProcess(), email, null,
                null, null);
        assertNotNull(createCodeResponse);

        long consumeStart = System.currentTimeMillis();
        Passwordless.ConsumeCodeResponse consumeCodeResponse = Passwordless.consumeCode(process.getProcess(), null,
                null, createCodeResponse.linkCode);
        assertNotNull(consumeCodeResponse);
        checkUserWithConsumeResponse(storage, consumeCodeResponse, email, null, consumeStart);

        PasswordlessDevice[] devices = storage.getDevicesByEmail(email);
        assertEquals(0, devices.length);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    /**
     * Success without existing user - input code
     * 
     * @throws Exception
     */
    @Test
    public void testConsumeUserInputCode() throws Exception {
        String[] args = { "../" };

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        PasswordlessStorage storage = StorageLayer.getPasswordlessStorage(process.getProcess());

        String email = "test@example.com";

        Passwordless.CreateCodeResponse createCodeResponse = Passwordless.createCode(process.getProcess(), email, null,
                null, null);
        assertNotNull(createCodeResponse);

        long consumeStart = System.currentTimeMillis();
        Passwordless.ConsumeCodeResponse consumeCodeResponse = Passwordless.consumeCode(process.getProcess(),
                createCodeResponse.deviceId, createCodeResponse.userInputCode, null);
        assertNotNull(consumeCodeResponse);
        assert (consumeCodeResponse.createdNewUser);
        checkUserWithConsumeResponse(storage, consumeCodeResponse, email, null, consumeStart);

        PasswordlessDevice[] devices = storage.getDevicesByEmail(email);
        assertEquals(0, devices.length);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    /**
     * Check device clean up when user input code is generated via email & phone number
     * 
     * @throws Exception
     */
    @Test
    public void testConsumeCodeCleanupUserInputCodeWithEmailAndPhoneNumber() throws Exception {
        String[] args = { "../" };

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        PasswordlessStorage storage = StorageLayer.getPasswordlessStorage(process.getProcess());

        String email = "test@example.com";
        String phoneNumber = "+442071838750";
        UserInfo user = null;

        Passwordless.CreateCodeResponse createCodeResponse = Passwordless.createCode(process.getProcess(), email, null,
                null, null);
        assertNotNull(createCodeResponse);

        Passwordless.ConsumeCodeResponse consumeCodeResponse = Passwordless.consumeCode(process.getProcess(),
                createCodeResponse.deviceId, createCodeResponse.userInputCode, null);
        assertNotNull(consumeCodeResponse);

        user = storage.getUserById(consumeCodeResponse.user.id);
        Passwordless.updateUser(process.getProcess(), user.id, null, new Passwordless.FieldUpdate(phoneNumber));
        user = storage.getUserById(consumeCodeResponse.user.id);
        assertEquals(user.phoneNumber, phoneNumber);

        // create code with email twice
        {
            Passwordless.CreateCodeResponse codeResponse = Passwordless.createCode(process.getProcess(), email, null,
                    null, null);
        }
        {
            Passwordless.CreateCodeResponse codeResponse = Passwordless.createCode(process.getProcess(), email, null,
                    null, null);
        }

        // create code with phone number twice
        {
            Passwordless.CreateCodeResponse codeResponse = Passwordless.createCode(process.getProcess(), null,
                    phoneNumber, null, null);
        }

        Passwordless.CreateCodeResponse codeResponse = Passwordless.createCode(process.getProcess(), null, phoneNumber,
                null, null);

        // 4 codes should be available
        PasswordlessDevice[] devices = storage.getDevicesByPhoneNumber(phoneNumber);
        assertEquals(2, devices.length);

        devices = storage.getDevicesByEmail(email);
        assertEquals(2, devices.length);

        // consume code
        Passwordless.ConsumeCodeResponse consumeCode = Passwordless.consumeCode(process.getProcess(),
                codeResponse.deviceId, codeResponse.userInputCode, null);

        devices = storage.getDevicesByEmail(email);
        assertEquals(0, devices.length);

        devices = storage.getDevicesByPhoneNumber(phoneNumber);
        assertEquals(0, devices.length);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    /**
     * Check device clean up when link code is generated via email & phone number
     * 
     * @throws Exception
     */
    @Test
    public void testConsumeCodeCleanupLinkCodeWithEmailAndPhoneNumber() throws Exception {
        String[] args = { "../" };

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        PasswordlessStorage storage = StorageLayer.getPasswordlessStorage(process.getProcess());

        String email = "test@example.com";
        String phoneNumber = "+442071838750";
        UserInfo user = null;

        Passwordless.CreateCodeResponse createCodeResponse = Passwordless.createCode(process.getProcess(), email, null,
                null, null);
        assertNotNull(createCodeResponse);

        Passwordless.ConsumeCodeResponse consumeCodeResponse = Passwordless.consumeCode(process.getProcess(),
                createCodeResponse.deviceId, createCodeResponse.userInputCode, null);
        assertNotNull(consumeCodeResponse);

        user = storage.getUserById(consumeCodeResponse.user.id);
        Passwordless.updateUser(process.getProcess(), user.id, null, new Passwordless.FieldUpdate(phoneNumber));
        user = storage.getUserById(consumeCodeResponse.user.id);
        assertEquals(user.phoneNumber, phoneNumber);

        // create code with email twice
        {
            Passwordless.CreateCodeResponse codeResponse = Passwordless.createCode(process.getProcess(), email, null,
                    null, null);
        }
        {
            Passwordless.CreateCodeResponse codeResponse = Passwordless.createCode(process.getProcess(), email, null,
                    null, null);
        }

        // create code with phone number twice
        {
            Passwordless.CreateCodeResponse codeResponse = Passwordless.createCode(process.getProcess(), null,
                    phoneNumber, null, null);
        }

        Passwordless.CreateCodeResponse codeResponse = Passwordless.createCode(process.getProcess(), null, phoneNumber,
                null, null);

        // 4 codes should be available
        PasswordlessDevice[] devices = storage.getDevicesByPhoneNumber(phoneNumber);
        assertEquals(2, devices.length);

        devices = storage.getDevicesByEmail(email);
        assertEquals(2, devices.length);

        // consume code
        Passwordless.ConsumeCodeResponse consumeCode = Passwordless.consumeCode(process.getProcess(),
                codeResponse.deviceId, null, codeResponse.linkCode);

        devices = storage.getDevicesByEmail(email);
        assertEquals(0, devices.length);

        devices = storage.getDevicesByPhoneNumber(phoneNumber);
        assertEquals(0, devices.length);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testConsumeUserInputCodeWithExistingUser() throws Exception {
        String[] args = { "../" };

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        PasswordlessStorage storage = StorageLayer.getPasswordlessStorage(process.getProcess());

        String email = "test@example.com";
        UserInfo user = null;
        {
            Passwordless.CreateCodeResponse createCodeResponse = Passwordless.createCode(process.getProcess(), email,
                    null, null, null);
            assertNotNull(createCodeResponse);

            long consumeStart = System.currentTimeMillis();
            Passwordless.ConsumeCodeResponse consumeCodeResponse = Passwordless.consumeCode(process.getProcess(),
                    createCodeResponse.deviceId, createCodeResponse.userInputCode, null);
            assertNotNull(consumeCodeResponse);
            user = checkUserWithConsumeResponse(storage, consumeCodeResponse, email, null, consumeStart);
        }
        {
            Passwordless.CreateCodeResponse createCodeResponse = Passwordless.createCode(process.getProcess(), email,
                    null, null, null);
            assertNotNull(createCodeResponse);

            Passwordless.ConsumeCodeResponse consumeCodeResponse = Passwordless.consumeCode(process.getProcess(),
                    createCodeResponse.deviceId, createCodeResponse.userInputCode, null);
            assertNotNull(consumeCodeResponse);
            assert (!consumeCodeResponse.createdNewUser);
            UserInfo user2 = checkUserWithConsumeResponse(storage, consumeCodeResponse, email, null, 0);

            assert (user.equals(user2));
        }

        PasswordlessDevice[] devices = storage.getDevicesByEmail(email);
        assertEquals(0, devices.length);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    /**
     * Test consume link code with existing user
     * 
     * @throws Exception
     */
    @Test
    public void testConsumeLinkCodeWithExistingUser() throws Exception {
        String[] args = { "../" };

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        PasswordlessStorage storage = StorageLayer.getPasswordlessStorage(process.getProcess());

        String email = "test@example.com";
        UserInfo user = null;
        {
            Passwordless.CreateCodeResponse createCodeResponse = Passwordless.createCode(process.getProcess(), email,
                    null, null, null);
            assertNotNull(createCodeResponse);

            long consumeStart = System.currentTimeMillis();
            Passwordless.ConsumeCodeResponse consumeCodeResponse = Passwordless.consumeCode(process.getProcess(),
                    createCodeResponse.deviceId, null, createCodeResponse.linkCode);
            assertNotNull(consumeCodeResponse);
            user = checkUserWithConsumeResponse(storage, consumeCodeResponse, email, null, consumeStart);
        }
        {
            Passwordless.CreateCodeResponse createCodeResponse = Passwordless.createCode(process.getProcess(), email,
                    null, null, null);
            assertNotNull(createCodeResponse);

            Passwordless.ConsumeCodeResponse consumeCodeResponse = Passwordless.consumeCode(process.getProcess(),
                    createCodeResponse.deviceId, null, createCodeResponse.linkCode);
            assertNotNull(consumeCodeResponse);
            assert (!consumeCodeResponse.createdNewUser);
            UserInfo user2 = checkUserWithConsumeResponse(storage, consumeCodeResponse, email, null, 0);

            assert (user.equals(user2));
        }

        PasswordlessDevice[] devices = storage.getDevicesByEmail(email);
        assertEquals(0, devices.length);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    /**
     * consume code malformed linkCode
     * 
     * @throws Exception
     */
    @Test
    public void testConsumeMalformedLinkCode() throws Exception {
        String[] args = { "../" };

        Utils.setValueInConfig("passwordless_code_lifetime", "100");
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        PasswordlessStorage storage = StorageLayer.getPasswordlessStorage(process.getProcess());

        String email = "test@example.com";

        Passwordless.CreateCodeResponse createCodeResponse = Passwordless.createCode(process.getProcess(), email, null,
                null, null);
        assertNotNull(createCodeResponse);

        Exception error = null;
        try {
            Passwordless.consumeCode(process.getProcess(), null, null, createCodeResponse.linkCode + "##");
        } catch (Exception ex) {
            error = ex;
        }
        assertNotNull(error);
        assert (error instanceof IllegalArgumentException);

        PasswordlessDevice[] devices = storage.getDevicesByEmail(email);
        assertNotEquals(0, devices.length);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    /**
     * non existing linkCode
     * 
     * @throws Exception
     */
    @Test
    public void testConsumeNonExistingLinkCode() throws Exception {
        String[] args = { "../" };

        Utils.setValueInConfig("passwordless_code_lifetime", "100");
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        PasswordlessStorage storage = StorageLayer.getPasswordlessStorage(process.getProcess());

        String email = "test@example.com";

        Passwordless.CreateCodeResponse createCodeResponse = Passwordless.createCode(process.getProcess(), email, null,
                null, null);
        assertNotNull(createCodeResponse);

        Exception error = null;
        try {
            Passwordless.consumeCode(process.getProcess(), null, null, "bbbbbbbbbbbbbb20JgnKPL5lwFBrfJexadmg9I_Ilp0=");
        } catch (Exception ex) {
            error = ex;
        }
        assertNotNull(error);
        assert (error instanceof RestartFlowException);

        // verify that devices have not been cleared
        PasswordlessDevice[] devices = storage.getDevicesByEmail(email);
        assertNotEquals(0, devices.length);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testConsumeExpiredLinkCode() throws Exception {
        String[] args = { "../" };

        Utils.setValueInConfig("passwordless_code_lifetime", "100");
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        PasswordlessStorage storage = StorageLayer.getPasswordlessStorage(process.getProcess());

        String email = "test@example.com";

        Passwordless.CreateCodeResponse createCodeResponse = Passwordless.createCode(process.getProcess(), email, null,
                null, null);
        assertNotNull(createCodeResponse);

        Thread.sleep(120);
        Exception error = null;
        try {
            Passwordless.consumeCode(process.getProcess(), null, null, createCodeResponse.linkCode);
        } catch (Exception ex) {
            error = ex;
        }
        assertNotNull(error);
        assert (error instanceof RestartFlowException);

        // verify that devices have not been cleared
        PasswordlessDevice[] devices = storage.getDevicesByEmail(email);
        assertNotEquals(0, devices.length);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testConsumeExpiredUserInputCode() throws Exception {
        String[] args = { "../" };

        Utils.setValueInConfig("passwordless_code_lifetime", "100");
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        PasswordlessStorage storage = StorageLayer.getPasswordlessStorage(process.getProcess());

        String email = "test@example.com";

        Passwordless.CreateCodeResponse createCodeResponse = Passwordless.createCode(process.getProcess(), email, null,
                null, null);
        assertNotNull(createCodeResponse);

        Thread.sleep(120);
        Exception error = null;
        try {
            Passwordless.consumeCode(process.getProcess(), createCodeResponse.deviceId,
                    createCodeResponse.userInputCode, null);
        } catch (Exception ex) {
            error = ex;
        }
        assertNotNull(error);
        assert (error instanceof ExpiredUserInputCodeException);

        // verify that devices have not been cleared
        PasswordlessDevice[] devices = storage.getDevicesByEmail(email);
        assertNotEquals(0, devices.length);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testConsumeExpiredUserInputCodeAfterResend() throws Exception {
        String[] args = { "../" };

        Utils.setValueInConfig("passwordless_code_lifetime", "100");
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        PasswordlessStorage storage = StorageLayer.getPasswordlessStorage(process.getProcess());

        String email = "test@example.com";

        Passwordless.CreateCodeResponse createCodeResponse = Passwordless.createCode(process.getProcess(), email, null,
                null, null);
        assertNotNull(createCodeResponse);

        Thread.sleep(120);
        Passwordless.createCode(process.getProcess(), null, null, createCodeResponse.deviceId, null);
        Exception error = null;
        try {
            Passwordless.consumeCode(process.getProcess(), createCodeResponse.deviceId,
                    createCodeResponse.userInputCode, null);
        } catch (Exception ex) {
            error = ex;
        }
        assertNotNull(error);
        assert (error instanceof ExpiredUserInputCodeException);

        // verify that devices have not been cleared
        PasswordlessDevice[] devices = storage.getDevicesByEmail(email);
        assertNotEquals(0, devices.length);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testConsumeWrongUserInputCode() throws Exception {
        String[] args = { "../" };

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        PasswordlessStorage storage = StorageLayer.getPasswordlessStorage(process.getProcess());

        String email = "test@example.com";

        Passwordless.CreateCodeResponse createCodeResponse = Passwordless.createCode(process.getProcess(), email, null,
                null, null);
        assertNotNull(createCodeResponse);

        Exception error = null;
        try {
            Passwordless.consumeCode(process.getProcess(), createCodeResponse.deviceId, "n0p321", null);
        } catch (Exception ex) {
            error = ex;
        }
        assertNotNull(error);
        assert (error instanceof IncorrectUserInputCodeException);

        // verify that devices have not been cleared
        PasswordlessDevice[] devices = storage.getDevicesByEmail(email);
        assertNotEquals(0, devices.length);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    /**
     * wrong userInputCode exceeding maxCodeInputAttempts
     * 
     * @throws Exception
     *
     */

    @Test
    public void testConsumeWrongUserInputCodeExceedingMaxAttempts() throws Exception {
        String[] args = { "../" };

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        PasswordlessStorage storage = StorageLayer.getPasswordlessStorage(process.getProcess());

        String email = "test@example.com";

        Passwordless.CreateCodeResponse createCodeResponse = Passwordless.createCode(process.getProcess(), email, null,
                null, null);
        assertNotNull(createCodeResponse);

        Exception error = null;
        int maxCodeInputAttempts = Config.getConfig(process.getProcess()).getPasswordlessMaxCodeInputAttempts();
        for (int counter = 0; counter < maxCodeInputAttempts; counter++) {

            try {
                Passwordless.consumeCode(process.getProcess(), createCodeResponse.deviceId, "n0p321", null);
            } catch (Exception ex) {

            }

        }
        try {
            Passwordless.consumeCode(process.getProcess(), createCodeResponse.deviceId, "n0p321", null);
        } catch (Exception ex) {
            error = ex;
        }
        assertNotNull(error);
        assert (error instanceof RestartFlowException);

        // verify that devices have been cleared, since max attempt reached
        PasswordlessDevice[] devices = storage.getDevicesByEmail(email);
        assertEquals(0, devices.length);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    /**
     * wrong linkCode exceeding maxCodeInputAttempts
     * 
     * @throws Exception
     *
     */

    @Test
    public void testConsumeWrongLinkCodeExceedingMaxAttempts() throws Exception {
        String[] args = { "../" };

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        PasswordlessStorage storage = StorageLayer.getPasswordlessStorage(process.getProcess());

        String email = "test@example.com";

        Passwordless.CreateCodeResponse createCodeResponse = Passwordless.createCode(process.getProcess(), email, null,
                null, null);
        assertNotNull(createCodeResponse);

        Exception error = null;
        int maxCodeInputAttempts = Config.getConfig(process.getProcess()).getPasswordlessMaxCodeInputAttempts();

        for (int counter = 0; counter < maxCodeInputAttempts; counter++) {

            try {
                Passwordless.consumeCode(process.getProcess(), createCodeResponse.deviceId, null, "n0p321");
            } catch (Exception ex) {
                error = ex;
            }

        }

        assertNotNull(error);
        assert (error instanceof RestartFlowException);

        // verify that devices have not been cleared
        PasswordlessDevice[] devices = storage.getDevicesByEmail(email);
        assertNotEquals(0, devices.length);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    /**
     * link with too many failedAttempts (changed maxCodeInputAttempts configuration between consumes)
     * 
     * @throws Exception
     *
     */

    @Test
    public void testConsumeWrongUserInputCodeExceedingMaxAttemptsWithConfigUpdate() throws Exception {
        String[] args = { "../" };

        // start process with default configuration for max attempt
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        PasswordlessStorage storage = StorageLayer.getPasswordlessStorage(process.getProcess());

        String email = "test@example.com";

        Passwordless.CreateCodeResponse createCodeResponse = Passwordless.createCode(process.getProcess(), email, null,
                null, null);
        assertNotNull(createCodeResponse);

        Exception error = null;
        int maxCodeInputAttempts = Config.getConfig(process.getProcess()).getPasswordlessMaxCodeInputAttempts();

        // input wrong code max attempts - 2 times
        for (int counter = 0; counter < maxCodeInputAttempts - 2; counter++) {

            try {
                Passwordless.consumeCode(process.getProcess(), createCodeResponse.deviceId, "n0p321", null);
            } catch (Exception ex) {

            }

        }

        // verify that devices have not been cleared
        PasswordlessDevice[] devices = storage.getDevicesByEmail(email);
        assertNotEquals(0, devices.length);

        // kill process and restart with increased max code input attempts
        process.kill();
        process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED);

        Utils.setValueInConfig("passwordless_max_code_input_attempts", "6");
        process = TestingProcessManager.start(args);

        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        storage = StorageLayer.getPasswordlessStorage(process.getProcess());

        createCodeResponse = Passwordless.createCode(process.getProcess(), email, null, null, null);

        assertNotNull(createCodeResponse);

        try {
            Passwordless.consumeCode(process.getProcess(), createCodeResponse.deviceId, "n0p321", null);
        } catch (Exception ex) {
            error = ex;
        }
        assertNotNull(error);
        assert (error instanceof IncorrectUserInputCodeException);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    private UserInfo checkUserWithConsumeResponse(PasswordlessStorage storage, ConsumeCodeResponse resp, String email,
            String phoneNumber, long joinedAfter) throws StorageQueryException {
        UserInfo user = storage.getUserById(resp.user.id);
        assertNotNull(user);

        assertEquals(email, resp.user.email);
        assertEquals(email, user.email);

        assertEquals(phoneNumber, user.phoneNumber);
        assertEquals(phoneNumber, resp.user.phoneNumber);

        assert (user.timeJoined >= joinedAfter);
        assertEquals(user.timeJoined, resp.user.timeJoined);

        return user;
    }

    /**
     * getDeviceWithCodesByIdHash
     */

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

        String email = "test@example.com";

        Passwordless.CreateCodeResponse createCodeResponse = Passwordless.createCode(process.getProcess(), email, null,
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
        assertEquals(email, device.email);
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

        String email = "test@example.com";

        Passwordless.CreateCodeResponse createCodeResponse = Passwordless.createCode(process.getProcess(), email, null,
                null, null);
        assertNotNull(createCodeResponse);

        // create multiple codes with this device
        for (int counter = 0; counter < NUMBER_OF_CODES_TO_GENERATE; counter++) {

            createCodeResponse = Passwordless.createCode(process.getProcess(), email, null, createCodeResponse.deviceId,
                    null);
            assertNotNull(createCodeResponse);

        }
        PasswordlessDevice[] devices = storage.getDevicesByEmail(email);

        for (int counter = 0; counter < devices.length; counter++) {
            PasswordlessDevice device = devices[counter];

            // verify device retrieved
            assertNotNull(device);
            assertEquals(createCodeResponse.deviceIdHash, device.deviceIdHash);
            assertEquals(email, device.email);
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

        String phoneNumber = "+442071838750";

        Passwordless.CreateCodeResponse createCodeResponse = Passwordless.createCode(process.getProcess(), null,
                phoneNumber, null, null);
        assertNotNull(createCodeResponse);

        // create multiple codes with this device
        for (int counter = 0; counter < NUMBER_OF_CODES_TO_GENERATE; counter++) {

            createCodeResponse = Passwordless.createCode(process.getProcess(), null, phoneNumber,
                    createCodeResponse.deviceId, null);
            assertNotNull(createCodeResponse);

        }
        PasswordlessDevice[] devices = storage.getDevicesByPhoneNumber(phoneNumber);

        for (int counter = 0; counter < devices.length; counter++) {
            PasswordlessDevice device = devices[counter];

            // verify device retrieved
            assertNotNull(device);
            assertEquals(createCodeResponse.deviceIdHash, device.deviceIdHash);
            assertEquals(null, device.email);
            assertEquals(phoneNumber, device.phoneNumber);
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

    /**
     * removeCode
     */

    /**
     * deletes code and leaves device if other code exists
     * 
     * @throws Exception
     */
    @Test
    public void deleteCodeAndLeaveDevices() throws Exception {

        int NUMBER_OF_CODES_TO_GENERATE = 5;

        TestingProcessManager.TestingProcess process = startApplicationWithDefaultArgs();

        PasswordlessStorage storage = StorageLayer.getPasswordlessStorage(process.getProcess());

        String phoneNumber = "+442071838750";

        Passwordless.CreateCodeResponse createCodeResponse = Passwordless.createCode(process.getProcess(), null,
                phoneNumber, null, null);
        assertNotNull(createCodeResponse);

        // create multiple codes with this device
        for (int counter = 0; counter < NUMBER_OF_CODES_TO_GENERATE; counter++) {

            createCodeResponse = Passwordless.createCode(process.getProcess(), null, phoneNumber,
                    createCodeResponse.deviceId, null);
            assertNotNull(createCodeResponse);

        }

        Passwordless.removeCode(process.getProcess(), createCodeResponse.codeId);

        PasswordlessDevice[] devices = storage.getDevicesByPhoneNumber(phoneNumber);

        for (int counter = 0; counter < devices.length; counter++) {
            PasswordlessDevice device = devices[counter];

            // verify device retrieved
            assertNotNull(device);
            assertEquals(createCodeResponse.deviceIdHash, device.deviceIdHash);
            assertEquals(null, device.email);
            assertEquals(phoneNumber, device.phoneNumber);
            assertEquals(0, device.failedAttempts);

            PasswordlessCode[] codes = storage.getCodesOfDevice(device.deviceIdHash);
            assertEquals(NUMBER_OF_CODES_TO_GENERATE, codes.length);

            for (int counter_code = 0; counter_code < codes.length; counter_code++) {

                PasswordlessCode code = codes[counter];
                assertEquals(device.deviceIdHash, code.deviceIdHash);

            }
        }

        killApplication(process);

    }

    /**
     * deletes code and cleans device if no other code exists
     * 
     * @throws Exception
     */
    @Test
    public void deleteCodeAndCleansDevice() throws Exception {

        TestingProcessManager.TestingProcess process = startApplicationWithDefaultArgs();

        PasswordlessStorage storage = StorageLayer.getPasswordlessStorage(process.getProcess());

        String phoneNumber = "+442071838750";

        Passwordless.CreateCodeResponse createCodeResponse = Passwordless.createCode(process.getProcess(), null,
                phoneNumber, null, null);
        assertNotNull(createCodeResponse);

        Passwordless.removeCode(process.getProcess(), createCodeResponse.codeId);

        PasswordlessDevice[] devices = storage.getDevicesByPhoneNumber(phoneNumber);
        assertEquals(0, devices.length);

        killApplication(process);

    }

    /**
     * no op if code doesn't exist
     * 
     * @throws Exception
     */
    @Test
    public void doNothingIfCodeDoesNotExist() throws Exception {

        TestingProcessManager.TestingProcess process = startApplicationWithDefaultArgs();

        PasswordlessStorage storage = StorageLayer.getPasswordlessStorage(process.getProcess());

        String phoneNumber = "+442071838750";

        Passwordless.CreateCodeResponse createCodeResponse = Passwordless.createCode(process.getProcess(), null,
                phoneNumber, null, null);
        assertNotNull(createCodeResponse);

        Passwordless.removeCode(process.getProcess(), "1234");

        PasswordlessDevice[] devices = storage.getDevicesByPhoneNumber(phoneNumber);
        assertEquals(1, devices.length);

        PasswordlessCode[] codes = storage.getCodesOfDevice(createCodeResponse.deviceIdHash);
        assertEquals(1, codes.length);

        killApplication(process);

    }

    /**
     * removeCodesByEmail
     */

    /**
     * removes devices with matching email (leaving others)
     * 
     * @throws Exception
     */
    @Test
    public void removeDevicesFromEmail() throws Exception {

        TestingProcessManager.TestingProcess process = startApplicationWithDefaultArgs();

        PasswordlessStorage storage = StorageLayer.getPasswordlessStorage(process.getProcess());

        String email = "test@example.com";
        String alternate_email = "alternateTest@example.com";

        Passwordless.CreateCodeResponse createCodeResponse = Passwordless.createCode(process.getProcess(), email, null,
                null, null);
        assertNotNull(createCodeResponse);

        createCodeResponse = Passwordless.createCode(process.getProcess(), email, null, null, null);
        assertNotNull(createCodeResponse);

        createCodeResponse = Passwordless.createCode(process.getProcess(), alternate_email, null, null, null);
        assertNotNull(createCodeResponse);

        Passwordless.removeCodesByEmail(process.getProcess(), email);

        PasswordlessDevice[] devices = storage.getDevicesByEmail(email);
        assertEquals(0, devices.length);

        devices = storage.getDevicesByEmail(alternate_email);
        assertEquals(1, devices.length);

        PasswordlessCode[] codes = storage.getCodesOfDevice(devices[0].deviceIdHash);
        assertEquals(1, codes.length);

        killApplication(process);

    }

    /**
     * removeCodesByPhoneNumber
     */

    /**
     * removes devices with matching phone number (leaving others)
     * 
     * @throws Exception
     */
    @Test
    public void removeDevicesFromPhoneNumber() throws Exception {

        TestingProcessManager.TestingProcess process = startApplicationWithDefaultArgs();

        PasswordlessStorage storage = StorageLayer.getPasswordlessStorage(process.getProcess());

        String phoneNumber = "+442071838750";
        String alternate_phoneNumber = "+442071838751";

        Passwordless.CreateCodeResponse createCodeResponse = Passwordless.createCode(process.getProcess(), null,
                phoneNumber, null, null);
        assertNotNull(createCodeResponse);

        createCodeResponse = Passwordless.createCode(process.getProcess(), null, phoneNumber, null, null);
        assertNotNull(createCodeResponse);

        createCodeResponse = Passwordless.createCode(process.getProcess(), null, alternate_phoneNumber, null, null);
        assertNotNull(createCodeResponse);

        Passwordless.removeCodesByPhoneNumber(process.getProcess(), phoneNumber);

        PasswordlessDevice[] devices = storage.getDevicesByPhoneNumber(phoneNumber);
        assertEquals(0, devices.length);

        devices = storage.getDevicesByPhoneNumber(alternate_phoneNumber);
        assertEquals(1, devices.length);

        PasswordlessCode[] codes = storage.getCodesOfDevice(devices[0].deviceIdHash);
        assertEquals(1, codes.length);

        killApplication(process);

    }

    /**
     * getUserById
     */

    /**
     * with email set
     * 
     * @throws Exception
     */
    @Test
    public void getUserByIdWithEmail() throws Exception {
        TestingProcessManager.TestingProcess process = startApplicationWithDefaultArgs();

        PasswordlessStorage storage = StorageLayer.getPasswordlessStorage(process.getProcess());
        UserInfo user = null;

        Passwordless.ConsumeCodeResponse consumeCodeResponse = createUserWith(process, EMAIL, null);

        user = storage.getUserById(consumeCodeResponse.user.id);
        assertNotNull(user);
        assertEquals(user.email, EMAIL);

    }

    /**
     * with phone number set
     * 
     * @throws Exception
     */
    @Test
    public void getUserByIdWithPhoneNumber() throws Exception {
        TestingProcessManager.TestingProcess process = startApplicationWithDefaultArgs();

        PasswordlessStorage storage = StorageLayer.getPasswordlessStorage(process.getProcess());
        UserInfo user = null;

        Passwordless.ConsumeCodeResponse consumeCodeResponse = createUserWith(process, null, PHONE_NUMBER);

        user = storage.getUserById(consumeCodeResponse.user.id);
        assertNotNull(user);
        assertEquals(user.phoneNumber, PHONE_NUMBER);
    }

    /**
     * with both email and phoneNumber set
     * 
     * @throws Exception
     */
    @Test
    public void getUserByIdWithEmailAndPhoneNumber() throws Exception {
        TestingProcessManager.TestingProcess process = startApplicationWithDefaultArgs();

        PasswordlessStorage storage = StorageLayer.getPasswordlessStorage(process.getProcess());
        UserInfo user = null;

        Passwordless.ConsumeCodeResponse consumeCodeResponse = createUserWith(process, EMAIL, PHONE_NUMBER);

        user = storage.getUserById(consumeCodeResponse.user.id);
        assertNotNull(user);
        assertEquals(user.email, EMAIL);
        assertEquals(user.phoneNumber, PHONE_NUMBER);
    }

    /**
     * returns null if it doesn't exist
     * 
     * @throws Exception
     */
    @Test
    public void getUserByInvalidId() throws Exception {
        TestingProcessManager.TestingProcess process = startApplicationWithDefaultArgs();

        PasswordlessStorage storage = StorageLayer.getPasswordlessStorage(process.getProcess());
        UserInfo user = null;

        Passwordless.ConsumeCodeResponse consumeCodeResponse = createUserWith(process, EMAIL, null);

        user = storage.getUserById(consumeCodeResponse.user.id + "1");
        assertNull(user);
    }

    /**
     * getUserByEmail
     * 
     * @throws Exception
     */
    @Test
    public void getUserByEmail() throws Exception {
        TestingProcessManager.TestingProcess process = startApplicationWithDefaultArgs();

        PasswordlessStorage storage = StorageLayer.getPasswordlessStorage(process.getProcess());
        UserInfo user = null;

        Passwordless.ConsumeCodeResponse consumeCodeResponse = createUserWith(process, EMAIL, null);

        user = storage.getUserByEmail(EMAIL);
        assertNotNull(user);
        assertEquals(user.email, EMAIL);

    }

    /**
     * getUserByEmail
     * 
     * @throws Exception
     */
    @Test
    public void getUserByInvalidEmail() throws Exception {
        TestingProcessManager.TestingProcess process = startApplicationWithDefaultArgs();

        PasswordlessStorage storage = StorageLayer.getPasswordlessStorage(process.getProcess());
        UserInfo user = null;

        Passwordless.ConsumeCodeResponse consumeCodeResponse = createUserWith(process, EMAIL, null);

        user = storage.getUserByEmail(EMAIL + "A");
        assertNull(user);

    }

    /**
     * getUserByPhoneNumber
     * 
     * @throws Exception
     */
    @Test
    public void getUserByPhoneNumber() throws Exception {
        TestingProcessManager.TestingProcess process = startApplicationWithDefaultArgs();

        PasswordlessStorage storage = StorageLayer.getPasswordlessStorage(process.getProcess());
        UserInfo user = null;

        Passwordless.ConsumeCodeResponse consumeCodeResponse = createUserWith(process, null, PHONE_NUMBER);

        user = storage.getUserByPhoneNumber(PHONE_NUMBER);
        assertNotNull(user);
        assertEquals(user.phoneNumber, PHONE_NUMBER);
    }

    /**
     * getUserByPhoneNumber
     * 
     * @throws Exception
     */
    @Test
    public void getUserByInvalidPhoneNumber() throws Exception {
        TestingProcessManager.TestingProcess process = startApplicationWithDefaultArgs();

        PasswordlessStorage storage = StorageLayer.getPasswordlessStorage(process.getProcess());
        UserInfo user = null;

        Passwordless.ConsumeCodeResponse consumeCodeResponse = createUserWith(process, null, PHONE_NUMBER);

        user = storage.getUserByPhoneNumber(PHONE_NUMBER + "1");
        assertNull(user);
    }

    /**
     * updateUser
     */

    /**
     * try update email to an existing one -> DuplicateEmailException + no change
     * 
     * @throws Exception
     */
    @Test
    public void updateEmailToAnExistingOne() throws Exception {
        String alternate_email = "alternate_testing@example.com";
        TestingProcessManager.TestingProcess process = startApplicationWithDefaultArgs();

        PasswordlessStorage storage = StorageLayer.getPasswordlessStorage(process.getProcess());
        UserInfo user = null;

        Passwordless.ConsumeCodeResponse consumeCodeResponse = createUserWith(process, EMAIL, null);

        Passwordless.ConsumeCodeResponse consumeCodeResponseAlternate = createUserWith(process, alternate_email, null);

        user = storage.getUserByEmail(EMAIL);
        assertNotNull(user);

        Exception ex = null;
        try {
            Passwordless.updateUser(process.getProcess(), user.id, new Passwordless.FieldUpdate(alternate_email), null);
        } catch (Exception e) {
            ex = e;
        }

        assertNotNull(ex);
        assert (ex instanceof DuplicateEmailException);

        assertEquals(EMAIL, storage.getUserByEmail(EMAIL).email);
    }

    /**
     * try update phone number to an existing one -> DuplicatePhoneNumberException + no change
     * 
     * @throws Exception
     */
    @Test
    public void updatePhoneNumberToAnExistingOne() throws Exception {
        String alternate_phoneNumber = PHONE_NUMBER + "1";
        TestingProcessManager.TestingProcess process = startApplicationWithDefaultArgs();

        PasswordlessStorage storage = StorageLayer.getPasswordlessStorage(process.getProcess());
        UserInfo user = null;

        Passwordless.ConsumeCodeResponse consumeCodeResponse = createUserWith(process, null, PHONE_NUMBER);

        Passwordless.ConsumeCodeResponse consumeCodeResponseAlternate = createUserWith(process, null,
                alternate_phoneNumber);

        user = storage.getUserByPhoneNumber(PHONE_NUMBER);
        assertNotNull(user);

        Exception ex = null;
        try {
            Passwordless.updateUser(process.getProcess(), user.id, null,
                    new Passwordless.FieldUpdate(alternate_phoneNumber));
        } catch (Exception e) {
            ex = e;
        }

        assertNotNull(ex);
        assert (ex instanceof DuplicatePhoneNumberException);

        assertEquals(PHONE_NUMBER, storage.getUserByPhoneNumber(PHONE_NUMBER).phoneNumber);
    }

    /**
     * update email leaving phoneNumber
     * 
     * @throws Exception
     */
    @Test
    public void updateEmail() throws Exception {
        String alternate_email = "alternate_testing@example.com";
        TestingProcessManager.TestingProcess process = startApplicationWithDefaultArgs();

        PasswordlessStorage storage = StorageLayer.getPasswordlessStorage(process.getProcess());
        UserInfo user = null;

        Passwordless.ConsumeCodeResponse consumeCodeResponse = createUserWith(process, EMAIL, null);

        user = storage.getUserByEmail(EMAIL);
        assertNotNull(user);

        Passwordless.updateUser(process.getProcess(), user.id, new Passwordless.FieldUpdate(alternate_email), null);

        assertEquals(alternate_email, storage.getUserById(user.id).email);
    }

    /**
     * update phone leaving email
     * 
     * @throws Exception
     */
    @Test
    public void updatePhoneNumber() throws Exception {
        String alternate_phoneNumber = PHONE_NUMBER + "1";
        TestingProcessManager.TestingProcess process = startApplicationWithDefaultArgs();

        PasswordlessStorage storage = StorageLayer.getPasswordlessStorage(process.getProcess());
        UserInfo user = null;

        Passwordless.ConsumeCodeResponse consumeCodeResponse = createUserWith(process, null, PHONE_NUMBER);

        user = storage.getUserByPhoneNumber(PHONE_NUMBER);
        assertNotNull(user);

        Passwordless.updateUser(process.getProcess(), user.id, null,
                new Passwordless.FieldUpdate(alternate_phoneNumber));

        assertEquals(alternate_phoneNumber, storage.getUserById(user.id).phoneNumber);
    }

    /**
     * clear email + set phoneNumber
     * 
     * @throws Exception
     */
    @Test
    public void clearEmailSetPhoneNumber() throws Exception {
        TestingProcessManager.TestingProcess process = startApplicationWithDefaultArgs();

        PasswordlessStorage storage = StorageLayer.getPasswordlessStorage(process.getProcess());
        UserInfo user = null;

        Passwordless.ConsumeCodeResponse consumeCodeResponse = createUserWith(process, EMAIL, null);

        user = storage.getUserByEmail(EMAIL);
        assertNotNull(user);

        Passwordless.updateUser(process.getProcess(), user.id, new Passwordless.FieldUpdate(null),
                new Passwordless.FieldUpdate(PHONE_NUMBER));

        assertEquals(PHONE_NUMBER, storage.getUserById(user.id).phoneNumber);
        assertEquals(null, storage.getUserById(user.id).email);

    }

    /**
     * clear phone + set email
     * 
     * @throws Exception
     */
    @Test
    public void clearPhoneNumberSetEmail() throws Exception {
        TestingProcessManager.TestingProcess process = startApplicationWithDefaultArgs();

        PasswordlessStorage storage = StorageLayer.getPasswordlessStorage(process.getProcess());
        UserInfo user = null;

        Passwordless.ConsumeCodeResponse consumeCodeResponse = createUserWith(process, null, PHONE_NUMBER);

        user = storage.getUserByPhoneNumber(PHONE_NUMBER);
        assertNotNull(user);

        Passwordless.updateUser(process.getProcess(), user.id, new Passwordless.FieldUpdate(EMAIL),
                new Passwordless.FieldUpdate(null));

        assertEquals(EMAIL, storage.getUserById(user.id).email);
        assertEquals(null, storage.getUserById(user.id).phoneNumber);

    }

    /**
     * clear both email and phone -> UserWithoutContactInfoException
     * 
     * @throws Exception
     *                   TODO: no exception is thrown
     */
    @Test
    public void clearPhoneNumberAndEmail() throws Exception {
        TestingProcessManager.TestingProcess process = startApplicationWithDefaultArgs();

        PasswordlessStorage storage = StorageLayer.getPasswordlessStorage(process.getProcess());
        UserInfo user = null;

        Passwordless.ConsumeCodeResponse consumeCodeResponse = createUserWith(process, null, PHONE_NUMBER);

        user = storage.getUserByPhoneNumber(PHONE_NUMBER);
        assertNotNull(user);
        Exception ex = null;

        try {
            Passwordless.updateUser(process.getProcess(), user.id, new Passwordless.FieldUpdate(null),
                    new Passwordless.FieldUpdate(null));
        } catch (Exception e) {
            ex = e;
        }

        assertNotNull(ex);

    }

    /**
     * set both email and phone
     * 
     * @throws Exception
     */
    @Test
    public void setPhoneNumberSetEmail() throws Exception {
        String alternate_phoneNumber = PHONE_NUMBER + "1";
        TestingProcessManager.TestingProcess process = startApplicationWithDefaultArgs();

        PasswordlessStorage storage = StorageLayer.getPasswordlessStorage(process.getProcess());
        UserInfo user = null;

        Passwordless.ConsumeCodeResponse consumeCodeResponse = createUserWith(process, null, PHONE_NUMBER);

        user = storage.getUserByPhoneNumber(PHONE_NUMBER);
        assertNotNull(user);

        Passwordless.updateUser(process.getProcess(), user.id, new Passwordless.FieldUpdate(EMAIL),
                new Passwordless.FieldUpdate(alternate_phoneNumber));

        assertEquals(EMAIL, storage.getUserById(user.id).email);
        assertEquals(alternate_phoneNumber, storage.getUserById(user.id).phoneNumber);

    }

    /**
     * Helper function to create a user
     * 
     * @param email
     * @param phoneNumber
     * @throws Exception
     */
    private Passwordless.ConsumeCodeResponse createUserWith(TestingProcessManager.TestingProcess process, String email,
            String phoneNumber) throws Exception {

        Passwordless.CreateCodeResponse createCodeResponse = Passwordless.createCode(process.getProcess(), email,
                phoneNumber, null, null);
        assertNotNull(createCodeResponse);

        Passwordless.ConsumeCodeResponse consumeCodeResponse = Passwordless.consumeCode(process.getProcess(),
                createCodeResponse.deviceId, createCodeResponse.userInputCode, null);
        assertNotNull(consumeCodeResponse);

        return consumeCodeResponse;
    }

    /**
     * Helper function to kill application
     * 
     * @throws Exception
     */
    private void killApplication(TestingProcessManager.TestingProcess process) throws InterruptedException {

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));

    }

    /**
     * Helper function to load application with default args
     * 
     * @throws Exception
     */
    private TestingProcessManager.TestingProcess startApplicationWithDefaultArgs() throws Exception {

        String[] args = { "../" };

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return null;
        }

        return process;

    }

}
