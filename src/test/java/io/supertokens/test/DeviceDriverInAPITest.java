/*
 *    Copyright (c) 2020, VRAI Labs and/or its affiliates. All rights reserved.
 *
 *    This program is licensed under the SuperTokens Community License (the
 *    "License") as published by VRAI Labs. You may not use this file except in
 *    compliance with the License. You are not permitted to transfer or
 *    redistribute this file without express written permission from VRAI Labs.
 *
 *    A copy of the License is available in the file titled
 *    "SuperTokensLicense.pdf" inside this repository or included with your copy of
 *    the software or its source code. If you have not received a copy of the
 *    License, please write to VRAI Labs at team@supertokens.io.
 *
 *    Please read the License carefully before accessing, downloading, copying,
 *    using, modifying, merging, transferring or sharing this software. By
 *    undertaking any of these activities, you indicate your agreement to the terms
 *    of the License.
 *
 *    This program is distributed with certain software that is licensed under
 *    separate terms, as designated in a particular file or component or in
 *    included license documentation. VRAI Labs hereby grants you an additional
 *    permission to link the program and your derivative works with the separately
 *    licensed software that they have included with this program, however if you
 *    modify this program, you shall be solely liable to ensure compliance of the
 *    modified program with the terms of licensing of the separately licensed
 *    software.
 *
 *    Unless required by applicable law or agreed to in writing, this program is
 *    distributed under the License on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 *    CONDITIONS OF ANY KIND, either express or implied. See the License for the
 *    specific language governing permissions and limitations under the License.
 *
 */

package io.supertokens.test;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.supertokens.ProcessState;
import io.supertokens.backendAPI.Ping;
import io.supertokens.httpRequest.HttpRequest;
import io.supertokens.httpRequest.HttpResponseException;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static org.junit.Assert.*;


