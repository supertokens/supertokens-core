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
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.pluginInterface.emailpassword.exceptions.DuplicateEmailException;
import io.supertokens.pluginInterface.emailpassword.exceptions.DuplicateUserIdException;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.exceptions.StorageTransactionLogicException;
import io.supertokens.pluginInterface.passwordless.PasswordlessCode;
import io.supertokens.pluginInterface.passwordless.PasswordlessDevice;
import io.supertokens.pluginInterface.passwordless.PasswordlessStorage;
import io.supertokens.pluginInterface.passwordless.UserInfo;
import io.supertokens.pluginInterface.passwordless.exception.DuplicateCodeIdException;
import io.supertokens.pluginInterface.passwordless.exception.DuplicateDeviceIdHashException;
import io.supertokens.pluginInterface.passwordless.exception.DuplicateLinkCodeHashException;
import io.supertokens.pluginInterface.passwordless.exception.DuplicatePhoneNumberException;
import io.supertokens.pluginInterface.passwordless.exception.UnknownDeviceIdHash;
import io.supertokens.pluginInterface.passwordless.sqlStorage.PasswordlessSQLStorage;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.test.TestingProcessManager;
import io.supertokens.test.Utils;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import static org.junit.Assert.*;

public class PasswordlessStorageTest {

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
    public void testCreateDeviceWithCodeExceptions() throws Exception {
        String[] args = { "../" };

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        PasswordlessSQLStorage storage = StorageLayer.getPasswordlessStorage(process.getProcess());

        String email = "test@example.com";
        String codeId = io.supertokens.utils.Utils.getUUID();
        String codeId2 = io.supertokens.utils.Utils.getUUID();

        String deviceIdHash = "pZ9SP0USbXbejGFO6qx7x3JBjupJZVtw4RkFiNtJGqc";
        String deviceIdHash2 = "CBrV6o5XICEdnK4iPjxwVHetDBVjhRIgVYH8CzdFMhQ";

        String linkCodeHash = "wo5UcFFVSblZEd1KOUOl-dpJ5zpSr_Qsor1Eg4TzDRE";
        String linkCodeHash2 = "F0aZHCBYSJIghP5e0flGa8gvoUYEgGus2yIJYmdpFY4";

        storage.createDeviceWithCode(email, null,
                new PasswordlessCode(codeId, deviceIdHash, linkCodeHash, System.currentTimeMillis()));
        assertEquals(1, storage.getDevicesByEmail(email).length);

        {
            Exception error = null;
            try {
                storage.createDeviceWithCode(email, null,
                        new PasswordlessCode(codeId, deviceIdHash2, linkCodeHash2, System.currentTimeMillis()));
            } catch (Exception e) {
                error = e;
            }

            assertNotNull(error);
            assert (error instanceof DuplicateCodeIdException);
            assertEquals(1, storage.getDevicesByEmail(email).length);
            assertEquals(1, storage.getCodesOfDevice(deviceIdHash).length);
            assertNull(storage.getDevice(deviceIdHash2));
        }

        {
            Exception error = null;
            try {
                storage.createDeviceWithCode(email, null,
                        new PasswordlessCode(codeId2, deviceIdHash, linkCodeHash2, System.currentTimeMillis()));
            } catch (Exception e) {
                error = e;
            }

            assertNotNull(error);
            assert (error instanceof DuplicateDeviceIdHashException);
            assertEquals(1, storage.getDevicesByEmail(email).length);
            assertEquals(1, storage.getCodesOfDevice(deviceIdHash).length);
        }

        {
            Exception error = null;
            try {
                storage.createDeviceWithCode(email, null,
                        new PasswordlessCode(codeId2, deviceIdHash2, linkCodeHash, System.currentTimeMillis()));
            } catch (Exception e) {
                error = e;
            }

            assertNotNull(error);
            assert (error instanceof DuplicateLinkCodeHashException);
            assertEquals(1, storage.getDevicesByEmail(email).length);
            assertNull(storage.getDevice(deviceIdHash2));
        }

        {
            Exception error = null;
            try {
                storage.createDeviceWithCode(null, null,
                        new PasswordlessCode(codeId2, deviceIdHash2, linkCodeHash2, System.currentTimeMillis()));
            } catch (Exception e) {
                error = e;
            }

            assertNotNull(error);
            assert (error instanceof IllegalArgumentException);
            assertNull(storage.getDevice(deviceIdHash2));
        }

        storage.createDeviceWithCode(email, null,
                new PasswordlessCode(codeId2, deviceIdHash2, linkCodeHash2, System.currentTimeMillis()));

        assertEquals(2, storage.getDevicesByEmail(email).length);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testCreateCodeExceptions() throws Exception {
        String[] args = { "../" };

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        PasswordlessSQLStorage storage = StorageLayer.getPasswordlessStorage(process.getProcess());

        String email = "test@example.com";
        String codeId = io.supertokens.utils.Utils.getUUID();
        String codeId2 = io.supertokens.utils.Utils.getUUID();

        String deviceIdHash = "pZ9SP0USbXbejGFO6qx7x3JBjupJZVtw4RkFiNtJGqc";
        String linkCodeHash = "wo5UcFFVSblZEd1KOUOl-dpJ5zpSr_Qsor1Eg4TzDRE";
        String linkCodeHash2 = "F0aZHCBYSJIghP5e0flGa8gvoUYEgGus2yIJYmdpFY4";

        {
            Exception error = null;
            try {
                storage.createCode(
                        new PasswordlessCode(codeId, deviceIdHash, linkCodeHash, System.currentTimeMillis()));
            } catch (Exception e) {
                error = e;
            }

            assertNotNull(error);
            assert (error instanceof UnknownDeviceIdHash);
            assertEquals(0, storage.getDevicesByEmail(email).length);
            assertNull(storage.getCode(codeId));
        }

        storage.createDeviceWithCode(email, null,
                new PasswordlessCode(codeId, deviceIdHash, linkCodeHash, System.currentTimeMillis()));
        assertEquals(1, storage.getDevicesByEmail(email).length);

        {
            Exception error = null;
            try {
                storage.createCode(
                        new PasswordlessCode(codeId, deviceIdHash, linkCodeHash2, System.currentTimeMillis()));
            } catch (Exception e) {
                error = e;
            }

            assertNotNull(error);
            assert (error instanceof DuplicateCodeIdException);
            assertEquals(1, storage.getCodesOfDevice(deviceIdHash).length);
        }

        {
            Exception error = null;
            try {
                storage.createCode(
                        new PasswordlessCode(codeId2, deviceIdHash, linkCodeHash, System.currentTimeMillis()));
            } catch (Exception e) {
                error = e;
            }

            assertNotNull(error);
            assert (error instanceof DuplicateLinkCodeHashException);
            assertEquals(1, storage.getCodesOfDevice(deviceIdHash).length);
            assertNull(storage.getCode(codeId2));
        }

        storage.createCode(new PasswordlessCode(codeId2, deviceIdHash, linkCodeHash2, System.currentTimeMillis()));

        assertEquals(2, storage.getCodesOfDevice(deviceIdHash).length);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testCreateUserExceptions() throws Exception {
        String[] args = { "../" };

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        PasswordlessSQLStorage storage = StorageLayer.getPasswordlessStorage(process.getProcess());

        String email = "test@example.com";
        String email2 = "test2@example.com";

        String phoneNumber = "+442071838750";
        String phoneNumber2 = "+442082949861";

        String userId = io.supertokens.utils.Utils.getUUID();
        String userId2 = io.supertokens.utils.Utils.getUUID();
        String userId3 = io.supertokens.utils.Utils.getUUID();

        long joinedAt = System.currentTimeMillis();

        storage.createUser(new UserInfo(userId, email, null, joinedAt));
        storage.createUser(new UserInfo(userId2, null, phoneNumber, joinedAt));
        assertNotNull(storage.getUserById(userId));

        {
            Exception error = null;
            try {
                storage.createUser(new UserInfo(userId, email2, null, joinedAt));
            } catch (Exception e) {
                error = e;
            }

            assertNotNull(error);
            assert (error instanceof DuplicateUserIdException);
            assertNull(storage.getUserByEmail(email2));
        }

        {
            Exception error = null;
            try {
                storage.createUser(new UserInfo(userId, null, phoneNumber2, joinedAt));
            } catch (Exception e) {
                error = e;
            }

            assertNotNull(error);
            assert (error instanceof DuplicateUserIdException);
            assertNull(storage.getUserByPhoneNumber(phoneNumber2));
        }

        {
            Exception error = null;
            try {
                storage.createUser(new UserInfo(userId3, email, null, joinedAt));
            } catch (Exception e) {
                error = e;
            }

            assertNotNull(error);
            assert (error instanceof DuplicateEmailException);
            assertNull(storage.getUserById(userId3));
        }

        {
            Exception error = null;
            try {
                storage.createUser(new UserInfo(userId3, null, phoneNumber, joinedAt));
            } catch (Exception e) {
                error = e;
            }

            assertNotNull(error);
            assert (error instanceof DuplicatePhoneNumberException);
            assertNull(storage.getUserById(userId3));
        }

        {
            Exception error = null;
            try {
                storage.createUser(new UserInfo(userId3, null, null, joinedAt));
            } catch (Exception e) {
                error = e;
            }

            assertNotNull(error);
            assert (error instanceof IllegalArgumentException);
            assertNull(storage.getUserById(userId3));
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testUpdateUserExceptions() throws Exception {
        String[] args = { "../" };

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        PasswordlessSQLStorage storage = StorageLayer.getPasswordlessStorage(process.getProcess());

        String email = "test@example.com";
        String email2 = "test2@example.com";

        String phoneNumber = "+442071838750";
        String phoneNumber2 = "+442082949861";

        String userIdEmail1 = io.supertokens.utils.Utils.getUUID();
        String userIdEmail2 = io.supertokens.utils.Utils.getUUID();
        String userIdPhone1 = io.supertokens.utils.Utils.getUUID();
        String userIdPhone2 = io.supertokens.utils.Utils.getUUID();

        long joinedAt = System.currentTimeMillis();

        storage.createUser(new UserInfo(userIdEmail1, email, null, joinedAt));
        storage.createUser(new UserInfo(userIdEmail2, email2, null, joinedAt));
        storage.createUser(new UserInfo(userIdPhone1, null, phoneNumber, joinedAt));
        storage.createUser(new UserInfo(userIdPhone2, null, phoneNumber2, joinedAt));

        assertNotNull(storage.getUserById(userIdEmail1));

        {
            Exception error = null;
            try {
                storage.updateUser(userIdEmail1, email2, null);
            } catch (Exception e) {
                error = e;
            }

            assertNotNull(error);
            assert (error instanceof DuplicateEmailException);
            assertEquals(email, storage.getUserById(userIdEmail1).email);
        }

        {
            Exception error = null;
            try {
                storage.updateUser(userIdPhone1, null, phoneNumber2);
            } catch (Exception e) {
                error = e;
            }

            assertNotNull(error);
            assert (error instanceof DuplicatePhoneNumberException);
            assertEquals(phoneNumber, storage.getUserById(userIdPhone1).phoneNumber);
        }

        {
            Exception error = null;
            try {
                storage.updateUser(userIdEmail1, null, phoneNumber);
            } catch (Exception e) {
                error = e;
            }

            assertNotNull(error);
            assert (error instanceof DuplicatePhoneNumberException);
            UserInfo userInDb = storage.getUserById(userIdEmail1);
            assertEquals(email, userInDb.email);
            assertEquals(null, userInDb.phoneNumber);
        }

        {
            Exception error = null;
            try {
                storage.updateUser(userIdPhone1, email, null);
            } catch (Exception e) {
                error = e;
            }

            assertNotNull(error);
            assert (error instanceof DuplicateEmailException);
            UserInfo userInDb = storage.getUserById(userIdPhone1);
            assertEquals(null, userInDb.email);
            assertEquals(phoneNumber, userInDb.phoneNumber);
        }

        {
            Exception error = null;
            try {
                storage.updateUser(userIdPhone1, null, null);
            } catch (Exception e) {
                error = e;
            }

            assertNotNull(error);
            assert (error instanceof IllegalArgumentException);
            UserInfo userInDb = storage.getUserById(userIdPhone1);
            assertEquals(null, userInDb.email);
            assertEquals(phoneNumber, userInDb.phoneNumber);
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testUpdateUser() throws Exception {
        String[] args = { "../" };

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        PasswordlessSQLStorage storage = StorageLayer.getPasswordlessStorage(process.getProcess());

        String email = "test@example.com";
        String email2 = "test2@example.com";

        String phoneNumber = "+442071838750";
        String phoneNumber2 = "+442082949861";

        String userId = io.supertokens.utils.Utils.getUUID();

        long joinedAt = System.currentTimeMillis();

        storage.createUser(new UserInfo(userId, email, null, joinedAt));

        assertNotNull(storage.getUserById(userId));

        storage.updateUser(userId, email2, null);
        checkUser(storage, userId, email2, null);

        storage.updateUser(userId, null, phoneNumber);
        checkUser(storage, userId, null, phoneNumber);

        storage.updateUser(userId, null, phoneNumber2);
        checkUser(storage, userId, null, phoneNumber2);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    private void checkUser(PasswordlessSQLStorage storage, String userId, String email, String phoneNumber)
            throws StorageQueryException {
        UserInfo userById = storage.getUserById(userId);
        assertEquals(email, userById.email);
        assertEquals(phoneNumber, userById.phoneNumber);
        if (email != null) {
            UserInfo user = storage.getUserByEmail(email);
            assert (user.equals(userById));
        }
        if (phoneNumber != null) {
            UserInfo user = storage.getUserByPhoneNumber(phoneNumber);
            assert (user.equals(userById));
        }
    }
}
