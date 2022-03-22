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

package io.supertokens.test.emailpassword;

import io.supertokens.ProcessState;
import io.supertokens.config.Config;
import io.supertokens.config.CoreConfig;
import io.supertokens.emailpassword.EmailPassword;
import io.supertokens.emailpassword.PasswordHashing;
import io.supertokens.emailpassword.exceptions.WrongCredentialsException;
import io.supertokens.inmemorydb.Start;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.pluginInterface.emailpassword.UserInfo;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.test.TestingProcessManager;
import io.supertokens.test.Utils;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.*;

public class PasswordHashingTest {
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
    public void hashAndVerifyWithBcrypt() throws Exception {
        String[] args = { "../" };

        Utils.setValueInConfig("password_hashing_alg", "BCRYPT");

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        EmailPassword.signUp(process.getProcess(), "test@example.com", "somePassword");

        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.PASSWORD_HASH_BCRYPT));

        EmailPassword.signIn(process.getProcess(), "test@example.com", "somePassword");

        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.PASSWORD_VERIFY_BCRYPT));

        try {
            EmailPassword.signIn(process.getProcess(), "test@example.com", "wrongPass");
            fail();
        } catch (WrongCredentialsException ignored) {
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void hashAndVerifyWithArgon() throws Exception {
        String[] args = { "../" };

        Utils.setValueInConfig("password_hashing_alg", "ARGON2");

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        EmailPassword.signUp(process.getProcess(), "test@example.com", "somePassword");

        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.PASSWORD_HASH_ARGON));

        EmailPassword.signIn(process.getProcess(), "test@example.com", "somePassword");

        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.PASSWORD_VERIFY_ARGON));

        try {
            EmailPassword.signIn(process.getProcess(), "test@example.com", "wrongPass");
            fail();
        } catch (WrongCredentialsException ignored) {
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void hashAndVerifyWithBcryptChangeToArgon() throws Exception {
        String[] args = { "../" };
        String hash = "";

        {
            Utils.setValueInConfig("password_hashing_alg", "BCRYPT");

            TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
            assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

            if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
                return;
            }

            hash = PasswordHashing.createHashWithSalt(process.getProcess(), "somePassword");

            assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.PASSWORD_HASH_BCRYPT));

            process.kill();
            assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));

        }
        {
            Utils.setValueInConfig("password_hashing_alg", "ARGON2");
            TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
            assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));
            assert (Config.getConfig(process.getProcess())
                    .getPasswordHashingAlg() == CoreConfig.PASSWORD_HASHING_ALG.ARGON2);

            assert (PasswordHashing.verifyPasswordWithHash(process.getProcess(), "somePassword", hash));

            assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.PASSWORD_VERIFY_BCRYPT));

            process.kill();
            assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
        }
    }

    @Test
    public void hashAndVerifyWithArgonChangeToBcrypt() throws Exception {
        String[] args = { "../" };
        String hash = "";

        {
            Utils.setValueInConfig("password_hashing_alg", "ARGON2");

            TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
            assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

            if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
                return;
            }

            hash = PasswordHashing.createHashWithSalt(process.getProcess(), "somePassword");

            assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.PASSWORD_HASH_ARGON));

            process.kill();
            assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));

        }
        {
            Utils.setValueInConfig("password_hashing_alg", "BCRYPT");
            TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
            assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));
            assert (Config.getConfig(process.getProcess())
                    .getPasswordHashingAlg() == CoreConfig.PASSWORD_HASHING_ALG.BCRYPT);

            PasswordHashing.verifyPasswordWithHash(process.getProcess(), "somePassword", hash);

            assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.PASSWORD_VERIFY_ARGON));

            process.kill();
            assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
        }
    }

    @Test
    public void defaultConfigIsArgon() throws Exception {
        String[] args = { "../" };

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        CoreConfig config = Config.getConfig(process.getProcess());

        assert (config.getPasswordHashingAlg() == CoreConfig.PASSWORD_HASHING_ALG.ARGON2);
        assert (config.getArgon2Iterations() == 3);
        assert (config.getArgon2MemoryBytes() == 65536);
        assert (config.getArgon2Parallelism() == 1);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void invalidConfig() throws Exception {
        {
            String[] args = { "../" };
            Utils.setValueInConfig("password_hashing_alg", "RANDOM");

            TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
            ProcessState.EventAndException e = process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.INIT_FAILURE);
            assertNotNull(e);
            assertEquals(e.exception.getMessage(), "'password_hashing_alg' must be one of 'ARGON2' or 'BCRYPT'");

            process.kill();
            assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
        }

        Utils.reset();

        {
            String[] args = { "../" };
            Utils.setValueInConfig("argon2_iterations", "-1");

            TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
            ProcessState.EventAndException e = process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.INIT_FAILURE);
            assertNotNull(e);
            assertEquals(e.exception.getMessage(), "'argon2_iterations' must be >= 1");

            process.kill();
            assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
        }

        Utils.reset();

        {
            String[] args = { "../" };
            Utils.setValueInConfig("argon2_parallelism", "-1");

            TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
            ProcessState.EventAndException e = process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.INIT_FAILURE);
            assertNotNull(e);
            assertEquals(e.exception.getMessage(), "'argon2_parallelism' must be >= 1");

            process.kill();
            assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
        }

        Utils.reset();

        {
            String[] args = { "../" };
            Utils.setValueInConfig("argon2_memory_bytes", "-1");

            TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
            ProcessState.EventAndException e = process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.INIT_FAILURE);
            assertNotNull(e);
            assertEquals(e.exception.getMessage(), "'argon2_memory_bytes' must be >= 1");

            process.kill();
            assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
        }
    }

    @Test
    public void hashAndVerufyArgon2HashWithDifferentConfigs() throws Exception {
        String[] args = { "../" };
        String hash = "";

        {
            TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
            assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

            if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
                return;
            }

            hash = PasswordHashing.createHashWithSalt(process.getProcess(), "somePassword");

            assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.PASSWORD_HASH_ARGON));

            process.kill();
            assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));

        }
        {
            Utils.setValueInConfig("argon2_memory_bytes", "100");
            Utils.setValueInConfig("argon2_parallelism", "2");
            Utils.setValueInConfig("argon2_iterations", "10");
            TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
            assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

            assert (PasswordHashing.verifyPasswordWithHash(process.getProcess(), "somePassword", hash));

            assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.PASSWORD_VERIFY_ARGON));

            String newHash = PasswordHashing.createHashWithSalt(process.getProcess(), "somePassword");
            assert (newHash.contains("m=100"));
            assert (newHash.contains("p=2"));
            assert (newHash.contains("t=10"));

            process.kill();
            assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
        }
    }

    @Test
    public void hashAndVerifyWithBcryptChangeToArgonPasswordWithResetFlow() throws Exception {
        String[] args = { "../" };

        Utils.setValueInConfig("password_hashing_alg", "BCRYPT");

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        UserInfo user = EmailPassword.signUp(process.getProcess(), "t@example.com", "somePass");

        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.PASSWORD_HASH_BCRYPT));
        ProcessState.getInstance(process.getProcess()).clear();

        Config.getConfig(process.getProcess()).setPasswordHashingAlg(CoreConfig.PASSWORD_HASHING_ALG.ARGON2);

        String token = EmailPassword.generatePasswordResetToken(process.getProcess(), user.id);
        EmailPassword.resetPassword(process.getProcess(), token, "somePass2");

        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.PASSWORD_HASH_ARGON));

        EmailPassword.signIn(process.getProcess(), "t@example.com", "somePass2");
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.PASSWORD_VERIFY_ARGON));

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void hashAndVerifyWithArgonChangeToBcryptPasswordWithResetFlow() throws Exception {
        String[] args = { "../" };

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        UserInfo user = EmailPassword.signUp(process.getProcess(), "t@example.com", "somePass");

        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.PASSWORD_HASH_ARGON));
        ProcessState.getInstance(process.getProcess()).clear();

        Config.getConfig(process.getProcess()).setPasswordHashingAlg(CoreConfig.PASSWORD_HASHING_ALG.BCRYPT);

        String token = EmailPassword.generatePasswordResetToken(process.getProcess(), user.id);
        EmailPassword.resetPassword(process.getProcess(), token, "somePass2");

        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.PASSWORD_HASH_BCRYPT));

        EmailPassword.signIn(process.getProcess(), "t@example.com", "somePass2");
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.PASSWORD_VERIFY_BCRYPT));

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void hashAndVerifyWithBcryptChangeToArgonChangePassword() throws Exception {
        String[] args = { "../" };

        Utils.setValueInConfig("password_hashing_alg", "BCRYPT");

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        UserInfo user = EmailPassword.signUp(process.getProcess(), "t@example.com", "somePass");

        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.PASSWORD_HASH_BCRYPT));
        ProcessState.getInstance(process.getProcess()).clear();

        Config.getConfig(process.getProcess()).setPasswordHashingAlg(CoreConfig.PASSWORD_HASHING_ALG.ARGON2);

        EmailPassword.updateUsersEmailOrPassword(process.getProcess(), user.id, null, "somePass2");

        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.PASSWORD_HASH_ARGON));

        EmailPassword.signIn(process.getProcess(), "t@example.com", "somePass2");
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.PASSWORD_VERIFY_ARGON));

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void hashAndVerifyWithArgonChangeToBcryptChangePassword() throws Exception {
        String[] args = { "../" };

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        UserInfo user = EmailPassword.signUp(process.getProcess(), "t@example.com", "somePass");

        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.PASSWORD_HASH_ARGON));
        ProcessState.getInstance(process.getProcess()).clear();

        Config.getConfig(process.getProcess()).setPasswordHashingAlg(CoreConfig.PASSWORD_HASHING_ALG.BCRYPT);

        EmailPassword.updateUsersEmailOrPassword(process.getProcess(), user.id, null, "somePass2");

        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.PASSWORD_HASH_BCRYPT));

        EmailPassword.signIn(process.getProcess(), "t@example.com", "somePass2");
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.PASSWORD_VERIFY_BCRYPT));

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void differentPasswordHashGenerated() throws Exception {
        String[] args = { "../" };

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        String hash = PasswordHashing.createHashWithSalt(process.getProcess(), "somePass");
        String hash2 = PasswordHashing.createHashWithSalt(process.getProcess(), "somePass");
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.PASSWORD_HASH_ARGON));

        assert (!hash.equals(hash2));

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void parallelSignUpSignIn() throws Exception {
        String[] args = { "../" };

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL
                || StorageLayer.getStorage(process.getProcess()) instanceof Start) {
            // if this is in mem, we do not want to run this test as sqlite locks the entire table and throws
            // error in the threads below.
            return;
        }

        AtomicBoolean success = new AtomicBoolean(true);

        ExecutorService ex = Executors.newFixedThreadPool(1000);
        for (int i = 0; i < 50; i++) {
            int y = i;
            ex.execute(() -> {
                try {
                    EmailPassword.signUp(process.getProcess(), "test@example.com" + y, "somePassword" + y);
                    EmailPassword.signIn(process.getProcess(), "test@example.com" + y, "somePassword" + y);
                } catch (Exception e) {
                    success.set(false);
                }
            });
        }

        ex.shutdown();

        ex.awaitTermination(2, TimeUnit.MINUTES);

        assert (success.get());

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }
}
