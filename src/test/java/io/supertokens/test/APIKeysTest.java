/*
 *    Copyright (c) 2020, VRAI Labs and/or its affiliates. All rights reserved.
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

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.supertokens.Main;
import io.supertokens.ProcessState;
import io.supertokens.cliOptions.CLIOptions;
import io.supertokens.config.Config;
import io.supertokens.test.httpRequest.HttpRequestForTesting;
import io.supertokens.test.httpRequest.HttpRequestMocking;
import io.supertokens.utils.SemVer;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.*;

public class APIKeysTest {

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

    // * - set API key and check that config.getAPIKeys() does not return null
    @Test
    public void testGetApiKeysDoesNotReturnNullWhenAPIKeyIsSet() throws Exception {
        String[] args = {"../"};

        Utils.setValueInConfig("api_keys", "abctijenbogweg=-2438243u98"); // set api_keys

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        String[] apiKeys = Config.getConfig(process.getProcess()).getAPIKeys();
        assertNotNull(apiKeys);
        assertEquals(apiKeys.length, 1);
        assertEquals(apiKeys[0], "abctijenbogweg=-2438243u98");

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    // * - don't set API key and check that config.getAPIKeys() returns null
    @Test
    public void testGetApiKeysReturnsNullWhenAPIKeyIsNotSet() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));
        assertNull(Config.getConfig(process.getProcess()).getAPIKeys());

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));

    }

    // * - set an invalid API key and check that an error is thrown.
    @Test
    public void testErrorIsThrownWhenInvalidApiKeyIsSet() throws Exception {
        String[] args = {"../"};

        // api key length less that minimum length 20
        Utils.setValueInConfig("api_keys", "abc"); // set api_keys

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        ProcessState.EventAndException event = process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.INIT_FAILURE);
        assertNotNull(event);
        assertEquals(event.exception.getCause().getMessage(),
                "One of the API keys is too short. Please use at least 20 characters");

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));

        Utils.reset();

        // setting api key with non-supported symbols
        Utils.setValueInConfig("api_keys", "abC&^0t4t3t40t4@#%greognr"); // set api_keys
        process = TestingProcessManager.start(args);

        event = process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.INIT_FAILURE);
        assertNotNull(event);
        assertEquals(event.exception.getCause().getMessage(),
                "Invalid characters in API key. Please only use '=', '-' and alpha-numeric (including capitals)");

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));

    }

    // * - set one valid, and one invalid API key and check error is thrown
    @Test
    public void testSettingValidAndInvalidApiKeysAndErrorIsThrown() throws Exception {
        String[] args = {"../"};
        String validKey = "abdein30934=-DJNIigwe39";
        String invalidKey = "%93*4=JN39";

        Utils.setValueInConfig("api_keys", validKey + "," + invalidKey); // set api_keys

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        ProcessState.EventAndException event = process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.INIT_FAILURE);
        assertNotNull(event);
        assertEquals(event.exception.getCause().getMessage(),
                "One of the API keys is too short. Please use at least 20 characters");

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    // * - set a valid API key (with small and capital letter, numbers, =, -) and check that creating a new session
    // * requires that key (send request without key and it should fail with 401 and proper message, and then send
    // * with key and it should succeed and then send with wrong key and check it fails).
    @Test
    public void testCreatingSessionWithAndWithoutAPIKey() throws Exception {
        String[] args = {"../"};

        String apiKey = "hg40239oirjgBHD9450=Beew123-";
        Utils.setValueInConfig("api_keys", apiKey); // set api_keys

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        String userId = "userId";
        JsonObject userDataInJWT = new JsonObject();
        userDataInJWT.addProperty("key", "value");
        JsonObject userDataInDatabase = new JsonObject();
        userDataInDatabase.addProperty("key", "value");

        JsonObject request = new JsonObject();
        request.addProperty("userId", userId);
        request.add("userDataInJWT", userDataInJWT);
        request.add("userDataInDatabase", userDataInDatabase);
        request.addProperty("enableAntiCsrf", false);

        try {
            HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "", "http://localhost:3567/recipe/session",
                    request, 1000, 1000, null, Utils.getCdiVersionStringLatestForTests(), null);
            fail();
        } catch (io.supertokens.test.httpRequest.HttpResponseException e) {
            assertTrue(e.statusCode == 401
                    && e.getMessage().equals("Http error. Status Code: 401. Message: Invalid API key"));
        }

        JsonObject sessionInfo = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/session", request, 1000, 1000, null, SemVer.v2_21.get(),
                apiKey, "");
        assertEquals(sessionInfo.get("status").getAsString(), "OK");
        checkSessionResponse(sessionInfo, process, userId, userDataInJWT);

        try {
            HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "", "http://localhost:3567/recipe/session",
                    request, 1000, 1000, null, Utils.getCdiVersionStringLatestForTests(), "abd#%034t0g4in40t40v0j");
            fail();
        } catch (io.supertokens.test.httpRequest.HttpResponseException e) {
            assertTrue(e.statusCode == 401
                    && e.getMessage().equals("Http error. Status Code: 401. Message: Invalid API key"));
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    // * - set API key and check that you can still call /config and /hello without it
    @Test
    public void testSettingAPIKeyAndCallingConfigAndHelloWithoutIt() throws Exception {
        String[] args = {"../"};

        String apiKey = "hg40239oirjgBHD9450=Beew123-";
        Utils.setValueInConfig("api_keys", apiKey); // set api_keys

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        String response = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/hello", null, 1000, 1000, null, Utils.getCdiVersionStringLatestForTests(), "");
        assertEquals(response, "Hello");

        // map to store pid as parameter
        Map<String, String> map = new HashMap<>();
        map.put("pid", ProcessHandle.current().pid() + "");
        JsonObject response2 = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                "http://localhost:3567/config", map, 1000, 1000, null, Utils.getCdiVersionStringLatestForTests(), "");

        File f = new File(CLIOptions.get(process.getProcess()).getInstallationPath() + "config.yaml");
        String path = f.getAbsolutePath();

        assertEquals(response2.get("status").getAsString(), "OK");
        assertEquals(response2.get("path").getAsString(), path);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    // * - set 3 API keys and check that any one of them can be used. Then check that if you give no key or give wrong
    // * key, it fails
    @Test
    public void testSettingMultipleAPIKeys() throws Exception {
        String[] args = {"../"};

        String apiKey1 = "hg40239oirjgBHD9450=Beew123-1";
        String apiKey2 = "hg40239oirjgBHD9450=Beew123-2";
        String apiKey3 = "hg40239oirjgBHD9450=Beew123-3";

        Utils.setValueInConfig("api_keys", apiKey1 + "," + apiKey2 + "," + apiKey3); // set api_keys

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        String userId = "userId";
        JsonObject userDataInJWT = new JsonObject();
        userDataInJWT.addProperty("key", "value");
        JsonObject userDataInDatabase = new JsonObject();
        userDataInDatabase.addProperty("key", "value");

        JsonObject request = new JsonObject();
        request.addProperty("userId", userId);
        request.add("userDataInJWT", userDataInJWT);
        request.add("userDataInDatabase", userDataInDatabase);
        request.addProperty("enableAntiCsrf", false);

        // check that any one of the keys can be used
        JsonObject sessionInfo = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/session", request, 1000, 1000, null, SemVer.v2_21.get(),
                apiKey1, "");
        assertEquals(sessionInfo.get("status").getAsString(), "OK");
        checkSessionResponse(sessionInfo, process, userId, userDataInJWT);

        sessionInfo = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/session", request, 1000, 1000, null, SemVer.v2_21.get(),
                apiKey2, "");
        assertEquals(sessionInfo.get("status").getAsString(), "OK");
        checkSessionResponse(sessionInfo, process, userId, userDataInJWT);

        sessionInfo = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/session", request, 1000, 1000, null, SemVer.v2_21.get(),
                apiKey3, "");
        assertEquals(sessionInfo.get("status").getAsString(), "OK");
        checkSessionResponse(sessionInfo, process, userId, userDataInJWT);

        // sending request with no api key
        try {
            HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "", "http://localhost:3567/recipe/session",
                    request, 1000, 1000, null, Utils.getCdiVersionStringLatestForTests(), null);
            fail();
        } catch (io.supertokens.test.httpRequest.HttpResponseException e) {
            assertTrue(e.statusCode == 401
                    && e.getMessage().equals("Http error. Status Code: 401. Message: Invalid API key"));
        }

        // sending request with invalid api key
        try {
            HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "", "http://localhost:3567/recipe/session",
                    request, 1000, 1000, null, Utils.getCdiVersionStringLatestForTests(), "abd#%034t0g4in40t40v0j");
            fail();
        } catch (io.supertokens.test.httpRequest.HttpResponseException e) {
            assertTrue(e.statusCode == 401
                    && e.getMessage().equals("Http error. Status Code: 401. Message: Invalid API key"));
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    // - set API key like " key1, key2 , key3,key4 " and check that each of the keys work (the spaces are important)
    // * - set API key and check that request with " key ", " key" and "key" work
    @Test
    public void testSettingMultipleAPIKeysWithSpacing() throws Exception {
        String[] args = {"../"};

        String apiKey1 = "hg40239oirjgBHD9450=Beew123-1";
        String apiKey2 = "hg40239oirjgBHD9450=Beew123-2";
        String apiKey3 = "hg40239oirjgBHD9450=Beew123-3";
        String apiKey4 = "hg40239oirjgBHD9450=Beew123-4";

        Utils.setValueInConfig("api_keys", " " + apiKey1 + ", " + apiKey2 + ", " + apiKey3 + "," + apiKey4); // set
        // api_keys

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        String userId = "userId";
        JsonObject userDataInJWT = new JsonObject();
        userDataInJWT.addProperty("key", "value");
        JsonObject userDataInDatabase = new JsonObject();
        userDataInDatabase.addProperty("key", "value");

        JsonObject request = new JsonObject();
        request.addProperty("userId", userId);
        request.add("userDataInJWT", userDataInJWT);
        request.add("userDataInDatabase", userDataInDatabase);
        request.addProperty("enableAntiCsrf", false);
        request.addProperty("useStaticKey", false);

        // check that any one of the keys can be used
        JsonObject sessionInfo = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/session", request, 1000, 1000, null, SemVer.v2_21.get(),
                " " + apiKey1 + " ", "");

        assertEquals(sessionInfo.get("status").getAsString(), "OK");
        checkSessionResponse(sessionInfo, process, userId, userDataInJWT);

        sessionInfo = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/session", request, 1000, 1000, null, SemVer.v2_21.get(),
                " " + apiKey2, "");

        assertEquals(sessionInfo.get("status").getAsString(), "OK");
        checkSessionResponse(sessionInfo, process, userId, userDataInJWT);

        sessionInfo = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/session", request, 1000, 1000, null, SemVer.v2_21.get(),
                apiKey3, "");

        assertEquals(sessionInfo.get("status").getAsString(), "OK");
        checkSessionResponse(sessionInfo, process, userId, userDataInJWT);

        sessionInfo = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/session", request, 1000, 1000, null, SemVer.v2_21.get(),
                apiKey4, "");

        assertEquals(sessionInfo.get("status").getAsString(), "OK");
        checkSessionResponse(sessionInfo, process, userId, userDataInJWT);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    public static void checkSessionResponse(JsonObject response, TestingProcessManager.TestingProcess process,
                                            String userId, JsonObject userDataInJWT) {
        assertNotNull(response.get("session").getAsJsonObject().get("handle").getAsString());
        assertEquals(response.get("session").getAsJsonObject().get("userId").getAsString(), userId);
        assertEquals(response.get("session").getAsJsonObject().get("userDataInJWT").getAsJsonObject().toString(),
                userDataInJWT.toString());
        assertEquals(response.get("session").getAsJsonObject().entrySet().size(), 3);

        assertTrue(response.get("accessToken").getAsJsonObject().has("token"));
        assertTrue(response.get("accessToken").getAsJsonObject().has("expiry"));
        assertTrue(response.get("accessToken").getAsJsonObject().has("createdTime"));
        assertEquals(response.get("accessToken").getAsJsonObject().entrySet().size(), 3);

        assertTrue(response.get("refreshToken").getAsJsonObject().has("token"));
        assertTrue(response.get("refreshToken").getAsJsonObject().has("expiry"));
        assertTrue(response.get("refreshToken").getAsJsonObject().has("createdTime"));
        assertEquals(response.get("refreshToken").getAsJsonObject().entrySet().size(), 3);
    }

    @Test
    public void testDifferentWaysToPassAPIKey() throws Exception {
        String[] args = {"../"};

        String apiKey1 = "hg40239oirjgBHD9450=Beew123-1";

        Utils.setValueInConfig("api_keys", apiKey1); // set api_keys

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        String userId = "userId";
        JsonObject userDataInJWT = new JsonObject();
        userDataInJWT.addProperty("key", "value");
        JsonObject userDataInDatabase = new JsonObject();
        userDataInDatabase.addProperty("key", "value");

        JsonObject request = new JsonObject();
        request.addProperty("userId", userId);
        request.add("userDataInJWT", userDataInJWT);
        request.add("userDataInDatabase", userDataInDatabase);
        request.addProperty("enableAntiCsrf", false);

        {
            // check that any one of the keys can be used
            Map<String, String> headers = new HashMap<>();
            headers.put("api-key", apiKey1);
            headers.put("cdi-version", "2.21");
            JsonObject sessionInfo = sendJsonRequest(process.getProcess(),
                    "http://localhost:3567/recipe/session", request, 1000, 1000, "POST", headers);
            assertEquals(sessionInfo.get("status").getAsString(), "OK");
            checkSessionResponse(sessionInfo, process, userId, userDataInJWT);
        }

        {
            // check that any one of the keys can be used
            Map<String, String> headers = new HashMap<>();
            headers.put("Authorization", apiKey1);
            headers.put("cdi-version", "2.21");
            JsonObject sessionInfo = sendJsonRequest(process.getProcess(),
                    "http://localhost:3567/recipe/session", request, 1000, 1000, "POST", headers);
            assertEquals(sessionInfo.get("status").getAsString(), "OK");
            checkSessionResponse(sessionInfo, process, userId, userDataInJWT);
        }

        {
            // check that any one of the keys can be used
            Map<String, String> headers = new HashMap<>();
            headers.put("Authorization", "bearer " + apiKey1);
            headers.put("cdi-version", "2.21");
            JsonObject sessionInfo = sendJsonRequest(process.getProcess(),
                    "http://localhost:3567/recipe/session", request, 1000, 1000, "POST", headers);
            assertEquals(sessionInfo.get("status").getAsString(), "OK");
            checkSessionResponse(sessionInfo, process, userId, userDataInJWT);
        }

        {
            // check that any one of the keys can be used
            Map<String, String> headers = new HashMap<>();
            headers.put("Authorization", "bEaReR " + apiKey1);
            headers.put("cdi-version", "2.21");
            JsonObject sessionInfo = sendJsonRequest(process.getProcess(),
                    "http://localhost:3567/recipe/session", request, 1000, 1000, "POST", headers);
            assertEquals(sessionInfo.get("status").getAsString(), "OK");
            checkSessionResponse(sessionInfo, process, userId, userDataInJWT);
        }

        {
            // check that any one of the keys can be used
            Map<String, String> headers = new HashMap<>();
            headers.put("authorization", "bEaReR " + apiKey1);
            headers.put("cdi-version", "2.21");
            JsonObject sessionInfo = sendJsonRequest(process.getProcess(),
                    "http://localhost:3567/recipe/session", request, 1000, 1000, "POST", headers);
            assertEquals(sessionInfo.get("status").getAsString(), "OK");
            checkSessionResponse(sessionInfo, process, userId, userDataInJWT);
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    private static final int STATUS_CODE_ERROR_THRESHOLD = 400;

    private static URL getURL(Main main, String requestID, String url) throws MalformedURLException {
        URL obj = new URL(url);
        if (Main.isTesting) {
            URL mock = HttpRequestMocking.getInstance(main).getMockURL(requestID, url);
            if (mock != null) {
                obj = mock;
            }
        }
        return obj;
    }

    private static boolean isJsonValid(String jsonInString) {
        JsonElement el = null;
        try {
            el = new JsonParser().parse(jsonInString);
            el.getAsJsonObject();
            return true;
        } catch (Exception ex) {
            try {
                assert el != null;
                el.getAsJsonArray();
                return true;
            } catch (Throwable e) {
                return false;
            }
        }
    }

    private static <T> T sendJsonRequest(Main main, String url, JsonElement requestBody,
                                         int connectionTimeoutMS, int readTimeoutMS, String method,
                                         Map<String, String> headers) throws IOException,
            io.supertokens.test.httpRequest.HttpResponseException {
        URL obj = getURL(main, "", url);
        InputStream inputStream = null;
        HttpURLConnection con = null;
        try {
            con = (HttpURLConnection) obj.openConnection();
            con.setRequestMethod(method);
            con.setConnectTimeout(connectionTimeoutMS);
            con.setReadTimeout(readTimeoutMS + 1000);
            con.setRequestProperty("Content-Type", "application/json; charset=UTF-8");

            if (headers != null) {
                for (Map.Entry<String, String> entry : headers.entrySet()) {
                    con.setRequestProperty(entry.getKey(), entry.getValue());
                }
            }

            if (requestBody != null) {
                con.setDoOutput(true);
                try (OutputStream os = con.getOutputStream()) {
                    byte[] input = requestBody.toString().getBytes(StandardCharsets.UTF_8);
                    os.write(input, 0, input.length);
                }
            }

            int responseCode = con.getResponseCode();

            if (responseCode < STATUS_CODE_ERROR_THRESHOLD) {
                inputStream = con.getInputStream();
            } else {
                inputStream = con.getErrorStream();
            }

            StringBuilder response = new StringBuilder();
            try (BufferedReader in = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
                String inputLine;
                while ((inputLine = in.readLine()) != null) {
                    response.append(inputLine);
                }
            }

            if (responseCode < STATUS_CODE_ERROR_THRESHOLD) {
                if (!isJsonValid(response.toString())) {
                    return (T) response.toString();
                }
                return (T) (new JsonParser().parse(response.toString()));
            }
            throw new io.supertokens.test.httpRequest.HttpResponseException(responseCode, response.toString());
        } finally {
            if (inputStream != null) {
                inputStream.close();
            }

            if (con != null) {
                con.disconnect();
            }
        }
    }

}
