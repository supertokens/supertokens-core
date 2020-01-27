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
import com.google.gson.JsonParser;
import io.supertokens.ProcessState;
import io.supertokens.backendAPI.Ping;
import io.supertokens.cliOptions.CLIOptions;
import io.supertokens.config.Config;
import io.supertokens.cronjobs.CronTaskTest;
import io.supertokens.cronjobs.memoryWatcher.MemoryWatcher;
import io.supertokens.cronjobs.serverPing.ServerPing;
import io.supertokens.httpRequest.HttpRequest;
import io.supertokens.httpRequest.HttpRequestMocking;
import io.supertokens.licenseKey.LicenseKey;
import io.supertokens.version.Version;
import io.supertokens.version.VersionFile;
import io.supertokens.webserver.RPMCalculator;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;
import org.mockito.Mockito;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.*;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.*;


public class ServerPingTest extends Mockito {
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
    public void testMemoryInfoAndRPMField() throws Exception {

        String[] args = {"../", "DEV"};
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args, false);

        final HttpURLConnection mockCon = mock(HttpURLConnection.class);
        InputStream inputStrm = new ByteArrayInputStream("".getBytes(StandardCharsets.UTF_8));
        when(mockCon.getInputStream()).thenReturn(inputStrm);
        when(mockCon.getResponseCode()).thenReturn(200);
        when(mockCon.getOutputStream()).thenReturn(new OutputStream() {
            @Override
            public void write(int b) {
                output.write(b);
            }
        });


        HttpRequestMocking.getInstance(process.getProcess()).setMockURL(
                Ping.REQUEST_ID, new HttpRequestMocking.URLGetter() {

                    @Override
                    public URL getUrl(String url) throws MalformedURLException {
                        URLStreamHandler stubURLStreamHandler = new URLStreamHandler() {
                            @Override
                            protected URLConnection openConnection(URL u) {
                                return mockCon;
                            }
                        };
                        return new URL(null, url, stubURLStreamHandler);
                    }
                });

        RPMCalculator.getInstance(process.getProcess()).RPM_HOUR_DELTA = 4;
        RPMCalculator.getInstance(process.getProcess()).RPM_MIN_DELTA = 0.167;
        MemoryWatcher.getInstance(process.getProcess()).HOUR_DELTA = 4;
        CronTaskTest.getInstance(process.getProcess()).setIntervalInSeconds(MemoryWatcher.RESOURCE_KEY, 1);

