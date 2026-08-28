/*
 *    Copyright (c) 2026, VRAI Labs and/or its affiliates. All rights reserved.
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

package io.supertokens.test;

import io.supertokens.ProcessState;
import io.supertokens.pluginInterface.Storage;
import io.supertokens.pluginInterface.authRecipe.AuthRecipeStorage;
import io.supertokens.pluginInterface.authRecipe.LoginMethod;
import io.supertokens.pluginInterface.emailpassword.exceptions.DuplicateEmailException;
import io.supertokens.pluginInterface.emailpassword.exceptions.DuplicateUserIdException;
import io.supertokens.pluginInterface.emailpassword.sqlStorage.EmailPasswordSQLStorage;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.exceptions.StorageTransactionLogicException;
import io.supertokens.pluginInterface.multitenancy.AppIdentifier;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifier;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.pluginInterface.passwordless.exception.DuplicatePhoneNumberException;
import io.supertokens.pluginInterface.passwordless.sqlStorage.PasswordlessSQLStorage;
import io.supertokens.pluginInterface.thirdparty.exception.DuplicateThirdPartyUserException;
import io.supertokens.pluginInterface.thirdparty.sqlStorage.ThirdPartySQLStorage;
import io.supertokens.storageLayer.StorageLayer;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Tests for the connection-taking sign-up / user-creation / tenant-removal variants on the in-memory storage.
 * These are the behaviour-preserving counterparts of the auto-commit methods (the auto-commit methods now delegate
 * to them), added so the lifecycle audit event can be committed or rolled back together with the mutation by the
 * caller. See supertokens-plugin-interface#216.
 */
public class TransactionalUserWriteInMemoryTest {
    @Rule
    public TestRule watchman = Utils.getOnFailure();

    @Rule
    public TestRule retryFlaky = Utils.retryFlakyTest();

    @AfterClass
    public static void afterTesting() {
        Utils.afterTesting();
    }

    @Before
    public void beforeEach() {
        Utils.reset();
    }

    private static final AppIdentifier APP = new AppIdentifier(null, null);
    private static final TenantIdentifier TENANT = new TenantIdentifier(null, null, null);

