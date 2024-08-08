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

package io.supertokens.test.emailpassword.api;

import com.google.gson.JsonObject;
import io.supertokens.ProcessState;
import io.supertokens.config.CoreConfig.PASSWORD_HASHING_ALG;
import io.supertokens.emailpassword.EmailPassword;
import io.supertokens.emailpassword.ParsedFirebaseSCryptResponse;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.pluginInterface.authRecipe.AuthRecipeUserInfo;
import io.supertokens.pluginInterface.emailpassword.sqlStorage.EmailPasswordSQLStorage;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifier;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.test.TestingProcessManager;
import io.supertokens.test.Utils;
import io.supertokens.test.httpRequest.HttpRequestForTesting;
import io.supertokens.utils.SemVer;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import static org.junit.Assert.*;

public class ImportUserWithPasswordHashAPITest {
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
    public void badInputTest() throws Exception {
        String[] args = {"../"};

        Utils.setValueInConfig("firebase_password_hashing_signer_key",
                "gRhC3eDeQOdyEn4bMd9c6kxguWVmcIVq/SKa0JDPFeM6TcEevkaW56sIWfx88OHbJKnCXdWscZx0l2WbCJ1wbg==");

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        // do not pass input
        try {
            HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/user/passwordhash/import", null, 1000, 1000, null,
                    SemVer.v2_16.get(), "emailpassword");
            throw new Exception("Should not come here");
        } catch (io.supertokens.test.httpRequest.HttpResponseException e) {
            assertTrue(e.statusCode == 400
                    && e.getMessage().equals("Http error. Status Code: 400. Message: Invalid Json Input"));
        }

