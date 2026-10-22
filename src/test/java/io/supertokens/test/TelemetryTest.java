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
import io.supertokens.authRecipe.ApproximateUserCount;
import io.supertokens.cronjobs.telemetry.Telemetry;
import io.supertokens.dashboard.Dashboard;
import io.supertokens.emailpassword.EmailPassword;
import io.supertokens.featureflag.EE_FEATURES;
import io.supertokens.featureflag.FeatureFlagTestContent;
import io.supertokens.httpRequest.HttpRequestMocking;
import io.supertokens.multitenancy.Multitenancy;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.pluginInterface.authRecipe.AuthRecipeUserInfo;
import io.supertokens.pluginInterface.multitenancy.*;
import io.supertokens.storageLayer.StorageLayer;
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
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.*;

public class TelemetryTest extends Mockito {
    @Rule
    public TestRule watchman = Utils.getOnFailure();

    @Rule
    public TestRule retryFlaky = Utils.retryFlakyTest();

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
        String[] args = {"../"};

        Utils.setValueInConfig("disable_telemetry", "true");

        TestingProcess process = TestingProcessManager.startIsolatedProcess(args);
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STARTED));

        assertNull(process.checkOrWaitForEvent(PROCESS_STATE.SENDING_TELEMETRY, 2000));

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STOPPED));
    }

    @Test
    public void testThatTelemetryDoesNotSendOneIfInMemDb() throws Exception {
        String[] args = {"../"};

        TestingProcess process = TestingProcessManager.startIsolatedProcess(args);
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
        String[] args = {"../"};

        TestingProcess process = TestingProcessManager.startIsolatedProcess(args, false);
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
        String[] args = {"../"};

        TestingProcess process = TestingProcessManager.startIsolatedProcess(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getBaseStorage(process.getProcess()).getType() == STORAGE_TYPE.SQL) {
            Dashboard.signUpDashboardUser(process.getProcess(), "test@example.com", "password123");
        }

        // Restarting the process to send telemetry again
        process.kill(false);
        process = TestingProcessManager.startIsolatedProcess(args, false);

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
        assertEquals(7, telemetryData.entrySet().size());

        assertTrue(telemetryData.has("telemetryId"));
        assertEquals(telemetryData.get("superTokensVersion").getAsString(),
                Version.getVersion(process.getProcess()).getCoreVersion());
        assertEquals(telemetryData.get("appId").getAsString(), "public");
        assertEquals(telemetryData.get("connectionUriDomain").getAsString(), "");
        assertTrue(telemetryData.has("maus"));
        assertTrue(telemetryData.has("dashboardUserEmails"));

        if (StorageLayer.getBaseStorage(process.getProcess()).getType() == STORAGE_TYPE.SQL) {
            assertEquals(1, telemetryData.get("dashboardUserEmails").getAsJsonArray().size());
            assertEquals("test@example.com",
                    telemetryData.get("dashboardUserEmails").getAsJsonArray().get(0).getAsString());
            assertEquals(31, telemetryData.get("maus").getAsJsonArray().size());
            assertEquals(0, telemetryData.get("usersCount").getAsInt());
        } else {
            assertEquals(0, telemetryData.get("dashboardUserEmails").getAsJsonArray().size());
            assertEquals(0, telemetryData.get("maus").getAsJsonArray().size());
            assertEquals(-1, telemetryData.get("usersCount").getAsInt());
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STOPPED));
    }

    @Test
    public void testThatTelemetryWorksWithApiDomainAndWebsiteDomainSet() throws Exception {
        String[] args = {"../"};

        TestingProcess process = TestingProcessManager.startIsolatedProcess(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getBaseStorage(process.getProcess()).getType() == STORAGE_TYPE.SQL) {
            Dashboard.signUpDashboardUser(process.getProcess(), "test@example.com", "password123");
        }

        Multitenancy.saveWebsiteAndAPIDomainForApp(StorageLayer.getBaseStorage(process.getProcess()),
                new AppIdentifier(null, null), "https://example.com", "https://api.example.com");

        // Restarting the process to send telemetry again
        process.kill(false);
        process = TestingProcessManager.startIsolatedProcess(args, false);

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
        assertEquals(9, telemetryData.entrySet().size());

        assertTrue(telemetryData.has("telemetryId"));
        assertEquals(telemetryData.get("superTokensVersion").getAsString(),
                Version.getVersion(process.getProcess()).getCoreVersion());
        assertEquals(telemetryData.get("appId").getAsString(), "public");
        assertEquals(telemetryData.get("connectionUriDomain").getAsString(), "");
        assertTrue(telemetryData.has("maus"));
        assertTrue(telemetryData.has("dashboardUserEmails"));
        assertEquals("https://example.com", telemetryData.get("websiteDomain").getAsString());
        assertEquals("https://api.example.com", telemetryData.get("apiDomain").getAsString());

        if (StorageLayer.getBaseStorage(process.getProcess()).getType() == STORAGE_TYPE.SQL) {
            assertEquals(1, telemetryData.get("dashboardUserEmails").getAsJsonArray().size());
            assertEquals("test@example.com",
                    telemetryData.get("dashboardUserEmails").getAsJsonArray().get(0).getAsString());
            assertEquals(31, telemetryData.get("maus").getAsJsonArray().size());
            assertEquals(0, telemetryData.get("usersCount").getAsInt());
        } else {
            assertEquals(0, telemetryData.get("dashboardUserEmails").getAsJsonArray().size());
            assertEquals(0, telemetryData.get("maus").getAsJsonArray().size());
            assertEquals(-1, telemetryData.get("usersCount").getAsInt());
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STOPPED));
    }

    @Test
    public void testThatTelemetryIdDoesNotChange() throws Exception {
        String telemetryId = null;
        {
            String[] args = {"../"};

            TestingProcess process = TestingProcessManager.startIsolatedProcess(args, false);

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
            String[] args = {"../"};

            TestingProcess process = TestingProcessManager.startIsolatedProcess(args, false);

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
        String[] args = {"../"};

        TestingProcess process = TestingProcessManager.startIsolatedProcess(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (Version.getVersion(process.getProcess()).getPluginName().equals("sqlite")) {
            return;
        }

        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.SENDING_TELEMETRY));
        assertNull(process.checkOrWaitForEvent(PROCESS_STATE.SENT_TELEMETRY, 2000));

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STOPPED));
    }

    // Outside tests the first ping is delayed by 60s plus up to 60s of jitter, so cores started together don't all
    // count their users at once, and the ping lands after EELicenseCheck's first run (30-60s).
    @Test
    public void initialWaitIsJitteredBetween60And120Seconds() {
        Set<Integer> seen = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            int wait = Telemetry.getJitteredInitialWaitTimeSeconds();
            assertTrue("initial wait " + wait + " is outside [60, 120)", wait >= 60 && wait < 120);
            seen.add(wait);
        }
        assertTrue("initial wait is not jittered", seen.size() > 1);
    }

    // A single-tenant app's telemetry user count is read from the shared ApproximateUserCount cache, not a
    // separate exact count.
    @Test
    public void singleTenantUsersCountComesFromTheCachedCount() throws Exception {
        String[] args = {"../"};

        TestingProcess process = TestingProcessManager.startIsolatedProcess(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getBaseStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            process.kill();
            assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STOPPED));
            return;
        }

        EmailPassword.signUp(process.getProcess(), "user1@example.com", "password123");
        EmailPassword.signUp(process.getProcess(), "user2@example.com", "password123");

        // Restart so the startup ping runs against an empty cache
        process.kill(false);
        process = TestingProcessManager.startIsolatedProcess(args, false);
        ByteArrayOutputStream output = mockTelemetryRequest(process);

        process.startProcess();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.SENT_TELEMETRY));

        JsonObject telemetryData = new JsonParser().parse(output.toString()).getAsJsonObject();
        assertEquals(2, telemetryData.get("usersCount").getAsLong());

        TenantIdentifier publicTenant = TenantIdentifier.BASE_TENANT;
        assertTrue(ApproximateUserCount.getInstance(process.getProcess(), publicTenant.toAppIdentifier())
                .hasAnchorForTesting(publicTenant));

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STOPPED));
    }

    // With several tenants, telemetry keeps the exact app-level count: a user shared by two tenants is counted
    // once, where adding up the per-tenant counts would count them twice.
    @Test
    public void multiTenantUsersCountCountsSharedUsersOnce() throws Exception {
        String[] args = {"../"};

        TestingProcess process = TestingProcessManager.startIsolatedProcess(args, false);
        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{EE_FEATURES.MULTI_TENANCY});
        process.startProcess();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getBaseStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            process.kill();
            assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STOPPED));
            return;
        }

        TenantIdentifier publicTenant = TenantIdentifier.BASE_TENANT;
        TenantIdentifier t1 = new TenantIdentifier(null, null, "t1");
        Multitenancy.addNewOrUpdateAppOrTenant(process.getProcess(), publicTenant,
                new TenantConfig(t1, new EmailPasswordConfig(true), new ThirdPartyConfig(true, null),
                        new PasswordlessConfig(true), null, null, new JsonObject()));

        AuthRecipeUserInfo shared = EmailPassword.signUp(process.getProcess(), "shared@example.com", "password123");
        EmailPassword.signUp(process.getProcess(), "public-only@example.com", "password123");
        Multitenancy.addUserIdToTenant(process.getProcess(), t1,
                StorageLayer.getStorage(t1, process.getProcess()), shared.getSupertokensUserId());

        // Restart so the startup ping runs with both tenants loaded
        process.kill(false);
        process = TestingProcessManager.startIsolatedProcess(args, false);
        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{EE_FEATURES.MULTI_TENANCY});
        ByteArrayOutputStream output = mockTelemetryRequest(process);

        process.startProcess();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.SENT_TELEMETRY));

        JsonObject telemetryData = new JsonParser().parse(output.toString()).getAsJsonObject();
        assertEquals(2, telemetryData.get("usersCount").getAsLong());

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STOPPED));
    }

    // Points the telemetry request of a not-yet-started process at a mock connection and returns what it sends.
    private static ByteArrayOutputStream mockTelemetryRequest(TestingProcess process) throws Exception {
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
        return output;
    }
}
