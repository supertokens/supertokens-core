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
import io.supertokens.passwordless.Passwordless;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifier;
import io.supertokens.pluginInterface.passwordless.PasswordlessCode;
import io.supertokens.pluginInterface.passwordless.PasswordlessStorage;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.test.TestingProcessManager;
import io.supertokens.test.Utils;
import io.supertokens.test.httpRequest.HttpRequestForTesting;

import io.supertokens.utils.SemVer;
import io.supertokens.test.httpRequest.HttpResponseException;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import java.util.HashMap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class PasswordlessGetCodesAPITest2_11 {
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
            HashMap<String, String> map = new HashMap<>();
            map.put("deviceId", "anythin");
            map.put("email", "anythin");
            HttpResponseException error = null;
            try {
                HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                        "http://localhost:3567/recipe/signinup/codes", map, 1000, 1000, null,
                        SemVer.v2_10.get(), "passwordless");
            } catch (HttpResponseException e) {
                error = e;
            }
            assertNotNull(error);
            assertEquals(400, error.statusCode);
            assertEquals(
                    "Http error. Status Code: 400. Message: Please provide exactly one of email, phoneNumber, " +
                            "deviceId or preAuthSessionId",
                    error.getMessage());
        }

        // no input param
        {
            HashMap<String, String> map = new HashMap<>();
            HttpResponseException error = null;
            try {
                HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                        "http://localhost:3567/recipe/signinup/codes", map, 1000, 1000, null,
                        SemVer.v2_10.get(), "passwordless");
            } catch (HttpResponseException e) {
                error = e;
            }
            assertNotNull(error);
            assertEquals(400, error.statusCode);
            assertEquals(
                    "Http error. Status Code: 400. Message: Please provide exactly one of email, phoneNumber, " +
                            "deviceId or preAuthSessionId",
                    error.getMessage());
        }

        // malformed device ID
        {
            HashMap<String, String> map = new HashMap<>();
            map.put("deviceId", "anythin-does-anything");
            HttpResponseException error = null;
            try {
                HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                        "http://localhost:3567/recipe/signinup/codes", map, 1000, 1000, null,
                        SemVer.v2_10.get(), "passwordless");
            } catch (HttpResponseException e) {
                error = e;
            }
            assertNotNull(error);
            assertEquals(400, error.statusCode);
            assertEquals("Http error. Status Code: 400. Message: Input encoding error in deviceId", error.getMessage());
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testGetCodesNoMatch() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        HashMap<String, String> map = new HashMap<>();
        map.put("deviceId", "nothing");
        JsonObject response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/signinup/codes", map, 1000, 1000, null, SemVer.v2_10.get(),
                "passwordless");

        assertEquals("OK", response.get("status").getAsString());
        assertEquals(2, response.entrySet().size());
        assert (response.has("devices"));
        assertEquals(0, response.get("devices").getAsJsonArray().size());

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testGetCodes() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        PasswordlessStorage storage = (PasswordlessStorage) StorageLayer.getStorage(process.getProcess());

        String email = "test@example.com";
        String codeId = io.supertokens.utils.Utils.getUUID();

        String deviceIdHash = "pZ9SP0USbXbejGFO6qx7x3JBjupJZVtw4RkFiNtJGqc=";
        String linkCodeHash = "wo5UcFFVSblZEd1KOUOl-dpJ5zpSr_Qsor1Eg4TzDRE";

        // no match

        {
            HashMap<String, String> map = new HashMap<>();
            map.put("preAuthSessionId", deviceIdHash);
            JsonObject response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/signinup/codes", map, 1000, 1000, null,
                    SemVer.v2_10.get(), "passwordless");

            assertEquals("OK", response.get("status").getAsString());
            assertEquals(2, response.entrySet().size());
            assert (response.has("devices"));
            assertEquals(0, response.get("devices").getAsJsonArray().size());
        }

        storage.createDeviceWithCode(new TenantIdentifier(null, null, null), email, null, "linkCodeSalt",
                new PasswordlessCode(codeId, deviceIdHash, linkCodeHash, System.currentTimeMillis()));
        assertEquals(1, storage.getDevicesByEmail(new TenantIdentifier(null, null, null), email).length);

        // match

        {
            HashMap<String, String> map = new HashMap<>();
            map.put("preAuthSessionId", deviceIdHash);
            JsonObject response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/signinup/codes", map, 1000, 1000, null,
                    SemVer.v2_10.get(), "passwordless");

            assertEquals("OK", response.get("status").getAsString());
            assertEquals(2, response.entrySet().size());
            assert (response.has("devices"));
            JsonArray jsonDeviceList = response.get("devices").getAsJsonArray();
            assertEquals(1, jsonDeviceList.size());

            checkDevice(jsonDeviceList, 0, email, null, deviceIdHash, new String[]{codeId});
        }
        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testGetCodesWithEmail() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        PasswordlessStorage storage = (PasswordlessStorage) StorageLayer.getStorage(process.getProcess());
        String email = "test@example.com";
        String codeId = io.supertokens.utils.Utils.getUUID();

        String deviceIdHash = "pZ9SP0USbXbejGFO6qx7x3JBjupJZVtw4RkFiNtJGqc=";
        String linkCodeHash = "wo5UcFFVSblZEd1KOUOl-dpJ5zpSr_Qsor1Eg4TzDRE";

        // OK without matching codes
        {
            HashMap<String, String> map = new HashMap<>();
            map.put("email", email);
            JsonObject response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/signinup/codes", map, 1000, 1000, null,
                    SemVer.v2_10.get(), "passwordless");

            assertEquals("OK", response.get("status").getAsString());
            assertEquals(2, response.entrySet().size());
            assert (response.has("devices"));
            assertEquals(0, response.get("devices").getAsJsonArray().size());
        }

        // OK with matching codes
        {
            storage.createDeviceWithCode(new TenantIdentifier(null, null, null), email, null, "linkCodeSalt",
                    new PasswordlessCode(codeId, deviceIdHash, linkCodeHash, System.currentTimeMillis()));
            assertEquals(1, storage.getDevicesByEmail(new TenantIdentifier(null, null, null), email).length);

            {
                HashMap<String, String> map = new HashMap<>();
                map.put("email", email.toUpperCase());
                JsonObject response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                        "http://localhost:3567/recipe/signinup/codes", map, 1000, 1000, null,
                        SemVer.v2_10.get(), "passwordless");

                assertEquals("OK", response.get("status").getAsString());
                assertEquals(2, response.entrySet().size());
                assert (response.has("devices"));
                JsonArray jsonDeviceList = response.get("devices").getAsJsonArray();
                assertEquals(1, jsonDeviceList.size());
                checkDevice(jsonDeviceList, 0, email, null, deviceIdHash, new String[]{codeId});
            }
        }
        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testGetCodesWithPhoneNumber() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        PasswordlessStorage storage = (PasswordlessStorage) StorageLayer.getStorage(process.getProcess());
        String phoneNumber = "+918989898989";
        String codeId = io.supertokens.utils.Utils.getUUID();

        String deviceIdHash = "pZ9SP0USbXbejGFO6qx7x3JBjupJZVtw4RkFiNtJGqc=";
        String linkCodeHash = "wo5UcFFVSblZEd1KOUOl-dpJ5zpSr_Qsor1Eg4TzDRE";

        // OK without matching codes
        {
            HashMap<String, String> map = new HashMap<>();
            map.put("phoneNumber", phoneNumber);
            JsonObject response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/signinup/codes", map, 1000, 1000, null,
                    SemVer.v2_10.get(), "passwordless");

            assertEquals("OK", response.get("status").getAsString());
            assertEquals(2, response.entrySet().size());
            assert (response.has("devices"));
            assertEquals(0, response.get("devices").getAsJsonArray().size());
        }

        // OK with matching codes
        {
            storage.createDeviceWithCode(new TenantIdentifier(null, null, null), null, phoneNumber, "linkCodeSalt",
                    new PasswordlessCode(codeId, deviceIdHash, linkCodeHash, System.currentTimeMillis()));
            assertEquals(1,
                    storage.getDevicesByPhoneNumber(new TenantIdentifier(null, null, null), phoneNumber).length);

            {
                HashMap<String, String> map = new HashMap<>();
                map.put("phoneNumber", phoneNumber);
                JsonObject response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                        "http://localhost:3567/recipe/signinup/codes", map, 1000, 1000, null,
                        SemVer.v2_10.get(), "passwordless");

                assertEquals("OK", response.get("status").getAsString());
                assertEquals(2, response.entrySet().size());
                assert (response.has("devices"));
                JsonArray jsonDeviceList = response.get("devices").getAsJsonArray();
                assertEquals(1, jsonDeviceList.size());
                checkDevice(jsonDeviceList, 0, null, phoneNumber, deviceIdHash, new String[]{codeId});
            }
        }
        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testGetCodesWithDeviceID() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        PasswordlessStorage storage = (PasswordlessStorage) StorageLayer.getStorage(process.getProcess());
        String deviceID = "randomDeviceID";
        String phoneNumber = "+918989898989";

        // OK without matching codes
        {
            HashMap<String, String> map = new HashMap<>();
            map.put("deviceId", deviceID);
            JsonObject response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/signinup/codes", map, 1000, 1000, null,
                    SemVer.v2_10.get(), "passwordless");

            assertEquals("OK", response.get("status").getAsString());
            assertEquals(2, response.entrySet().size());
            assert (response.has("devices"));
            assertEquals(0, response.get("devices").getAsJsonArray().size());
        }

        // OK with matching codes
        {
            Passwordless.CreateCodeResponse createCodeResponse = Passwordless.createCode(process.getProcess(), null,
                    phoneNumber, null, null);
            assertNotNull(createCodeResponse);

            deviceID = createCodeResponse.deviceId;

            assertEquals(1,
                    storage.getDevicesByPhoneNumber(new TenantIdentifier(null, null, null), phoneNumber).length);
            // TODO: deviceID =
            {
                HashMap<String, String> map = new HashMap<>();
                map.put("deviceId", deviceID);
                JsonObject response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                        "http://localhost:3567/recipe/signinup/codes", map, 1000, 1000, null,
                        SemVer.v2_10.get(), "passwordless");

                assertEquals("OK", response.get("status").getAsString());
                assertEquals(2, response.entrySet().size());
                assert (response.has("devices"));
                JsonArray jsonDeviceList = response.get("devices").getAsJsonArray();
                assertEquals(1, jsonDeviceList.size());
                checkDevice(jsonDeviceList, 0, null, phoneNumber, createCodeResponse.deviceIdHash,
                        new String[]{createCodeResponse.codeId});
            }
        }
        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    private void checkDevice(JsonArray jsonDeviceList, int ind, String email, String phoneNumber, String deviceIdHash,
                             String[] codeIds) {
        JsonObject device = jsonDeviceList.get(ind).getAsJsonObject();

        assertEquals(deviceIdHash, device.get("preAuthSessionId").getAsString());
        assertEquals(0, device.get("failedCodeInputAttemptCount").getAsInt());

        if (email == null) {
            assert (!device.has("email"));
        } else {
            assertEquals(email, device.get("email").getAsString());
        }

        if (phoneNumber == null) {
            assert (!device.has("phoneNumber"));
        } else {
            assertEquals(phoneNumber, device.get("phoneNumber").getAsString());
        }

        assert (device.has("codes"));
        JsonArray jsonCodeList = device.get("codes").getAsJsonArray();
        assertEquals(codeIds.length, jsonCodeList.size());
        for (int i = 0; i < codeIds.length; ++i) {
            checkCodeInJsonArray(jsonCodeList, i, codeIds[i]);
        }

        assertEquals(4, device.entrySet().size());
    }

    private void checkCodeInJsonArray(JsonArray jsonCodeList, int index, String codeId2) {
        JsonObject code = jsonCodeList.get(index).getAsJsonObject();
        assertEquals(codeId2, code.get("codeId").getAsString());
        assert (code.has("timeCreated"));
        assert (code.has("codeLifetime"));
        assertEquals(3, code.entrySet().size());
    }
}
