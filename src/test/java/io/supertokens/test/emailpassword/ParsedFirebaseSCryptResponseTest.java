/*
 *    Copyright (c) 2022, VRAI Labs and/or its affiliates. All rights reserved.
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

package io.supertokens.test.emailpassword;

import io.supertokens.ProcessState;
import io.supertokens.emailpassword.ParsedFirebaseSCryptResponse;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.test.TestingProcessManager;
import io.supertokens.test.Utils;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public class ParsedFirebaseSCryptResponseTest {
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
    public void testGoodInputToParsedFirebaseSCryptResponse() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        // format for Firebase SCrypt hashes stored in supertokens.
        // $f_scrypt$passwordHash$salt$m=memory$r=rounds$s=saltSeparator

        String passwordHash = "passwordHash";
        String salt = "salt";
        int memory = 14;
        int rounds = 8;
        String saltSeparator = "Bw==";

        // when password hash is in the regular format
        {
            String validPasswordHash = "$" + ParsedFirebaseSCryptResponse.FIREBASE_SCRYPT_PREFIX + "$" + passwordHash
                    + "$" + salt + "$" + "m=" + memory + "$" + "r=" + rounds + "$" + "s=" + saltSeparator;

            ParsedFirebaseSCryptResponse response = ParsedFirebaseSCryptResponse.fromHashString(validPasswordHash);
            assertNotNull(response);

            assertEquals(passwordHash, response.passwordHash);
            assertEquals(salt, response.salt);
            assertEquals(memory, response.memCost);
            assertEquals(saltSeparator, response.saltSeparator);
        }

        // with memory, rounds and saltSeparator switched around
        {
            String validPasswordHash = "$" + ParsedFirebaseSCryptResponse.FIREBASE_SCRYPT_PREFIX + "$" + passwordHash
                    + "$" + salt + "$" + "s=" + saltSeparator + "$" + "m=" + memory + "$" + "r=" + rounds;

            ParsedFirebaseSCryptResponse response = ParsedFirebaseSCryptResponse.fromHashString(validPasswordHash);
            assertNotNull(response);

            assertEquals(passwordHash, response.passwordHash);
            assertEquals(salt, response.salt);
            assertEquals(memory, response.memCost);
            assertEquals(saltSeparator, response.saltSeparator);
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testBadInputToParsedFirebaseSCryptResponse() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        // passing empty string
        assertNull(ParsedFirebaseSCryptResponse.fromHashString(""));

        // passing a random string
        assertNull(ParsedFirebaseSCryptResponse.fromHashString("randmString"));

        // passing just $
        assertNull(ParsedFirebaseSCryptResponse.fromHashString("$"));

        String passwordHash = "passwordHash";
        String salt = "salt";
        int memory = 14;
        int rounds = 8;
        String saltSeparator = "Bw==";

        {
            // input before the prefix
            String invalidPasswordhash = "invalidField$" + ParsedFirebaseSCryptResponse.FIREBASE_SCRYPT_PREFIX + "$"
                    + passwordHash + "$" + salt + "$" + "s=" + saltSeparator + "$" + "m=" + memory + "$" + "r="
                    + rounds;
            assertNull(ParsedFirebaseSCryptResponse.fromHashString(invalidPasswordhash));

            // missing prefix
            String invalidPasswordHash = "$" + passwordHash + "$" + salt + "$" + "s=" + saltSeparator + "$" + "m="
                    + memory + "$" + "r=" + rounds;
            assertNull(ParsedFirebaseSCryptResponse.fromHashString(invalidPasswordHash));

            // invalid prefix
            String invalidPasswordHash_2 = "$invalidPrefix$" + passwordHash + "$" + salt + "$" + "m=" + memory + "$"
                    + "r=" + rounds + "$" + "s=" + saltSeparator;
            assertNull(ParsedFirebaseSCryptResponse.fromHashString(invalidPasswordHash_2));

        }

        {
            // missing password hash
            String invalidPasswordHash = "$" + ParsedFirebaseSCryptResponse.FIREBASE_SCRYPT_PREFIX + "$" + salt + "$"
                    + "m=" + memory + "$" + "r=" + rounds + "$" + "s=" + saltSeparator;
            assertNull(ParsedFirebaseSCryptResponse.fromHashString(invalidPasswordHash));
        }

        {
            // missing salt
            String invalidPasswordHash = "$" + ParsedFirebaseSCryptResponse.FIREBASE_SCRYPT_PREFIX + "$" + passwordHash
                    + "$" + "m=" + memory + "$" + "r=" + rounds + "$" + "s=" + saltSeparator;
            assertNull(ParsedFirebaseSCryptResponse.fromHashString(invalidPasswordHash));
        }

        {
            // missing memCost
            String invalidPasswordHash = "$" + ParsedFirebaseSCryptResponse.FIREBASE_SCRYPT_PREFIX + "$" + passwordHash
                    + "$" + salt + "$" + "r=" + rounds + "$" + "s=" + saltSeparator;
            assertNull(ParsedFirebaseSCryptResponse.fromHashString(invalidPasswordHash));

            // memCost as invalid type(string)
            String invalidPasswordHash_2 = "$" + ParsedFirebaseSCryptResponse.FIREBASE_SCRYPT_PREFIX + "$"
                    + passwordHash + "$" + salt + "$" + "m=" + "randomString" + "$" + "r=" + rounds + "$" + "s="
                    + saltSeparator;
            assertNull(ParsedFirebaseSCryptResponse.fromHashString(invalidPasswordHash_2));
        }

        {
            // missing rounds
            String invalidPasswordHash = "$" + ParsedFirebaseSCryptResponse.FIREBASE_SCRYPT_PREFIX + "$" + passwordHash
                    + "$" + salt + "$" + "m=" + memory + "$" + "s=" + saltSeparator;
            assertNull(ParsedFirebaseSCryptResponse.fromHashString(invalidPasswordHash));

            // rounds as invalid type(string)
            String invalidPasswordHash_2 = "$" + ParsedFirebaseSCryptResponse.FIREBASE_SCRYPT_PREFIX + "$"
                    + passwordHash + "$" + salt + "$" + "m=" + memory + "$" + "r=" + "randomString" + "$" + "s="
                    + saltSeparator;
            assertNull(ParsedFirebaseSCryptResponse.fromHashString(invalidPasswordHash_2));
        }

        {
            // missing saltSeparator
            String invalidPasswordHash = "$" + ParsedFirebaseSCryptResponse.FIREBASE_SCRYPT_PREFIX + "$" + passwordHash
                    + "$" + salt + "$" + "m=" + memory + "$" + "r=" + rounds;
            assertNull(ParsedFirebaseSCryptResponse.fromHashString(invalidPasswordHash));
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }
}
