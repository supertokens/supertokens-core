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
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.pluginInterface.authRecipe.AuthRecipeUserInfo;
import io.supertokens.pluginInterface.emailpassword.exceptions.DuplicateEmailException;
import io.supertokens.pluginInterface.emailpassword.exceptions.DuplicateUserIdException;
import io.supertokens.pluginInterface.emailpassword.exceptions.UnknownUserIdException;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.exceptions.StorageTransactionLogicException;
import io.supertokens.pluginInterface.multitenancy.AppIdentifier;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifier;
import io.supertokens.pluginInterface.passwordless.PasswordlessCode;
import io.supertokens.pluginInterface.passwordless.exception.*;
import io.supertokens.pluginInterface.passwordless.sqlStorage.PasswordlessSQLStorage;
import io.supertokens.pluginInterface.sqlStorage.TransactionConnection;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.test.TestingProcessManager;
import io.supertokens.test.Utils;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicReference;

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
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        PasswordlessSQLStorage storage = (PasswordlessSQLStorage) StorageLayer.getStorage(process.getProcess());

        String email = "test@example.com";
        PasswordlessCode code1 = getRandomCodeInfo();
        PasswordlessCode code2 = getRandomCodeInfo();

        storage.createDeviceWithCode(new TenantIdentifier(null, null, null), email, null, "linkCodeSalt", code1);
        assertEquals(1, storage.getDevicesByEmail(new TenantIdentifier(null, null, null), email).length);

        {
            Exception error = null;
            try {
                storage.createDeviceWithCode(new TenantIdentifier(null, null, null), email, null, "linkCodeSalt",
                        new PasswordlessCode(code1.id,
                                code2.deviceIdHash, code2.linkCodeHash, System.currentTimeMillis()));
            } catch (Exception e) {
                error = e;
            }

            assertNotNull(error);
            assert (error instanceof DuplicateCodeIdException);
            assertEquals(1, storage.getDevicesByEmail(new TenantIdentifier(null, null, null), email).length);
            assertEquals(1,
                    storage.getCodesOfDevice(new TenantIdentifier(null, null, null), code1.deviceIdHash).length);
            assertNull(storage.getDevice(new TenantIdentifier(null, null, null), code2.deviceIdHash));
        }

        {
            Exception error = null;
            try {
                storage.createDeviceWithCode(new TenantIdentifier(null, null, null), email, null, "linkCodeSalt",
                        new PasswordlessCode(code2.id,
                                code1.deviceIdHash, code2.linkCodeHash, System.currentTimeMillis()));
            } catch (Exception e) {
                error = e;
            }

            assertNotNull(error);
            assert (error instanceof DuplicateDeviceIdHashException);
            assertEquals(1, storage.getDevicesByEmail(new TenantIdentifier(null, null, null), email).length);
            assertEquals(1,
                    storage.getCodesOfDevice(new TenantIdentifier(null, null, null), code1.deviceIdHash).length);
        }

        {
            Exception error = null;
            try {
                storage.createDeviceWithCode(new TenantIdentifier(null, null, null), email, null, "linkCodeSalt",
                        new PasswordlessCode(code2.id,
                                code2.deviceIdHash, code1.linkCodeHash, System.currentTimeMillis()));
            } catch (Exception e) {
                error = e;
            }

            assertNotNull(error);
            assert (error instanceof DuplicateLinkCodeHashException);
            assertEquals(1, storage.getDevicesByEmail(new TenantIdentifier(null, null, null), email).length);
            assertNull(storage.getDevice(new TenantIdentifier(null, null, null), code2.deviceIdHash));
        }

        {
            Exception error = null;
            try {
                storage.createDeviceWithCode(new TenantIdentifier(null, null, null), null, null, "linkCodeSalt", code2);
            } catch (Exception e) {
                error = e;
            }

            assertNotNull(error);
            assert (error instanceof IllegalArgumentException);
            assertNull(storage.getDevice(new TenantIdentifier(null, null, null), code2.deviceIdHash));
        }

        storage.createDeviceWithCode(new TenantIdentifier(null, null, null), email, null, "linkCodeSalt", code2);

        assertEquals(2, storage.getDevicesByEmail(new TenantIdentifier(null, null, null), email).length);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testCreateCodeExceptions() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        PasswordlessSQLStorage storage = (PasswordlessSQLStorage) StorageLayer.getStorage(process.getProcess());

        String email = "test@example.com";
        PasswordlessCode code1 = getRandomCodeInfo();
        PasswordlessCode code2 = getRandomCodeInfo(code1.deviceIdHash);

        {
            Exception error = null;
            try {
                storage.createCode(new TenantIdentifier(null, null, null),
                        new PasswordlessCode(code1.id, code1.deviceIdHash, code1.linkCodeHash,
                                System.currentTimeMillis()));
            } catch (Exception e) {
                error = e;
            }

            assertNotNull(error);
            assert (error instanceof UnknownDeviceIdHash);
            assertEquals(0, storage.getDevicesByEmail(new TenantIdentifier(null, null, null), email).length);
            assertNull(storage.getCode(new TenantIdentifier(null, null, null), code1.id));
        }

        storage.createDeviceWithCode(new TenantIdentifier(null, null, null), email, null, "linkCodeSalt", code1);
        assertEquals(1, storage.getDevicesByEmail(new TenantIdentifier(null, null, null), email).length);

        {
            Exception error = null;
            try {
                storage.createCode(new TenantIdentifier(null, null, null),
                        new PasswordlessCode(code1.id, code1.deviceIdHash, code2.linkCodeHash,
                                System.currentTimeMillis()));
            } catch (Exception e) {
                error = e;
            }

            assertNotNull(error);
            assert (error instanceof DuplicateCodeIdException);
            assertEquals(1,
                    storage.getCodesOfDevice(new TenantIdentifier(null, null, null), code1.deviceIdHash).length);
        }

        {
            Exception error = null;
            try {
                storage.createCode(new TenantIdentifier(null, null, null),
                        new PasswordlessCode(code2.id, code1.deviceIdHash, code1.linkCodeHash,
                                System.currentTimeMillis()));
            } catch (Exception e) {
                error = e;
            }

            assertNotNull(error);
            assert (error instanceof DuplicateLinkCodeHashException);
            assertEquals(1,
                    storage.getCodesOfDevice(new TenantIdentifier(null, null, null), code1.deviceIdHash).length);
            assertNull(storage.getCode(new TenantIdentifier(null, null, null), code2.id));
        }

        storage.createCode(new TenantIdentifier(null, null, null), code2);

        assertEquals(2, storage.getCodesOfDevice(new TenantIdentifier(null, null, null), code1.deviceIdHash).length);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testCreateUserExceptions() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        PasswordlessSQLStorage storage = (PasswordlessSQLStorage) StorageLayer.getStorage(process.getProcess());

        String email = "test@example.com";
        String email2 = "test2@example.com";

        String phoneNumber = "+442071838750";
        String phoneNumber2 = "+442082949861";

        String userId = io.supertokens.utils.Utils.getUUID();
        String userId2 = io.supertokens.utils.Utils.getUUID();
        String userId3 = io.supertokens.utils.Utils.getUUID();

        long timeJoined = System.currentTimeMillis();

        storage.createUser(new TenantIdentifier(null, null, null), userId, email, null, timeJoined);
        storage.createUser(new TenantIdentifier(null, null, null),
                userId2, null, phoneNumber, timeJoined);
        assertNotNull(storage.getPrimaryUserById(new AppIdentifier(null, null), userId));

        {
            Exception error = null;
            try {
                storage.createUser(new TenantIdentifier(null, null, null),
                        userId, email2, null, timeJoined);
            } catch (Exception e) {
                error = e;
            }

            assertNotNull(error);
            assert (error instanceof DuplicateUserIdException);
            assertEquals(0, storage.listPrimaryUsersByEmail(new TenantIdentifier(null, null, null), email2).length);
        }

        {
            Exception error = null;
            try {
                storage.createUser(new TenantIdentifier(null, null, null),
                        userId, null, phoneNumber2, timeJoined);
            } catch (Exception e) {
                error = e;
            }

            assertNotNull(error);
            assert (error instanceof DuplicateUserIdException);
            assert (storage.listPrimaryUsersByPhoneNumber(new TenantIdentifier(null, null, null),
                    phoneNumber2).length == 0);
        }

        {
            Exception error = null;
            try {
                storage.createUser(new TenantIdentifier(null, null, null),
                        userId3, email, null, timeJoined);
            } catch (Exception e) {
                error = e;
            }

            assertNotNull(error);
            assert (error instanceof DuplicateEmailException);
            assertNull(storage.getPrimaryUserById(new AppIdentifier(null, null), userId3));
        }

        {
            Exception error = null;
            try {
                storage.createUser(new TenantIdentifier(null, null, null),
                        userId3, null, phoneNumber, timeJoined);
            } catch (Exception e) {
                error = e;
            }

            assertNotNull(error);
            assert (error instanceof DuplicatePhoneNumberException);
            assertNull(storage.getPrimaryUserById(new AppIdentifier(null, null), userId3));
        }

        {
            Exception error = null;
            try {
                storage.createUser(new TenantIdentifier(null, null, null),
                        userId3, null, null, timeJoined);
            } catch (Exception e) {
                error = e;
            }

            assertNotNull(error);
            assert (error instanceof IllegalArgumentException);
            assertNull(storage.getPrimaryUserById(new AppIdentifier(null, null), userId3));
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testUpdateUserExceptions() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        PasswordlessSQLStorage storage = (PasswordlessSQLStorage) StorageLayer.getStorage(process.getProcess());

        String email = "test@example.com";
        String email2 = "test2@example.com";
        String email3 = "test3@example.com";

        String phoneNumber = "+442071838750";
        String phoneNumber2 = "+442082949861";
        String phoneNumber3 = "+442082949862";

        String userIdNotExists = io.supertokens.utils.Utils.getUUID();
        String userIdEmail1 = io.supertokens.utils.Utils.getUUID();
        String userIdEmail2 = io.supertokens.utils.Utils.getUUID();
        String userIdPhone1 = io.supertokens.utils.Utils.getUUID();
        String userIdPhone2 = io.supertokens.utils.Utils.getUUID();

        long timeJoined = System.currentTimeMillis();

        storage.createUser(new TenantIdentifier(null, null, null), userIdEmail1, email, null, timeJoined);
        storage.createUser(new TenantIdentifier(null, null, null),
                userIdEmail2, email2, null, timeJoined);
        storage.createUser(new TenantIdentifier(null, null, null),
                userIdPhone1, null, phoneNumber, timeJoined);
        storage.createUser(new TenantIdentifier(null, null, null),
                userIdPhone2, null, phoneNumber2, timeJoined);

        assertNotNull(storage.getPrimaryUserById(new AppIdentifier(null, null), userIdEmail1));

        {
            Exception error = null;
            try {
                storage.startTransaction(con -> {
                    try {
                        storage.updateUserEmail_Transaction(new AppIdentifier(null, null), con, userIdNotExists,
                                email3);
                    } catch (UnknownUserIdException | DuplicateEmailException e) {
                        throw new StorageTransactionLogicException(e);
                    }
                    storage.commitTransaction(con);
                    return null;
                });
            } catch (StorageTransactionLogicException e) {
                error = e.actualException;
            }

            assertNotNull(error);
            assert (error instanceof UnknownUserIdException);
            assertNull(storage.getPrimaryUserById(new AppIdentifier(null, null), userIdNotExists));
        }

        {
            Exception error = null;
            try {
                storage.startTransaction(con -> {
                    try {
                        storage.updateUserPhoneNumber_Transaction(new AppIdentifier(null, null), con, userIdNotExists,
                                phoneNumber3);
                    } catch (UnknownUserIdException | DuplicatePhoneNumberException e) {
                        throw new StorageTransactionLogicException(e);
                    }
                    storage.commitTransaction(con);
                    return null;
                });
            } catch (StorageTransactionLogicException e) {
                error = e.actualException;
            }

            assertNotNull(error);
            assert (error instanceof UnknownUserIdException);
            assertNull(storage.getPrimaryUserById(new AppIdentifier(null, null), userIdNotExists));
        }

        {
            Exception error = null;
            try {
                storage.startTransaction(con -> {
                    try {
                        storage.updateUserEmail_Transaction(
                                new AppIdentifier(null, null), con, userIdEmail1, email2);
                    } catch (UnknownUserIdException | DuplicateEmailException e) {
                        throw new StorageTransactionLogicException(e);
                    }
                    storage.commitTransaction(con);
                    return null;
                });
            } catch (StorageTransactionLogicException e) {
                error = e.actualException;
            }

            assertNotNull(error);
            assert (error instanceof DuplicateEmailException);
            assertEquals(email,
                    storage.getPrimaryUserById(new AppIdentifier(null, null), userIdEmail1).loginMethods[0].email);
        }

        {
            Exception error = null;
            try {
                storage.startTransaction(con -> {
                    try {
                        storage.updateUserEmail_Transaction(new AppIdentifier(null, null), con, userIdEmail1, email2);
                    } catch (UnknownUserIdException | DuplicateEmailException e) {
                        throw new StorageTransactionLogicException(e);
                    }
                    storage.commitTransaction(con);
                    return null;
                });
            } catch (StorageTransactionLogicException e) {
                error = e.actualException;
            }

            assertNotNull(error);
            assert (error instanceof DuplicateEmailException);
            assertEquals(email,
                    storage.getPrimaryUserById(new AppIdentifier(null, null), userIdEmail1).loginMethods[0].email);
        }

        {
            Exception error = null;
            try {
                storage.startTransaction(con -> {
                    try {
                        storage.updateUserPhoneNumber_Transaction(new AppIdentifier(null, null), con, userIdPhone1,
                                phoneNumber2);
                    } catch (UnknownUserIdException | DuplicatePhoneNumberException e) {
                        throw new StorageTransactionLogicException(e);
                    }
                    storage.commitTransaction(con);
                    return null;
                });
            } catch (StorageTransactionLogicException e) {
                error = e.actualException;
            }

            assertNotNull(error);
            assert (error instanceof DuplicatePhoneNumberException);
            assertEquals(phoneNumber,
                    storage.getPrimaryUserById(new AppIdentifier(null, null),
                            userIdPhone1).loginMethods[0].phoneNumber);
        }

        {
            Exception error = null;
            try {
                storage.startTransaction(con -> {
                    try {
                        storage.updateUserPhoneNumber_Transaction(new AppIdentifier(null, null), con, userIdEmail1,
                                phoneNumber);
                    } catch (UnknownUserIdException | DuplicatePhoneNumberException e) {
                        throw new StorageTransactionLogicException(e);
                    }
                    storage.commitTransaction(con);
                    return null;
                });
            } catch (StorageTransactionLogicException e) {
                error = e.actualException;
            }

            assertNotNull(error);
            assert (error instanceof DuplicatePhoneNumberException);
            AuthRecipeUserInfo userInDb = storage.getPrimaryUserById(new AppIdentifier(null, null), userIdEmail1);
            assertEquals(email, userInDb.loginMethods[0].email);
            assertNull(userInDb.loginMethods[0].phoneNumber);
        }

        {
            Exception error = null;
            try {
                storage.startTransaction(con -> {
                    try {
                        storage.updateUserEmail_Transaction(new AppIdentifier(null, null), con, userIdPhone1, email);
                    } catch (UnknownUserIdException | DuplicateEmailException e) {
                        throw new StorageTransactionLogicException(e);
                    }
                    storage.commitTransaction(con);
                    return null;
                });
            } catch (StorageTransactionLogicException e) {
                error = e.actualException;
            }

            assertNotNull(error);
            assert (error instanceof DuplicateEmailException);
            AuthRecipeUserInfo userInDb = storage.getPrimaryUserById(new AppIdentifier(null, null), userIdPhone1);
            assertNull(userInDb.loginMethods[0].email);
            assertEquals(phoneNumber, userInDb.loginMethods[0].phoneNumber);
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testUpdateUser() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        PasswordlessSQLStorage storage = (PasswordlessSQLStorage) StorageLayer.getStorage(process.getProcess());

        String email = "test@example.com";
        String email2 = "test2@example.com";

        String phoneNumber = "+442071838750";
        String phoneNumber2 = "+442082949861";

        String userId = io.supertokens.utils.Utils.getUUID();

        long timeJoined = System.currentTimeMillis();

        storage.createUser(new TenantIdentifier(null, null, null), userId, email, null, timeJoined);

        assertNotNull(storage.getPrimaryUserById(new AppIdentifier(null, null), userId));

        storage.startTransaction(con -> {
            try {
                storage.updateUserEmail_Transaction(new AppIdentifier(null, null), con, userId, email2);
            } catch (UnknownUserIdException | DuplicateEmailException e) {
                throw new StorageTransactionLogicException(e);
            }
            storage.commitTransaction(con);
            return null;
        });
        checkUser(storage, userId, email2, null);

        storage.startTransaction(con -> {
            try {
                storage.updateUserEmail_Transaction(new AppIdentifier(null, null), con, userId, null);
            } catch (UnknownUserIdException | DuplicateEmailException e) {
                throw new StorageTransactionLogicException(e);
            }
            try {
                storage.updateUserPhoneNumber_Transaction(new AppIdentifier(null, null), con, userId, phoneNumber);
            } catch (UnknownUserIdException | DuplicatePhoneNumberException e) {
                throw new StorageTransactionLogicException(e);
            }
            storage.commitTransaction(con);
            return null;
        });
        checkUser(storage, userId, null, phoneNumber);

        storage.startTransaction(con -> {
            try {
                storage.updateUserPhoneNumber_Transaction(new AppIdentifier(null, null), con, userId, phoneNumber2);
            } catch (UnknownUserIdException | DuplicatePhoneNumberException e) {
                throw new StorageTransactionLogicException(e);
            }
            storage.commitTransaction(con);
            return null;
        });
        checkUser(storage, userId, null, phoneNumber2);

        storage.startTransaction(con -> {
            try {
                storage.updateUserEmail_Transaction(new AppIdentifier(null, null), con, userId, email);
            } catch (UnknownUserIdException | DuplicateEmailException e) {
                throw new StorageTransactionLogicException(e);
            }
            try {
                storage.updateUserPhoneNumber_Transaction(new AppIdentifier(null, null), con, userId, null);
            } catch (UnknownUserIdException | DuplicatePhoneNumberException e) {
                throw new StorageTransactionLogicException(e);
            }
            storage.commitTransaction(con);
            return null;
        });

        checkUser(storage, userId, email, null);
        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testDeleteDeviceCascades() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        PasswordlessSQLStorage storage = (PasswordlessSQLStorage) StorageLayer.getStorage(process.getProcess());

        String email = "test@example.com";
        PasswordlessCode code1 = getRandomCodeInfo();
        PasswordlessCode code2 = getRandomCodeInfo(code1.deviceIdHash);

        storage.createDeviceWithCode(new TenantIdentifier(null, null, null), email, null, "linkCodeSalt", code1);
        assertEquals(1, storage.getDevicesByEmail(new TenantIdentifier(null, null, null), email).length);

        storage.createCode(new TenantIdentifier(null, null, null), code2);

        storage.startTransaction(con -> {
            storage.deleteDevice_Transaction(new TenantIdentifier(null, null, null), con, code1.deviceIdHash);
            storage.commitTransaction(con);
            return null;
        });

        assertNull(storage.getDevice(new TenantIdentifier(null, null, null), code1.deviceIdHash));
        assertNull(storage.getCode(new TenantIdentifier(null, null, null), code1.id));
        assertNull(storage.getCode(new TenantIdentifier(null, null, null), code2.id));

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testDeleteDevicesByEmailCascades() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        PasswordlessSQLStorage storage = (PasswordlessSQLStorage) StorageLayer.getStorage(process.getProcess());

        String email = "test@example.com";
        String email2 = "test2@example.com";

        PasswordlessCode code1 = getRandomCodeInfo();
        PasswordlessCode code2 = getRandomCodeInfo();

        storage.createDeviceWithCode(new TenantIdentifier(null, null, null), email, null, "linkCodeSalt", code1);
        storage.createDeviceWithCode(new TenantIdentifier(null, null, null), email2, null, "linkCodeSalt", code2);

        storage.startTransaction(con -> {
            storage.deleteDevicesByEmail_Transaction(new TenantIdentifier(null, null, null), con, email);
            storage.commitTransaction(con);
            return null;
        });

        assertEquals(0, storage.getDevicesByEmail(new TenantIdentifier(null, null, null), email).length);
        assertNull(storage.getDevice(new TenantIdentifier(null, null, null), code1.deviceIdHash));
        assertNull(storage.getCode(new TenantIdentifier(null, null, null), code1.id));

        assertEquals(1, storage.getDevicesByEmail(new TenantIdentifier(null, null, null), email2).length);
        assertNotNull(storage.getDevice(new TenantIdentifier(null, null, null), code2.deviceIdHash));
        assertNotNull(storage.getCode(new TenantIdentifier(null, null, null), code2.id));

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testDeleteDevicesByPhoneNumberCascades() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        PasswordlessSQLStorage storage = (PasswordlessSQLStorage) StorageLayer.getStorage(process.getProcess());

        String phoneNumber = "+442071838750";
        String phoneNumber2 = "+442082949861";

        PasswordlessCode code1 = getRandomCodeInfo();
        PasswordlessCode code2 = getRandomCodeInfo();

        storage.createDeviceWithCode(new TenantIdentifier(null, null, null), null, phoneNumber, "linkCodeSalt", code1);
        storage.createDeviceWithCode(new TenantIdentifier(null, null, null), null, phoneNumber2, "linkCodeSalt", code2);

        storage.startTransaction(con -> {
            storage.deleteDevicesByPhoneNumber_Transaction(new TenantIdentifier(null, null, null), con, phoneNumber);
            storage.commitTransaction(con);
            return null;
        });

        assertEquals(0, storage.getDevicesByPhoneNumber(new TenantIdentifier(null, null, null), phoneNumber).length);
        assertNull(storage.getDevice(new TenantIdentifier(null, null, null), code1.deviceIdHash));
        assertNull(storage.getCode(new TenantIdentifier(null, null, null), code1.id));

        assertEquals(1, storage.getDevicesByPhoneNumber(new TenantIdentifier(null, null, null), phoneNumber2).length);
        assertNotNull(storage.getDevice(new TenantIdentifier(null, null, null), code2.deviceIdHash));
        assertNotNull(storage.getCode(new TenantIdentifier(null, null, null), code2.id));

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testLocking() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args, false);
        process.getProcess().setForceInMemoryDB();
        process.startProcess();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        PasswordlessSQLStorage storage = (PasswordlessSQLStorage) StorageLayer.getStorage(process.getProcess());

        String email = "test@example.com";
        String phoneNumber = "+442071838750";

        PasswordlessCode code1 = getRandomCodeInfo();
        PasswordlessCode code2 = getRandomCodeInfo();

        // These functions are called in a transaction and they all add a lock on code1
        TestFunction[] lockingFuncs = new TestFunction[]{(con) -> {
            storage.getDevice_Transaction(new TenantIdentifier(null, null, null), con, code1.deviceIdHash);
        }, (con) -> {
            storage.deleteDevicesByEmail_Transaction(new TenantIdentifier(null, null, null), con, email);
        }, (con) -> {
            storage.deleteDevicesByPhoneNumber_Transaction(new TenantIdentifier(null, null, null), con, phoneNumber);
        }, (con) -> {
            storage.deleteDevice_Transaction(new TenantIdentifier(null, null, null), con, code1.deviceIdHash);
        },};

        // We don't have createCode and createDeviceWithCode here, because in implementations with foreign key checking
        // they don't need to lock anything

        for (TestFunction func1 : lockingFuncs) {
            // We are intentionally testing: AB, BA and AA as well, since these are all different testcases
            for (TestFunction func2 : lockingFuncs) {
                // Setup
                storage.createDeviceWithCode(new TenantIdentifier(null, null, null), email, null, "linkCodeSalt",
                        code1);
                storage.createDeviceWithCode(new TenantIdentifier(null, null, null), null, phoneNumber, "linkCodeSalt",
                        code2);

                checkLockingCalls(storage, func1, func2);

                storage.startTransaction(con -> {
                    storage.deleteDevicesByEmail_Transaction(new TenantIdentifier(null, null, null), con, email);
                    storage.deleteDevicesByPhoneNumber_Transaction(new TenantIdentifier(null, null, null), con,
                            phoneNumber);
                    storage.commitTransaction(con);
                    return null;
                });
            }
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    private PasswordlessCode getRandomCodeInfo(String deviceIdHash) {
        String codeId = io.supertokens.utils.Utils.getUUID();

        SecureRandom gen = new SecureRandom();
        byte[] randomBytes = new byte[32];
        gen.nextBytes(randomBytes);
        String linkCodeHash = Base64.getUrlEncoder().encodeToString(randomBytes);

        return new PasswordlessCode(codeId, deviceIdHash, linkCodeHash, System.currentTimeMillis());

    }

    private PasswordlessCode getRandomCodeInfo() {
        SecureRandom gen = new SecureRandom();

        byte[] randomBytes = new byte[32];
        gen.nextBytes(randomBytes);
        String deviceIdHash = Base64.getUrlEncoder().encodeToString(randomBytes);

        return getRandomCodeInfo(deviceIdHash);
    }

    // This tests if func1 blocks the execution of func2 until the transaction of func1 ends.
    private void checkLockingCalls(PasswordlessSQLStorage storage, TestFunction func1, TestFunction func2)
            throws InterruptedException {
        AtomicReference<String> state = new AtomicReference<>("init");
        final Object syncObject = new Object();

        Runnable r1 = () -> {
            try {
                storage.startTransaction(con -> {
                    func1.mainLogic(con);

                    synchronized (syncObject) {
                        state.set("locked");
                        syncObject.notifyAll();
                    }

                    synchronized (syncObject) {
                        while (!state.get().equals("done")) {
                            try {
                                syncObject.wait();
                            } catch (InterruptedException e) {
                            }
                        }
                    }
                    return null;
                });
            } catch (Exception ignored) {
            }
        };

        Runnable r2 = () -> {
            try {
                storage.startTransaction(con -> {
                    synchronized (syncObject) {
                        while (!state.get().equals("locked")) {
                            try {
                                syncObject.wait();
                            } catch (InterruptedException e) {
                            }
                        }
                    }

                    synchronized (syncObject) {
                        state.set("before_read");
                    }

                    func2.mainLogic(con);

                    synchronized (syncObject) {
                        state.set("after_read");
                    }

                    return null;
                });
            } catch (Exception ignored) {
            }
        };

        Thread t1 = new Thread(r1);
        Thread t2 = new Thread(r2);

        t1.start();
        t2.start();

        t2.join(1000);
        assertEquals("before_read", state.get());

        synchronized (syncObject) {
            state.set("done");
            syncObject.notifyAll();
        }
        t2.join();
        t1.join();
    }

    private void checkUser(PasswordlessSQLStorage storage, String userId, String email, String phoneNumber)
            throws StorageQueryException {
        AuthRecipeUserInfo userById = storage.getPrimaryUserById(new AppIdentifier(null, null), userId);
        assertEquals(email, userById.loginMethods[0].email);
        assertEquals(phoneNumber, userById.loginMethods[0].phoneNumber);
        if (email != null) {
            AuthRecipeUserInfo[] user = storage.listPrimaryUsersByEmail(new TenantIdentifier(null, null, null), email);
            assert (user.length == 1 && user[0].equals(userById));
        }
        if (phoneNumber != null) {
            AuthRecipeUserInfo[] user = storage.listPrimaryUsersByPhoneNumber(new TenantIdentifier(null, null, null),
                    phoneNumber);
            assert (user[0].equals(userById));
        }
    }

    interface TestFunction {
        void mainLogic(TransactionConnection con) throws StorageQueryException, StorageTransactionLogicException;
    }
}