    private TestingProcessManager.TestingProcess startInMemoryProcess() throws InterruptedException {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.startIsolatedProcess(args, false);
        process.getProcess().setForceInMemoryDB();
        process.startProcess();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));
        return process;
    }

    @Test
    public void emailPasswordSignUpTransaction() throws Exception {
        TestingProcessManager.TestingProcess process = startInMemoryProcess();
        Storage storage = StorageLayer.getStorage(process.getProcess());
        EmailPasswordSQLStorage epStorage = (EmailPasswordSQLStorage) storage;
        AuthRecipeStorage authStorage = (AuthRecipeStorage) storage;
        long now = System.currentTimeMillis();

        // committed sign-up persists
        epStorage.startTransaction(con -> {
            try {
                epStorage.signUp_Transaction(TENANT, con, "ep-commit", "commit@example.com", "hash", now);
            } catch (DuplicateUserIdException | DuplicateEmailException e) {
                throw new StorageTransactionLogicException(e);
            }
            epStorage.commitTransaction(con);
            return null;
        });
        assertTrue(authStorage.doesUserIdExist(APP, "ep-commit"));

        // a rolled-back caller-owned transaction leaves no row
        try {
            epStorage.startTransaction(con -> {
                try {
                    epStorage.signUp_Transaction(TENANT, con, "ep-rollback", "rollback@example.com", "hash", now);
                } catch (DuplicateUserIdException | DuplicateEmailException e) {
                    throw new StorageTransactionLogicException(e);
                }
                // caller aborts before committing
                throw new StorageTransactionLogicException(new RuntimeException("force rollback"));
            });
            fail("expected the transaction to be rolled back");
        } catch (StorageTransactionLogicException expected) {
            // ignore
        }
        assertFalse(authStorage.doesUserIdExist(APP, "ep-rollback"));

        // duplicate email raises the same exception as the auto-commit variant
        try {
            epStorage.startTransaction(con -> {
                try {
                    epStorage.signUp_Transaction(TENANT, con, "ep-dup-email", "commit@example.com", "hash", now);
                } catch (DuplicateUserIdException | DuplicateEmailException e) {
                    throw new StorageTransactionLogicException(e);
                }
                epStorage.commitTransaction(con);
                return null;
            });
            fail("expected DuplicateEmailException");
        } catch (StorageTransactionLogicException e) {
            assertTrue(e.actualException instanceof DuplicateEmailException);
        }
        assertFalse(authStorage.doesUserIdExist(APP, "ep-dup-email"));

        // duplicate user id raises the same exception as the auto-commit variant
        try {
            epStorage.startTransaction(con -> {
                try {
                    epStorage.signUp_Transaction(TENANT, con, "ep-commit", "other@example.com", "hash", now);
                } catch (DuplicateUserIdException | DuplicateEmailException e) {
                    throw new StorageTransactionLogicException(e);
                }
                epStorage.commitTransaction(con);
                return null;
            });
            fail("expected DuplicateUserIdException");
        } catch (StorageTransactionLogicException e) {
            assertTrue(e.actualException instanceof DuplicateUserIdException);
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void thirdPartySignUpTransaction() throws Exception {
        TestingProcessManager.TestingProcess process = startInMemoryProcess();
        Storage storage = StorageLayer.getStorage(process.getProcess());
        ThirdPartySQLStorage tpStorage = (ThirdPartySQLStorage) storage;
        AuthRecipeStorage authStorage = (AuthRecipeStorage) storage;
        long now = System.currentTimeMillis();
        LoginMethod.ThirdParty google = new LoginMethod.ThirdParty("google", "g-1");

        // committed sign-up persists
        tpStorage.startTransaction(con -> {
            try {
                tpStorage.signUp_Transaction(TENANT, con, "tp-commit", "tp@example.com", google, now);
            } catch (io.supertokens.pluginInterface.thirdparty.exception.DuplicateUserIdException
                     | DuplicateThirdPartyUserException e) {
                throw new StorageTransactionLogicException(e);
            }
            tpStorage.commitTransaction(con);
            return null;
        });
        assertTrue(authStorage.doesUserIdExist(APP, "tp-commit"));

        // a rolled-back caller-owned transaction leaves no row
        try {
            tpStorage.startTransaction(con -> {
                try {
                    tpStorage.signUp_Transaction(TENANT, con, "tp-rollback", "tp2@example.com",
                            new LoginMethod.ThirdParty("google", "g-2"), now);
                } catch (io.supertokens.pluginInterface.thirdparty.exception.DuplicateUserIdException
                         | DuplicateThirdPartyUserException e) {
                    throw new StorageTransactionLogicException(e);
                }
                throw new StorageTransactionLogicException(new RuntimeException("force rollback"));
            });
            fail("expected the transaction to be rolled back");
        } catch (StorageTransactionLogicException expected) {
            // ignore
        }
        assertFalse(authStorage.doesUserIdExist(APP, "tp-rollback"));

        // duplicate third party id raises the same exception as the auto-commit variant
        try {
            tpStorage.startTransaction(con -> {
                try {
                    tpStorage.signUp_Transaction(TENANT, con, "tp-dup", "tp3@example.com", google, now);
                } catch (io.supertokens.pluginInterface.thirdparty.exception.DuplicateUserIdException
                         | DuplicateThirdPartyUserException e) {
                    throw new StorageTransactionLogicException(e);
                }
                tpStorage.commitTransaction(con);
                return null;
            });
            fail("expected DuplicateThirdPartyUserException");
        } catch (StorageTransactionLogicException e) {
            assertTrue(e.actualException instanceof DuplicateThirdPartyUserException);
        }
        assertFalse(authStorage.doesUserIdExist(APP, "tp-dup"));

        // duplicate user id raises the same exception as the auto-commit variant
        try {
            tpStorage.startTransaction(con -> {
                try {
                    tpStorage.signUp_Transaction(TENANT, con, "tp-commit", "tp4@example.com",
                            new LoginMethod.ThirdParty("facebook", "f-1"), now);
                } catch (io.supertokens.pluginInterface.thirdparty.exception.DuplicateUserIdException
                         | DuplicateThirdPartyUserException e) {
                    throw new StorageTransactionLogicException(e);
                }
                tpStorage.commitTransaction(con);
                return null;
            });
            fail("expected DuplicateUserIdException");
        } catch (StorageTransactionLogicException e) {
            assertTrue(e.actualException
                    instanceof io.supertokens.pluginInterface.thirdparty.exception.DuplicateUserIdException);
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void passwordlessCreateUserTransaction() throws Exception {
        TestingProcessManager.TestingProcess process = startInMemoryProcess();
        Storage storage = StorageLayer.getStorage(process.getProcess());
        PasswordlessSQLStorage plStorage = (PasswordlessSQLStorage) storage;
        AuthRecipeStorage authStorage = (AuthRecipeStorage) storage;
        long now = System.currentTimeMillis();

        // committed creation persists
        plStorage.startTransaction(con -> {
            try {
                plStorage.createUser_Transaction(TENANT, con, "pl-commit", "pl@example.com", "+15551110000", now);
            } catch (DuplicateEmailException | DuplicatePhoneNumberException | DuplicateUserIdException e) {
                throw new StorageTransactionLogicException(e);
            }
            plStorage.commitTransaction(con);
            return null;
        });
        assertTrue(authStorage.doesUserIdExist(APP, "pl-commit"));

        // a rolled-back caller-owned transaction leaves no row
        try {
            plStorage.startTransaction(con -> {
                try {
                    plStorage.createUser_Transaction(TENANT, con, "pl-rollback", null, "+15551110001", now);
                } catch (DuplicateEmailException | DuplicatePhoneNumberException | DuplicateUserIdException e) {
                    throw new StorageTransactionLogicException(e);
                }
                throw new StorageTransactionLogicException(new RuntimeException("force rollback"));
            });
            fail("expected the transaction to be rolled back");
        } catch (StorageTransactionLogicException expected) {
            // ignore
        }
        assertFalse(authStorage.doesUserIdExist(APP, "pl-rollback"));

        // duplicate phone number raises the same exception as the auto-commit variant
        try {
            plStorage.startTransaction(con -> {
                try {
                    plStorage.createUser_Transaction(TENANT, con, "pl-dup-phone", null, "+15551110000", now);
                } catch (DuplicateEmailException | DuplicatePhoneNumberException | DuplicateUserIdException e) {
                    throw new StorageTransactionLogicException(e);
                }
                plStorage.commitTransaction(con);
                return null;
            });
            fail("expected DuplicatePhoneNumberException");
        } catch (StorageTransactionLogicException e) {
            assertTrue(e.actualException instanceof DuplicatePhoneNumberException);
        }
        assertFalse(authStorage.doesUserIdExist(APP, "pl-dup-phone"));

        // duplicate email raises the same exception as the auto-commit variant
        try {
            plStorage.startTransaction(con -> {
                try {
                    plStorage.createUser_Transaction(TENANT, con, "pl-dup-email", "pl@example.com", null, now);
                } catch (DuplicateEmailException | DuplicatePhoneNumberException | DuplicateUserIdException e) {
                    throw new StorageTransactionLogicException(e);
                }
                plStorage.commitTransaction(con);
                return null;
            });
            fail("expected DuplicateEmailException");
        } catch (StorageTransactionLogicException e) {
            assertTrue(e.actualException instanceof DuplicateEmailException);
        }

        // duplicate user id raises the same exception as the auto-commit variant
        try {
            plStorage.startTransaction(con -> {
                try {
                    plStorage.createUser_Transaction(TENANT, con, "pl-commit", "other-pl@example.com", null, now);
                } catch (DuplicateEmailException | DuplicatePhoneNumberException | DuplicateUserIdException e) {
                    throw new StorageTransactionLogicException(e);
                }
                plStorage.commitTransaction(con);
                return null;
            });
            fail("expected DuplicateUserIdException");
        } catch (StorageTransactionLogicException e) {
            assertTrue(e.actualException instanceof DuplicateUserIdException);
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void removeUserIdFromTenantTransaction() throws Exception {
        TestingProcessManager.TestingProcess process = startInMemoryProcess();
        Storage storage = StorageLayer.getStorage(process.getProcess());
        EmailPasswordSQLStorage epStorage = (EmailPasswordSQLStorage) storage;
        io.supertokens.pluginInterface.multitenancy.sqlStorage.MultitenancySQLStorage mtStorage =
                (io.supertokens.pluginInterface.multitenancy.sqlStorage.MultitenancySQLStorage) storage;
        AuthRecipeStorage authStorage = (AuthRecipeStorage) storage;
        long now = System.currentTimeMillis();

        // seed a user (auto-commit) associated with the public tenant
        epStorage.signUp(TENANT, "rm-user", "rm@example.com", "hash", now);
        assertTrue(authStorage.doesUserIdExist(TENANT, "rm-user"));

        // a rolled-back caller-owned removal leaves the association in place
        try {
            mtStorage.startTransaction(con -> {
                boolean removed = mtStorage.removeUserIdFromTenant_Transaction(TENANT, con, "rm-user");
                assertTrue(removed);
                throw new StorageTransactionLogicException(new RuntimeException("force rollback"));
            });
            fail("expected the transaction to be rolled back");
        } catch (StorageTransactionLogicException expected) {
            // ignore
        }
        assertTrue(authStorage.doesUserIdExist(TENANT, "rm-user"));

        // removing an unknown user returns false without writing
        Boolean unknownResult = mtStorage.startTransaction(con -> {
            boolean removed = mtStorage.removeUserIdFromTenant_Transaction(TENANT, con, "does-not-exist");
            mtStorage.commitTransaction(con);
            return removed;
        });
        assertFalse(unknownResult);

        // a committed removal disassociates the user from the tenant
        Boolean removedResult = mtStorage.startTransaction(con -> {
            boolean removed = mtStorage.removeUserIdFromTenant_Transaction(TENANT, con, "rm-user");
            mtStorage.commitTransaction(con);
            return removed;
        });
        assertTrue(removedResult);
        assertFalse(authStorage.doesUserIdExist(TENANT, "rm-user"));

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }
}
