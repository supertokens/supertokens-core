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

import com.google.gson.Gson;
import io.supertokens.session.accessToken.AccessToken;
import io.supertokens.session.jwt.JWT;
import io.supertokens.session.jwt.JWT.JWTException;
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
            String token = JWT.createJWT(new Gson().toJsonTree(input), rsa.privateKey, AccessToken.VERSION.V1);
            TestInput output = new Gson()
                    .fromJson(JWT.verifyJWTAndGetPayload(token, rsa.publicKey).payload, TestInput.class);
            assertEquals(input, output);
        }

        {
            TestInput input = new TestInput("value");
            io.supertokens.utils.Utils.PubPriKey rsa = io.supertokens.utils.Utils.generateNewPubPriKey();
            String token = JWT.createJWT(new Gson().toJsonTree(input), rsa.privateKey, AccessToken.VERSION.V2);
            TestInput output = new Gson()
                    .fromJson(JWT.verifyJWTAndGetPayload(token, rsa.publicKey).payload, TestInput.class);
            assertEquals(input, output);
        }
    }

    // wrong signature error
    @Test
    public void wrongSignatureUsage() throws Exception {
        {
            TestInput input = new TestInput("value");
            io.supertokens.utils.Utils.PubPriKey rsa = io.supertokens.utils.Utils.generateNewPubPriKey();
            String token = JWT.createJWT(new Gson().toJsonTree(input), rsa.privateKey, AccessToken.VERSION.V1);
            try {
                JWT.verifyJWTAndGetPayload(token, "signingKey2");
                fail();
            } catch (JWTException e) {
                assertEquals("JWT verification failed", e.getMessage());
            }
        }

        {
            TestInput input = new TestInput("value");
            io.supertokens.utils.Utils.PubPriKey rsa = io.supertokens.utils.Utils.generateNewPubPriKey();
            String token = JWT.createJWT(new Gson().toJsonTree(input), rsa.privateKey, AccessToken.VERSION.V2);
            try {
                JWT.verifyJWTAndGetPayload(token, "signingKey2");
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
        String signature = io.supertokens.utils.Utils.signWithPrivateKey("hello", key.privateKey);
        assertTrue(io.supertokens.utils.Utils.verifyWithPublicKey("hello", signature, key.publicKey));
    }

    @Test
    public void signingFailure()
            throws NoSuchAlgorithmException, InvalidKeySpecException, InvalidKeyException, SignatureException {
        io.supertokens.utils.Utils.PubPriKey key = io.supertokens.utils.Utils.generateNewPubPriKey();
        String signature = io.supertokens.utils.Utils.signWithPrivateKey("hello", key.privateKey);
        try {
            io.supertokens.utils.Utils.verifyWithPublicKey("hello", signature + "random", key.publicKey);
            fail();
        } catch (IllegalArgumentException e) {
        }
        assertFalse(io.supertokens.utils.Utils.verifyWithPublicKey("helloo", signature, key.publicKey));
        try {
            io.supertokens.utils.Utils.verifyWithPublicKey("hello", signature,
                    key.publicKey.substring(0, 10) + "random" + key.publicKey.substring(10));
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
