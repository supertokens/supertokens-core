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

package io.supertokens.test.jwt.api;

import com.auth0.jwt.JWT;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.supertokens.ProcessState;
import io.supertokens.signingkeys.AccessTokenSigningKey;
import io.supertokens.signingkeys.SigningKeys;
import io.supertokens.test.TestingProcessManager;
import io.supertokens.test.Utils;
import io.supertokens.test.httpRequest.HttpRequestForTesting;
import io.supertokens.utils.SemVer;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import java.util.List;

import static junit.framework.TestCase.*;
import static org.junit.Assert.assertNotNull;

public class JWKSAPITest2_21 {
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
    public void testThatNewDynamicKeysAreAdded() throws Exception {
        Utils.setValueInConfig("access_token_dynamic_signing_key_update_interval", "0.00027"); // 1 second
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        JsonObject oldResponse = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/jwt/jwks", null, 1000, 1000, null,
                Utils.getCdiVersionStringLatestForTests(),
                "jwt");

        JsonArray oldKeys = oldResponse.getAsJsonArray("keys");
        assertEquals(oldKeys.size(), 2); // 1 static + 1 dynamic key

        Thread.sleep(1500);

        JsonObject response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/jwt/jwks", null, 1000, 1000, null,
                Utils.getCdiVersionStringLatestForTests(),
                "jwt");

        JsonArray keys = response.getAsJsonArray("keys");
        assertEquals(keys.size(), oldKeys.size() + 1);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testThatNewDynamicKeysAreReflectedIfAddedByAnotherCore() throws Exception {
        Utils.setValueInConfig("access_token_dynamic_signing_key_update_interval", "0.00027"); // 1 second
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        JsonObject oldResponse = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/jwt/jwks", null, 1000, 1000, null,
                Utils.getCdiVersionStringLatestForTests(),
                "jwt");

        JsonArray oldKeys = oldResponse.getAsJsonArray("keys");
        assertEquals(oldKeys.size(), 2); // 1 static + 1 dynamic key

        Thread.sleep(1500);

        // Simulate another core adding a new key
        List<SigningKeys.KeyInfo> keyList = AccessTokenSigningKey.getInstance(process.getProcess())
                .getOrCreateAndGetSigningKeys();

        JsonObject responseAfterWait = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/jwt/jwks", null, 1000, 1000, null,
                Utils.getCdiVersionStringLatestForTests(),
                "jwt");

        JsonArray keys = responseAfterWait.getAsJsonArray("keys");
        assertEquals(keys.size(), oldKeys.size() + 1);

        // The key list returned by jwt should contain the dynamic keys on keyList + the static key
        assertEquals(keys.size(), keyList.size() + 1);


        for (int i = 0; i < keyList.size(); ++i) {
            assertEquals(keyList.get(i).id, keys.get(i).getAsJsonObject().get("kid").getAsString());
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testThatKeysContainsMatchingKeyIdForAccessToken() throws Exception {
        String[] args = {"../"};
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
        request.addProperty("useStaticSigningKey", false);
        request.addProperty("enableAntiCsrf", false);

        JsonObject createResponse = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/session", request, 1000, 1000, null, SemVer.v2_21.get(),
                "session");
        DecodedJWT decodedJWT = JWT.decode(
                createResponse.get("accessToken").getAsJsonObject().get("token").getAsString());
        String keyIdFromHeader = decodedJWT.getHeaderClaim("kid").asString();

        JsonObject response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/jwt/jwks", null, 1000, 1000, null,
                Utils.getCdiVersionStringLatestForTests(),
                "jwt");

        JsonArray keys = response.getAsJsonArray("keys");
        boolean didFindKey = false;

        for (int i = 0; i < keys.size(); i++) {
            JsonObject currentKey = keys.get(i).getAsJsonObject();

            if (currentKey.get("kid").getAsString().equals(keyIdFromHeader)) {
                didFindKey = true;
                break;
            }
        }

        assert didFindKey;

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testThatKeysContainsMatchingKeyIdForStaticAccessToken() throws Exception {
        String[] args = {"../"};
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
        request.addProperty("useStaticSigningKey", true);
        request.addProperty("enableAntiCsrf", false);

        JsonObject createResponse = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/session", request, 1000, 1000, null, SemVer.v2_21.get(),
                "session");
        DecodedJWT decodedJWT = JWT.decode(
                createResponse.get("accessToken").getAsJsonObject().get("token").getAsString());
        String keyIdFromHeader = decodedJWT.getHeaderClaim("kid").asString();

        JsonObject response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/jwt/jwks", null, 1000, 1000, null,
                Utils.getCdiVersionStringLatestForTests(),
                "jwt");

        JsonArray keys = response.getAsJsonArray("keys");
        boolean didFindKey = false;

        for (int i = 0; i < keys.size(); i++) {
            JsonObject currentKey = keys.get(i).getAsJsonObject();

            if (currentKey.get("kid").getAsString().equals(keyIdFromHeader)) {
                didFindKey = true;
                break;
            }
        }

        assert didFindKey;

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }
}
