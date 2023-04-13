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

public class PasswordlessDeleteCodeAPITest2_11 {
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
    public void testDeleteCode() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        PasswordlessSQLStorage storage = (PasswordlessSQLStorage) StorageLayer.getStorage(process.getProcess());

        String phoneNumber = "+442071838750";
        String codeId = "codeId";

        String deviceIdHash = "pZ9SP0USbXbejGFO6qx7x3JBjupJZVtw4RkFiNtJGqc";
        String linkCodeHash = "wo5UcFFVSblZEd1KOUOl-dpJ5zpSr_Qsor1Eg4TzDRE";

        storage.createDeviceWithCode(new TenantIdentifier(null, null, null), null, phoneNumber, "linkCodeSalt",
                new PasswordlessCode(codeId, deviceIdHash, linkCodeHash, System.currentTimeMillis()));

        JsonObject createCodeRequestBody = new JsonObject();
        createCodeRequestBody.addProperty("codeId", codeId);

        JsonObject response = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/signinup/code/remove", createCodeRequestBody, 1000, 1000, null,
                SemVer.v2_10.get(), "passwordless");

        assertEquals("OK", response.get("status").getAsString());

        assertNull(storage.getCode(new TenantIdentifier(null, null, null), codeId));
        assertNull(storage.getDevice(new TenantIdentifier(null, null, null), deviceIdHash));
        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testDeleteNonExistentCode() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        String codeId = "codeId";

        JsonObject createCodeRequestBody = new JsonObject();
        createCodeRequestBody.addProperty("codeId", codeId);

        JsonObject response = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/signinup/code/remove", createCodeRequestBody, 1000, 1000, null,
                SemVer.v2_10.get(), "passwordless");

        assertEquals("OK", response.get("status").getAsString());

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    /**
     * empty request body -> BadRequest
     *
     * @throws Exception
     */
    @Test
    public void testEmptyRequestBody() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        JsonObject createCodeRequestBody = new JsonObject();

        HttpResponseException error = null;
        try {
            HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/signinup/code/remove", createCodeRequestBody, 1000, 1000, null,
                    SemVer.v2_10.get(), "passwordless");

        } catch (HttpResponseException ex) {
            error = ex;
        }

        assertNotNull(error);
        assertEquals(400, error.statusCode);
        assertEquals("Http error. Status Code: 400. Message: Field name 'codeId' is invalid in JSON input",
                error.getMessage());

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }
}