        process.startProcess();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        HttpRequest.sendJsonPOSTRequest(process.getProcess(), "", "http://localhost:3567/handshake", new JsonObject(),
                1000, 1000,
                null);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.SERVER_PING));
        int indexOfLastPingData = output.toString().length();

        Thread.sleep(5000);
        Ping.getInstance(process.getProcess()).doPing();

        JsonObject pingData = new JsonParser().parse(output.toString().substring(indexOfLastPingData))
                .getAsJsonObject();

        assertTrue(pingData.get("memoryInfo").getAsJsonArray().get(0).getAsJsonObject().has("time"));
        assertTrue(pingData.get("memoryInfo").getAsJsonArray().get(0).getAsJsonObject().has("totalMemory"));
        assertEquals(3,
                pingData.get("memoryInfo").getAsJsonArray().get(0).getAsJsonObject().get("totalMemory")
                        .getAsJsonObject().entrySet().size());
        assertTrue(pingData.get("memoryInfo").getAsJsonArray().get(0).getAsJsonObject().has("maxMemory"));
        assertEquals(
                pingData.get("memoryInfo").getAsJsonArray().get(0).getAsJsonObject().get("maxMemory").getAsJsonObject()
                        .entrySet().size(),
                3);

        assertTrue(pingData.get("requestsPerMin").getAsJsonArray().get(0).getAsJsonObject().has("value"));
        assertTrue(pingData.get("requestsPerMin").getAsJsonArray().get(0).getAsJsonObject().has("time"));

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));

    }

    @Test
    public void testPingHappensWithRelevantInformation() throws Exception {
        String[] args = {"../", "DEV"};
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args, false);

        final HttpURLConnection mockCon = mock(HttpURLConnection.class);
        InputStream inputStrm = new ByteArrayInputStream("".getBytes(StandardCharsets.UTF_8));
        when(mockCon.getInputStream()).thenReturn(inputStrm);
        when(mockCon.getResponseCode()).thenReturn(200);
        when(mockCon.getOutputStream()).thenReturn(new OutputStream() {
            @Override
            public void write(int b) {
                output.write(b);
            }
        });


        HttpRequestMocking.getInstance(process.getProcess()).setMockURL(
                Ping.REQUEST_ID, new HttpRequestMocking.URLGetter() {

                    @Override
                    public URL getUrl(String url) throws MalformedURLException {
                        URLStreamHandler stubURLStreamHandler = new URLStreamHandler() {
                            @Override
                            protected URLConnection openConnection(URL u) {
                                return mockCon;
                            }
                        };
                        return new URL(null, url, stubURLStreamHandler);
                    }
                });

        MemoryWatcher.getInstance(process.getProcess()).HOUR_DELTA = 4;
        CronTaskTest.getInstance(process.getProcess()).setIntervalInSeconds(MemoryWatcher.RESOURCE_KEY, 1);


        process.startProcess();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.SERVER_PING));
        JsonObject pingData = new JsonParser().parse(output.toString()).getAsJsonObject();

        VersionFile version = Version.getVersion(process.getProcess());

        assertEquals("Server ping instanceID does not match process instanceID",
                pingData.get("instanceId").getAsString(), process.getProcess().getProcessId());
        assertEquals("Server ping cookieDomain does not match process cookieDomain",
                pingData.get("cookieDomain").getAsString(), Config.getConfig(process.getProcess()).getCookieDomain());
        assertEquals("Server ping instanceStartTime does not match process instanceStartTime",
                pingData.get("instanceStartTime").getAsLong(), process.getProcess().getProcessStartTime());
        assertEquals("Server ping licenseKeyId does not match process licenseKeyId",
                pingData.get("licenseKeyId").getAsString(), LicenseKey.get(process.getProcess()).getLicenseKeyId());
        assertEquals("Server ping currentlyRunningPlanType does not match process plan type",
                pingData.get("currentlyRunningPlanType").getAsString(),
                LicenseKey.get(process.getProcess()).getPlanType().toString());
        assertEquals("Server ping plugin version does not match process plugin version",
                pingData.get("plugin").getAsJsonObject().get("name").getAsString(), version.getPluginName());
        assertEquals("Server ping ",
                pingData.get("plugin").getAsJsonObject().get("version").getAsString(), version.getPluginVersion());
        assertEquals("Server ping coreVersion does not match process coreVersion",
                pingData.get("coreVersion").getAsString(), version.getCoreVersion());
        assertEquals("Server ping pluginInterfaceVersion does not match process pluginInterfaceVersion",
                pingData.get("pluginInterfaceVersion").getAsString(), version.getPluginInterfaceVersion());
        assertTrue(pingData.has("memoryInfo"));
        assertTrue(pingData.has("requestsPerMin"));
        assertEquals("Server ping userDevProductionMode does not match process userDevProductionMode",
                pingData.get("userDevProductionMode").getAsString(),
                CLIOptions.get(process.getProcess()).getUserDevProductionMode());
        assertFalse("Server ping", pingData.get("hasMovedFromCommercialBinaryToFreeBinary").getAsBoolean());
        assertEquals("Server ping frontendSDK does not match process frontendSDK", 0,
                pingData.get("frontendSDK").getAsJsonArray().size());
        assertEquals(pingData.entrySet().size(), 13);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testIntervalAndStartTime() throws Exception {
        String[] args = {"../", "DEV"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        assertEquals(ServerPing.getInstance(process.getProcess()).getIntervalTimeSeconds(), 24 * 3600);
        assertEquals(ServerPing.getInstance(process.getProcess()).getInitialWaitTimeSeconds(), 0);
        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));

    }


    @Test
    public void testDeviceDriverInfoInServerPing() throws Exception {
        String[] args = {"../", "DEV"};

        ByteArrayOutputStream output = new ByteArrayOutputStream();

        String userId = "userId";
        JsonObject userDataInJWT = new JsonObject();
        userDataInJWT.addProperty("key", "value");
        JsonObject userDataInDatabase = new JsonObject();
        userDataInDatabase.addProperty("key", "value");

        JsonObject frontendSDKEntry = new JsonObject();
        frontendSDKEntry.addProperty("name", "testName");
        frontendSDKEntry.addProperty("version", "testVersion");

        JsonArray frontendSDK = new JsonArray();
        frontendSDK.add(frontendSDKEntry);

        JsonObject driver = new JsonObject();
        driver.addProperty("name", "testName");
        driver.addProperty("version", "testVersion");


        JsonObject deviceDriverInfo = new JsonObject();
        deviceDriverInfo.add("frontendSDK", frontendSDK);
        deviceDriverInfo.add("driver", driver);

        JsonObject request = new JsonObject();
        request.addProperty("userId", userId);
        request.add("userDataInJWT", userDataInJWT);
        request.add("userDataInDatabase", userDataInDatabase);
        request.add("deviceDriverInfo", deviceDriverInfo);

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args, false);

        final HttpURLConnection mockCon = mock(HttpURLConnection.class);
        InputStream inputStrm = new ByteArrayInputStream("".getBytes(StandardCharsets.UTF_8));
        when(mockCon.getInputStream()).thenReturn(inputStrm);
        when(mockCon.getResponseCode()).thenReturn(200);
        when(mockCon.getOutputStream()).thenReturn(new OutputStream() {
            @Override
            public void write(int b) {
                output.write(b);
            }
        });


        HttpRequestMocking.getInstance(process.getProcess()).setMockURL(
                Ping.REQUEST_ID, new HttpRequestMocking.URLGetter() {

                    @Override
                    public URL getUrl(String url) throws MalformedURLException {
                        URLStreamHandler stubURLStreamHandler = new URLStreamHandler() {
                            @Override
                            protected URLConnection openConnection(URL u) {
                                return mockCon;
                            }
                        };
                        return new URL(null, url, stubURLStreamHandler);
                    }
                });


        process.startProcess();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        HttpRequest.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/session", request, 1000, 1000, null);

        int indexOfLastPingData = output.toString().length();
        Ping.getInstance(process.getProcess()).doPing();

        JsonObject pingData = new JsonParser().parse(output.toString().substring(indexOfLastPingData))
                .getAsJsonObject();

        assertTrue(pingData.get("frontendSDK").getAsJsonArray().contains(frontendSDKEntry));

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));

    }

}
