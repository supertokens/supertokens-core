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

package io.supertokens.test.jwt;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.Claim;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.google.gson.JsonObject;
import io.supertokens.ProcessState;
import io.supertokens.jwt.JWTSigningFunctions;
import io.supertokens.test.TestingProcessManager;
import io.supertokens.test.Utils;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.interfaces.RSAKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.EncodedKeySpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.util.Base64;
import java.util.List;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

public class JWTCreateTest {
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
     * Call JWTSigningFunctions.createJWTToken with valid params and ensure that it does not throw any errors
     */
    @Test
    public void testNormalFunctioningOfCreateToken() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        String algorithm = "RS256";
        JsonObject payload = new JsonObject();
        payload.addProperty("customClaim", "customValue");
        String jwksDomain = "http://localhost";
        long validity = 3600;

        JWTSigningFunctions.createJWTToken(process.getProcess(), algorithm, payload, jwksDomain, validity);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    /**
     * Trying to create a JWT with an unsupported algorithm should throw an error
     */
    @Test
    public void testInvalidAlgorithmThrowsError() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        String algorithm = "HS256";
        JsonObject payload = new JsonObject();
        payload.addProperty("customClaim", "customValue");
        String jwksDomain = "http://localhost";
        long validity = 3600;

        try {
            JWTSigningFunctions.createJWTToken(process.getProcess(), algorithm, payload, jwksDomain, validity);
            fail("JWTSigningFunctions.createJWTToken succeeded when it should have failed");
        } catch (NoSuchAlgorithmException e) {
            // Do nothing
        }
    }

    /**
     * Verify that the JWT header has the required properties and that the values are valid
     */
    @Test
    public void testThatDecodedJWTHasAValidHeader() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        String algorithm = "RS256";
        JsonObject payload = new JsonObject();
        payload.addProperty("customClaim", "customValue");
        String jwksDomain = "http://localhost";
        long validity = 3600;

        String jwt = JWTSigningFunctions.createJWTToken(process.getProcess(), algorithm, payload, jwksDomain, validity);

        DecodedJWT decodedJWT = JWT.decode(jwt);

        Claim headerAlg = decodedJWT.getHeaderClaim("alg");
        Claim headerType = decodedJWT.getHeaderClaim("typ");
        Claim headerKeyId = decodedJWT.getHeaderClaim("kid");

        if (headerAlg.isNull() || headerType.isNull() || headerKeyId.isNull()) {
            throw new Exception("JWT header is missing one or more required claim (alg, typ, kid)");
        }

        if (!headerAlg.asString().equals(algorithm)) {
            throw new Exception("Algorithm in JWT header does not match algorithm passed to JWTSigningFunctions.createJWTToken");
        }

        if (!headerType.asString().equals("JWT")) {
            throw new Exception("JWT header contains wrong type: Expected: JWT, Actual: " + headerType.toString());
        }

        if (headerKeyId.asString().isEmpty()) {
            throw new Exception("Value for kid in JWT header is invalid");
        }
    }

    /**
     * Verify that the JWT payload has the required properties and the values as valid
     */
    @Test
    public void testThatDecodedJWTPayloadHasRequiredClaims() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        String algorithm = "RS256";
        JsonObject payload = new JsonObject();
        payload.addProperty("customClaim", "customValue");
        String jwksDomain = "http://localhost";
        long validity = 3600;

        long expectedIssuedAtTime = System.currentTimeMillis() / 1000;
        long expectedExpiry = expectedIssuedAtTime + validity;

        String jwt = JWTSigningFunctions.createJWTToken(process.getProcess(), algorithm, payload, jwksDomain, validity);

        DecodedJWT decodedJWT = JWT.decode(jwt);

        Claim issuer = decodedJWT.getClaim("iss");
        Claim issuedAtTime = decodedJWT.getClaim("iat");
        Claim expiry = decodedJWT.getClaim("exp");

        if (issuer.isNull() || issuedAtTime.isNull() || expiry.isNull()) {
            throw new Exception("JWT payload is missing one or more required claim (iss, iat, exp)");
        }

        if (!issuer.asString().equals(jwksDomain)) {
            throw new Exception("JWT payload has invalid iss claim");
        }

        if (Math.abs(issuedAtTime.asLong() - expectedIssuedAtTime) > 1) { // 1 second error margin
            throw new Exception("JWT iat claim does not match expected value");
        }

        if (Math.abs(expiry.asLong() - expectedExpiry) > 1) {
            throw new Exception("JWT exp claim does not match expected value");
        }
    }

    /**
     * Test that custom payload properties are present and valid in the JWT payload
     */
    @Test
    public void testThatDecodedJWTPayloadHasCustomProperties() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        String algorithm = "RS256";
        String customClaimKey = "customClaim";
        String customClaimValue = "customValue";

        JsonObject payload = new JsonObject();
        payload.addProperty(customClaimKey, customClaimValue);

        String jwksDomain = "http://localhost";
        long validity = 3600;

        String jwt = JWTSigningFunctions.createJWTToken(process.getProcess(), algorithm, payload, jwksDomain, validity);

        DecodedJWT decodedJWT = JWT.decode(jwt);

        Claim customClaim = decodedJWT.getClaim(customClaimKey);

        if (customClaim.isNull()) {
            throw new Exception("Decoded JWT does not contain properties from payload passed to JWTSigningFunctions.createJWTToken");
        }

        if (!customClaim.asString().equals(customClaimValue)) {
            throw new Exception("Decoded JWT does not contain the correct value for custom claim");
        }
    }
}
