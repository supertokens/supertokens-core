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
import io.supertokens.config.Config;
import io.supertokens.passwordless.Passwordless;
import io.supertokens.passwordless.exceptions.Base64EncodingException;
import io.supertokens.passwordless.exceptions.ExpiredUserInputCodeException;
import io.supertokens.passwordless.exceptions.IncorrectUserInputCodeException;
import io.supertokens.passwordless.exceptions.RestartFlowException;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.pluginInterface.authRecipe.AuthRecipeUserInfo;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.multitenancy.AppIdentifier;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifier;
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
 * This test class encompasses tests related to consume code flow
 */
public class PasswordlessConsumeCodeTest {

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
     * success without existing user - link code
     *
     * @throws Exception
     */
    @Test
    public void testConsumeLinkCode() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        PasswordlessStorage storage = (PasswordlessStorage) StorageLayer.getStorage(process.getProcess());

        Passwordless.CreateCodeResponse createCodeResponse = Passwordless.createCode(process.getProcess(), EMAIL, null,
                null, null);
        assertNotNull(createCodeResponse);

        long consumeStart = System.currentTimeMillis();
        Passwordless.ConsumeCodeResponse consumeCodeResponse = Passwordless.consumeCode(process.getProcess(), null,
                createCodeResponse.deviceIdHash, null, createCodeResponse.linkCode);
        assertNotNull(consumeCodeResponse);
        checkUserWithConsumeResponse(storage, consumeCodeResponse, EMAIL, null, consumeStart);

        PasswordlessDevice[] devices = storage.getDevicesByEmail(new TenantIdentifier(null, null, null), EMAIL);
        assertEquals(0, devices.length);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    /**
     * success without existing user - link code with equal signs removed
     *
     * @throws Exception
     */
    @Test
    public void testConsumeLinkCodeWithoutEqualSigns() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        PasswordlessStorage storage = (PasswordlessStorage) StorageLayer.getStorage(process.getProcess());

        Passwordless.CreateCodeResponse createCodeResponse = Passwordless.createCode(process.getProcess(), EMAIL, null,
                null, null);
        assertNotNull(createCodeResponse);

        assert (!createCodeResponse.deviceIdHash.contains("="));
        assert (!createCodeResponse.linkCode.contains("="));

        long consumeStart = System.currentTimeMillis();
        Passwordless.ConsumeCodeResponse consumeCodeResponse = Passwordless.consumeCode(process.getProcess(), null,
                createCodeResponse.deviceIdHash, null, createCodeResponse.linkCode);

        assertNotNull(consumeCodeResponse);
        checkUserWithConsumeResponse(storage, consumeCodeResponse, EMAIL, null, consumeStart);

        PasswordlessDevice[] devices = storage.getDevicesByEmail(new TenantIdentifier(null, null, null), EMAIL);
        assertEquals(0, devices.length);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    /**
     * success without existing user - link code with equal signs (padding)
     *
     * @throws Exception
     */
    @Test
    public void testConsumeLinkCodeWithEqualSigns() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        PasswordlessStorage storage = (PasswordlessStorage) StorageLayer.getStorage(process.getProcess());

        Passwordless.CreateCodeResponse createCodeResponse = Passwordless.createCode(process.getProcess(), EMAIL, null,
                null, null);
        assertNotNull(createCodeResponse);

        long consumeStart = System.currentTimeMillis();
        Passwordless.ConsumeCodeResponse consumeCodeResponse = Passwordless.consumeCode(process.getProcess(), null,
                createCodeResponse.deviceIdHash + "=", null, createCodeResponse.linkCode + "=");

        assertNotNull(consumeCodeResponse);
        checkUserWithConsumeResponse(storage, consumeCodeResponse, EMAIL, null, consumeStart);

        PasswordlessDevice[] devices = storage.getDevicesByEmail(new TenantIdentifier(null, null, null), EMAIL);
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
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        PasswordlessStorage storage = (PasswordlessStorage) StorageLayer.getStorage(process.getProcess());

        Passwordless.CreateCodeResponse createCodeResponse = Passwordless.createCode(process.getProcess(), EMAIL, null,
                null, null);
        assertNotNull(createCodeResponse);

