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
