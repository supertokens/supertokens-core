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
import com.auth0.jwt.interfaces.Claim;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.google.gson.JsonObject;
import io.supertokens.ProcessState;
import io.supertokens.test.httpRequest.HttpRequestForTesting;
import io.supertokens.test.httpRequest.HttpResponseException;
import io.supertokens.test.TestingProcessManager;
import io.supertokens.test.Utils;
import io.supertokens.webserver.api.jwt.JWTSigningAPI;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import static org.junit.Assert.*;

public class JWTSigningAPITest2_9 {
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

    /**
     * Test that the API returns 400 if the algorithm parameter is not provided
     */
    @Test
    public void testThatNullAlgorithmThrowsError() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("jwksDomain", "http://localhost");
        requestBody.add("payload", new JsonObject());
        requestBody.addProperty("validity", 3600);

        try {
            JsonObject response = HttpRequestForTesting
                    .sendJsonPOSTRequest(process.getProcess(), "", "http://localhost:3567/recipe/jwt",
                            requestBody, 1000, 1000, null, Utils.getCdiVersion2_9ForTests(), "jwt");
            fail();
        } catch (HttpResponseException e) {
            assertTrue(e.statusCode == 400 && e.getMessage().equals("Http error. Status Code: 400. Message: Field name 'algorithm' is invalid in JSON input"));
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    /**
     * Test that the API returns status for unsupported algorithm if an invalid algorithm parameter is provided
     */
    @Test
    public void testThatWrongAlgorithmReturnsUnsupportedError() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("algorithm", "rs512");
        requestBody.addProperty("jwksDomain", "http://localhost");
        requestBody.add("payload", new JsonObject());
        requestBody.addProperty("validity", 3600);

        JsonObject response = HttpRequestForTesting
                .sendJsonPOSTRequest(process.getProcess(), "", "http://localhost:3567/recipe/jwt",
                        requestBody, 1000, 1000, null, Utils.getCdiVersion2_9ForTests(), "jwt");
        assertEquals(response.get("status").getAsString(), JWTSigningAPI.UNSUPPORTED_ALGORITHM_ERROR_STATUS);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    /**
     * Test that the API returns 400 if the jwksDomain parameter is not provided
     */
    @Test
    public void testThatNullJwksDomainThrowsError() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("algorithm", "rs256");
        requestBody.add("payload", new JsonObject());
        requestBody.addProperty("validity", 3600);

        try {
            JsonObject response = HttpRequestForTesting
                    .sendJsonPOSTRequest(process.getProcess(), "", "http://localhost:3567/recipe/jwt",
                            requestBody, 1000, 1000, null, Utils.getCdiVersion2_9ForTests(), "jwt");
            fail();
        } catch (HttpResponseException e) {
            assertTrue(e.statusCode == 400 && e.getMessage().equals("Http error. Status Code: 400. Message: Field name 'jwksDomain' is invalid in JSON input"));
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    /**
     * Test that the API returns 400 if the payload parameter is not provided
     */
    @Test
    public void testThatNullPayloadThrowsError() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("algorithm", "rs256");
        requestBody.addProperty("jwksDomain", "http://localhost");
        requestBody.addProperty("validity", 3600);

        try {
            HttpRequestForTesting
                    .sendJsonPOSTRequest(process.getProcess(), "", "http://localhost:3567/recipe/jwt",
                            requestBody, 1000, 1000, null, Utils.getCdiVersion2_9ForTests(), "jwt");
            fail();
        } catch (HttpResponseException e) {
            assertTrue(e.statusCode == 400 && e.getMessage().equals("Http error. Status Code: 400. Message: Field name 'payload' is invalid in JSON input"));
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    /**
     * Test that the API returns 400 if the payload provided is not a valid JSON object
     */
    @Test
    public void testThatNonJSONParseablePayloadThrowsError() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("algorithm", "rs256");
        requestBody.addProperty("jwksDomain", "http://localhost");
        requestBody.addProperty("payload", "nonjsonparseablestring");
        requestBody.addProperty("validity", 3600);

        try {
            HttpRequestForTesting
                    .sendJsonPOSTRequest(process.getProcess(), "", "http://localhost:3567/recipe/jwt",
                            requestBody, 1000, 1000, null, Utils.getCdiVersion2_9ForTests(), "jwt");
            fail();
        } catch (HttpResponseException e) {
            assertTrue(e.statusCode == 400 && e.getMessage().equals("Http error. Status Code: 400. Message: Field name 'payload' is invalid in JSON input"));
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    /**
     * Test that the API returns 400 if the validity parameter is not provided
     */
    @Test
    public void testThatNullValidityThrowsError() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("algorithm", "rs256");
        requestBody.addProperty("jwksDomain", "http://localhost");
        requestBody.add("payload", new JsonObject());

        try {
            JsonObject response = HttpRequestForTesting
                    .sendJsonPOSTRequest(process.getProcess(), "", "http://localhost:3567/recipe/jwt",
                            requestBody, 1000, 1000, null, Utils.getCdiVersion2_9ForTests(), "jwt");
            fail();
        } catch (HttpResponseException e) {
            assertTrue(e.statusCode == 400 && e.getMessage().equals("Http error. Status Code: 400. Message: Field name 'validity' is invalid in JSON input"));
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    /**
     * Test that the API returns 400 if the validity provided is < 0
     */
    @Test
    public void testThatNegativeValidityThrowsError() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("algorithm", "rs256");
        requestBody.addProperty("jwksDomain", "http://localhost");
        requestBody.add("payload", new JsonObject());
        requestBody.addProperty("validity", -3600);

        try {
            JsonObject response = HttpRequestForTesting
                    .sendJsonPOSTRequest(process.getProcess(), "", "http://localhost:3567/recipe/jwt",
                            requestBody, 1000, 1000, null, Utils.getCdiVersion2_9ForTests(), "jwt");
            fail();
        } catch (HttpResponseException e) {
            // TODO: Check how to handle this, shuld be a 400 but assertion errors are considered 500s
            assertTrue(e.statusCode == 400 && e.getMessage().equals("Http error. Status Code: 400. Message: validity must be greater than or equal to 0"));
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    /**
     * Test that the API returns 200 with valid response body when called with a valid request body
     */
    @Test
    public void testThatCallingWithValidParamsSucceeds() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("algorithm", "rs256");
        requestBody.addProperty("jwksDomain", "http://localhost");
        requestBody.add("payload", new JsonObject());
        requestBody.addProperty("validity", 3600);

        JsonObject response = HttpRequestForTesting
                .sendJsonPOSTRequest(process.getProcess(), "", "http://localhost:3567/recipe/jwt",
                        requestBody, 1000, 1000, null, Utils.getCdiVersion2_9ForTests(), "jwt");

        assertEquals(response.get("status").getAsString(), "OK");
        assertNotNull(response.get("jwt"));

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    /**
     * Test that the returned JWT has a valid header
     */
    @Test
    public void testThatReturnedJWTHasValidHeader() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("algorithm", "rs256");
        requestBody.addProperty("jwksDomain", "http://localhost");
        requestBody.add("payload", new JsonObject());
        requestBody.addProperty("validity", 3600);

        JsonObject response = HttpRequestForTesting
                .sendJsonPOSTRequest(process.getProcess(), "", "http://localhost:3567/recipe/jwt",
                        requestBody, 1000, 1000, null, Utils.getCdiVersion2_9ForTests(), "jwt");

        String jwt = response.get("jwt").getAsString();
        DecodedJWT decodedJWT = JWT.decode(jwt);

        Claim headerAlg = decodedJWT.getHeaderClaim("alg");
        Claim headerType = decodedJWT.getHeaderClaim("typ");
        Claim headerKeyId = decodedJWT.getHeaderClaim("kid");

        assertTrue(!headerAlg.isNull() && !headerType.isNull() && !headerKeyId.isNull());
        assert headerAlg.asString().equalsIgnoreCase("rs256");
        assert headerType.asString().equalsIgnoreCase("jwt");
        assert !headerKeyId.asString().isEmpty();

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    /**
     * Test that the returned JWT payload contains provided custom payload properties
     */
    @Test
    public void testThatDecodedJWTHasCustomPayload() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("algorithm", "rs256");
        requestBody.addProperty("jwksDomain", "http://localhost");

        JsonObject customPayload = new JsonObject();
        customPayload.addProperty("customClaim", "customValue");
        requestBody.add("payload", customPayload);

        requestBody.addProperty("validity", 3600);

        JsonObject response = HttpRequestForTesting
                .sendJsonPOSTRequest(process.getProcess(), "", "http://localhost:3567/recipe/jwt",
                        requestBody, 1000, 1000, null, Utils.getCdiVersion2_9ForTests(), "jwt");

        String jwt = response.get("jwt").getAsString();
        DecodedJWT decodedJWT = JWT.decode(jwt);
        Claim customClaim = decodedJWT.getClaim("customClaim");
        assertTrue(!customClaim.isNull() && customClaim.asString().equals("customValue"));

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }
}
