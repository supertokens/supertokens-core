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

package io.supertokens.test.session.api;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.supertokens.ProcessState;
import io.supertokens.config.Config;
import io.supertokens.jwt.exceptions.UnsupportedJWTSigningAlgorithmException;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.exceptions.StorageTransactionLogicException;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
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

import static org.junit.Assert.*;

public class HandshakeAPITest2_7 {
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
    public void inputErrorsInHandshakeAPITest() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        // null in request body with cdi-version set to 2.0
        try {
            HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/handshake", null, 1000, 1000, null, SemVer.v2_7.get(),
                    "session");
        } catch (io.supertokens.test.httpRequest.HttpResponseException e) {
            assertTrue(e.statusCode == 400
                    && e.getMessage().equals("Http error. Status Code: 400. Message: Invalid Json Input"));
        }
        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));

    }

    @Test
    public void signingKeyHandshakeAPITest() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

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

        JsonObject handshakeResponse = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/handshake", deviceDriverInfo, 1000, 1000, null,
                SemVer.v2_7.get(), "session");
        checkHandshakeAPIResponse(handshakeResponse, process);
        assertEquals(handshakeResponse.entrySet().size(), 6);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));

    }

    @Test
    public void signingKeyHandshakeAPIWithCookiesTest() throws Exception {
        String[] args = {"../"};

        Utils.setValueInConfig("cookie_domain", "localhost");

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

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

        JsonObject handshakeResponse = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/handshake", deviceDriverInfo, 1000, 1000, null,
                SemVer.v2_7.get(), "session");
        checkHandshakeAPIResponse(handshakeResponse, process);
        assertEquals(handshakeResponse.entrySet().size(), 6);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));

    }

    @Test
    public void changingSigningKeyHandshakeAPITest() throws Exception {
        String[] args = {"../"};

        Utils.setValueInConfig("access_token_dynamic_signing_key_update_interval", "0.00081"); // 0.00027*3 = 3 seconds
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        String jsonInput = "{" + "\"deviceDriverInfo\": {" + "\"frontendSDK\": [{" + "\"name\": \"hName\","
                + "\"version\": \"hVersion\"" + "}]," + "\"driver\": {" + "\"name\": \"hDName\","
                + "\"version\": \"nDVersion\"" + "}" + "}" + "}";
        JsonObject response = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/handshake", new JsonParser().parse(jsonInput), 1000, 1000, null,
                SemVer.v2_7.get(), "session");

        assertEquals(response.entrySet().size(), 6);

        assertEquals(response.get("jwtSigningPublicKey").getAsString(), new io.supertokens.utils.Utils.PubPriKey(
                SigningKeys.getInstance(process.main).getLatestIssuedDynamicKey().value).publicKey);

        Thread.sleep(4000);

        JsonObject changedResponse = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/handshake", new JsonParser().parse(jsonInput), 1000, 1000, null,
                SemVer.v2_7.get(), "session");

        assertEquals(changedResponse.entrySet().size(), 6);

        // check that changed response has the same signing key as the current signing key and it is different from
        // the previous signing key
        assertTrue(changedResponse.get("jwtSigningPublicKey").getAsString()
                .equals(new io.supertokens.utils.Utils.PubPriKey(
                        SigningKeys.getInstance(process.main).getLatestIssuedDynamicKey().value).publicKey)
                && !(changedResponse.get("jwtSigningPublicKey").getAsString()
                .equals(response.get("jwtSigningPublicKey").getAsString())));

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    private static void checkHandshakeAPIResponse(JsonObject response, TestingProcessManager.TestingProcess process)
            throws StorageQueryException, StorageTransactionLogicException, TenantOrAppNotFoundException,
            UnsupportedJWTSigningAlgorithmException {
        // check status
        assertEquals(response.get("status").getAsString(), "OK");

        // check jwtSigningPublicKey
        assertEquals(response.get("jwtSigningPublicKey").getAsString(), new io.supertokens.utils.Utils.PubPriKey(
                SigningKeys.getInstance(process.main).getLatestIssuedDynamicKey().value).publicKey);

        // check jwtSigningPublicKeyExpiryTime
        assertEquals(response.get("jwtSigningPublicKeyExpiryTime").getAsLong(),
                SigningKeys.getInstance(process.getProcess()).getDynamicSigningKeyExpiryTime());

        // check accessTokenBlacklistingEnabled
        assertEquals(response.get("accessTokenBlacklistingEnabled").getAsBoolean(),
                Config.getConfig(process.getProcess()).getAccessTokenBlacklisting());

        assertEquals(response.get("accessTokenValidity").getAsLong(),
                Config.getConfig(process.getProcess()).getAccessTokenValidityInMillis());

        assertEquals(response.get("refreshTokenValidity").getAsLong(),
                Config.getConfig(process.getProcess()).getRefreshTokenValidityInMillis());
    }

}