        long consumeStart = System.currentTimeMillis();
        Passwordless.ConsumeCodeResponse consumeCodeResponse = Passwordless.consumeCode(process.getProcess(),
                createCodeResponse.deviceId, createCodeResponse.deviceIdHash, createCodeResponse.userInputCode, null);
        assertNotNull(consumeCodeResponse);
        assert (consumeCodeResponse.createdNewUser);
        checkUserWithConsumeResponse(storage, consumeCodeResponse, EMAIL, null, consumeStart);

        PasswordlessDevice[] devices = storage.getDevicesByEmail(new TenantIdentifier(null, null, null), EMAIL);
        assertEquals(0, devices.length);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    /**
     * success with existing user - inputcode
     *
     * @throws Exception
     */
    @Test
    public void testConsumeUserInputCodeWithExistingUser() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        PasswordlessStorage storage = (PasswordlessStorage) StorageLayer.getStorage(process.getProcess());

        AuthRecipeUserInfo user;
        {
            Passwordless.CreateCodeResponse createCodeResponse = Passwordless.createCode(process.getProcess(), EMAIL,
                    null, null, null);
            assertNotNull(createCodeResponse);

            long consumeStart = System.currentTimeMillis();
            Passwordless.ConsumeCodeResponse consumeCodeResponse = Passwordless.consumeCode(process.getProcess(),
                    createCodeResponse.deviceId, createCodeResponse.deviceIdHash, createCodeResponse.userInputCode,
                    null);
            assertNotNull(consumeCodeResponse);
            user = checkUserWithConsumeResponse(storage, consumeCodeResponse, EMAIL, null, consumeStart);
        }
        {
            Passwordless.CreateCodeResponse createCodeResponse = Passwordless.createCode(process.getProcess(), EMAIL,
                    null, null, null);
            assertNotNull(createCodeResponse);

            Passwordless.ConsumeCodeResponse consumeCodeResponse = Passwordless.consumeCode(process.getProcess(),
                    createCodeResponse.deviceId, createCodeResponse.deviceIdHash, createCodeResponse.userInputCode,
                    null);
            assertNotNull(consumeCodeResponse);
            assert (!consumeCodeResponse.createdNewUser);
            AuthRecipeUserInfo user2 = checkUserWithConsumeResponse(storage, consumeCodeResponse, EMAIL, null, 0);

            assert (user.equals(user2));
        }

        PasswordlessDevice[] devices = storage.getDevicesByEmail(new TenantIdentifier(null, null, null), EMAIL);
        assertEquals(0, devices.length);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    /**
     * success with existing user - link code
     *
     * @throws Exception
     */
    @Test
    public void testConsumeLinkCodeWithExistingUser() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        PasswordlessStorage storage = (PasswordlessStorage) StorageLayer.getStorage(process.getProcess());

        AuthRecipeUserInfo user;
        {
            Passwordless.CreateCodeResponse createCodeResponse = Passwordless.createCode(process.getProcess(), EMAIL,
                    null, null, null);
            assertNotNull(createCodeResponse);

            long consumeStart = System.currentTimeMillis();
            Passwordless.ConsumeCodeResponse consumeCodeResponse = Passwordless.consumeCode(process.getProcess(),
                    createCodeResponse.deviceId, createCodeResponse.deviceIdHash, null, createCodeResponse.linkCode);
            assertNotNull(consumeCodeResponse);
            user = checkUserWithConsumeResponse(storage, consumeCodeResponse, EMAIL, null, consumeStart);
        }
        {
            Passwordless.CreateCodeResponse createCodeResponse = Passwordless.createCode(process.getProcess(), EMAIL,
                    null, null, null);
            assertNotNull(createCodeResponse);

            Passwordless.ConsumeCodeResponse consumeCodeResponse = Passwordless.consumeCode(process.getProcess(),
                    createCodeResponse.deviceId, createCodeResponse.deviceIdHash, null, createCodeResponse.linkCode);
            assertNotNull(consumeCodeResponse);
            assert (!consumeCodeResponse.createdNewUser);
            AuthRecipeUserInfo user2 = checkUserWithConsumeResponse(storage, consumeCodeResponse, EMAIL, null, 0);

            assert (user.equals(user2));
        }

        PasswordlessDevice[] devices = storage.getDevicesByEmail(new TenantIdentifier(null, null, null), EMAIL);
        assertEquals(0, devices.length);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    /**
     * Check device clean up when user input code is generated via email & phone
     * number
     *
     * @throws Exception
     */
    @Test
    public void testConsumeCodeCleanupUserInputCodeWithEmailAndPhoneNumber() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        PasswordlessStorage storage = (PasswordlessStorage) StorageLayer.getStorage(process.getProcess());

