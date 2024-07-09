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

import io.supertokens.ProcessState.PROCESS_STATE;
import io.supertokens.config.Config;
import io.supertokens.exceptions.UnauthorisedException;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.exceptions.StorageTransactionLogicException;
import io.supertokens.session.info.TokenInfo;
import io.supertokens.session.refreshToken.RefreshToken;
import io.supertokens.session.refreshToken.RefreshToken.RefreshTokenInfo;
import io.supertokens.session.refreshToken.RefreshToken.TYPE;
import io.supertokens.test.TestingProcessManager;
import io.supertokens.test.TestingProcessManager.TestingProcess;
import io.supertokens.test.Utils;
import io.supertokens.version.Version;
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
    public void encryptAndDecryptWorksWithSameKey()
            throws InvalidKeyException, NoSuchAlgorithmException, InvalidKeySpecException, NoSuchPaddingException,
            InvalidAlgorithmParameterException, IllegalBlockSizeException, BadPaddingException {
        String key = "1000"
                +
                ":79a6cbeb2066a3ab80f951037b90cc52bc216d9507998454184daeb3ef47cf387aab9c65e5fc69209fa6f0f67aee486c9d292cfc159a41c4b02415ba669f3219:d305504825a1b109";
        String message = "I am to be encrypted and then decrypted";
        String enc = io.supertokens.utils.Utils.encrypt(message, key);
        String dec = io.supertokens.utils.Utils.decrypt(enc, key);
        assertEquals(dec, message);
    }

    @Test
    public void encryptAndDecryptDoesNotWorksWithDifferentKey()
            throws InvalidKeyException, NoSuchAlgorithmException, InvalidKeySpecException, NoSuchPaddingException,
            InvalidAlgorithmParameterException, IllegalBlockSizeException, BadPaddingException {
        String key = "1000"
                +
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
        assertEquals("V2", TYPE.FREE_OPTIMISED.toString());
        assertSame(TYPE.fromString("V0"), TYPE.FREE);
        assertSame(TYPE.fromString("V1"), TYPE.PAID);
        assertSame(TYPE.fromString("V2"), TYPE.FREE_OPTIMISED);
        assertNull(TYPE.fromString("random"));
    }

    @Test
    public void createRefreshTokenAndLoadAfterProcessRestart()
            throws InterruptedException, InvalidKeyException, NoSuchAlgorithmException, InvalidKeySpecException,
            NoSuchPaddingException, InvalidAlgorithmParameterException, IllegalBlockSizeException, BadPaddingException,
            StorageQueryException, StorageTransactionLogicException, UnauthorisedException {
        String[] args = {"../"};
        TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STARTED));

        TokenInfo tokenInfo = RefreshToken.createNewRefreshToken(process.getProcess(), "sessionHandle", "userId",
                "parentRefreshTokenHash1", "antiCsrfToken");

        process.kill(false);
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STOPPED));

        if (Version.getVersion(process.getProcess()).getPluginName().equals("sqlite")) {
            // in mem db cannot pass this test
            return;
        }

        process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STARTED));

        RefreshTokenInfo infoFromToken = RefreshToken.getInfoFromRefreshToken(process.getProcess(), tokenInfo.token);
        assertEquals("parentRefreshTokenHash1", infoFromToken.parentRefreshTokenHash1);
        assertEquals("userId", infoFromToken.userId);
        assertEquals("sessionHandle", infoFromToken.sessionHandle);
        assertEquals("antiCsrfToken", infoFromToken.antiCsrfToken);
        assertNull(infoFromToken.parentRefreshTokenHash2);
        assertSame(infoFromToken.type, TYPE.FREE_OPTIMISED);
        // -5000 for some grace period for creation and checking above
        assertTrue(tokenInfo.expiry > System.currentTimeMillis()
                + Config.getConfig(process.getProcess()).getRefreshTokenValidityInMillis() - 5000);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STOPPED));

    }

    @Test
    public void createRefreshTokenButVerifyWithDifferentSigningKeyFailure()
            throws InterruptedException, InvalidKeyException, NoSuchAlgorithmException, InvalidKeySpecException,
            NoSuchPaddingException, InvalidAlgorithmParameterException, IllegalBlockSizeException, BadPaddingException,
            StorageQueryException, StorageTransactionLogicException {
        String[] args = {"../"};
        TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STARTED));

        TokenInfo tokenInfo = RefreshToken.createNewRefreshToken(process.getProcess(), "sessionHandle", "userId",
                "parentRefreshTokenHash1", "antiCsrfToken");

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STOPPED));

        Utils.reset();

        process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STARTED));

        try {
            RefreshToken.getInfoFromRefreshToken(process.getProcess(), tokenInfo.token);
        } catch (UnauthorisedException e) {
            assertEquals("javax.crypto.AEADBadTagException: Tag mismatch!", e.getMessage());
            return;
        }
        fail();
    }

}
