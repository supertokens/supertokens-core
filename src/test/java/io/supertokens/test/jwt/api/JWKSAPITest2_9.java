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
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.auth0.jwt.interfaces.RSAKeyProvider;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.supertokens.ProcessState;
import io.supertokens.test.TestingProcessManager;
import io.supertokens.test.Utils;
import io.supertokens.test.httpRequest.HttpRequestForTesting;
import io.supertokens.utils.SemVer;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.util.Base64;

import static org.junit.Assert.assertNotNull;

public class JWKSAPITest2_9 {
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
     * Test that list of JWKs after creating a JWT is not empty
     */
    @Test
    public void testThatGettingKeysAfterCreatingJWTDoesNotReturnEmpty() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("algorithm", "rs256");
        requestBody.addProperty("jwksDomain", "http://localhost");
        requestBody.add("payload", new JsonObject());
        requestBody.addProperty("validity", 3600);

        HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "", "http://localhost:3567/recipe/jwt",
                requestBody, 1000, 1000, null, SemVer.v2_9.get(), "jwt");

        JsonObject response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/jwt/jwks", null, 1000, 1000, null, SemVer.v2_9.get(),
                "jwt");

        JsonArray keys = response.getAsJsonArray("keys");
        assert keys.size() != 0;

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    /**
     * Test that after creating a JWT the returned list of JWKs has a JWK with the same key id as the JWT header
     */
    @Test
    public void testThatKeysContainsMatchingKeyId() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("algorithm", "rs256");
        requestBody.addProperty("jwksDomain", "http://localhost");
        requestBody.add("payload", new JsonObject());
        requestBody.addProperty("validity", 3600);

        JsonObject jwtResponse = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/jwt", requestBody, 1000, 1000, null, SemVer.v2_9.get(),
                "jwt");

        String jwt = jwtResponse.get("jwt").getAsString();
        DecodedJWT decodedJWT = JWT.decode(jwt);

        String keyIdFromHeader = decodedJWT.getHeaderClaim("kid").asString();

        JsonObject response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/jwt/jwks", null, 1000, 1000, null, SemVer.v2_9.get(),
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

    /**
     * Test that the JWK with the same kid as the JWT header can be used to verify the JWT signature
     */
    @Test
    public void testThatKeyFromResponseCanBeUsedForJWTVerification() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("algorithm", "rs256");
        requestBody.addProperty("jwksDomain", "http://localhost");
        requestBody.add("payload", new JsonObject());
        requestBody.addProperty("validity", 3600);

        JsonObject jwtResponse = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/jwt", requestBody, 1000, 1000, null, SemVer.v2_9.get(),
                "jwt");

        String jwt = jwtResponse.get("jwt").getAsString();
        DecodedJWT decodedJWT = JWT.decode(jwt);

        String keyIdFromHeader = decodedJWT.getHeaderClaim("kid").asString();

        JsonObject response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/jwt/jwks", null, 1000, 1000, null, SemVer.v2_9.get(),
                "jwt");

        JsonArray keys = response.getAsJsonArray("keys");
        JsonObject keyToUse = null;

        for (int i = 0; i < keys.size(); i++) {
            JsonObject currentKey = keys.get(i).getAsJsonObject();

            if (currentKey.get("kid").getAsString().equals(keyIdFromHeader)) {
                keyToUse = currentKey;
                break;
            }
        }

        assert keyToUse != null;

        String modulusString = keyToUse.get("n").getAsString();
        String exponentString = keyToUse.get("e").getAsString();

        BigInteger modulus = new BigInteger(1, Base64.getUrlDecoder().decode(modulusString));
        BigInteger exponent = new BigInteger(1, Base64.getUrlDecoder().decode(exponentString));

        RSAPublicKey publicKey = (RSAPublicKey) KeyFactory.getInstance("RSA")
                .generatePublic(new RSAPublicKeySpec(modulus, exponent));

        Algorithm verificationAlgorithm = Algorithm.RSA256(new RSAKeyProvider() {
            @Override
            public RSAPublicKey getPublicKeyById(String keyId) {
                return publicKey;
            }

            @Override
            public RSAPrivateKey getPrivateKey() {
                return null;
            }

            @Override
            public String getPrivateKeyId() {
                return null;
            }
        });

        JWTVerifier verifier = JWT.require(verificationAlgorithm).build();
        verifier.verify(jwt);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }
}
