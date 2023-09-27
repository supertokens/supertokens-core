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

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.supertokens.ProcessState;
import io.supertokens.ProcessState.PROCESS_STATE;
import io.supertokens.cronjobs.telemetry.Telemetry;
import io.supertokens.httpRequest.HttpRequestMocking;
import io.supertokens.test.TestingProcessManager.TestingProcess;
import io.supertokens.version.Version;
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

public class TelemetryTest extends Mockito {
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
    public void testThatDisablingTelemetryDoesNotSendOne() throws Exception {
        String[] args = { "../" };

        Utils.setValueInConfig("disable_telemetry", "true");

        TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STARTED));

        assertNull(process.checkOrWaitForEvent(PROCESS_STATE.SENDING_TELEMETRY, 2000));

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STOPPED));
    }

    @Test
    public void testThatTelemetryDoesNotSendOneIfInMemDb() throws Exception {
        String[] args = { "../" };

        TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STARTED));

        if (!Version.getVersion(process.getProcess()).getPluginName().equals("sqlite")) {
            return;
        }

        assertNull(process.checkOrWaitForEvent(PROCESS_STATE.SENDING_TELEMETRY, 2000));

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STOPPED));
    }

    @Test
    public void testThatTelemetryDoesNotSendOneIfInMemDbButActualDBThere() throws Exception {
        String[] args = { "../" };

        TestingProcess process = TestingProcessManager.start(args, false);
        process.getProcess().setForceInMemoryDB();
        process.startProcess();
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STARTED));

        if (Version.getVersion(process.getProcess()).getPluginName().equals("sqlite")) {
            // we are testing with in mem db which is already done above.
            return;
        }

        assertNull(process.checkOrWaitForEvent(PROCESS_STATE.SENDING_TELEMETRY, 2000));

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STOPPED));
    }

    @Test
    public void testThatTelemetryWorks() throws Exception {
        String[] args = { "../" };

        TestingProcess process = TestingProcessManager.start(args, false);

        ByteArrayOutputStream output = new ByteArrayOutputStream();
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

        HttpRequestMocking.getInstance(process.getProcess()).setMockURL(Telemetry.REQUEST_ID,
                new HttpRequestMocking.URLGetter() {

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

        if (Version.getVersion(process.getProcess()).getPluginName().equals("sqlite")) {
            return;
        }

        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.SENT_TELEMETRY));

        JsonObject telemetryData = new JsonParser().parse(output.toString()).getAsJsonObject();

        assertTrue(telemetryData.has("telemetryId"));
        assertEquals(telemetryData.get("superTokensVersion").getAsString(),
                Version.getVersion(process.getProcess()).getCoreVersion());
        assertEquals(telemetryData.get("appId").getAsString(), "public");
        assertEquals(telemetryData.get("connectionUriDomain").getAsString(), "");
        assertTrue(telemetryData.has("mau"));

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STOPPED));
    }

    @Test
    public void testThatTelemetryIdDoesNotChange() throws Exception {
        String telemetryId = null;
        {
            String[] args = { "../" };

            TestingProcess process = TestingProcessManager.start(args, false);

            ByteArrayOutputStream output = new ByteArrayOutputStream();
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

            HttpRequestMocking.getInstance(process.getProcess()).setMockURL(Telemetry.REQUEST_ID,
                    new HttpRequestMocking.URLGetter() {

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

            if (Version.getVersion(process.getProcess()).getPluginName().equals("sqlite")) {
                return;
            }

            assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.SENT_TELEMETRY));

            JsonObject telemetryData = new JsonParser().parse(output.toString()).getAsJsonObject();

            telemetryId = telemetryData.get("telemetryId").getAsString();

            process.kill(false);
            assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STOPPED));
        }

        {
            String[] args = { "../" };

            TestingProcess process = TestingProcessManager.start(args, false);

            ByteArrayOutputStream output = new ByteArrayOutputStream();
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

            HttpRequestMocking.getInstance(process.getProcess()).setMockURL(Telemetry.REQUEST_ID,
                    new HttpRequestMocking.URLGetter() {

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

            if (Version.getVersion(process.getProcess()).getPluginName().equals("sqlite")) {
                return;
            }

            assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.SENT_TELEMETRY));

            JsonObject telemetryData = new JsonParser().parse(output.toString()).getAsJsonObject();

            String thisTelemetryId = telemetryData.get("telemetryId").getAsString();

            assertNotNull(thisTelemetryId);
            assertNotNull(telemetryId);
            assertEquals(thisTelemetryId, telemetryId);

            process.kill();
            assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STOPPED));
        }
    }

    @Test
    public void testThatTelemetryWillNotGoIfTestingAndNoMockRequest() throws Exception {
        String[] args = { "../" };

        TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (Version.getVersion(process.getProcess()).getPluginName().equals("sqlite")) {
            return;
        }

        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.SENDING_TELEMETRY));
        assertNull(process.checkOrWaitForEvent(PROCESS_STATE.SENT_TELEMETRY, 2000));

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STOPPED));
    }

}
