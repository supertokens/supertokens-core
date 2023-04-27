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

package io.supertokens.test.passwordless.api;

import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import io.supertokens.ProcessState;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifier;
import io.supertokens.pluginInterface.passwordless.PasswordlessStorage;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.test.TestingProcessManager;
import io.supertokens.test.Utils;
import io.supertokens.test.httpRequest.HttpRequestForTesting;
import io.supertokens.test.httpRequest.HttpResponseException;
import io.supertokens.utils.SemVer;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import static org.junit.Assert.*;

public class PasswordlessUserPutAPITest2_11 {
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
    public void testBadInput() throws Exception {
        String[] args = { "../" };

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        String userId = "userId";

        String email = "test@example.com";
        String email2 = "test2@example.com";
        String phoneNumber = "+442071838750";

        PasswordlessStorage storage = (PasswordlessStorage) StorageLayer.getStorage(process.getProcess());
        storage.createUser(new TenantIdentifier(null, null, null),
                userId, email, null, System.currentTimeMillis());
        storage.createUser(new TenantIdentifier(null, null, null),
                "userId2", email2, null, System.currentTimeMillis());
        storage.createUser(new TenantIdentifier(null, null, null),
                "userId3", null, phoneNumber, System.currentTimeMillis());

        {
            JsonObject updateUserRequestBody = new JsonObject();
            Exception ex = null;
            try {
                HttpRequestForTesting.sendJsonPUTRequest(process.getProcess(), "",
                        "http://localhost:3567/recipe/user", updateUserRequestBody, 1000, 1000, null,
                        SemVer.v2_10.get(), "passwordless");
            } catch (Exception e) {
                ex = e;
            }

            assertNotNull(ex);
            assert (ex instanceof HttpResponseException);

            assertEquals(400, ((HttpResponseException) ex).statusCode);
            assertEquals("Http error. Status Code: 400. Message: Field name 'userId' is invalid in JSON input",
                    ex.getMessage());
        }

        {
            JsonObject updateUserRequestBody = new JsonObject();
            updateUserRequestBody.addProperty("userId", "notexists");
            updateUserRequestBody.addProperty("email", "notexists");

            JsonObject response = HttpRequestForTesting.sendJsonPUTRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/user", updateUserRequestBody, 1000, 1000, null,
                    SemVer.v2_10.get(), "passwordless");

            assertEquals("UNKNOWN_USER_ID_ERROR", response.get("status").getAsString());
        }

        {
            JsonObject updateUserRequestBody = new JsonObject();
            updateUserRequestBody.addProperty("userId", userId);
            updateUserRequestBody.addProperty("email", email2);

            JsonObject response = HttpRequestForTesting.sendJsonPUTRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/user", updateUserRequestBody, 1000, 1000, null,
                    SemVer.v2_10.get(), "passwordless");

