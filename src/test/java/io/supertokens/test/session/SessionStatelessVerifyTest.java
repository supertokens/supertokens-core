/*
 *    Copyright (c) 2024, VRAI Labs and/or its affiliates. All rights reserved.
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

import com.google.gson.JsonObject;
import io.supertokens.Main;
import io.supertokens.ProcessState;
import io.supertokens.exceptions.UnauthorisedException;
import io.supertokens.pluginInterface.session.SessionInfo;
import io.supertokens.pluginInterface.session.sqlStorage.SessionSQLStorage;
import io.supertokens.session.Session;
import io.supertokens.session.accessToken.AccessToken;
import io.supertokens.session.info.SessionInformationHolder;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.test.TestingProcessManager;
import io.supertokens.test.Utils;
import io.supertokens.utils.SemVer;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Exercises the CDI >= 5.5 stateless session verification (PLAN-002 unit 6, decisions 5-6): verify performs no
 * DB write and mints no replacement token on any path, the {@code parentRefreshTokenHash1 == null} precondition
 * on the stateless early-return is dropped, {@code checkDatabase} verifies reject rotated-out token branches with
 * Unauthorised (option J) and surface payload staleness via a read-only {@code payloadUpdateAvailable} flag
 * instead of swapping tokens. CDI <= 5.4 verify behaviour is asserted byte-identical (still promotes and mints).
 *
 * The new behaviour is driven by passing {@link SemVer#v5_5} directly to
 * {@link Session#getSession(Main, String, String, boolean, Boolean, boolean, SemVer)} - core does not yet
 * advertise CDI 5.5 over HTTP, so it is reached only through direct calls here.
 */
public class SessionStatelessVerifyTest {

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

    private static String hash2(String token) throws Exception {
        return io.supertokens.utils.Utils.hashSHA256(io.supertokens.utils.Utils.hashSHA256(token));
    }

    private static SessionInformationHolder createSession(Main main) throws Exception {
        JsonObject jwt = new JsonObject();
        jwt.addProperty("k", "v");
        JsonObject db = new JsonObject();
        db.addProperty("k", "v");
        return Session.createNewSession(main, "userId", jwt, db);
    }

    private static SessionInformationHolder refresh5_4(Main main, SessionInformationHolder from) throws Exception {
        return Session.refreshSession(main, from.refreshToken.token, from.antiCsrfToken, false,
                AccessToken.getLatestVersion());
    }

    private static SessionInformationHolder refresh5_5(Main main, SessionInformationHolder from) throws Exception {
        return Session.refreshSession(main, from.refreshToken.token, from.antiCsrfToken, false,
                AccessToken.getLatestVersion(), SemVer.v5_5);
    }

    private static SessionInformationHolder verify(Main main, String accessToken, boolean checkDatabase,
                                                   SemVer cdiVersion) throws Exception {
        return Session.getSession(main, accessToken, null, false, false, checkDatabase, cdiVersion);
    }