public class DeviceDriverInAPITest {
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
    public void sessionPostDeviceDriverInfoTest() throws Exception {
        String[] args = {"../"};

        String userId = "userId";
        JsonObject userDataInJWT = new JsonObject();
        userDataInJWT.addProperty("key", "value");

        JsonObject userDataInDatabase = new JsonObject();
        userDataInDatabase.addProperty("key", "value");

        JsonObject frontendSDKEntry = new JsonObject();
        frontendSDKEntry.addProperty("name", "frontendSDK_test_name");
        frontendSDKEntry.addProperty("version", "frontendSDK_test_version");
        JsonArray frontendSDK = new JsonArray();
        frontendSDK.add(frontendSDKEntry);

        JsonObject driver = new JsonObject();
        driver.addProperty("name", "driver_test_name");
        driver.addProperty("version", "driver_test_version");

        JsonObject deviceDriverInfo = new JsonObject();

        JsonObject request = new JsonObject();
        request.addProperty("userId", userId);
        request.add("userDataInJWT", userDataInJWT);
        request.add("userDataInDatabase", userDataInDatabase);
        request.add("deviceDriverInfo", deviceDriverInfo);

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));
        try {
            //sessionPost deviceDriverInfo, both frontendSDK and driver
            request.get("deviceDriverInfo").getAsJsonObject().add("frontendSDK", frontendSDK);
            request.get("deviceDriverInfo").getAsJsonObject().add("driver", driver);
            JsonObject sessionResponse = HttpRequest
                    .sendJsonPOSTRequest(process.getProcess(), "", "http://localhost:3567/session", request,
                            1000,
                            1000, null);

            assertEquals(sessionResponse.get("status").getAsString(), "OK");

            assertTrue(compareLists(Objects.requireNonNull(frontendSDKList(request)),
                    Ping.getInstance(process.getProcess()).frontendSDK) &&
                    compareLists(Objects.requireNonNull(driverList(request)),
                            Ping.getInstance(process.getProcess()).driver));
        } finally {
            process.kill();
            assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
        }

        //sessionPost frontendSDK only
        process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));
        request = new JsonObject();
        try {

            request.addProperty("userId", userId);
            request.add("userDataInJWT", userDataInJWT);
            request.add("userDataInDatabase", userDataInDatabase);

            deviceDriverInfo = new JsonObject();
            deviceDriverInfo.add("frontendSDK", frontendSDK);
            request.add("deviceDriverInfo", deviceDriverInfo);

            JsonObject sessionResponse = HttpRequest
                    .sendJsonPOSTRequest(process.getProcess(), "", "http://localhost:3567/session", request,
                            1000,
                            1000, null);
            assertEquals(sessionResponse.get("status").getAsString(), "OK");

            assertTrue(compareLists(Objects.requireNonNull(frontendSDKList(request)),
                    Ping.getInstance(process.getProcess()).frontendSDK));

            assertEquals(0, Ping.getInstance(process.getProcess()).driver.size());
        } finally {
            process.kill();
            assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
        }


        //sessionPost driver only
        process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));
        try {

            request = new JsonObject();
            request.addProperty("userId", userId);
            request.add("userDataInJWT", userDataInJWT);
            request.add("userDataInDatabase", userDataInDatabase);

            deviceDriverInfo = new JsonObject();
            deviceDriverInfo.add("driver", driver);
            request.add("deviceDriverInfo", deviceDriverInfo);


            JsonObject sessionResponse = HttpRequest
                    .sendJsonPOSTRequest(process.getProcess(), "", "http://localhost:3567/session",
                            request,
                            1000,
                            1000, null);
            assertEquals(sessionResponse.get("status").getAsString(), "OK");

            assertTrue(compareLists(Objects.requireNonNull(driverList(request)),
                    Ping.getInstance(process.getProcess()).driver));

            assertEquals(Ping.getInstance(process.getProcess()).frontendSDK.size(), 0);

        } finally {
            process.kill();
            assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
        }

        // multiple drivers sent
        process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));
        try {
            request = new JsonObject();
            request.addProperty("userId", userId);
            request.add("userDataInJWT", userDataInJWT);
            request.add("userDataInDatabase", userDataInDatabase);
            deviceDriverInfo = new JsonObject();
            request.add("deviceDriverInfo", deviceDriverInfo);
            request.get("deviceDriverInfo").getAsJsonObject().add("driver", driver);

            JsonObject response = HttpRequest
                    .sendJsonPOSTRequest(process.getProcess(), "", "http://localhost:3567/session",
                            request,
                            1000,
                            1000, null);
            assertEquals(response.get("status").getAsString(), "OK");

            JsonObject driver2 = new JsonObject();
            driver2.addProperty("name", "driver2Name");
            driver2.addProperty("version", "driver2Version");

            request = new JsonObject();
            request.addProperty("userId", userId);
            request.add("userDataInJWT", userDataInJWT);
            request.add("userDataInDatabase", userDataInDatabase);
            request.add("deviceDriverInfo", deviceDriverInfo);
            request.get("deviceDriverInfo").getAsJsonObject().add("driver", driver2);

            response = HttpRequest
                    .sendJsonPOSTRequest(process.getProcess(), "", "http://localhost:3567/session",
                            request,
                            1000,
                            1000, null);
            assertEquals(response.get("status").getAsString(), "OK");

            assertEquals(Ping.getInstance(process.getProcess()).driver.size(), 2);
            List<Ping.NameVersion> nameVersionInRequest = driverList(request);
            assert nameVersionInRequest != null;
            nameVersionInRequest.add(new Ping.NameVersion("driver_test_name", "driver_test_version"));
            assertTrue(compareLists(nameVersionInRequest,
                    Ping.getInstance(process.getProcess()).driver));

        } finally {
            process.kill();
            assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
        }


        //check empty JsonArray input for frontendSDK does not append to frontendSDK list
        process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        try {
            request = new JsonObject();
            request.addProperty("userId", userId);
            request.add("userDataInJWT", userDataInJWT);
            request.add("userDataInDatabase", userDataInDatabase);
            deviceDriverInfo = new JsonObject();
            request.add("deviceDriverInfo", deviceDriverInfo);

            JsonArray frontendSDKEmpty = new JsonArray();
            request.get("deviceDriverInfo").getAsJsonObject().add("frontendSDK", frontendSDKEmpty);

            HttpRequest
                    .sendJsonPOSTRequest(process.getProcess(), "", "http://localhost:3567/session", request, 1000, 1000,
                            null);
            assertEquals(Ping.getInstance(process.getProcess()).frontendSDK.size(), 0);
            assertEquals(Ping.getInstance(process.getProcess()).driver.size(), 0);

        } finally {
            process.kill();
            assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
        }

    }

    @Test
    public void badInputTest() throws Exception {
        String[] args = {"../"};

        String userId = "userId";
        JsonObject userDataInJWT = new JsonObject();
        userDataInJWT.addProperty("key", "value");

        JsonObject userDataInDatabase = new JsonObject();
        userDataInDatabase.addProperty("key", "value");

        JsonObject frontendSDKEntry = new JsonObject();
        frontendSDKEntry.addProperty("name", "frontendSDK_test_name");
        frontendSDKEntry.addProperty("version", "frontendSDK_test_version");
        JsonArray frontendSDK = new JsonArray();
        frontendSDK.add(frontendSDKEntry);

        JsonObject driver = new JsonObject();
        driver.addProperty("name", "driver_test_name");
        driver.addProperty("version", "driver_test_version");

        JsonObject deviceDriverInfo = new JsonObject();
        deviceDriverInfo.add("frontendSDK", frontendSDK);
        deviceDriverInfo.add("driver", driver);

        JsonObject request = new JsonObject();


        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        //typo in DeviceDriverInfo
        try {
            request.addProperty("userId", userId);
            request.add("userDataInJWT", userDataInJWT);
            request.add("userDataInDatabase", userDataInDatabase);
            request.add("deviceDrivInfo", deviceDriverInfo);

            JsonObject response = HttpRequest
                    .sendJsonPOSTRequest(process.getProcess(), "", "http://localhost:3567/session", request, 1000, 1000,
                            null);
            assertEquals(response.get("status").getAsString(), "OK");

            assertEquals(Ping.getInstance(process.getProcess()).frontendSDK.size(), 0);
            assertEquals(Ping.getInstance(process.getProcess()).driver.size(), 0);
        } finally {
            process.kill();
            assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
        }

        //typo in userId field
        process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));
        try {
            request = new JsonObject();
            request.addProperty("useId", userId);
            request.add("userDataInJWT", userDataInJWT);
            request.add("userDataInDatabase", userDataInDatabase);
            request.add("deviceDriverInfo", deviceDriverInfo);

            try {
                JsonObject response = HttpRequest
                        .sendJsonPOSTRequest(process.getProcess(), "", "http://localhost:3567/session", request, 1000,
                                1000,
                                null);
                fail();
            } catch (HttpResponseException e) {
                assertEquals(e.statusCode, 400);
                assertEquals(e.getMessage(),
                        "Http error. Status Code: 400. Message: Field name 'userId' is invalid in JSON input");
                assertEquals(Ping.getInstance(process.getProcess()).frontendSDK.size(), 0);
                assertEquals(Ping.getInstance(process.getProcess()).driver.size(), 0);

            }

        } finally {
            process.kill();
            assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
        }

    }

    @Test
    public void sessionVerifyPostDeviceDriverInfoTest() throws Exception {
        String[] args = {"../"};

        String userId = "userId";
        JsonObject userDataInJWT = new JsonObject();
        userDataInJWT.addProperty("key", "value");

        JsonObject userDataInDatabase = new JsonObject();
        userDataInDatabase.addProperty("key", "value");

        JsonObject frontendSDKEntry = new JsonObject();
        frontendSDKEntry.addProperty("name", "frontendSDK_test_name");
        frontendSDKEntry.addProperty("version", "frontendSDK_test_version");
        JsonArray frontendSDK = new JsonArray();
        frontendSDK.add(frontendSDKEntry);

        JsonObject driver = new JsonObject();
        driver.addProperty("name", "driver_test_name");
        driver.addProperty("version", "driver_test_version");

        JsonObject deviceDriverInfo = new JsonObject();
        deviceDriverInfo.add("frontendSDK", frontendSDK);
        deviceDriverInfo.add("driver", driver);

        JsonObject createSession = new JsonObject();
        createSession.addProperty("userId", userId);
        createSession.add("userDataInJWT", userDataInJWT);
        createSession.add("userDataInDatabase", userDataInDatabase);

        JsonObject request = new JsonObject();
        request.addProperty("doAntiCsrfCheck", false);
        request.add("deviceDriverInfo", deviceDriverInfo);

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        try {
            //deviceDriverInfo; both frontendSDK and driver
            JsonObject sessionResponse = HttpRequest
                    .sendJsonPOSTRequest(process.getProcess(), "", "http://localhost:3567/session",
                            createSession,
                            1000,
                            1000, null);
            assertEquals(sessionResponse.get("status").getAsString(), "OK");

            request.add("accessToken", sessionResponse.get("accessToken").getAsJsonObject().get("token"));

            JsonObject sessionVerifyResponse = HttpRequest
                    .sendJsonPOSTRequest(process.getProcess(), "", "http://localhost:3567/session/verify",
                            request,
                            1000,
                            1000, null);

            assertEquals(sessionVerifyResponse.get("status").getAsString(), "OK");
            assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.DEVICE_DRIVER_INFO_SAVED));

        } finally {
            process.kill();
            assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));

        }
    }

    @Test
    public void badInputSessionVerifyTest() throws Exception {
        String[] args = {"../"};
        String userId = "userId";
        JsonObject userDataInJWT = new JsonObject();
        userDataInJWT.addProperty("key", "value");

        JsonObject userDataInDatabase = new JsonObject();
        userDataInDatabase.addProperty("key", "value");

        JsonObject frontendSDKEntry = new JsonObject();
        frontendSDKEntry.addProperty("name", "frontendSDK_test_name");
        frontendSDKEntry.addProperty("version", "frontendSDK_test_version");
        JsonArray frontendSDK = new JsonArray();
        frontendSDK.add(frontendSDKEntry);

        JsonObject driver = new JsonObject();
        driver.addProperty("name", "driver_test_name");
        driver.addProperty("version", "driver_test_version");

        JsonObject deviceDriverInfo = new JsonObject();
        deviceDriverInfo.add("frontendSDK", frontendSDK);
        deviceDriverInfo.add("driver", driver);

        JsonObject createSession = new JsonObject();
        createSession.addProperty("userId", userId);
        createSession.add("userDataInJWT", userDataInJWT);
        createSession.add("userDataInDatabase", userDataInDatabase);

        JsonObject request = new JsonObject();
        request.addProperty("doAntiCsrfCheck", false);
        request.add("deviceDriverInfo", deviceDriverInfo);

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        try {
            //typo in accessToken
            JsonObject sessionResponse = HttpRequest
                    .sendJsonPOSTRequest(process.getProcess(), "", "http://localhost:3567/session",
                            createSession,
                            1000,
                            1000, null);
            assertEquals(sessionResponse.get("status").getAsString(), "OK");

            request.add("acceToken", sessionResponse.get("accessToken").getAsJsonObject().get("token"));

            try {

                HttpRequest
                        .sendJsonPOSTRequest(process.getProcess(), "", "http://localhost:3567/session/verify",
                                request,
                                1000,
                                1000, null);
                fail();
            } catch (HttpResponseException e) {
                assertEquals(e.getMessage(),
                        "Http error. Status Code: 400. Message: Field name 'accessToken' is invalid in JSON input");
                assertEquals(Ping.getInstance(process.getProcess()).frontendSDK.size(), 0);
                assertEquals(Ping.getInstance(process.getProcess()).driver.size(), 0);
            }

        } finally {
            process.kill();
            assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
        }

        //typo in deviceDriverInfo field
        process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        try {
            JsonObject sessionResponse = HttpRequest
                    .sendJsonPOSTRequest(process.getProcess(), "", "http://localhost:3567/session",
                            createSession,
                            1000,
                            1000, null);
            assertEquals(sessionResponse.get("status").getAsString(), "OK");

            request = new JsonObject();
            request.add("accessToken", sessionResponse.get("accessToken").getAsJsonObject().get("token"));
            request.add("deviceDrivInfo", deviceDriverInfo);
            request.addProperty("doAntiCsrfCheck", false);


            JsonObject sessionVerifyResponse = HttpRequest
                    .sendJsonPOSTRequest(process.getProcess(), "", "http://localhost:3567/session/verify",
                            request,
                            1000,
                            1000, null);
            assertEquals(sessionVerifyResponse.get("status").getAsString(), "OK");

            assertEquals(Ping.getInstance(process.getProcess()).frontendSDK.size(), 0);
            assertEquals(Ping.getInstance(process.getProcess()).driver.size(), 0);

        } finally {
            process.kill();
            assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
        }

    }

    @Test
    public void sessionRefreshPostDeviceDriverInfoTest() throws Exception {
        String[] args = {"../"};

        String userId = "userId";
        JsonObject userDataInJWT = new JsonObject();
        userDataInJWT.addProperty("key", "value");

        JsonObject userDataInDatabase = new JsonObject();
        userDataInDatabase.addProperty("key", "value");

        JsonObject frontendSDKEntry = new JsonObject();
        frontendSDKEntry.addProperty("name", "frontendSDK_test_name");
        frontendSDKEntry.addProperty("version", "frontendSDK_test_version");
        JsonArray frontendSDK = new JsonArray();
        frontendSDK.add(frontendSDKEntry);

        JsonObject driver = new JsonObject();
        driver.addProperty("name", "driver_test_name");
        driver.addProperty("version", "driver_test_version");

        JsonObject deviceDriverInfo = new JsonObject();
        deviceDriverInfo.add("frontendSDK", frontendSDK);
        deviceDriverInfo.add("driver", driver);

        JsonObject createSession = new JsonObject();
        createSession.addProperty("userId", userId);
        createSession.add("userDataInJWT", userDataInJWT);
        createSession.add("userDataInDatabase", userDataInDatabase);

        JsonObject request = new JsonObject();
        request.add("deviceDriverInfo", deviceDriverInfo);

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        try {
            //deviceDriverInfo; both frontendSDK and driver
            JsonObject createSessionResponse = HttpRequest
                    .sendJsonPOSTRequest(process.getProcess(), "", "http://localhost:3567/session",
                            createSession, 1000, 1000, null);
            assertEquals(createSessionResponse.get("status").getAsString(), "OK");

            request.add("refreshToken", createSessionResponse.get("refreshToken").getAsJsonObject().get
                    ("token"));

            JsonObject sessionRefreshResponse = HttpRequest
                    .sendJsonPOSTRequest(process.getProcess(), "", "http://localhost:3567/session/refresh",
                            request,
                            1000,
                            1000, null);
            assertEquals(sessionRefreshResponse.get("status").getAsString(),
                    "OK");
            assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.DEVICE_DRIVER_INFO_SAVED));

        } finally {

            process.kill();
            assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));

        }

    }

    @Test
    public void badInputSessionRefreshTest() throws Exception {
        String[] args = {"../"};

        String userId = "userId";
        JsonObject userDataInJWT = new JsonObject();
        userDataInJWT.addProperty("key", "value");

        JsonObject userDataInDatabase = new JsonObject();
        userDataInDatabase.addProperty("key", "value");

        JsonObject frontendSDKEntry = new JsonObject();
        frontendSDKEntry.addProperty("name", "frontendSDK_test_name");
        frontendSDKEntry.addProperty("version", "frontendSDK_test_version");
        JsonArray frontendSDK = new JsonArray();
        frontendSDK.add(frontendSDKEntry);

        JsonObject driver = new JsonObject();
        driver.addProperty("name", "driver_test_name");
        driver.addProperty("version", "driver_test_version");

        JsonObject deviceDriverInfo = new JsonObject();
        deviceDriverInfo.add("frontendSDK", frontendSDK);
        deviceDriverInfo.add("driver", driver);

        JsonObject createSession = new JsonObject();
        createSession.addProperty("userId", userId);
        createSession.add("userDataInJWT", userDataInJWT);
        createSession.add("userDataInDatabase", userDataInDatabase);

        JsonObject request = new JsonObject();
        request.add("devicDrivInfo", deviceDriverInfo);

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        try {
            //typo in deviceDriverInfo field
            JsonObject createSessionResponse = HttpRequest
                    .sendJsonPOSTRequest(process.getProcess(), "", "http://localhost:3567/session",
                            createSession, 1000, 1000, null);
            assertEquals(createSessionResponse.get("status").getAsString(), "OK");

            request.add("refreshToken", createSessionResponse.get("refreshToken").getAsJsonObject().get
                    ("token"));

            JsonObject refreshTokenResponse = HttpRequest
                    .sendJsonPOSTRequest(process.getProcess(), "", "http://localhost:3567/session/refresh",
                            request,
                            1000,
                            1000, null);
            assertNotNull(refreshTokenResponse.get("status").getAsString(), "OK");

            assertEquals(Ping.getInstance(process.getProcess()).frontendSDK.size(), 0);
            assertEquals(Ping.getInstance(process.getProcess()).driver.size(), 0);


        } finally {

            process.kill();
            assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));

        }

        process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        try {
            //typo in refreshToken field
            JsonObject createSessionResponse = HttpRequest
                    .sendJsonPOSTRequest(process.getProcess(), "", "http://localhost:3567/session",
                            createSession, 1000, 1000, null);
            assertEquals(createSessionResponse.get("status").getAsString(), "OK");

            request = new JsonObject();
            request.add("deviceDriverInfo", deviceDriverInfo);
            request.add("refrToken", createSessionResponse.get("refreshToken").getAsJsonObject().get
                    ("token"));

            try {
                HttpRequest
                        .sendJsonPOSTRequest(process.getProcess(), "", "http://localhost:3567/session/refresh",
                                request,
                                1000,
                                1000, null);
            } catch (HttpResponseException e) {
                assertEquals(e.getMessage(),
                        "Http error. Status Code: 400. Message: Field name 'refreshToken' is invalid in JSON input");
                assertEquals(Ping.getInstance(process.getProcess()).frontendSDK.size(), 0);
                assertEquals(Ping.getInstance(process.getProcess()).driver.size(), 0);

            }

        } finally {
            process.kill();
            assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
        }

    }

    @Test
    public void handshakePostDeviceDriverInfoTest() throws Exception {
        String[] args = {"../"};


        JsonObject frontendSDKEntry = new JsonObject();
        frontendSDKEntry.addProperty("name", "frontendSDK_test_name");
        frontendSDKEntry.addProperty("version", "frontendSDK_test_version");
        JsonArray frontendSDK = new JsonArray();
        frontendSDK.add(frontendSDKEntry);

        JsonObject driver = new JsonObject();
        driver.addProperty("name", "driver_test_name");
        driver.addProperty("version", "driver_test_version");

        JsonObject deviceDriverInfo = new JsonObject();
        deviceDriverInfo.add("frontendSDK", frontendSDK);
        deviceDriverInfo.add("driver", driver);


        JsonObject request = new JsonObject();
        request.add("deviceDriverInfo", deviceDriverInfo);

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));
        try {
            //deviceDriverInfo; both frontendSDK and driver

            JsonObject handshakeResponse = HttpRequest
                    .sendJsonPOSTRequest(process.getProcess(), "", "http://localhost:3567/handshake",
                            request,
                            1000,
                            1000, null);
            assertEquals(handshakeResponse.get("status").getAsString(),
                    "OK");

            assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.DEVICE_DRIVER_INFO_SAVED));

        } finally {
            process.kill();
            assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
        }

    }

    @Test
    public void badInputHandshakeTest() throws Exception {
        String[] args = {"../"};


        JsonObject frontendSDKEntry = new JsonObject();
        frontendSDKEntry.addProperty("name", "frontendSDK_test_name");
        frontendSDKEntry.addProperty("version", "frontendSDK_test_version");
        JsonArray frontendSDK = new JsonArray();
        frontendSDK.add(frontendSDKEntry);

        JsonObject driver = new JsonObject();
        driver.addProperty("name", "driver_test_name");
        driver.addProperty("version", "driver_test_version");

        JsonObject deviceDriverInfo = new JsonObject();
        deviceDriverInfo.add("frontendSDK", frontendSDK);
        deviceDriverInfo.add("driver", driver);


        JsonObject request = new JsonObject();
        request.add("deviceDrivInfo", deviceDriverInfo);

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        try {
            //typo in deviceDriverInfo

            JsonObject handshakeResponse = HttpRequest
                    .sendJsonPOSTRequest(process.getProcess(), "", "http://localhost:3567/handshake",
                            request,
                            1000,
                            1000, null);
            assertEquals(handshakeResponse.get("status").getAsString(),
                    "OK");

            assertEquals(Ping.getInstance(process.getProcess()).frontendSDK.size(), 0);
            assertEquals(Ping.getInstance(process.getProcess()).driver.size(), 0);

        } finally {
            process.kill();
            assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
        }

    }

    private static List<Ping.NameVersion> frontendSDKList(JsonObject element) {
        if (!element.has("deviceDriverInfo")) {
            return null;
        }
        List<Ping.NameVersion> frontendSDK = new ArrayList<>();
        JsonObject deviceDriverInfo = element.getAsJsonObject("deviceDriverInfo");
        if (deviceDriverInfo.has("frontendSDK")) {
            JsonArray info = deviceDriverInfo.getAsJsonArray("frontendSDK");
            info.forEach(jsonElement -> {
                JsonObject obj = jsonElement.getAsJsonObject();
                Ping.NameVersion nv = new Ping.NameVersion(obj.get("name").getAsString(),
                        obj.get("version").getAsString());
                if (!frontendSDK.contains(nv)) {
                    frontendSDK.add(nv);
                }
            });
        }
        return frontendSDK;
    }

    private static List<Ping.NameVersion> driverList(JsonObject element) {
        if (!element.has("deviceDriverInfo")) {
            return null;
        }
        List<Ping.NameVersion> driver = new ArrayList<>();
        JsonObject deviceDriverInfo = element.getAsJsonObject("deviceDriverInfo");
        if (deviceDriverInfo.has("driver")) {
            JsonObject obj = deviceDriverInfo.getAsJsonObject("driver");
            Ping.NameVersion nv = new Ping.NameVersion(obj.get("name").getAsString(),
                    obj.get("version").getAsString());
            driver.add(nv);
        }
        return driver;
    }

    private static boolean compareLists(List list1, List list2) {
        if (list1.size() == list2.size()) {
            for (Object val : list1) {
                if (!list2.contains(val)) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }


}