        AuthRecipeUserInfo user;

        Passwordless.CreateCodeResponse createCodeResponse = Passwordless.createCode(process.getProcess(), EMAIL, null,
                null, null);
        assertNotNull(createCodeResponse);

        Passwordless.ConsumeCodeResponse consumeCodeResponse = Passwordless.consumeCode(process.getProcess(),
                createCodeResponse.deviceId, createCodeResponse.deviceIdHash, createCodeResponse.userInputCode, null);
        assertNotNull(consumeCodeResponse);

        AuthRecipeUserInfo authUser = storage.getPrimaryUserById(new AppIdentifier(null, null),
                consumeCodeResponse.user.getSupertokensUserId());
        Passwordless.updateUser(process.getProcess(), authUser.getSupertokensUserId(), null,
                new Passwordless.FieldUpdate(PHONE_NUMBER));
        authUser = storage.getPrimaryUserById(new AppIdentifier(null, null),
                consumeCodeResponse.user.getSupertokensUserId());
        assertEquals(authUser.loginMethods[0].phoneNumber, PHONE_NUMBER);

        // create code with email twice
        {
            Passwordless.createCode(process.getProcess(), EMAIL, null,
                    null, null);
        }
        {
            Passwordless.createCode(process.getProcess(), EMAIL, null,
                    null, null);
        }

        // create code with phone number twice
        {
            Passwordless.createCode(process.getProcess(), null,
                    PHONE_NUMBER, null, null);
        }

        Passwordless.CreateCodeResponse codeResponse = Passwordless.createCode(process.getProcess(), null, PHONE_NUMBER,
                null, null);

        // 4 codes should be available
        PasswordlessDevice[] devices = storage.getDevicesByPhoneNumber(new TenantIdentifier(null, null, null),
                PHONE_NUMBER);
        assertEquals(2, devices.length);

        devices = storage.getDevicesByEmail(new TenantIdentifier(null, null, null), EMAIL);
        assertEquals(2, devices.length);

        // consume code
        Passwordless.consumeCode(process.getProcess(),
                codeResponse.deviceId, codeResponse.deviceIdHash, codeResponse.userInputCode, null);

        devices = storage.getDevicesByEmail(new TenantIdentifier(null, null, null), EMAIL);
        assertEquals(0, devices.length);

        devices = storage.getDevicesByPhoneNumber(new TenantIdentifier(null, null, null), PHONE_NUMBER);
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
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        PasswordlessStorage storage = (PasswordlessStorage) StorageLayer.getStorage(process.getProcess());

        AuthRecipeUserInfo user;

        Passwordless.CreateCodeResponse createCodeResponse = Passwordless.createCode(process.getProcess(), EMAIL, null,
                null, null);
        assertNotNull(createCodeResponse);

        Passwordless.ConsumeCodeResponse consumeCodeResponse = Passwordless.consumeCode(process.getProcess(),
                createCodeResponse.deviceId, createCodeResponse.deviceIdHash, createCodeResponse.userInputCode, null);
        assertNotNull(consumeCodeResponse);

        AuthRecipeUserInfo authUser = storage.getPrimaryUserById(new AppIdentifier(null, null),
                consumeCodeResponse.user.getSupertokensUserId());
        Passwordless.updateUser(process.getProcess(), authUser.getSupertokensUserId(), null,
                new Passwordless.FieldUpdate(PHONE_NUMBER));
        authUser = storage.getPrimaryUserById(new AppIdentifier(null, null),
                consumeCodeResponse.user.getSupertokensUserId());
        assertEquals(authUser.loginMethods[0].phoneNumber, PHONE_NUMBER);

        // create code with email twice
        {
            Passwordless.createCode(process.getProcess(), EMAIL, null,
                    null, null);
        }
        {
            Passwordless.createCode(process.getProcess(), EMAIL, null,
                    null, null);
        }

        // create code with phone number twice
        {
            Passwordless.createCode(process.getProcess(), null,
                    PHONE_NUMBER, null, null);
        }

        Passwordless.CreateCodeResponse codeResponse = Passwordless.createCode(process.getProcess(), null, PHONE_NUMBER,
                null, null);

