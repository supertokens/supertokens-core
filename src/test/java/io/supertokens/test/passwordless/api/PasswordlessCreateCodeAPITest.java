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

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.supertokens.ProcessState;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.test.TestingProcessManager;
import io.supertokens.test.Utils;
import io.supertokens.test.httpRequest.HttpRequestForTesting;

import io.supertokens.webserver.WebserverAPI;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import static org.junit.Assert.*;

import java.util.HashMap;

public class PasswordlessCreateCodeAPITest {
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
    public void testPhoneNumberNormalisation() throws Exception {
        String[] args = {"../"};

        String phoneNumber = "+44-207 183 8750";
        String normalisedPhoneNumber = io.supertokens.utils.Utils.normalizeIfPhoneNumber(phoneNumber);

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        JsonObject createCodeRequestBody = new JsonObject();
        createCodeRequestBody.addProperty("phoneNumber", phoneNumber);

        JsonObject createCodeResponse = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/signinup/code", createCodeRequestBody, 1000, 1000, null,
                WebserverAPI.getLatestCDIVersion().get(), "passwordless");

        assertEquals("OK", createCodeResponse.get("status").getAsString());

        HashMap<String, String> params = new HashMap<>();

        params.put("phoneNumber", phoneNumber);

        JsonObject getCodeResponse = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/signinup/codes", params, 1000, 1000, null,
                WebserverAPI.getLatestCDIVersion().get(), "passwordless");

        assertEquals("OK", getCodeResponse.get("status").getAsString());

        JsonArray devicesArray = getCodeResponse.getAsJsonArray("devices");
        assertEquals(1, devicesArray.size());
        JsonObject device = devicesArray.get(0).getAsJsonObject();
        assertEquals(normalisedPhoneNumber, device.get("phoneNumber").getAsString());

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }
}
