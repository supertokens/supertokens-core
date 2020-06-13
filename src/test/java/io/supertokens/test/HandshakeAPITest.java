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

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.supertokens.ProcessState;
import io.supertokens.config.Config;
import io.supertokens.httpRequest.HttpRequest;
import io.supertokens.httpRequest.HttpResponseException;
import io.supertokens.session.accessToken.AccessTokenSigningKey;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import static org.junit.Assert.*;


public class HandshakeAPITest {
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

        // null in the request body
        try {
            HttpRequest
                    .sendJsonPOSTRequest(process.getProcess(), "", "http://localhost:3567/handshake", null, 1000, 1000,
                            null);
            fail();
        } catch (HttpResponseException e) {
            assertTrue(e.statusCode == 400 &&
                    e.getMessage().equals("Http error. Status Code: 400. Message: Invalid Json Input"));
        }

    }

    @Test
    public void signingKeyHandshakeAPITest() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        String jsonInput = "{" +
                "\"deviceDriverInfo\": {" +
                "\"frontendSDK\": [{" +
                "\"name\": \"hName\"," +
                "\"version\": \"hVersion\"" +
                "}]," +
                "\"driver\": {" +
                "\"name\": \"hDName\"," +
                "\"version\": \"nDVersion\"" +
                "}" +
                "}" +
                "}";
        JsonObject response = HttpRequest
                .sendJsonPOSTRequest(process.getProcess(), "", "http://localhost:3567/handshake",
                        new JsonParser().parse(jsonInput), 1000,
                        1000, null);

        //check status
        assertEquals(response.get("status").getAsString(), "OK");

        //check number of values
        assertEquals(response.entrySet().size(), 9);

        //check jwtSigningPublicKey
        assertEquals(response.get("jwtSigningPublicKey").getAsString(),
                AccessTokenSigningKey.getInstance(process.getProcess()).getKey().publicKey);

        //check jwtSigningPublicKeyExpiryTime
        assertEquals(response.get("jwtSigningPublicKeyExpiryTime").getAsLong(),
                AccessTokenSigningKey.getInstance(process.getProcess()).getKeyExpiryTime());

        //check cookieDomain
        assertEquals(response.get("cookieDomain").getAsString(),
                Config.getConfig(process.getProcess()).getCookieDomain());

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

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

}