        // 4 codes should be available
        PasswordlessDevice[] devices = storage.getDevicesByPhoneNumber(new TenantIdentifier(null, null, null),
                PHONE_NUMBER);
        assertEquals(2, devices.length);

        devices = storage.getDevicesByEmail(new TenantIdentifier(null, null, null), EMAIL);
        assertEquals(2, devices.length);

        // consume code
        Passwordless.consumeCode(process.getProcess(),
                codeResponse.deviceId, codeResponse.deviceIdHash, null, codeResponse.linkCode);

        devices = storage.getDevicesByEmail(new TenantIdentifier(null, null, null), EMAIL);
        assertEquals(0, devices.length);

        devices = storage.getDevicesByPhoneNumber(new TenantIdentifier(null, null, null), PHONE_NUMBER);
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
        String[] args = {"../"};

        Utils.setValueInConfig("passwordless_code_lifetime", "100");
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        PasswordlessStorage storage = (PasswordlessStorage) StorageLayer.getStorage(process.getProcess());

        Passwordless.CreateCodeResponse createCodeResponse = Passwordless.createCode(process.getProcess(), EMAIL, null,
                null, null);
        assertNotNull(createCodeResponse);

        Exception error = null;
        try {
            Passwordless.consumeCode(process.getProcess(), null, null, null, createCodeResponse.linkCode + "##");
        } catch (Exception ex) {
            error = ex;
        }
        assertNotNull(error);
        assert (error instanceof Base64EncodingException);

        PasswordlessDevice[] devices = storage.getDevicesByEmail(new TenantIdentifier(null, null, null), EMAIL);
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
        String[] args = {"../"};

        Utils.setValueInConfig("passwordless_code_lifetime", "100");
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        PasswordlessStorage storage = (PasswordlessStorage) StorageLayer.getStorage(process.getProcess());

        Passwordless.CreateCodeResponse createCodeResponse = Passwordless.createCode(process.getProcess(), EMAIL, null,
                null, null);
        assertNotNull(createCodeResponse);

        Exception error = null;
        try {
            Passwordless.consumeCode(process.getProcess(), null, null, null,
                    "bbbbbbbbbbbbbb20JgnKPL5lwFBrfJexadmg9I_Ilp0=");

        } catch (Exception ex) {
            error = ex;
        }
        assertNotNull(error);
        assert (error instanceof RestartFlowException);

        // verify that devices have not been cleared
        PasswordlessDevice[] devices = storage.getDevicesByEmail(new TenantIdentifier(null, null, null), EMAIL);
        assertNotEquals(0, devices.length);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    /**
     * non existing input code
     *
     * @throws Exception
     */
    @Test
    public void testConsumeWrongUserInputCode() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        PasswordlessStorage storage = (PasswordlessStorage) StorageLayer.getStorage(process.getProcess());

        Passwordless.CreateCodeResponse createCodeResponse = Passwordless.createCode(process.getProcess(), EMAIL, null,
                null, null);
        assertNotNull(createCodeResponse);

        Exception error = null;
        try {
            Passwordless.consumeCode(process.getProcess(), createCodeResponse.deviceId, createCodeResponse.deviceIdHash,
                    "n0p321", null);
        } catch (Exception ex) {
            error = ex;
        }
        assertNotNull(error);
        assert (error instanceof IncorrectUserInputCodeException);

