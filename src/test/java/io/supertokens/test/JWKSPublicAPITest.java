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
import io.supertokens.ProcessState;
import io.supertokens.httpRequest.HttpRequest;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.*;

public class JWKSPublicAPITest {
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
    public void testSuccessOutput() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        // check regular output
        JsonObject response = HttpRequest.sendGETRequest(process.getProcess(), "",
                "http://localhost:3567/.well-known/jwks.json", null,
                1000, 1000, null);

        assertEquals(response.entrySet().size(), 1);

        assertTrue(response.has("keys"));
        JsonArray keys = response.get("keys").getAsJsonArray();
        assertEquals(keys.size(), 2);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testCacheControlValue() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args, false);
        Utils.setValueInConfig("access_token_dynamic_signing_key_update_interval", "1");
        process.startProcess();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        // check regular output
        Map<String, String> responseHeaders = new HashMap<>();
        JsonObject response = HttpRequest.sendGETRequestWithResponseHeaders(process.getProcess(), "",
                "http://localhost:3567/.well-known/jwks.json", null,
                1000, 1000, null, responseHeaders);

        assertEquals(response.entrySet().size(), 1);

        assertTrue(response.has("keys"));
        JsonArray keys = response.get("keys").getAsJsonArray();
        assertEquals(keys.size(), 2);

        long maxAge = getMaxAgeValue(responseHeaders.get("Cache-Control"));
        assertTrue(maxAge >= 3538 && maxAge <= 3540);

        Thread.sleep(2000);

        response = HttpRequest.sendGETRequestWithResponseHeaders(process.getProcess(), "",
                "http://localhost:3567/.well-known/jwks.json", null,
                1000, 1000, null, responseHeaders);

        assertEquals(response.entrySet().size(), 1);

        assertTrue(response.has("keys"));
        keys = response.get("keys").getAsJsonArray();
        assertEquals(keys.size(), 2);

        long newMaxAge = getMaxAgeValue(responseHeaders.get("Cache-Control"));
        assertTrue(maxAge - newMaxAge >= 2 && maxAge - newMaxAge <= 3);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    private static long getMaxAgeValue(String input) {
        String pattern = "max-age=(\\d+)";
        Pattern r = Pattern.compile(pattern);
        Matcher m = r.matcher(input);

        if (m.find()) {
            return Long.parseLong(m.group(1));
        } else {
            throw new IllegalArgumentException("No max-age found");
        }
    }
}