    // A CDI 5.5 verify of an un-promoted child (an access token that under CDI <= 5.4 would promote and mint a
    // replacement) neither writes the DB nor returns a new token.
    @Test
    public void verifyMakesNoWriteAndMintsNoTokenForUnpromotedChild() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));
        Main main = process.getProcess();

        SessionInformationHolder s = createSession(main);
        String handle = s.session.handle;
        SessionSQLStorage storage = (SessionSQLStorage) StorageLayer.getStorage(main);

        // A legacy (CDI <= 5.4) refresh produces an un-promoted child: the DB stays pointing at the original
        // refresh token, and the minted access token carries a non-null parentRefreshTokenHash1.
        SessionInformationHolder r = refresh5_4(main, s);
        assertEquals(hash2(s.refreshToken.token), storage.getSession(process.getAppForTesting(), handle)
                .refreshTokenHash2);
        AccessToken.AccessTokenInfo at = AccessToken.getInfoFromAccessTokenWithoutVerifying(r.accessToken.token);
        assertNotNull(at.parentRefreshTokenHash1);

        SessionInformationHolder verified = verify(main, r.accessToken.token, true, SemVer.v5_5);

        // No replacement token minted...
        assertNull(verified.accessToken);
        assertNull(verified.refreshToken);
        // ...and no promotion write: the DB still points at the original refresh token.
        assertEquals(hash2(s.refreshToken.token), storage.getSession(process.getAppForTesting(), handle)
                .refreshTokenHash2);
        assertNull(storage.getSession(process.getAppForTesting(), handle).prevRefreshTokenHash2);
        // The mint code path was never entered.
        assertNull(ProcessState.getInstance(main)
                .getLastEventByName(ProcessState.PROCESS_STATE.GET_SESSION_NEW_TOKENS));

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    // The stateless early-return no longer requires parentRefreshTokenHash1 == null: a token with a non-null
    // parent hash short-circuits on a checkDatabase=false verify without minting.
    @Test
    public void statelessEarlyReturnRegardlessOfParentHash() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));
        Main main = process.getProcess();

        SessionInformationHolder s = createSession(main);
        SessionInformationHolder r = refresh5_4(main, s); // un-promoted child, parentRefreshTokenHash1 != null
        assertNotNull(AccessToken.getInfoFromAccessTokenWithoutVerifying(r.accessToken.token).parentRefreshTokenHash1);

        SessionInformationHolder verified = verify(main, r.accessToken.token, false, SemVer.v5_5);
        assertNull(verified.accessToken);
        assertEquals(s.session.handle, verified.session.handle);
        // checkDatabase=false performs no read, so no payload flag is computed.
        assertNull(verified.payloadUpdateAvailable);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    // When the stored payload differs from the token's, a checkDatabase verify reports payloadUpdateAvailable=true
    // as a read-only flag (H3) and mints no token; the returned session keeps the token's (old) payload.
    @Test
    public void payloadUpdateAvailableTrueWhenStoredPayloadDiffers() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));
        Main main = process.getProcess();

        SessionInformationHolder s = createSession(main);
        String handle = s.session.handle;

        JsonObject newJwt = new JsonObject();
        newJwt.addProperty("k", "v2");
        Session.updateSession(main, handle, null, newJwt, AccessToken.getLatestVersion());

        SessionInformationHolder verified = verify(main, s.accessToken.token, true, SemVer.v5_5);

        assertNull(verified.accessToken); // no implicit token swap
        assertEquals(Boolean.TRUE, verified.payloadUpdateAvailable);
        // The returned session carries the token's original payload (not the updated stored one).
        assertEquals("v", verified.session.userDataInJWT.get("k").getAsString());
        // And the serialized response carries the flag.
        assertTrue(verified.toJsonObject().get("payloadUpdateAvailable").getAsBoolean());
        assertNull(ProcessState.getInstance(main)
                .getLastEventByName(ProcessState.PROCESS_STATE.GET_SESSION_NEW_TOKENS));

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    // When the stored payload matches, the flag is present and false; no token is minted.
    @Test
    public void payloadUpdateAvailableFalseWhenPayloadMatches() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));
        Main main = process.getProcess();

        SessionInformationHolder s = createSession(main);

        SessionInformationHolder verified = verify(main, s.accessToken.token, true, SemVer.v5_5);
        assertNull(verified.accessToken);
        assertEquals(Boolean.FALSE, verified.payloadUpdateAvailable);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    // Option J: a checkDatabase verify of a token whose refresh lineage matches neither current nor prev is
    // rejected with Unauthorised (forcing a refresh). The same token verifies fine without checkDatabase.
    @Test
    public void forkRejectionRejectsRotatedOutBranch() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));
        Main main = process.getProcess();

        SessionInformationHolder s = createSession(main);
        SessionInformationHolder r1 = refresh5_5(main, s); // current = r1, prev = s
        refresh5_5(main, r1);                              // current = r2, prev = r1 -> s is now two generations old

        // s's access token lineage is neither current nor prev, and it has no parent hash -> Unauthorised.
        try {
            verify(main, s.accessToken.token, true, SemVer.v5_5);
            fail("expected Unauthorised for rotated-out branch");
        } catch (UnauthorisedException e) {
            assertNull(e.reuseSubtype); // not a theft classification; just forces a refresh
        }

        // Without checkDatabase there is no fork check: the stateless early-return accepts it and mints nothing.
        SessionInformationHolder stateless = verify(main, s.accessToken.token, false, SemVer.v5_5);
        assertNull(stateless.accessToken);
        assertEquals(s.session.handle, stateless.session.handle);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    // Option J accepts an in-flight token of the immediately previous generation (lineage == prev): a benign
    // parallel request during a rotation must not be forced to refresh.
    @Test
    public void forkRejectionAcceptsInFlightPrevGeneration() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));
        Main main = process.getProcess();

        SessionInformationHolder s = createSession(main);
        refresh5_5(main, s); // current = r1, prev = s -> s's access token lineage == prev

        SessionInformationHolder verified = verify(main, s.accessToken.token, true, SemVer.v5_5);
        assertNull(verified.accessToken);
        assertEquals(Boolean.FALSE, verified.payloadUpdateAvailable);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    // Guardrail: CDI <= 5.4 verify stays byte-identical - an un-promoted child still promotes (DB write) and
    // mints a replacement access token.
    @Test
    public void legacyVerifyStillPromotesAndMints() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));
        Main main = process.getProcess();

        SessionInformationHolder s = createSession(main);
        String handle = s.session.handle;
        SessionSQLStorage storage = (SessionSQLStorage) StorageLayer.getStorage(main);

        SessionInformationHolder r = refresh5_4(main, s); // un-promoted child
        assertEquals(hash2(s.refreshToken.token), storage.getSession(process.getAppForTesting(), handle)
                .refreshTokenHash2);

        SessionInformationHolder verified = verify(main, r.accessToken.token, true, SemVer.v5_4);

        // Legacy verify mints a replacement and promotes the DB to the child.
        assertNotNull(verified.accessToken);
        assertNull(verified.payloadUpdateAvailable);
        assertNotNull(ProcessState.getInstance(main)
                .getLastEventByName(ProcessState.PROCESS_STATE.GET_SESSION_NEW_TOKENS));
        SessionInfo after = storage.getSession(process.getAppForTesting(), handle);
        assertEquals(hash2(r.refreshToken.token), after.refreshTokenHash2);
        // Dual-write invariant (unit 5): the promote also records prev + rotated_at.
        assertEquals(hash2(s.refreshToken.token), after.prevRefreshTokenHash2);
        assertNotNull(after.refreshTokenRotatedAt);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }
}