            assertEquals("EMAIL_ALREADY_EXISTS_ERROR", response.get("status").getAsString());
        }

        {
            JsonObject updateUserRequestBody = new JsonObject();
            updateUserRequestBody.addProperty("userId", userId);
            updateUserRequestBody.addProperty("phoneNumber", phoneNumber);

            JsonObject response = HttpRequestForTesting.sendJsonPUTRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/user", updateUserRequestBody, 1000, 1000, null,
                    SemVer.v2_10.get(), "passwordless");

            assertEquals("PHONE_NUMBER_ALREADY_EXISTS_ERROR", response.get("status").getAsString());
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testEmailToPhone() throws Exception {
        String[] args = { "../" };

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        String userId = "userId";
        String phoneNumber = "+442071838750";

        PasswordlessStorage storage = (PasswordlessStorage) StorageLayer.getStorage(process.getProcess());
        String email = "email";
        storage.createUser(new TenantIdentifier(null, null, null),
                userId, email, null, System.currentTimeMillis());

        JsonObject updateUserRequestBody = new JsonObject();
        updateUserRequestBody.addProperty("userId", userId);
        updateUserRequestBody.add("email", JsonNull.INSTANCE);
        updateUserRequestBody.addProperty("phoneNumber", phoneNumber);

        JsonObject response = HttpRequestForTesting.sendJsonPUTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/user", updateUserRequestBody, 1000, 1000, null,
                SemVer.v2_10.get(), "passwordless");

        assertEquals("OK", response.get("status").getAsString());

        assertNull(storage.getUserByEmail(new TenantIdentifier(null, null, null), email));
        assertNotNull(storage.getUserByPhoneNumber(new TenantIdentifier(null, null, null), phoneNumber));
        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    /**
     * remove phoneNumber set email -> OK
     *
     * @throws Exception
     */
    @Test
    public void testPhoneToEmail() throws Exception {
        String[] args = { "../" };

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        String userId = "userId";
        String phoneNumber = "+442071838750";
        String email = "email";

        PasswordlessStorage storage = (PasswordlessStorage) StorageLayer.getStorage(process.getProcess());
        storage.createUser(new TenantIdentifier(null, null, null),
                userId, null, phoneNumber, System.currentTimeMillis());

        JsonObject updateUserRequestBody = new JsonObject();
        updateUserRequestBody.addProperty("userId", userId);
        updateUserRequestBody.add("phoneNumber", JsonNull.INSTANCE);
        updateUserRequestBody.addProperty("email", email.toUpperCase());

        JsonObject response = HttpRequestForTesting.sendJsonPUTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/user", updateUserRequestBody, 1000, 1000, null,
                SemVer.v2_10.get(), "passwordless");

        assertEquals("OK", response.get("status").getAsString());

        assertNotNull(storage.getUserByEmail(new TenantIdentifier(null, null, null), email));
        assertNull(storage.getUserByPhoneNumber(new TenantIdentifier(null, null, null), phoneNumber));
        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    /**
     * update both email and phoneNumber -> OK
     *
     * @throws Exception
     */
    @Test
    public void testPhoneAndEmail() throws Exception {
        String[] args = { "../" };

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        String userId = "userId";
        String phoneNumber = "+442071838750";
        String email = "email";
        String updatedPhoneNumber = "+442071838751";
        String updatedEmail = "test@example.com";

        PasswordlessStorage storage = (PasswordlessStorage) StorageLayer.getStorage(process.getProcess());
        storage.createUser(new TenantIdentifier(null, null, null),
                userId, email, phoneNumber, System.currentTimeMillis());

        JsonObject updateUserRequestBody = new JsonObject();
        updateUserRequestBody.addProperty("userId", userId);
        updateUserRequestBody.addProperty("phoneNumber", updatedPhoneNumber);
        updateUserRequestBody.addProperty("email", updatedEmail);

        JsonObject response = HttpRequestForTesting.sendJsonPUTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/user", updateUserRequestBody, 1000, 1000, null,
                SemVer.v2_10.get(), "passwordless");

        assertEquals("OK", response.get("status").getAsString());

        assertNull(storage.getUserByEmail(new TenantIdentifier(null, null, null), email));
        assertNull(storage.getUserByPhoneNumber(new TenantIdentifier(null, null, null), phoneNumber));

        assertNotNull(storage.getUserByEmail(new TenantIdentifier(null, null, null), updatedEmail));
        assertNotNull(storage.getUserByPhoneNumber(new TenantIdentifier(null, null, null), updatedPhoneNumber));

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    /**
     * remove both phoneNumber and email -> BadRequest
     *
     * @throws Exception
     */
    @Test
    public void clearEmailAndPhone() throws Exception {
        String[] args = { "../" };

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        String userId = "userId";
        String phoneNumber = "+442071838750";
        String email = "email";

        PasswordlessStorage storage = (PasswordlessStorage) StorageLayer.getStorage(process.getProcess());
        storage.createUser(new TenantIdentifier(null, null, null),
                userId, email, phoneNumber, System.currentTimeMillis());

        JsonObject updateUserRequestBody = new JsonObject();
        updateUserRequestBody.addProperty("userId", userId);
        updateUserRequestBody.add("phoneNumber", JsonNull.INSTANCE);
        updateUserRequestBody.add("email", JsonNull.INSTANCE);

        Exception ex = null;
        try {
            HttpRequestForTesting.sendJsonPUTRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/user", updateUserRequestBody, 1000, 1000, null,
                    SemVer.v2_10.get(), "passwordless");

        } catch (Exception e) {
            ex = e;
        }

        assertNotNull(ex);
        assert (ex instanceof HttpResponseException);
        assertEquals(((HttpResponseException) ex).statusCode, 400);
        assertEquals(ex.getMessage(),
                "Http error. Status Code: 400. Message: You cannot clear both email and phone number of a user");

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    /**
     * clear email of email only user -> BadRequest
     *
     * @throws Exception
     */
    @Test
    public void clearEmailOfEmailOnlyUser() throws Exception {
        String[] args = { "../" };

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        String userId = "userId";
        String email = "email";

        PasswordlessStorage storage = (PasswordlessStorage) StorageLayer.getStorage(process.getProcess());
        storage.createUser(new TenantIdentifier(null, null, null),
                userId, email, null, System.currentTimeMillis());

        JsonObject updateUserRequestBody = new JsonObject();
        updateUserRequestBody.addProperty("userId", userId);
        updateUserRequestBody.add("email", JsonNull.INSTANCE);

        Exception ex = null;
        try {
            HttpRequestForTesting.sendJsonPUTRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/user", updateUserRequestBody, 1000, 1000, null,
                    SemVer.v2_10.get(), "passwordless");

        } catch (Exception e) {
            ex = e;
        }

        assertNotNull(ex);
        assert (ex instanceof HttpResponseException);
        assertEquals(((HttpResponseException) ex).statusCode, 400);
        assertEquals(ex.getMessage(),
                "Http error. Status Code: 400. Message: You cannot clear both email and phone number of a user");

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    /**
     * clear phoneNumber of phoneNumber only user -> BadRequest
     * TODO: error messages could be more clearer from the API
     *
     * @throws Exception
     */
    @Test
    public void clearPhoneNUmberOfPhoneNumberOnlyUser() throws Exception {
        String[] args = { "../" };

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        String userId = "userId";
        String phoneNumber = "+91898989898";

        PasswordlessStorage storage = (PasswordlessStorage) StorageLayer.getStorage(process.getProcess());
        storage.createUser(new TenantIdentifier(null, null, null),
                userId, null, phoneNumber, System.currentTimeMillis());

        JsonObject updateUserRequestBody = new JsonObject();
        updateUserRequestBody.addProperty("userId", userId);
        updateUserRequestBody.add("phoneNumber", JsonNull.INSTANCE);

        Exception ex = null;
        try {
            HttpRequestForTesting.sendJsonPUTRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/user", updateUserRequestBody, 1000, 1000, null,
                    SemVer.v2_10.get(), "passwordless");

        } catch (Exception e) {
            ex = e;
        }

        assertNotNull(ex);
        assert (ex instanceof HttpResponseException);
        assertEquals(((HttpResponseException) ex).statusCode, 400);
        assertEquals(ex.getMessage(),
                "Http error. Status Code: 400. Message: You cannot clear both email and phone number of a user");

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    /**
     * clear email -> OK
     *
     * @throws Exception
     */
    @Test
    public void clearEmail() throws Exception {
        String[] args = { "../" };

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        String userId = "userId";
        String email = "email";
        String phoneNumber = "+9189898989";

        PasswordlessStorage storage = (PasswordlessStorage) StorageLayer.getStorage(process.getProcess());
        storage.createUser(new TenantIdentifier(null, null, null),
                userId, email, phoneNumber, System.currentTimeMillis());

        JsonObject updateUserRequestBody = new JsonObject();
        updateUserRequestBody.addProperty("userId", userId);
        updateUserRequestBody.add("email", JsonNull.INSTANCE);

        JsonObject response = HttpRequestForTesting.sendJsonPUTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/user", updateUserRequestBody, 1000, 1000, null,
                SemVer.v2_10.get(), "passwordless");

        assertEquals("OK", response.get("status").getAsString());

        assertNull(storage.getUserByEmail(new TenantIdentifier(null, null, null), email));
        assertNotNull(storage.getUserByPhoneNumber(new TenantIdentifier(null, null, null), phoneNumber));

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    /**
     * clear phoneNumber -> OK
     *
     * @throws Exception
     */
    @Test
    public void clearPhone() throws Exception {
        String[] args = { "../" };

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        String userId = "userId";
        String email = "email";
        String phoneNumber = "+9189898989";

        PasswordlessStorage storage = (PasswordlessStorage) StorageLayer.getStorage(process.getProcess());
        storage.createUser(new TenantIdentifier(null, null, null),
                userId, email, phoneNumber, System.currentTimeMillis());

        JsonObject updateUserRequestBody = new JsonObject();
        updateUserRequestBody.addProperty("userId", userId);
        updateUserRequestBody.add("phoneNumber", JsonNull.INSTANCE);

        JsonObject response = HttpRequestForTesting.sendJsonPUTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/user", updateUserRequestBody, 1000, 1000, null,
                SemVer.v2_10.get(), "passwordless");

        assertEquals("OK", response.get("status").getAsString());

        assertNotNull(storage.getUserByEmail(new TenantIdentifier(null, null, null), email));
        assertNull(storage.getUserByPhoneNumber(new TenantIdentifier(null, null, null), phoneNumber));

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    /**
     * update neither -> OK
     *
     * @throws Exception
     */
    @Test
    public void updateNothing() throws Exception {
        String[] args = { "../" };

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        String userId = "userId";
        String email = "email";
        String phoneNumber = "+9189898989";

        PasswordlessStorage storage = (PasswordlessStorage) StorageLayer.getStorage(process.getProcess());
        storage.createUser(new TenantIdentifier(null, null, null),
                userId, email, phoneNumber, System.currentTimeMillis());

        JsonObject updateUserRequestBody = new JsonObject();
        updateUserRequestBody.addProperty("userId", userId);

        JsonObject response = HttpRequestForTesting.sendJsonPUTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/user", updateUserRequestBody, 1000, 1000, null,
                SemVer.v2_10.get(), "passwordless");

        assertEquals("OK", response.get("status").getAsString());

        assertNotNull(storage.getUserByEmail(new TenantIdentifier(null, null, null), email));
        assertNotNull(storage.getUserByPhoneNumber(new TenantIdentifier(null, null, null), phoneNumber));

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    /**
     * update email only -> OK
     *
     * @throws Exception
     */
    @Test
    public void testUpdateEmail() throws Exception {
        String[] args = { "../" };

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        String userId = "userId";

        PasswordlessStorage storage = (PasswordlessStorage) StorageLayer.getStorage(process.getProcess());
        String email = "email";
        String updated_email = "test@example.com";
        storage.createUser(new TenantIdentifier(null, null, null),
                userId, email, null, System.currentTimeMillis());

        JsonObject updateUserRequestBody = new JsonObject();
        updateUserRequestBody.addProperty("userId", userId);
        updateUserRequestBody.addProperty("email", updated_email);

        JsonObject response = HttpRequestForTesting.sendJsonPUTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/user", updateUserRequestBody, 1000, 1000, null,
                SemVer.v2_10.get(), "passwordless");

        assertEquals("OK", response.get("status").getAsString());

        assertNotNull(storage.getUserByEmail(new TenantIdentifier(null, null, null), updated_email));
        assertNull(storage.getUserByEmail(new TenantIdentifier(null, null, null), email));

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    /**
     * update phoneNumber only -> OK
     *
     * @throws Exception
     */
    @Test
    public void testUpdatePhoneNumber() throws Exception {
        String[] args = { "../" };

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        String userId = "userId";
        String phoneNumber = "+442071838750";
        String updatedPhoneNumber = "+442071838751";

        PasswordlessStorage storage = (PasswordlessStorage) StorageLayer.getStorage(process.getProcess());
        storage.createUser(new TenantIdentifier(null, null, null),
                userId, null, phoneNumber, System.currentTimeMillis());

        JsonObject updateUserRequestBody = new JsonObject();
        updateUserRequestBody.addProperty("userId", userId);
        updateUserRequestBody.addProperty("phoneNumber", updatedPhoneNumber);

        JsonObject response = HttpRequestForTesting.sendJsonPUTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/user", updateUserRequestBody, 1000, 1000, null,
                SemVer.v2_10.get(), "passwordless");

        assertEquals("OK", response.get("status").getAsString());

        assertNotNull(storage.getUserByPhoneNumber(new TenantIdentifier(null, null, null), updatedPhoneNumber));
        assertNull(storage.getUserByPhoneNumber(new TenantIdentifier(null, null, null), phoneNumber));

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }
}
