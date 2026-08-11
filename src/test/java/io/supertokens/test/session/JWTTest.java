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

package io.supertokens.test.session;

import com.google.gson.Gson;
import io.supertokens.session.accessToken.AccessToken;
import io.supertokens.session.jwt.JWT;
import io.supertokens.session.jwt.JWT.JWTException;
import io.supertokens.test.Utils;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SignatureException;
import java.security.spec.InvalidKeySpecException;

import static org.junit.Assert.*;

public class JWTTest {
    @Rule
    public TestRule watchman = Utils.getOnFailure();

    @Rule
    public TestRule retryFlaky = Utils.retryFlakyTest();

    @AfterClass
    public static void afterTesting() {
        Utils.afterTesting();
    }

    @Before
    public void beforeEach() {
        Utils.reset();
    }

    // good use case
    @Test
    public void validUsage() throws Exception {
        {
            TestInput input = new TestInput("value");
            io.supertokens.utils.Utils.PubPriKey rsa = io.supertokens.utils.Utils.generateNewPubPriKey();
            String token = JWT.createAndSignLegacyAccessToken(new Gson().toJsonTree(input), rsa.privateKey,
                    AccessToken.VERSION.V1);
            TestInput output = new Gson().fromJson(
                    JWT.verifyJWTAndGetPayload(JWT.preParseJWTInfo(token), rsa.publicKey).payload,
                    TestInput.class);
            assertEquals(input, output);
        }

        {
            TestInput input = new TestInput("value");
            io.supertokens.utils.Utils.PubPriKey rsa = io.supertokens.utils.Utils.generateNewPubPriKey();
            String token = JWT.createAndSignLegacyAccessToken(new Gson().toJsonTree(input), rsa.privateKey,
                    AccessToken.VERSION.V2);
            TestInput output = new Gson().fromJson(
                    JWT.verifyJWTAndGetPayload(JWT.preParseJWTInfo(token), rsa.publicKey).payload,
                    TestInput.class);
            assertEquals(input, output);
        }
    }

    // wrong signature error
    @Test
    public void wrongSignatureUsage() throws Exception {
        {
            TestInput input = new TestInput("value");
            io.supertokens.utils.Utils.PubPriKey rsa = io.supertokens.utils.Utils.generateNewPubPriKey();
            String token = JWT.createAndSignLegacyAccessToken(new Gson().toJsonTree(input), rsa.privateKey,
                    AccessToken.VERSION.V1);
            try {
                JWT.verifyJWTAndGetPayload(JWT.preParseJWTInfo(token), "signingKey2");
                fail();
            } catch (JWTException e) {
                assertEquals("JWT verification failed", e.getMessage());
            }
        }

        {
            TestInput input = new TestInput("value");
            io.supertokens.utils.Utils.PubPriKey rsa = io.supertokens.utils.Utils.generateNewPubPriKey();
            String token = JWT.createAndSignLegacyAccessToken(new Gson().toJsonTree(input), rsa.privateKey,
                    AccessToken.VERSION.V2);
            try {
                JWT.verifyJWTAndGetPayload(JWT.preParseJWTInfo(token), "signingKey2");
                fail();
            } catch (JWTException e) {
                assertEquals("JWT verification failed", e.getMessage());
            }
        }
    }

