/*
 *    Copyright (c) 2022, VRAI Labs and/or its affiliates. All rights reserved.
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
import io.supertokens.config.CoreConfig.PASSWORD_HASHING_ALG;
import io.supertokens.emailpassword.EmailPassword;
import io.supertokens.emailpassword.ParsedFirebaseSCryptResponse;
import io.supertokens.emailpassword.exceptions.WrongCredentialsException;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.pluginInterface.authRecipe.AuthRecipeUserInfo;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.test.TestingProcessManager;
import io.supertokens.test.Utils;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import static org.junit.Assert.*;

public class UserMigrationTest {
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
    public void testSigningInUsersWithDifferentHashingConfigValues() throws Exception {
        String[] args = {"../"};

        Utils.setValueInConfig("firebase_password_hashing_signer_key",
                "gRhC3eDeQOdyEn4bMd9c6kxguWVmcIVq/SKa0JDPFeM6TcEevkaW56sIWfx88OHbJKnCXdWscZx0l2WbCJ1wbg==");

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        // user 1 has a password hash that was generated with 9 rounds
        {
            int firebaseMemCost = 14;
            int firebaseRounds = 9;
            String firebaseSaltSeparator = "Bw==";

            String email = "test@example.com";
            String password = "testPass123";
            String salt = "/cj0jC1br5o4+w==";
            String passwordHash = "9Y8ICWcqbzmI42DxV1jpyEjbrJPG8EQ6nI6oC32JYz+/dd7aEjI" +
                    "/R7jG9P5kYh8v9gyqFKaXMDzMg7eLCypbOA==";
            String combinedPasswordHash = "$" + ParsedFirebaseSCryptResponse.FIREBASE_SCRYPT_PREFIX + "$" + passwordHash
                    + "$" + salt + "$m=" + firebaseMemCost + "$r=" + firebaseRounds + "$s=" + firebaseSaltSeparator;
            EmailPassword.importUserWithPasswordHash(process.getProcess(), email, combinedPasswordHash,
                    PASSWORD_HASHING_ALG.FIREBASE_SCRYPT);

            // try signing in and check that it works
            AuthRecipeUserInfo userInfo = EmailPassword.signIn(process.getProcess(), email, password);
            assertEquals(userInfo.loginMethods[0].email, email);
            assertEquals(userInfo.loginMethods[0].passwordHash, combinedPasswordHash);
        }

        // user 2 has a password hash that was generated with mem cost as 15
        {
            int firebaseMemCost = 15;
            int firebaseRounds = 8;
            String firebaseSaltSeparator = "Bw==";

            String email = "test2@example.com";
            String password = "testPass123";
            String salt = "/cj0jC1br5o4+w==";
            String passwordHash = "LalFtzCxLIl14+ol6e/3cjHoa2B73ULiMN+Mjm" +
                    "+nJJEfQqtsXPpDX1VU4s9XyiuwGrQ5RN69PWL5DrHuNUH+RA==";
            String combinedPasswordHash = "$" + ParsedFirebaseSCryptResponse.FIREBASE_SCRYPT_PREFIX + "$" + passwordHash
                    + "$" + salt + "$m=" + firebaseMemCost + "$r=" + firebaseRounds + "$s=" + firebaseSaltSeparator;

            EmailPassword.importUserWithPasswordHash(process.getProcess(), email, combinedPasswordHash,
                    PASSWORD_HASHING_ALG.FIREBASE_SCRYPT);

            // try signing in and check that it works
            AuthRecipeUserInfo userInfo = EmailPassword.signIn(process.getProcess(), email, password);
            assertEquals(userInfo.loginMethods[0].email, email);
            assertEquals(userInfo.loginMethods[0].passwordHash, combinedPasswordHash);
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testBasicUserMigration() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        // with bcrypt
        {
            String email = "test@example.com";
            String plainTextPassword = "testPass123";
            String passwordHash = "$2a$10$GzEm3vKoAqnJCTWesRARCe/ovjt/07qjvcH9jbLUg44Fn77gMZkmm";

            // migrate user with passwordHash
            EmailPassword.ImportUserResponse importUserResponse = EmailPassword.importUserWithPasswordHash(process.main,
                    email, passwordHash);
            // check that the user was created
            assertFalse(importUserResponse.didUserAlreadyExist);
            // try and sign in with plainTextPassword
            AuthRecipeUserInfo userInfo = EmailPassword.signIn(process.main, email, plainTextPassword);

            assertEquals(userInfo.getSupertokensUserId(), importUserResponse.user.getSupertokensUserId());
            assertEquals(userInfo.loginMethods[0].passwordHash, passwordHash);
            assertEquals(userInfo.loginMethods[0].email, email);
        }

        // with argon2
        {
            String email = "test2@example.com";
            String plainTextPassword = "testPass123";
            String passwordHash = "$argon2id$v=19$m=16,t=2,p=1$VG1Oa1lMbzZLbzk5azQ2Qg$kjcNNtZ/b0t/8HgXUiQ76A";

            // migrate user with passwordHash
            EmailPassword.ImportUserResponse importUserResponse = EmailPassword.importUserWithPasswordHash(process.main,
                    email, passwordHash);
            // check that the user was created
            assertFalse(importUserResponse.didUserAlreadyExist);
            // try and sign in with plainTextPassword
            AuthRecipeUserInfo userInfo = EmailPassword.signIn(process.main, email, plainTextPassword);

            assertEquals(userInfo.getSupertokensUserId(), importUserResponse.user.getSupertokensUserId());
            assertEquals(userInfo.loginMethods[0].passwordHash, passwordHash);
            assertEquals(userInfo.loginMethods[0].email, email);
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testUpdatingAUsersPasswordHash() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        String email = "test@example.com";
        String originalPassword = "testPass123";

        AuthRecipeUserInfo signUpUserInfo = EmailPassword.signUp(process.main, email, originalPassword);

        // update passwordHash with new passwordHash
        String newPassword = "newTestPass123";
        String newPasswordHash = "$2a$10$uV17z2rVB3W5Rp4MeJeB4OdRX/Z7oFMLpUbdzyX9bDrk6kvZiOT1G";

        EmailPassword.ImportUserResponse response = EmailPassword.importUserWithPasswordHash(process.main, email,
                newPasswordHash);
        // check that the user already exists
        assertTrue(response.didUserAlreadyExist);

        // try signing in with the old password and check that it does not work
        Exception error = null;
        try {
            EmailPassword.signIn(process.main, email, originalPassword);
        } catch (WrongCredentialsException e) {
            error = e;
        }
        assertNotNull(error);

        // sign in with the newPassword and check that it works
        AuthRecipeUserInfo userInfo = EmailPassword.signIn(process.main, email, newPassword);
        assertEquals(userInfo.loginMethods[0].email, signUpUserInfo.loginMethods[0].email);
        assertEquals(userInfo.getSupertokensUserId(), signUpUserInfo.getSupertokensUserId());
        assertEquals(userInfo.timeJoined, signUpUserInfo.timeJoined);
        assertEquals(userInfo.loginMethods[0].passwordHash, newPasswordHash);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    // test bcrypt with different salt rounds
    @Test
    public void testAddingBcryptHashesWithDifferentSaltRounds() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        // adding a user with a bcrypt passwordHash with 5 rounds

        String email = "test@example.com";
        String password = "testPass123";
        String passwordHash = "$2a$05$vTNtOWhKVVLxCQDePmmsa.Loz9RuwwWajZtkchIVLIu4/.ncSTwfq";

        EmailPassword.ImportUserResponse response = EmailPassword.importUserWithPasswordHash(process.main, email,
                passwordHash);
        assertFalse(response.didUserAlreadyExist);

        // test that sign in works
        AuthRecipeUserInfo userInfo = EmailPassword.signIn(process.main, email, password);
        assertEquals(userInfo.loginMethods[0].email, email);
        assertEquals(userInfo.loginMethods[0].passwordHash, passwordHash);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testUsingArgon2HashesWithDifferentConfigValues() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        String email = "test@example.com";
        String password = "testing123";
        // using password hash generated with argon2id parallelism factor = 1, memory cost = 16 iterations = 2 hash
        // length =16
        String passwordHash = "$argon2id$v=19$m=16,t=2,p=1$alJQU1VpOG9VWXlqV0dlYw$Z/a978a9nPSlmwIFb5Mrjw";

        EmailPassword.ImportUserResponse response = EmailPassword.importUserWithPasswordHash(process.main, email,
                passwordHash);
        assertFalse(response.didUserAlreadyExist);

        // test that sign in works
        AuthRecipeUserInfo userInfo = EmailPassword.signIn(process.main, email, password);
        assertEquals(userInfo.loginMethods[0].email, email);
        assertEquals(userInfo.loginMethods[0].passwordHash, passwordHash);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testAddingArgon2WithDifferentVersions() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        // using argon2i
        {
            String email = "test@example.com";
            String password = "testing123";
            String passwordHash = "$argon2i$v=19$m=16,t=2,p=1$alJQU1VpOG9VWXlqV0dlYw$mThT4E5LULSyn/XhCZc9Hw";

            EmailPassword.ImportUserResponse response = EmailPassword.importUserWithPasswordHash(process.main, email,
                    passwordHash);
            assertFalse(response.didUserAlreadyExist);

            // test that sign in works
            AuthRecipeUserInfo userInfo = EmailPassword.signIn(process.main, email, password);
            assertEquals(userInfo.loginMethods[0].email, email);
            assertEquals(userInfo.loginMethods[0].passwordHash, passwordHash);
        }

        // $argon2d
        {
            String email = "test2@example.com";
            String password = "testing123";
            String passwordHash = "$argon2d$v=19$m=16,t=2,p=1$alJQU1VpOG9VWXlqV0dlYw$Ktlqf9xi1Toyx1XcbCwVUQ";

            EmailPassword.ImportUserResponse response = EmailPassword.importUserWithPasswordHash(process.main, email,
                    passwordHash);
            assertFalse(response.didUserAlreadyExist);

            // test that sign in works
            AuthRecipeUserInfo userInfo = EmailPassword.signIn(process.main, email, password);
            assertEquals(userInfo.loginMethods[0].email, email);
            assertEquals(userInfo.loginMethods[0].passwordHash, passwordHash);
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testAddingBcryptHashesWithDifferentVersions() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        // using $2a$

        {
            String email = "test@example.com";
            String password = "testPass123";
            String passwordHash = "$2a$05$vTNtOWhKVVLxCQDePmmsa.Loz9RuwwWajZtkchIVLIu4/.ncSTwfq";

            EmailPassword.ImportUserResponse response = EmailPassword.importUserWithPasswordHash(process.main, email,
                    passwordHash);
            assertFalse(response.didUserAlreadyExist);

            // test that sign in works
            AuthRecipeUserInfo userInfo = EmailPassword.signIn(process.main, email, password);
            assertEquals(userInfo.loginMethods[0].email, email);
            assertEquals(userInfo.loginMethods[0].passwordHash, passwordHash);
        }

        // using $2b$
        {
            String email = "test2@example.com";
            String password = "testPass123";
            String passwordHash = "$2b$10$Tix3Vpu93kiaZRLPPzD6QOIm62x0l5gRdvlyark5S.MLn/NY6t4gS";

            EmailPassword.ImportUserResponse response = EmailPassword.importUserWithPasswordHash(process.main, email,
                    passwordHash);
            assertFalse(response.didUserAlreadyExist);

            // test that sign in works
            AuthRecipeUserInfo userInfo = EmailPassword.signIn(process.main, email, password);
            assertEquals(userInfo.loginMethods[0].email, email);
            assertEquals(userInfo.loginMethods[0].passwordHash, passwordHash);
        }

        // using $2x$
        {
            String email = "test3@example.com";
            String password = "testPass123";
            String passwordHash = "$2x$05$vTNtOWhKVVLxCQDePmmsa.Loz9RuwwWajZtkchIVLIu4/.ncSTwfq";

            EmailPassword.ImportUserResponse response = EmailPassword.importUserWithPasswordHash(process.main, email,
                    passwordHash);
            assertFalse(response.didUserAlreadyExist);

            // test that sign in works
            AuthRecipeUserInfo userInfo = EmailPassword.signIn(process.main, email, password);
            assertEquals(userInfo.loginMethods[0].email, email);
            assertEquals(userInfo.loginMethods[0].passwordHash, passwordHash);
        }

        // using $2y$
        {
            String email = "test4@example.com";
            String password = "testPass123";
            String passwordHash = "$2y$10$lib5x4nbosKuK31FI8gG1OPVi/EuVHRVM7qmg1EiGADYYcIxTMJfa";

            EmailPassword.ImportUserResponse response = EmailPassword.importUserWithPasswordHash(process.main, email,
                    passwordHash);
            assertFalse(response.didUserAlreadyExist);

            // test that sign in works
            AuthRecipeUserInfo userInfo = EmailPassword.signIn(process.main, email, password);
            assertEquals(userInfo.loginMethods[0].email, email);
            assertEquals(userInfo.loginMethods[0].passwordHash, passwordHash);
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }
}
