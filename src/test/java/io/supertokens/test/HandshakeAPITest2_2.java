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
import io.supertokens.config.Config;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.exceptions.StorageTransactionLogicException;
import io.supertokens.session.accessToken.AccessTokenSigningKey;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import static org.junit.Assert.*;

public class HandshakeAPITest2_2 {
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
            io.supertokens.test.httpRequest.HttpRequest
                    .sendJsonPOSTRequest(process.getProcess(), "", "http://localhost:3567/handshake", null, 1000, 1000,
                            null, Utils.getCdiVersion2_2ForTests());
        } catch (io.supertokens.test.httpRequest.HttpResponseException e) {
            assertTrue(e.statusCode == 400 &&
                    e.getMessage().equals("Http error. Status Code: 400. Message: Invalid Json Input"));
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

        JsonObject handshakeResponse = io.supertokens.test.httpRequest.HttpRequest
                .sendJsonPOSTRequest(process.getProcess(), "", "http://localhost:3567/handshake", deviceDriverInfo,
                        1000, 1000,
                        null, Utils.getCdiVersion2_2ForTests());
        checkHandshakeAPIResponse(handshakeResponse, process, true);
        assertEquals(handshakeResponse.entrySet().size(), 11);

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

        JsonObject handshakeResponse = io.supertokens.test.httpRequest.HttpRequest
                .sendJsonPOSTRequest(process.getProcess(), "", "http://localhost:3567/handshake", deviceDriverInfo,
                        1000, 1000,
                        null, Utils.getCdiVersion2_2ForTests());
        checkHandshakeAPIResponse(handshakeResponse, process, false);
        assertEquals(handshakeResponse.entrySet().size(), 12);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));

    }


    private static void checkHandshakeAPIResponse(JsonObject response, TestingProcessManager.TestingProcess process,
                                                  boolean cookieDomainShouldBeNull)
            throws StorageQueryException, StorageTransactionLogicException {
        //check status
        assertEquals(response.get("status").getAsString(), "OK");

        //check jwtSigningPublicKey
        assertEquals(response.get("jwtSigningPublicKey").getAsString(),
                AccessTokenSigningKey.getInstance(process.getProcess()).getKey().publicKey);

        //check jwtSigningPublicKeyExpiryTime
        assertEquals(response.get("jwtSigningPublicKeyExpiryTime").getAsLong(),
                AccessTokenSigningKey.getInstance(process.getProcess()).getKeyExpiryTime());

        //check cookieDomain
        if (cookieDomainShouldBeNull) {
            assertNull(response.get("cookieDomain"));
        } else {
            assertEquals(response.get("cookieDomain").getAsString(),
                    Config.getConfig(process.getProcess()).getCookieDomain(Utils.getCdiVersion2_2ForTests()));
        }

        //check cookieSecure
        assertEquals(response.get("cookieSecure").getAsBoolean(),
                Config.getConfig(process.getProcess()).getCookieSecure(process.getProcess()));

        //check accessTokenPath
        assertEquals(response.get("accessTokenPath").getAsString(),
                Config.getConfig(process.getProcess()).getAccessTokenPath());

        //check refreshTokenPath
        assertEquals(response.get("refreshTokenPath").getAsString(),
                Config.getConfig(process.getProcess()).getRefreshAPIPath());

        //check enableAntiCsrf
        assertEquals(response.get("enableAntiCsrf").getAsBoolean(),
                Config.getConfig(process.getProcess()).getEnableAntiCSRF());

        //check accessTokenBlacklistingEnabled
        assertEquals(response.get("accessTokenBlacklistingEnabled").getAsBoolean(),
                Config.getConfig(process.getProcess()).getAccessTokenBlacklisting());

        //check cookieSameSite
        assertEquals(response.get("cookieSameSite").getAsString(),
                Config.getConfig(process.getProcess()).getCookieSameSite());

        //check idRefreshTokenPath
        assertEquals(response.get("idRefreshTokenPath").getAsString(),
                Config.getConfig(process.getProcess()).getAccessTokenPath());

        //check sessionExpiredStatusCode
        assertEquals(response.get("sessionExpiredStatusCode").getAsInt(),
                Config.getConfig(process.getProcess()).getSessionExpiredStatusCode());
    }

}
