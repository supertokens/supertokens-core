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
import com.auth0.jwt.interfaces.DecodedJWT;
import com.auth0.jwt.interfaces.RSAKeyProvider;
import com.google.gson.JsonObject;
import io.supertokens.ProcessState;
import io.supertokens.jwt.JWTSigningFunctions;
import io.supertokens.signingkeys.JWTSigningKey;
import io.supertokens.signingkeys.SigningKeys;
import io.supertokens.test.TestingProcessManager;
import io.supertokens.test.Utils;
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
import java.util.List;

import static org.junit.Assert.assertNotNull;

public class JWKSTest {
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
     * Test that after startup there is one JWK for each supported algorithm type in storage
     */
    @Test
    public void testThatThereAreTheSameNumberOfJWKSAsSupportedAlgorithmsBeforeJWTCreation() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        List<JsonObject> keysFromStorage = SigningKeys.getInstance(process.getProcess()).getJWKS();
        // We also get a dynamic key in the JWKs list
        assert keysFromStorage.size() == JWTSigningKey.SupportedAlgorithms.values().length + 1;

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    /**
     * Test that after creating a JWT the number of JWK in storage does not change, this is because a key for the
     * algorithm should already exist and a new key should not get created
     */
    @Test
    public void testThatNoNewJWKIsCreatedDuringJWTCreation() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        List<JsonObject> keysFromStorageBeforeJWTCreation = SigningKeys.getInstance(process.getProcess()).getJWKS();
        // We also get a dynamic key in the JWKs list
        assert keysFromStorageBeforeJWTCreation.size() == JWTSigningKey.SupportedAlgorithms.values().length + 1;
        int numberOfKeysBeforeJWTCreation = keysFromStorageBeforeJWTCreation.size();

        String algorithm = "RS256";
        JsonObject payload = new JsonObject();
        payload.addProperty("customClaim", "customValue");
        String jwksDomain = "http://localhost";
        long validity = 3600;

        JWTSigningFunctions.createJWTToken(process.getProcess(), algorithm, payload, jwksDomain, validity, false);

        List<JsonObject> keysFromStorageAfterJWTCreation = SigningKeys.getInstance(process.getProcess()).getJWKS();
        assert keysFromStorageAfterJWTCreation.size() == numberOfKeysBeforeJWTCreation;

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    /**
     * Test that JWK list contains a key with the same id as the kid in the JWT header
     */
    @Test
    public void testThatJWKListContainsValidKeyForCreatedJWT() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        String algorithm = "RS256";
        JsonObject payload = new JsonObject();
        payload.addProperty("customClaim", "customValue");
        String jwksDomain = "http://localhost";
        long validity = 3600;

        String jwt = JWTSigningFunctions.createJWTToken(process.getProcess(), algorithm, payload, jwksDomain, validity,
                false);
        DecodedJWT decodedJWT = JWT.decode(jwt);

        String headerKeyId = decodedJWT.getHeaderClaim("kid").asString();
        boolean didFindKey = false;

        List<JsonObject> keysFromStorage = SigningKeys.getInstance(process.getProcess()).getJWKS();
        for (int i = 0; i < keysFromStorage.size(); i++) {
            JsonObject key = keysFromStorage.get(i);
            if (key.get("kid").getAsString().equals(headerKeyId) && key.get("kty").getAsString().equalsIgnoreCase("rsa")
                    && key.get("alg").getAsString().equalsIgnoreCase("rs256")) {
                didFindKey = true;
                break;
            }
        }

        assert didFindKey;
        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    /**
     * Test that the JWK returned can be used to create a valid public key
     */
    @Test
    public void testThatAValidPublicKeyCanBeCreatedFromJWK() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        String algorithm = "RS256";
        JsonObject payload = new JsonObject();
        payload.addProperty("customClaim", "customValue");
        String jwksDomain = "http://localhost";
        long validity = 3600;

        JWTSigningFunctions.createJWTToken(process.getProcess(), algorithm, payload, jwksDomain, validity, false);

        List<JsonObject> keysFromStorage = SigningKeys.getInstance(process.getProcess()).getJWKS();

        // TODO: In the future when more algorithm types (EC etc) are supported this part of the test will need to be
        // updated
        for (int i = 0; i < keysFromStorage.size(); i++) {
            JsonObject key = keysFromStorage.get(i);
            String modulusString = key.get("n").getAsString();
            String exponentString = key.get("e").getAsString();

            BigInteger modulus = new BigInteger(1, Base64.getUrlDecoder().decode(modulusString));
            BigInteger exponent = new BigInteger(1, Base64.getUrlDecoder().decode(exponentString));

            KeyFactory.getInstance("RSA").generatePublic(new RSAPublicKeySpec(modulus, exponent));
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    /**
     * Test that the JWK can be used to create a public key and verify the signature of the JWT
     */
    @Test
    public void testThatJWKCanBeUsedForJWTVerification() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        String algorithm = "RS256";
        JsonObject payload = new JsonObject();
        payload.addProperty("customClaim", "customValue");
        String jwksDomain = "http://localhost";
        long validity = 3600;

        String jwt = JWTSigningFunctions.createJWTToken(process.getProcess(), algorithm, payload, jwksDomain, validity,
                false);
        DecodedJWT decodedJWT = JWT.decode(jwt);

        String headerKeyId = decodedJWT.getHeaderClaim("kid").asString();
        RSAPublicKey publicKey = null;

        List<JsonObject> keysFromStorage = SigningKeys.getInstance(process.getProcess()).getJWKS();
        for (int i = 0; i < keysFromStorage.size(); i++) {
            JsonObject key = keysFromStorage.get(i);
            if (key.get("kid").getAsString().equals(headerKeyId) && key.get("kty").getAsString().equalsIgnoreCase("rsa")
                    && key.get("alg").getAsString().equalsIgnoreCase("rs256")) {
                String modulusString = key.get("n").getAsString();
                String exponentString = key.get("e").getAsString();

                BigInteger modulus = new BigInteger(1, Base64.getUrlDecoder().decode(modulusString));
                BigInteger exponent = new BigInteger(1, Base64.getUrlDecoder().decode(exponentString));

                publicKey = (RSAPublicKey) KeyFactory.getInstance("RSA")
                        .generatePublic(new RSAPublicKeySpec(modulus, exponent));
                break;
            }
        }

        assert publicKey != null;

        RSAPublicKey finalPublicKey = publicKey;
        Algorithm verificationAlgorithm = Algorithm.RSA256(new RSAKeyProvider() {
            @Override
            public RSAPublicKey getPublicKeyById(String keyId) {
                return finalPublicKey;
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

    /**
     * Test that the modulus of the JWK is unsigned
     */
    @Test
    public void testThatJWKModulusIsUnsigned() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        List<JsonObject> keysFromStorage = SigningKeys.getInstance(process.getProcess()).getJWKS();

        for (int i = 0; i < keysFromStorage.size(); i++) {
            JsonObject key = keysFromStorage.get(i);
            byte[] modulusBytes = Base64.getUrlDecoder().decode(key.get("n").getAsString());

            // The modulus is always positive and should not contain the sign byte (0)
            assert modulusBytes[0] != 0;

            byte[] exponentBytes = Base64.getUrlDecoder().decode(key.get("e").getAsString());

            // The exponent is always positive and should not contain the sign byte (0)
            assert exponentBytes[0] != 0;
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }
}
