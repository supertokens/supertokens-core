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

import io.supertokens.ProcessState.PROCESS_STATE;
import io.supertokens.config.Config;
import io.supertokens.exceptions.UnauthorisedException;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.session.info.TokenInfo;
import io.supertokens.session.refreshToken.RefreshToken;
import io.supertokens.session.refreshToken.RefreshToken.RefreshTokenInfo;
import io.supertokens.session.refreshToken.RefreshToken.TYPE;
import io.supertokens.test.TestingProcessManager.TestingProcess;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import javax.crypto.AEADBadTagException;
import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;

import static org.junit.Assert.*;

public class RefreshTokenTest {
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
    public void encryptAndDecryptWorksWithSameKey() throws InvalidKeyException, NoSuchAlgorithmException,
            InvalidKeySpecException, NoSuchPaddingException, InvalidAlgorithmParameterException,
            IllegalBlockSizeException, BadPaddingException {
        String key = "1000" +
                ":79a6cbeb2066a3ab80f951037b90cc52bc216d9507998454184daeb3ef47cf387aab9c65e5fc69209fa6f0f67aee486c9d292cfc159a41c4b02415ba669f3219:d305504825a1b109";
        String message = "I am to be encrypted and then decrypted";
        String enc = io.supertokens.utils.Utils.encrypt(message, key);
        String dec = io.supertokens.utils.Utils.decrypt(enc, key);
        assertEquals(dec, message);
    }

    @Test
    public void encryptAndDecryptDoesNotWorksWithDifferentKey() throws InvalidKeyException, NoSuchAlgorithmException,
            InvalidKeySpecException, NoSuchPaddingException, InvalidAlgorithmParameterException,
            IllegalBlockSizeException, BadPaddingException {
        String key = "1000" +
                ":79a6cbeb2066a3ab80f951037b90cc52bc216d9507998454184daeb3ef47cf387aab9c65e5fc69209fa6f0f67aee486c9d292cfc159a41c4b02415ba669f3219:d305504825a1b109";
        String message = "I am to be encrypted and then decrypted";
        String enc = io.supertokens.utils.Utils.encrypt(message, key);
        try {
            io.supertokens.utils.Utils.decrypt(enc, "key2");
        } catch (AEADBadTagException e) {
            return;
        }
        fail();
    }

    @Test
    public void freePaidVersionTest() {
        assertEquals("V0", TYPE.FREE.toString());
        assertEquals("V1", TYPE.PAID.toString());
        assertSame(TYPE.fromString("V0"), TYPE.FREE);
        assertSame(TYPE.fromString("V1"), TYPE.PAID);
        assertNull(TYPE.fromString("random"));
    }

    @Test
    public void createRefreshTokenAndLoadAfterProcessRestart()
            throws InterruptedException, NoSuchAlgorithmException,
            StorageQueryException, UnauthorisedException {
        String[] args = {"../", "DEV"};
        TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STARTED));

        TokenInfo tokenInfo = RefreshToken.createNewRefreshToken(process.getProcess(), "sessionHandle", null);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STOPPED));

        process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STARTED));

        RefreshTokenInfo infoFromToken = RefreshToken.getInfoFromRefreshToken(process.getProcess(), tokenInfo.token);
        assertNull(infoFromToken.userId);
        assertEquals("sessionHandle", infoFromToken.sessionHandle);
        assertNotNull(infoFromToken.parentRefreshTokenHash2);
        assertSame(infoFromToken.type, TYPE.FREE);
        // -5000 for some grace period for creation and checking above
        assertTrue(tokenInfo.expiry > System.currentTimeMillis()
                + Config.getConfig(process.getProcess()).getRefreshTokenValidity() - 5000);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STOPPED));

    }

}
