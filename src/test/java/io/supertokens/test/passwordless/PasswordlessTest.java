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
import io.supertokens.passwordless.Passwordless;
import io.supertokens.passwordless.exceptions.ExpiredUserInputCodeException;
import io.supertokens.passwordless.exceptions.IncorrectUserInputCodeException;
import io.supertokens.passwordless.exceptions.RestartFlowException;
import io.supertokens.passwordless.Passwordless.ConsumeCodeResponse;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.passwordless.PasswordlessCode;
import io.supertokens.pluginInterface.passwordless.PasswordlessDevice;
import io.supertokens.pluginInterface.passwordless.PasswordlessStorage;
import io.supertokens.pluginInterface.passwordless.UserInfo;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.test.TestingProcessManager;
import io.supertokens.test.Utils;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

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

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

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
}
