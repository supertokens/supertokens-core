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

import com.google.gson.JsonObject;
import io.supertokens.ProcessState;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifier;
import io.supertokens.pluginInterface.passwordless.PasswordlessCode;
import io.supertokens.pluginInterface.passwordless.sqlStorage.PasswordlessSQLStorage;
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

public class PasswordlessDeleteCodesAPITest2_11 {
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
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        {
            HttpResponseException error = null;
            JsonObject createCodeRequestBody = new JsonObject();
            try {
                HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                        "http://localhost:3567/recipe/signinup/codes/remove", createCodeRequestBody, 1000, 1000, null,
                        SemVer.v2_10.get(), "passwordless");
            } catch (HttpResponseException e) {
                error = e;
            }
            assertNotNull(error);
            assertEquals(400, error.statusCode);
            assertEquals("Http error. Status Code: 400. Message: Please provide exactly one of email or phoneNumber",
                    error.getMessage());
        }

        {
            HttpResponseException error = null;
            JsonObject createCodeRequestBody = new JsonObject();
            createCodeRequestBody.addProperty("email", "email!");
            createCodeRequestBody.addProperty("phoneNumber", "phone!");
            try {
                HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                        "http://localhost:3567/recipe/signinup/codes/remove", createCodeRequestBody, 1000, 1000, null,
                        SemVer.v2_10.get(), "passwordless");
            } catch (HttpResponseException e) {
                error = e;
            }
            assertNotNull(error);
            assertEquals(400, error.statusCode);
            assertEquals("Http error. Status Code: 400. Message: Please provide exactly one of email or phoneNumber",
                    error.getMessage());
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testDeleteByEmailWithoutCodes() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        JsonObject createCodeRequestBody = new JsonObject();
        createCodeRequestBody.addProperty("phoneNumber", "phone!");

        JsonObject response = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/signinup/codes/remove", createCodeRequestBody, 1000, 1000, null,
                SemVer.v2_10.get(), "passwordless");

        assertEquals("OK", response.get("status").getAsString());

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testDeleteByPhoneWithoutCodes() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        JsonObject createCodeRequestBody = new JsonObject();
        createCodeRequestBody.addProperty("phoneNumber", "phone!");

        JsonObject response = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/signinup/codes/remove", createCodeRequestBody, 1000, 1000, null,
                SemVer.v2_10.get(), "passwordless");

        assertEquals("OK", response.get("status").getAsString());

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testDeleteByEmail() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        PasswordlessSQLStorage storage = (PasswordlessSQLStorage) StorageLayer.getStorage(process.getProcess());

        String email = "test@example.com";
        String codeId = "codeId";
        String codeId2 = "codeId2";

        String deviceIdHash = "pZ9SP0USbXbejGFO6qx7x3JBjupJZVtw4RkFiNtJGqc";
        String linkCodeHash = "wo5UcFFVSblZEd1KOUOl-dpJ5zpSr_Qsor1Eg4TzDRE";
        String linkCodeHash2 = "F0aZHCBYSJIghP5e0flGa8gvoUYEgGus2yIJYmdpFY4";

        storage.createDeviceWithCode(new TenantIdentifier(null, null, null), email, null, "linkCodeSalt",
                new PasswordlessCode(codeId, deviceIdHash, linkCodeHash, System.currentTimeMillis()));
        storage.createCode(new TenantIdentifier(null, null, null),
                new PasswordlessCode(codeId2, deviceIdHash, linkCodeHash2, System.currentTimeMillis()));

        JsonObject createCodeRequestBody = new JsonObject();
        createCodeRequestBody.addProperty("email", "test@examplE.com");

        JsonObject response = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/signinup/codes/remove", createCodeRequestBody, 1000, 1000, null,
                SemVer.v2_10.get(), "passwordless");

        assertEquals("OK", response.get("status").getAsString());

        assertNull(storage.getDevice(new TenantIdentifier(null, null, null), deviceIdHash));
        assertNull(storage.getCode(new TenantIdentifier(null, null, null), codeId));
        assertNull(storage.getCode(new TenantIdentifier(null, null, null), codeId2));

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testDeleteByPhoneNumber() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        PasswordlessSQLStorage storage = (PasswordlessSQLStorage) StorageLayer.getStorage(process.getProcess());

        String phoneNumber = "+442071838750";
        String codeId = "codeId";
        String codeId2 = "codeId2";

        String deviceIdHash = "pZ9SP0USbXbejGFO6qx7x3JBjupJZVtw4RkFiNtJGqc";
        String linkCodeHash = "wo5UcFFVSblZEd1KOUOl-dpJ5zpSr_Qsor1Eg4TzDRE";
        String linkCodeHash2 = "F0aZHCBYSJIghP5e0flGa8gvoUYEgGus2yIJYmdpFY4";

        storage.createDeviceWithCode(new TenantIdentifier(null, null, null), null, phoneNumber, "linkCodeSalt",
                new PasswordlessCode(codeId, deviceIdHash, linkCodeHash, System.currentTimeMillis()));
        storage.createCode(new TenantIdentifier(null, null, null),
                new PasswordlessCode(codeId2, deviceIdHash, linkCodeHash2, System.currentTimeMillis()));

        JsonObject createCodeRequestBody = new JsonObject();
        createCodeRequestBody.addProperty("phoneNumber", phoneNumber);

        JsonObject response = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/signinup/codes/remove", createCodeRequestBody, 1000, 1000, null,
                SemVer.v2_10.get(), "passwordless");

        assertEquals("OK", response.get("status").getAsString());

        assertNull(storage.getDevice(new TenantIdentifier(null, null, null), deviceIdHash));
        assertNull(storage.getCode(new TenantIdentifier(null, null, null), codeId));
        assertNull(storage.getCode(new TenantIdentifier(null, null, null), codeId2));

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }
}