    // every malformed header shape must surface as JWTException (mapped to 4xx by the APIs), never
    // as a runtime error (which would end up as a 500)
    @Test
    public void preParseJWTInfoMalformedInputs() {
        // not three dot-separated parts
        assertPreParseThrows("not-a-jwt", "Invalid JWT");

        // header is not valid base64
        assertPreParseThrows("!!!notbase64!!!.payload.signature", "Invalid JWT");

        // header is valid base64 but not valid JSON
        assertPreParseThrows(io.supertokens.utils.Utils.convertToBase64("{") + ".payload.signature",
                "Invalid JWT");

        // header is a JSON scalar instead of an object
        assertPreParseThrows(io.supertokens.utils.Utils.convertToBase64("\"hello\"") + ".payload.signature",
                "Invalid JWT");

        // header is a JSON array instead of an object
        assertPreParseThrows(io.supertokens.utils.Utils.convertToBase64("[1, 2]") + ".payload.signature",
                "Invalid JWT");

        // typ is not a primitive
        assertPreParseThrows(tokenWithHeader("{\"typ\": {}, \"alg\": \"RS256\", \"kid\": \"key1\"}"),
                "JWT header missing - typ");

        // alg is not a primitive
        assertPreParseThrows(tokenWithHeader("{\"typ\": \"JWT\", \"alg\": [], \"kid\": \"key1\"}"),
                "JWT header missing - alg");

        // version is not a primitive
        assertPreParseThrows(
                tokenWithHeader("{\"typ\": \"JWT\", \"alg\": \"RS256\", \"version\": {}, \"kid\": \"key1\"}"),
                "JWT header mismatch - version");

        // version string that does not map to a known access token version
        assertPreParseThrows(
                tokenWithHeader("{\"typ\": \"JWT\", \"alg\": \"RS256\", \"version\": \"999\", \"kid\": \"key1\"}"),
                "JWT header mismatch - version");

        // missing kid; this used to throw a NullPointerException because the null check happened
        // after the dereference
        assertPreParseThrows(tokenWithHeader("{\"typ\": \"JWT\", \"alg\": \"RS256\"}"),
                "JWT header missing - kid");

        // kid is not a primitive
        assertPreParseThrows(tokenWithHeader("{\"typ\": \"JWT\", \"alg\": \"RS256\", \"kid\": {}}"),
                "JWT header missing - kid");

        // kid is a primitive but not a string
        assertPreParseThrows(tokenWithHeader("{\"typ\": \"JWT\", \"alg\": \"RS256\", \"kid\": 42}"),
                "JWT header mismatch - kid");
    }

    private static String tokenWithHeader(String headerJson) {
        return io.supertokens.utils.Utils.convertToBase64(headerJson) + ".payload.signature";
    }

    private static void assertPreParseThrows(String jwt, String expectedMessage) {
        try {
            JWT.preParseJWTInfo(jwt);
            fail("expected JWTException for: " + jwt);
        } catch (JWTException e) {
            assertEquals(expectedMessage, e.getMessage());
        }
    }

    @Test
    public void signingSuccess()
            throws NoSuchAlgorithmException, InvalidKeySpecException, InvalidKeyException, SignatureException {
        io.supertokens.utils.Utils.PubPriKey key = io.supertokens.utils.Utils.generateNewPubPriKey();
        String signature = io.supertokens.utils.Utils.signWithPrivateKey("hello", key.privateKey, false);
        assertTrue(io.supertokens.utils.Utils.verifyWithPublicKey("hello", signature, key.publicKey, false));
    }

    @Test
    public void signingFailure()
            throws NoSuchAlgorithmException, InvalidKeySpecException, InvalidKeyException, SignatureException {
        io.supertokens.utils.Utils.PubPriKey key = io.supertokens.utils.Utils.generateNewPubPriKey();
        String signature = io.supertokens.utils.Utils.signWithPrivateKey("hello", key.privateKey, false);
        try {
            io.supertokens.utils.Utils.verifyWithPublicKey("hello", signature + "random", key.publicKey, false);
            fail();
        } catch (IllegalArgumentException e) {
        }
        assertFalse(io.supertokens.utils.Utils.verifyWithPublicKey("helloo", signature, key.publicKey, false));
        try {
            io.supertokens.utils.Utils.verifyWithPublicKey("hello", signature,
                    key.publicKey.substring(0, 10) + "random" + key.publicKey.substring(10), false);
            fail();
        } catch (InvalidKeySpecException e) {
        }
    }

    private static class TestInput {
        final String key;

        TestInput(String key) {
            this.key = key;
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof TestInput)) {
                return false;
            }
            TestInput otherTestInput = (TestInput) other;
            return otherTestInput.key.equals(this.key);
        }
    }
}
