/*
 *    Copyright (c) 2023, VRAI Labs and/or its affiliates. All rights reserved.
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
import io.supertokens.ProcessState;
import io.supertokens.featureflag.EE_FEATURES;
import io.supertokens.featureflag.FeatureFlagTestContent;
import io.supertokens.multitenancy.Multitenancy;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.pluginInterface.multitenancy.*;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.test.httpRequest.HttpRequestForTesting;
import io.supertokens.test.httpRequest.HttpResponseException;
import io.supertokens.test.multitenant.api.TestMultitenancyAPIHelper;
import io.supertokens.webserver.RequestStats;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

public class RequestStatsTest {
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
    public void testLastMinuteStats() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        // Wait for a minute to pass
        Thread.sleep(60000 - (System.currentTimeMillis() % 60000) + 100);

        ExecutorService ex = Executors.newFixedThreadPool(100);
        int numRequests = 1000;
        for (int i = 0; i < numRequests; i++) {
            int finalI = i;
            ex.execute(() -> {
                try {
                    TestMultitenancyAPIHelper.epSignUp(TenantIdentifier.BASE_TENANT, "test" + finalI + "@example.com",
                            "password", process.getProcess());
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
        }

        ex.shutdown();
        ex.awaitTermination(45, TimeUnit.SECONDS); // should finish in 45 seconds

        // Wait for a minute to pass
        Thread.sleep(60000 - (System.currentTimeMillis() % 60000) + 100);

        JsonObject stats = HttpRequestForTesting
                .sendGETRequest(process.getProcess(), "", "http://localhost:3567/requests/stats", null, 5000,
                        5000, null, Utils.getCdiVersionStringLatestForTests(), null);

        JsonArray avgRps = stats.get("averageRequestsPerSecond").getAsJsonArray();
        JsonArray peakRps = stats.get("peakRequestsPerSecond").getAsJsonArray();

        double avg = 10000;

        int count = 0;
        for (JsonElement e : avgRps) {
            if (e.getAsDouble() == -1) {
                count++;
            } else {
                assertEquals(numRequests, Math.round(e.getAsDouble() * 60));
                avg = e.getAsDouble();
            }
        }
        assertEquals(1439, count);

        count = 0;
        for (JsonElement e : peakRps) {
            if (e.getAsInt() == -1) {
                count++;
            } else {
                assertTrue(e.getAsInt() > avg);
            }
        }
        assertEquals(1439, count);

        assertEquals(System.currentTimeMillis() / 60000, stats.get("atMinute").getAsLong());

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testLastMinuteStatsPerApp() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args, false);
        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{
                        EE_FEATURES.MULTI_TENANCY});
        process.startProcess();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        Multitenancy.addNewOrUpdateAppOrTenant(process.getProcess(), new TenantConfig(
                new TenantIdentifier(null, "a1", null),
                new EmailPasswordConfig(true),
                new ThirdPartyConfig(true, null),
                new PasswordlessConfig(true),
                null, null,
                new JsonObject()
        ), false);

        // Wait for a minute to pass
        Thread.sleep(60000 - (System.currentTimeMillis() % 60000) + 100);

        ExecutorService ex = Executors.newFixedThreadPool(100);
        int numRequests = 500;
        for (int i = 0; i < numRequests; i++) {
            int finalI = i;
            ex.execute(() -> {
                try {
                    TestMultitenancyAPIHelper.epSignUp(TenantIdentifier.BASE_TENANT, "test" + finalI + "@example.com",
                            "password", process.getProcess());
                } catch (Exception e) {
                    // ignore
                }
                if (finalI < 400) {
                    try {
                        TestMultitenancyAPIHelper.epSignUp(new TenantIdentifier(null, "a1", null),
                                "test" + finalI + "@example.com", "password", process.getProcess());
                    } catch (Exception e) {
                        // ignore
                    }
                }
            });
        }

        ex.shutdown();
        ex.awaitTermination(45, TimeUnit.SECONDS); // should finish in 45 seconds

        // Wait for a minute to pass
        Thread.sleep(60000 - (System.currentTimeMillis() % 60000) + 100);

        {
            JsonObject stats = HttpRequestForTesting
                    .sendGETRequest(process.getProcess(), "", "http://localhost:3567/requests/stats", null, 5000,
                            5000, null, Utils.getCdiVersionStringLatestForTests(), null);

            JsonArray avgRps = stats.get("averageRequestsPerSecond").getAsJsonArray();
            JsonArray peakRps = stats.get("peakRequestsPerSecond").getAsJsonArray();

            double avg = 10000;

            int count = 0;
            for (JsonElement e : avgRps) {
                if (e.getAsDouble() == -1) {
                    count++;
                } else {
                    assertEquals(numRequests, Math.round(e.getAsDouble() * 60));
                    avg = e.getAsDouble();
                }
            }
            assertEquals(1439, count);

            count = 0;
            for (JsonElement e : peakRps) {
                if (e.getAsInt() == -1) {
                    count++;
                } else {
                    assertTrue(e.getAsInt() > avg);
                }
            }
            assertEquals(1439, count);

            assertEquals(System.currentTimeMillis() / 60000, stats.get("atMinute").getAsLong());
        }

        {
            JsonObject stats = HttpRequestForTesting
                    .sendGETRequest(process.getProcess(), "", "http://localhost:3567/appid-a1/requests/stats", null,
                            1000,
                            1000, null, Utils.getCdiVersionStringLatestForTests(), null);

            JsonArray avgRps = stats.get("averageRequestsPerSecond").getAsJsonArray();
            JsonArray peakRps = stats.get("peakRequestsPerSecond").getAsJsonArray();

            double avg = 10000;

            int count = 0;
            for (JsonElement e : avgRps) {
                if (e.getAsDouble() == -1) {
                    count++;
                } else {
                    assertEquals(400, Math.round(e.getAsDouble() * 60));
                    avg = e.getAsDouble();
                }
            }
            assertEquals(1439, count);

            count = 0;
            for (JsonElement e : peakRps) {
                if (e.getAsInt() == -1) {
                    count++;
                } else {
                    assertTrue(e.getAsInt() > avg);
                }
            }
            assertEquals(1439, count);

            assertEquals(System.currentTimeMillis() / 60000, stats.get("atMinute").getAsLong());
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testWithNonExistantApp() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args, false);
        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{
                        EE_FEATURES.MULTI_TENANCY});
        process.startProcess();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        try {
            RequestStats.getInstance(process.getProcess(), new AppIdentifier(null, "a1"));
            fail();
        } catch (TenantOrAppNotFoundException e) {
            // ok
        }

        try {
            JsonObject stats = HttpRequestForTesting
                    .sendGETRequest(process.getProcess(), "", "http://localhost:3567/appid-a1/requests/stats", null,
                            1000,
                            1000, null, Utils.getCdiVersionStringLatestForTests(), null);
            fail();
        } catch (HttpResponseException e) {
            assertEquals(400, e.statusCode);
            assertEquals(
                    "Http error. Status Code: 400. Message: AppId or tenantId not found => Tenant with the following " +
                            "connectionURIDomain, appId and tenantId combination not found: (, a1, public)",
                    e.getMessage());
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

}