        // verify that devices have not been cleared
        PasswordlessDevice[] devices = storage.getDevicesByEmail(new TenantIdentifier(null, null, null), EMAIL);
        assertNotEquals(0, devices.length);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    /**
     * consume expired link code
     *
     * @throws Exception
     */
    @Test
    public void testConsumeExpiredLinkCode() throws Exception {
        String[] args = {"../"};

        Utils.setValueInConfig("passwordless_code_lifetime", "100");
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        PasswordlessStorage storage = (PasswordlessStorage) StorageLayer.getStorage(process.getProcess());

        Passwordless.CreateCodeResponse createCodeResponse = Passwordless.createCode(process.getProcess(), EMAIL, null,
                null, null);
        assertNotNull(createCodeResponse);

        Thread.sleep(120);
        Exception error = null;
        try {
            Passwordless.consumeCode(process.getProcess(), null, null, null, createCodeResponse.linkCode);

        } catch (Exception ex) {
            error = ex;
        }
        assertNotNull(error);
        assert (error instanceof RestartFlowException);

        // verify that devices have not been cleared
        PasswordlessDevice[] devices = storage.getDevicesByEmail(new TenantIdentifier(null, null, null), EMAIL);
        assertNotEquals(0, devices.length);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    /**
     * Expired user input code
     *
     * @throws Exception
     */
    @Test
    public void testConsumeExpiredUserInputCode() throws Exception {
        String[] args = {"../"};

        Utils.setValueInConfig("passwordless_code_lifetime", "100");
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        PasswordlessStorage storage = (PasswordlessStorage) StorageLayer.getStorage(process.getProcess());

        Passwordless.CreateCodeResponse createCodeResponse = Passwordless.createCode(process.getProcess(), EMAIL, null,
                null, null);
        assertNotNull(createCodeResponse);

        Thread.sleep(120);
        Exception error = null;
        try {
            Passwordless.consumeCode(process.getProcess(), createCodeResponse.deviceId, createCodeResponse.deviceIdHash,
                    createCodeResponse.userInputCode, null);
        } catch (Exception ex) {
            error = ex;
        }
        assertNotNull(error);
        assert (error instanceof ExpiredUserInputCodeException);

        // verify that devices have not been cleared
        PasswordlessDevice[] devices = storage.getDevicesByEmail(new TenantIdentifier(null, null, null), EMAIL);
        assertNotEquals(0, devices.length);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testConsumeExpiredUserInputCodeAfterResend() throws Exception {
        String[] args = {"../"};

        Utils.setValueInConfig("passwordless_code_lifetime", "100");
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        PasswordlessStorage storage = (PasswordlessStorage) StorageLayer.getStorage(process.getProcess());

        Passwordless.CreateCodeResponse createCodeResponse = Passwordless.createCode(process.getProcess(), EMAIL, null,
                null, null);
        assertNotNull(createCodeResponse);

        Thread.sleep(120);

        Exception error = null;
        try {
            Passwordless.consumeCode(process.getProcess(), createCodeResponse.deviceId, createCodeResponse.deviceIdHash,
                    createCodeResponse.userInputCode, null);
        } catch (Exception ex) {
            error = ex;
        }
        assertNotNull(error);
        assert (error instanceof ExpiredUserInputCodeException);

        // verify that devices have not been cleared
        PasswordlessDevice[] devices = storage.getDevicesByEmail(new TenantIdentifier(null, null, null), EMAIL);
        assertNotEquals(0, devices.length);

        Passwordless.CreateCodeResponse newCodeResponse = Passwordless.createCode(process.getProcess(), null, null,
                createCodeResponse.deviceId, null);

        Passwordless.ConsumeCodeResponse consumeCodeResponse = Passwordless.consumeCode(process.getProcess(),
                newCodeResponse.deviceId, newCodeResponse.deviceIdHash, newCodeResponse.userInputCode, null);

        assertNotNull(consumeCodeResponse);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    /**
     * wrong userInputCode exceeding maxCodeInputAttempts
     *
     * @throws Exception
     */
    @Test
    public void testConsumeWrongUserInputCodeExceedingMaxAttempts() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        PasswordlessStorage storage = (PasswordlessStorage) StorageLayer.getStorage(process.getProcess());

        Passwordless.CreateCodeResponse createCodeResponse = Passwordless.createCode(process.getProcess(), EMAIL, null,
                null, null);
        assertNotNull(createCodeResponse);

        Exception error = null;
        int maxCodeInputAttempts = Config.getConfig(process.getProcess()).getPasswordlessMaxCodeInputAttempts();
        for (int counter = 0; counter < maxCodeInputAttempts - 1; counter++) {

            Exception exception = null;
            try {
                Passwordless.consumeCode(process.getProcess(), createCodeResponse.deviceId,
                        createCodeResponse.deviceIdHash, "n0p321", null);
            } catch (Exception ex) {
                exception = ex;
            }

            assertNotNull(exception);
            assert (exception instanceof IncorrectUserInputCodeException);
        }
        try {
            Passwordless.consumeCode(process.getProcess(), createCodeResponse.deviceId, createCodeResponse.deviceIdHash,
                    "n0p321", null);
        } catch (Exception ex) {
            error = ex;
        }
        assertNotNull(error);
        assert (error instanceof RestartFlowException);

        // verify that devices have been cleared, since max attempt reached
        PasswordlessDevice[] devices = storage.getDevicesByEmail(new TenantIdentifier(null, null, null), EMAIL);
        assertEquals(0, devices.length);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    /**
     * wrong linkCode exceeding maxCodeInputAttempts
     *
     * @throws Exception
     */

    @Test
    public void testConsumeWrongLinkCodeExceedingMaxAttempts() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        PasswordlessStorage storage = (PasswordlessStorage) StorageLayer.getStorage(process.getProcess());

        Passwordless.CreateCodeResponse createCodeResponse = Passwordless.createCode(process.getProcess(), EMAIL, null,
                null, null);
        assertNotNull(createCodeResponse);

        Exception error = null;
        int maxCodeInputAttempts = Config.getConfig(process.getProcess()).getPasswordlessMaxCodeInputAttempts();

        for (int counter = 0; counter < maxCodeInputAttempts; counter++) {

            try {
                Passwordless.consumeCode(process.getProcess(), null, createCodeResponse.deviceIdHash, null, "n0p321");
            } catch (Exception ex) {
                error = ex;
            }

        }

        assertNotNull(error);
        assert (error instanceof RestartFlowException);

        // verify that devices have not been cleared
        PasswordlessDevice[] devices = storage.getDevicesByEmail(new TenantIdentifier(null, null, null), EMAIL);
        assertNotEquals(0, devices.length);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    /**
     * user input code with too many failedAttempts (changed maxCodeInputAttempts
     * configuration between consumes)
     * TODO: review -> do we need to create code again post restart ?
     *
     * @throws Exception
     */

    @Test
    public void testConsumeWrongUserInputCodeExceedingMaxAttemptsWithConfigUpdate() throws Exception {
        // start process with default configuration for max attempt
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }
        PasswordlessStorage storage = (PasswordlessStorage) StorageLayer.getStorage(process.getProcess());

        Passwordless.CreateCodeResponse createCodeResponse = Passwordless.createCode(process.getProcess(), EMAIL, null,
                null, null);
        assertNotNull(createCodeResponse);

        Exception error = null;
        int maxCodeInputAttempts = Config.getConfig(process.getProcess()).getPasswordlessMaxCodeInputAttempts();

        // input wrong code max attempts - 1 times
        for (int counter = 0; counter < maxCodeInputAttempts - 1; counter++) {

            Exception exception = null;
            try {
                Passwordless.consumeCode(process.getProcess(), createCodeResponse.deviceId,
                        createCodeResponse.deviceIdHash, "n0p321", null);
            } catch (Exception ex) {
                exception = ex;
            }

            assertNotNull(exception);
            assert (exception instanceof IncorrectUserInputCodeException);

        }

        // verify that devices have not been cleared
        PasswordlessDevice[] devices = storage.getDevicesByEmail(new TenantIdentifier(null, null, null), EMAIL);
        assertNotEquals(0, devices.length);

        // kill process and restart with increased max code input attempts
        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));

        Utils.setValueInConfig("passwordless_max_code_input_attempts", String.valueOf(maxCodeInputAttempts - 3));
        process = TestingProcessManager.start(args);

        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        storage = (PasswordlessStorage) StorageLayer.getStorage(process.getProcess());

        try {
            Passwordless.consumeCode(process.getProcess(), createCodeResponse.deviceId, createCodeResponse.deviceIdHash,
                    createCodeResponse.userInputCode, null);
        } catch (Exception ex) {
            error = ex;
        }
        assertNotNull(error);
        assert (error instanceof RestartFlowException);

        devices = storage.getDevicesByEmail(new TenantIdentifier(null, null, null), EMAIL);
        assertEquals(0, devices.length);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    private AuthRecipeUserInfo checkUserWithConsumeResponse(PasswordlessStorage storage,
                                                            Passwordless.ConsumeCodeResponse resp,
                                                            String email, String phoneNumber, long joinedAfter)
            throws StorageQueryException {
        AuthRecipeUserInfo user = storage.getPrimaryUserById(new AppIdentifier(null, null),
                resp.user.getSupertokensUserId());
        assertNotNull(user);

        assertEquals(email, resp.user.loginMethods[0].email);
        assertEquals(email, user.loginMethods[0].email);

        assertEquals(phoneNumber, user.loginMethods[0].phoneNumber);
        assertEquals(phoneNumber, resp.user.loginMethods[0].phoneNumber);

        assert (user.timeJoined >= joinedAfter);
        assertEquals(user.timeJoined, resp.user.timeJoined);

        return user;
    }
}