        // pass empty json body
        try {

            JsonObject requestBody = new JsonObject();
            HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/user/passwordhash/import", requestBody, 1000, 1000, null,
                    SemVer.v2_16.get(), "emailpassword");
            throw new Exception("Should not come here");
        } catch (io.supertokens.test.httpRequest.HttpResponseException e) {
            assertTrue(e.statusCode == 400 && e.getMessage()
                    .equals("Http error. Status Code: 400. Message: Field name 'email' is invalid in JSON input"));
        }

        // pass empty json body
        try {
            JsonObject requestBody = new JsonObject();
            HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/user/passwordhash/import", requestBody, 1000, 1000, null,
                    SemVer.v2_16.get(), "emailpassword");
            throw new Exception("Should not come here");
        } catch (io.supertokens.test.httpRequest.HttpResponseException e) {
            assertTrue(e.statusCode == 400 && e.getMessage()
                    .equals("Http error. Status Code: 400. Message: Field name 'email' is invalid in JSON input"));
        }

        // missing email in request body
        try {
            JsonObject requestBody = new JsonObject();
            requestBody.addProperty("passwordHash", "somePasswordHash");
            HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/user/passwordhash/import", requestBody, 1000, 1000, null,
                    SemVer.v2_16.get(), "emailpassword");
            throw new Exception("Should not come here");
        } catch (io.supertokens.test.httpRequest.HttpResponseException e) {
            assertTrue(e.statusCode == 400 && e.getMessage()
                    .equals("Http error. Status Code: 400. Message: Field name 'email' is invalid in JSON input"));
        }

        // missing passwordHash
        try {
            JsonObject requestBody = new JsonObject();
            requestBody.addProperty("email", "test@example.com");
            HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/user/passwordhash/import", requestBody, 1000, 1000, null,
                    SemVer.v2_16.get(), "emailpassword");
            throw new Exception("Should not come here");
        } catch (io.supertokens.test.httpRequest.HttpResponseException e) {
            assertTrue(e.statusCode == 400 && e.getMessage().equals(
                    "Http error. Status Code: 400. Message: Field name 'passwordHash' is invalid in JSON input"));
        }

        // passing an empty passwordHash
        try {
            JsonObject requestBody = new JsonObject();
            requestBody.addProperty("email", "test@example.com");
            requestBody.addProperty("passwordHash", "");
            HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/user/passwordhash/import", requestBody, 1000, 1000, null,
                    SemVer.v2_16.get(), "emailpassword");
            throw new Exception("Should not come here");
        } catch (io.supertokens.test.httpRequest.HttpResponseException e) {
            assertTrue(e.statusCode == 400 && e.getMessage()
                    .equals("Http error. Status Code: 400. Message: Password hash cannot be an empty string"));
        }

        // passing a random string as passwordHash/ invalid format
        try {
            JsonObject requestBody = new JsonObject();
            requestBody.addProperty("email", "test@example.com");
            requestBody.addProperty("passwordHash", "invalidPasswordHash");
            HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/user/passwordhash/import", requestBody, 1000, 1000, null,
                    SemVer.v2_16.get(), "emailpassword");
            throw new Exception("Should not come here");
        } catch (io.supertokens.test.httpRequest.HttpResponseException e) {
            assertTrue(e.statusCode == 400 && e.getMessage()
                    .equals("Http error. Status Code: 400. Message: Password hash is in invalid format"));
        }

        // passing a random string as hashingAlgorithm
        try {
            JsonObject requestBody = new JsonObject();
            requestBody.addProperty("email", "test@example.com");
            requestBody.addProperty("passwordHash", "somePasswordHash");
            requestBody.addProperty("hashingAlgorithm", "random");
            HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/user/passwordhash/import", requestBody, 1000, 1000, null,
                    SemVer.v2_16.get(), "emailpassword");
            throw new Exception("Should not come here");
        } catch (io.supertokens.test.httpRequest.HttpResponseException e) {
            assertTrue(e.statusCode == 400 && e.getMessage()
                    .equals("Http error. Status Code: 400. Message: Unsupported password hashing algorithm"));
        }

        // passing hashingAlgorithm as bcrypt with random password hash
        try {
            JsonObject requestBody = new JsonObject();
            requestBody.addProperty("email", "test@example.com");
            requestBody.addProperty("passwordHash", "invalidHash");
            requestBody.addProperty("hashingAlgorithm", "bcrypt");
            HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/user/passwordhash/import", requestBody, 1000, 1000, null,
                    SemVer.v2_16.get(), "emailpassword");
            throw new Exception("Should not come here");
        } catch (io.supertokens.test.httpRequest.HttpResponseException e) {
            assertTrue(e.statusCode == 400 && e.getMessage()
                    .equals("Http error. Status Code: 400. Message: Password hash is in invalid BCrypt format"));
        }

        // passing hashingAlgorithm as argon2 with random password hash
        try {
            JsonObject requestBody = new JsonObject();
            requestBody.addProperty("email", "test@example.com");
            requestBody.addProperty("passwordHash", "invalidHash");
            requestBody.addProperty("hashingAlgorithm", "argon2");
            HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/user/passwordhash/import", requestBody, 1000, 1000, null,
                    SemVer.v2_16.get(), "emailpassword");
            throw new Exception("Should not come here");
        } catch (io.supertokens.test.httpRequest.HttpResponseException e) {
            assertTrue(e.statusCode == 400 && e.getMessage()
                    .equals("Http error. Status Code: 400. Message: Password hash is in invalid Argon2 format"));
        }

        // passing hashingAlgorithm as firebase_scrypt with random password hash
        try {
            JsonObject requestBody = new JsonObject();
            requestBody.addProperty("email", "test@example.com");
            requestBody.addProperty("passwordHash", "invalidHash");
            requestBody.addProperty("hashingAlgorithm", "firebase_scrypt");
            HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/user/passwordhash/import", requestBody, 1000, 1000, null,
                    SemVer.v2_16.get(), "emailpassword");
            throw new Exception("Should not come here");
        } catch (io.supertokens.test.httpRequest.HttpResponseException e) {
            assertTrue(e.statusCode == 400 && e.getMessage().equals(
                    "Http error. Status Code: 400. Message: Password hash is in invalid Firebase SCrypt format"));
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testSigningInWithFireBasePasswordWithInvalidSignerKey() throws Exception {

        String[] args = {"../"};

        Utils.setValueInConfig("firebase_password_hashing_signer_key", "invalidSignerkey");

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        int firebaseMemCost = 14;
        int firebaseRounds = 8;
        String firebaseSaltSeparator = "Bw==";

        String email = "test@example.com";
        String password = "testPass123";
        String salt = "/cj0jC1br5o4+w==";
        String passwordHash = "qZM035es5AXYqavsKD6/rhtxg7t5PhcyRgv5blc3doYbChX8keMfQLq1ra96O2Pf2TP/eZrR5xtPCYN6mX3ESA" +
                "==";
        String combinedPasswordHash = "$" + ParsedFirebaseSCryptResponse.FIREBASE_SCRYPT_PREFIX + "$" + passwordHash
                + "$" + salt + "$m=" + firebaseMemCost + "$r=" + firebaseRounds + "$s=" + firebaseSaltSeparator;

        EmailPassword.importUserWithPasswordHash(process.getProcess(), email, combinedPasswordHash,
                PASSWORD_HASHING_ALG.FIREBASE_SCRYPT);

        JsonObject signInRequestBody = new JsonObject();
        signInRequestBody.addProperty("email", email);
        signInRequestBody.addProperty("password", password);

        JsonObject signInResponse = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/signin", signInRequestBody, 1000, 1000, null,
                SemVer.v2_16.get(), "emailpassword");
        assertEquals(signInResponse.get("status").getAsString(), "WRONG_CREDENTIALS_ERROR");

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testImportingAUsesrFromFirebaseWithoutSettingTheSignerKey() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        int firebaseMemCost = 14;
        int firebaseRounds = 8;
        String firebaseSaltSeparator = "Bw==";

        String email = "test@example.com";
        String salt = "/cj0jC1br5o4+w==";
        String passwordHash = "qZM035es5AXYqavsKD6/rhtxg7t5PhcyRgv5blc3doYbChX8keMfQLq1ra96O2Pf2TP/eZrR5xtPCYN6mX3ESA" +
                "==";
        String combinedPasswordHash = "$" + ParsedFirebaseSCryptResponse.FIREBASE_SCRYPT_PREFIX + "$" + passwordHash
                + "$" + salt + "$m=" + firebaseMemCost + "$r=" + firebaseRounds + "$s=" + firebaseSaltSeparator;

        // import user without passwordHash wihout setting the firebase scrypt password hashing signer key
        JsonObject importUserRequestBody = new JsonObject();
        importUserRequestBody.addProperty("email", email);
        importUserRequestBody.addProperty("passwordHash", combinedPasswordHash);
        importUserRequestBody.addProperty("hashingAlgorithm", "firebase_scrypt");

        try {
            HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/user/passwordhash/import", importUserRequestBody, 1000, 1000, null,
                    SemVer.v2_16.get(), "emailpassword");
            throw new Exception("Should not come here");
        } catch (io.supertokens.test.httpRequest.HttpResponseException e) {
            assertTrue(e.statusCode == 500
                    && e.getMessage().equals("Http error. Status Code: 500. Message: 'firebase_password_hashing_signer_key' cannot be null"));
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testSigningInAUserWhenStoredPasswordHashIsIncorrect() throws Exception {
        String[] args = {"../"};

        Utils.setValueInConfig("firebase_password_hashing_signer_key",
                "gRhC3eDeQOdyEn4bMd9c6kxguWVmcIVq/SKa0JDPFeM6TcEevkaW56sIWfx88OHbJKnCXdWscZx0l2WbCJ1wbg==");

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        int firebaseMemCost = 14;
        int firebaseRounds = 8;
        String firebaseSaltSeparator = "Bw==";

        String email = "test@example.com";
        String password = "testPass123";
        String salt = "/cj0jC1br5o4+w==";
        String passwordHash = "incorrectHash";
        String combinedPasswordHash = "$" + ParsedFirebaseSCryptResponse.FIREBASE_SCRYPT_PREFIX + "$" + passwordHash
                + "$" + salt + "$m=" + firebaseMemCost + "$r=" + firebaseRounds + "$s=" + firebaseSaltSeparator;

        long timeJoined = System.currentTimeMillis();

        EmailPasswordSQLStorage storage = (EmailPasswordSQLStorage) StorageLayer.getStorage(process.getProcess());

        storage.signUp(new TenantIdentifier(null, null, null), "userId", email, combinedPasswordHash, timeJoined);

        JsonObject signInRequestBody = new JsonObject();
        signInRequestBody.addProperty("email", email);
        signInRequestBody.addProperty("password", password);

        JsonObject response = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/signin", signInRequestBody, 1000, 1000, null,
                SemVer.v2_16.get(), "emailpassword");
        assertEquals("WRONG_CREDENTIALS_ERROR", response.get("status").getAsString());

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testSigningInAUserWithFirebasePasswordHashWithoutSettingTheSignerKey() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        int firebaseMemCost = 14;
        int firebaseRounds = 8;
        String firebaseSaltSeparator = "Bw==";

        String email = "test@example.com";
        String password = "testPass123";
        String salt = "/cj0jC1br5o4+w==";
        String passwordHash = "qZM035es5AXYqavsKD6/rhtxg7t5PhcyRgv5blc3doYbChX8keMfQLq1ra96O2Pf2TP/eZrR5xtPCYN6mX3ESA" +
                "==";
        String combinedPasswordHash = "$" + ParsedFirebaseSCryptResponse.FIREBASE_SCRYPT_PREFIX + "$" + passwordHash
                + "$" + salt + "$m=" + firebaseMemCost + "$r=" + firebaseRounds + "$s=" + firebaseSaltSeparator;

        long timeJoined = System.currentTimeMillis();

        EmailPasswordSQLStorage storage = (EmailPasswordSQLStorage) StorageLayer.getStorage(process.getProcess());

        storage.signUp(new TenantIdentifier(null, null, null), "userId", email, combinedPasswordHash, timeJoined);

        // sign in should result in 500 error since the firebase signer key is not set
        JsonObject signInRequestBody = new JsonObject();
        signInRequestBody.addProperty("email", email);
        signInRequestBody.addProperty("password", password);

        try {
            HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "", "http://localhost:3567/recipe/signin",
                    signInRequestBody, 1000, 1000, null, SemVer.v2_16.get(), "emailpassword");
            throw new Exception("Should not come here");
        } catch (io.supertokens.test.httpRequest.HttpResponseException e) {
            assertTrue(e.statusCode == 500
                    && e.getMessage().equals("Http error. Status Code: 500. Message: 'firebase_password_hashing_signer_key' cannot be null"));
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testImportingAUserFromFireBaseWithFirebaseSCryptPasswordHash() throws Exception {
        String[] args = {"../"};

        Utils.setValueInConfig("firebase_password_hashing_signer_key",
                "gRhC3eDeQOdyEn4bMd9c6kxguWVmcIVq/SKa0JDPFeM6TcEevkaW56sIWfx88OHbJKnCXdWscZx0l2WbCJ1wbg==");

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        int firebaseMemCost = 14;
        int firebaseRounds = 8;
        String firebaseSaltSeparator = "Bw==";

        String email = "test@example.com";
        String password = "testPass123";
        String salt = "/cj0jC1br5o4+w==";
        String passwordHash = "qZM035es5AXYqavsKD6/rhtxg7t5PhcyRgv5blc3doYbChX8keMfQLq1ra96O2Pf2TP/eZrR5xtPCYN6mX3ESA" +
                "==";
        String combinedPasswordHash = "$" + ParsedFirebaseSCryptResponse.FIREBASE_SCRYPT_PREFIX + "$" + passwordHash
                + "$" + salt + "$m=" + firebaseMemCost + "$r=" + firebaseRounds + "$s=" + firebaseSaltSeparator;

        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("email", email);
        requestBody.addProperty("passwordHash", combinedPasswordHash);
        requestBody.addProperty("hashingAlgorithm", "firebase_scrypt");

        JsonObject response = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/user/passwordhash/import", requestBody, 1000, 1000, null,
                SemVer.v2_16.get(), "emailpassword");

        assertEquals("OK", response.get("status").getAsString());
        assertFalse(response.get("didUserAlreadyExist").getAsBoolean());

        // try signing in with incorrect password
        {
            JsonObject signInRequestBody = new JsonObject();
            signInRequestBody.addProperty("email", email);
            signInRequestBody.addProperty("password", "incorrectpassword");

            JsonObject signInResponse = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/signin", signInRequestBody, 1000, 1000, null,
                    SemVer.v2_16.get(), "emailpassword");
            assertEquals("WRONG_CREDENTIALS_ERROR", signInResponse.get("status").getAsString());
        }

        {
            // try signing in with the new user
            JsonObject signInRequestBody = new JsonObject();
            signInRequestBody.addProperty("email", email);
            signInRequestBody.addProperty("password", password);

            JsonObject signInResponse = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/signin", signInRequestBody, 1000, 1000, null,
                    SemVer.v2_16.get(), "emailpassword");
            assertEquals("OK", signInResponse.get("status").getAsString());
            assertEquals(signInResponse.get("user").getAsJsonObject(), response.get("user").getAsJsonObject());
        }
        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    // migrate a user with email and password hash sign in and check that the user is created and the password works
    @Test
    public void testGoodInput() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        String email = "test@example.com";
        String plainTextPassword = "testPass123";
        String passwordHash = "$2a$10$S6bOFset3wCUcgNGSBgFxOHBIopaiPEK53YFNalvmiPcOodCK2Ehq";

        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("email", email);
        requestBody.addProperty("passwordHash", passwordHash);

        JsonObject response = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/user/passwordhash/import", requestBody, 1000, 1000, null,
                SemVer.v2_16.get(), "emailpassword");

        assertEquals("OK", response.get("status").getAsString());
        assertFalse(response.get("didUserAlreadyExist").getAsBoolean());

        // try signing in with the new user
        JsonObject signInRequestBody = new JsonObject();
        signInRequestBody.addProperty("email", email);
        signInRequestBody.addProperty("password", plainTextPassword);

        JsonObject signInResponse = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/signin", signInRequestBody, 1000, 1000, null,
                SemVer.v2_16.get(), "emailpassword");
        assertEquals("OK", signInResponse.get("status").getAsString());
        assertEquals(signInResponse.get("user").getAsJsonObject(), response.get("user").getAsJsonObject());

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

        // sign up a user
        String email = "test@example.com";
        String password = "testPass123";

        AuthRecipeUserInfo initialUserInfo = EmailPassword.signUp(process.main, email, password);

        // update a user's passwordHash

        String newPassword = "newTestPass123";
        String passwordHash = "$2a$10$X2oX3mWh8JfgPTKtkKmc9OQwUVQAulwkGgIjcsK8h3rojHVQTebV6";

        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("email", email);
        requestBody.addProperty("passwordHash", passwordHash);

        JsonObject response = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/user/passwordhash/import", requestBody, 1000, 1000, null,
                SemVer.v2_16.get(), "emailpassword");
        assertEquals("OK", response.get("status").getAsString());
        assertTrue(response.get("didUserAlreadyExist").getAsBoolean());

        // check that a new user was not created by comparing userIds
        assertEquals(initialUserInfo.getSupertokensUserId(),
                response.get("user").getAsJsonObject().get("id").getAsString());

        // sign in with the new password to check if the password hash got updated
        AuthRecipeUserInfo updatedUserInfo = EmailPassword.signIn(process.main, email, newPassword);
        assertEquals(updatedUserInfo.loginMethods[0].passwordHash, passwordHash);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testImportingUsersWithHashingAlgorithmFieldWithMixedLowerAndUpperCase() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        {
            String email = "test@example.com";
            String password = "newTestPass123";
            String passwordHash = "$2a$10$X2oX3mWh8JfgPTKtkKmc9OQwUVQAulwkGgIjcsK8h3rojHVQTebV6";
            String hashingAlgorithm = "BcRyPt";

            JsonObject requestBody = new JsonObject();
            requestBody.addProperty("email", email);
            requestBody.addProperty("passwordHash", passwordHash);
            requestBody.addProperty("hashingAlgorithm", hashingAlgorithm);

            JsonObject response = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/user/passwordhash/import", requestBody, 1000, 1000, null,
                    SemVer.v2_16.get(), "emailpassword");
            assertEquals("OK", response.get("status").getAsString());
            assertFalse(response.get("didUserAlreadyExist").getAsBoolean());

            // check that the user is created by signing in
            AuthRecipeUserInfo userInfo = EmailPassword.signIn(process.main, email, password);
            assertEquals(email, userInfo.loginMethods[0].email);
            assertEquals(userInfo.loginMethods[0].passwordHash, passwordHash);

        }

        {
            String email = "test2@example.com";
            String password = "newTestPass123";
            String passwordHash = "$argon2id$v=19$m=16,t=2,p=1$V2hkNUdjQnAxTXZScGRjOQ$UxJn6d+dRB0hOe7FX/Qv+Q";
            String hashingAlgorithm = "ArGoN2";

            JsonObject requestBody = new JsonObject();
            requestBody.addProperty("email", email);
            requestBody.addProperty("passwordHash", passwordHash);
            requestBody.addProperty("hashingAlgorithm", hashingAlgorithm);

            JsonObject response = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/user/passwordhash/import", requestBody, 1000, 1000, null,
                    SemVer.v2_16.get(), "emailpassword");
            assertEquals("OK", response.get("status").getAsString());
            assertFalse(response.get("didUserAlreadyExist").getAsBoolean());

            // check that the user is created by signing in
            AuthRecipeUserInfo userInfo = EmailPassword.signIn(process.main, email, password);
            assertEquals(email, userInfo.loginMethods[0].email);
            assertEquals(userInfo.loginMethods[0].passwordHash, passwordHash);
        }
        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testImportingUsersWithHashingAlgorithmField() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        {
            String email = "test@example.com";
            String password = "newTestPass123";
            String passwordHash = "$2a$10$X2oX3mWh8JfgPTKtkKmc9OQwUVQAulwkGgIjcsK8h3rojHVQTebV6";
            String hashingAlgorithm = "bcrypt";

            JsonObject requestBody = new JsonObject();
            requestBody.addProperty("email", email);
            requestBody.addProperty("passwordHash", passwordHash);
            requestBody.addProperty("hashingAlgorithm", hashingAlgorithm);

            JsonObject response = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/user/passwordhash/import", requestBody, 1000, 1000, null,
                    SemVer.v2_16.get(), "emailpassword");
            assertEquals("OK", response.get("status").getAsString());
            assertFalse(response.get("didUserAlreadyExist").getAsBoolean());

            // check that the user is created by signing in
            AuthRecipeUserInfo userInfo = EmailPassword.signIn(process.main, email, password);
            assertEquals(email, userInfo.loginMethods[0].email);
            assertEquals(userInfo.loginMethods[0].passwordHash, passwordHash);

        }

        {
            String email = "test2@example.com";
            String password = "newTestPass123";
            String passwordHash = "$argon2id$v=19$m=16,t=2,p=1$V2hkNUdjQnAxTXZScGRjOQ$UxJn6d+dRB0hOe7FX/Qv+Q";
            String hashingAlgorithm = "argon2";

            JsonObject requestBody = new JsonObject();
            requestBody.addProperty("email", email);
            requestBody.addProperty("passwordHash", passwordHash);
            requestBody.addProperty("hashingAlgorithm", hashingAlgorithm);

            JsonObject response = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/user/passwordhash/import", requestBody, 1000, 1000, null,
                    SemVer.v2_16.get(), "emailpassword");
            assertEquals("OK", response.get("status").getAsString());
            assertFalse(response.get("didUserAlreadyExist").getAsBoolean());

            // check that the user is created by signing in
            AuthRecipeUserInfo userInfo = EmailPassword.signIn(process.main, email, password);
            assertEquals(email, userInfo.loginMethods[0].email);
            assertEquals(userInfo.loginMethods[0].passwordHash, passwordHash);
        }
        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

}
