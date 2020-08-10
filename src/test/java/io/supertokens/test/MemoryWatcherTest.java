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
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.supertokens.ProcessState;
import io.supertokens.backendAPI.Ping;
import io.supertokens.cronjobs.CronTaskTest;
import io.supertokens.cronjobs.memoryWatcher.MemoryWatcher;
import io.supertokens.httpRequest.HttpRequestMocking;
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

public class MemoryWatcherTest extends Mockito {
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

    //tests whether memInfo has the proper fields
    @Test
    public void testNormalMemoryWatcherWorking() throws Exception {
        String[] args = {"../"};
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
        int indexOfLastPingData = output.toString().length();

        Thread.sleep(5000);
        Ping.getInstance(process.getProcess()).doPing();

        JsonObject pingData = new JsonParser().parse(output.toString().substring(indexOfLastPingData))
                .getAsJsonObject();

        JsonObject memoryInfo = pingData.getAsJsonArray("memoryInfo").get(0).getAsJsonObject();

        assertTrue(memoryInfo.has("time"));
        assertTrue(memoryInfo.get("totalMemory").getAsJsonObject().has("min"));
        assertTrue(memoryInfo.get("totalMemory").getAsJsonObject().has("max"));
        assertTrue(memoryInfo.get("totalMemory").getAsJsonObject().has("avg"));

        assertEquals(memoryInfo.get("totalMemory").getAsJsonObject().entrySet().size(), 3);

        assertTrue(memoryInfo.get("maxMemory").getAsJsonObject().has("min"));
        assertTrue(memoryInfo.get("maxMemory").getAsJsonObject().has("max"));
        assertTrue(memoryInfo.get("maxMemory").getAsJsonObject().has("avg"));

        assertEquals(memoryInfo.get("maxMemory").getAsJsonObject().entrySet().size(), 3);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testMemoryWatcherStartAndInterval() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));
        MemoryWatcher memoryWatcher = MemoryWatcher.getInstance(process.getProcess());

        assertEquals(memoryWatcher.getInitialWaitTimeSeconds(), 0);
        assertEquals(memoryWatcher.getIntervalTimeSeconds(), 60);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    //tests that memInfo fields contain correct values
    @Test
    public void testMemoryWatcherHourDeltaWorkingCorrectly() throws Exception {
        String[] args = {"../"};
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


        MemoryWatcher.getInstance(process.getProcess()).HOUR_DELTA = 1;
        CronTaskTest.getInstance(process.getProcess()).setIntervalInSeconds(MemoryWatcher.RESOURCE_KEY, 1);

        process.startProcess();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.SERVER_PING));
        int indexOfLastPingData = output.toString().length();

        Thread.sleep(5000);
        Ping.getInstance(process.getProcess()).doPing();

        JsonObject pingData = new JsonParser().parse(output.toString().substring(indexOfLastPingData))
                .getAsJsonObject();

        JsonArray memoryInfo = pingData.getAsJsonArray("memoryInfo");

        assertTrue(memoryInfo.size() >= 3 && memoryInfo.size() <= 8);
        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }
}
