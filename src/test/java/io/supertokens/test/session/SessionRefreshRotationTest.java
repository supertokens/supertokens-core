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
import io.supertokens.exceptions.RefreshTokenReuseSubtype;
import io.supertokens.exceptions.TokenTheftDetectedException;
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
 * Exercises the CDI >= 5.5 refresh-time token rotation state machine (PLAN-002 unit 5, decisions 1-4):
 * immediate rotation with prev/rotated_at recording, the re-rotating grace window, and out-of-window
 * reuse classification (RECENT_PREV / ORPHANED_BRANCH / STALE_LINEAGE) with server-side revocation.
 *
 * The new behaviour is driven by passing {@link SemVer#v5_5} directly to
 * {@link Session#refreshSession(Main, String, String, boolean, AccessToken.VERSION, SemVer)} - core does not
 * yet advertise CDI 5.5 over HTTP (see the PR description), so it is reached only through direct calls here.
 */
public class SessionRefreshRotationTest {

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

    private static SessionInformationHolder refresh(Main main, SessionInformationHolder from) throws Exception {
        return Session.refreshSession(main, from.refreshToken.token, from.antiCsrfToken, false,
                AccessToken.getLatestVersion(), SemVer.v5_5);
    }

    // Case 1: a normal rotation records prev := retired hash and rotated_at, advances current, and the minted
    // access token carries no parent lineage.
    @Test
    public void normalRotationRecordsPrevAndRotatedAt() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));
        Main main = process.getProcess();

        SessionInformationHolder s = createSession(main);
        String handle = s.session.handle;
        SessionSQLStorage storage = (SessionSQLStorage) StorageLayer.getStorage(main);

        SessionInfo before = storage.getSession(process.getAppForTesting(), handle);
        assertNull(before.prevRefreshTokenHash2);
        assertNull(before.refreshTokenRotatedAt);

        long t0 = System.currentTimeMillis();
        SessionInformationHolder refreshed = refresh(main, s);
        assertNotNull(refreshed.refreshToken);

        SessionInfo after = storage.getSession(process.getAppForTesting(), handle);
        assertEquals(hash2(s.refreshToken.token), after.prevRefreshTokenHash2);
        assertNotNull(after.refreshTokenRotatedAt);
        assertTrue(after.refreshTokenRotatedAt >= t0);
        assertEquals(hash2(refreshed.refreshToken.token), after.refreshTokenHash2);

        AccessToken.AccessTokenInfo at = AccessToken.getInfoFromAccessTokenWithoutVerifying(
                refreshed.accessToken.token);
        assertNull(at.parentRefreshTokenHash1);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    // Case 3: presenting the window root again inside the grace window re-rotates (new working tokens), leaves
    // prev/rotated_at untouched, and emits grace telemetry.
    @Test
    public void graceWindowReRotation() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args); // default grace 30s
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));
        Main main = process.getProcess();

        SessionInformationHolder s = createSession(main);
        String handle = s.session.handle;
        SessionSQLStorage storage = (SessionSQLStorage) StorageLayer.getStorage(main);

        SessionInformationHolder r1 = refresh(main, s); // opens grace window on s.refreshToken
        Long rotatedAtAfterFirst = storage.getSession(process.getAppForTesting(), handle).refreshTokenRotatedAt;

        // present the window root (s.refreshToken) again -> re-rotation
        SessionInformationHolder r2 = refresh(main, s);
        assertNotNull(r2.refreshToken);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.REFRESH_TOKEN_GRACE_PERIOD_HIT));

        SessionInfo after = storage.getSession(process.getAppForTesting(), handle);
        // prev + rotated_at unchanged; current advanced to r2's token
        assertEquals(hash2(s.refreshToken.token), after.prevRefreshTokenHash2);
        assertEquals(rotatedAtAfterFirst, after.refreshTokenRotatedAt);
        assertEquals(hash2(r2.refreshToken.token), after.refreshTokenHash2);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    // Case 4 / RECENT_PREV: with the grace window closed, replaying the prev token is theft and revokes the session.
    @Test
    public void recentPrevReuseIsTheftAndRevokes() throws Exception {
        Utils.setValueInConfig("refresh_token_rotation_grace_period", "0"); // no grace window
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.startIsolatedProcess(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));
        Main main = process.getProcess();

        SessionInformationHolder s = createSession(main);
        String handle = s.session.handle;
        SessionSQLStorage storage = (SessionSQLStorage) StorageLayer.getStorage(main);

        refresh(main, s); // s.refreshToken becomes prev; grace = 0 so it is immediately out of window
        Thread.sleep(10); // ensure now > rotated_at so the window (length 0) is closed deterministically

        try {
            refresh(main, s);
            fail("expected token theft");
        } catch (TokenTheftDetectedException e) {
            assertEquals(RefreshTokenReuseSubtype.RECENT_PREV, e.reuseSubtype);
            assertEquals(handle, e.sessionHandle);
        }
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.REFRESH_TOKEN_REUSE_DETECTED));
        assertNull(storage.getSession(process.getAppForTesting(), handle)); // session revoked

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    // Case 4 / ORPHANED_BRANCH: the child displaced by an in-window re-rotation is dead; presenting it is theft.
    @Test
    public void orphanedBranchReuseIsTheftAndRevokes() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args); // default grace 30s
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));
        Main main = process.getProcess();

        SessionInformationHolder s = createSession(main);
        String handle = s.session.handle;
        SessionSQLStorage storage = (SessionSQLStorage) StorageLayer.getStorage(main);

        SessionInformationHolder r1 = refresh(main, s);  // current = r1, prev = s (grace open on s)
        refresh(main, s);                                // grace re-rotation: current = r2, r1 orphaned

        // r1 is a child displaced by the grace re-rotation -> ORPHANED_BRANCH
        try {
            refresh(main, r1);
            fail("expected token theft");
        } catch (TokenTheftDetectedException e) {
            assertEquals(RefreshTokenReuseSubtype.ORPHANED_BRANCH, e.reuseSubtype);
            assertEquals(handle, e.sessionHandle);
        }
        assertNull(storage.getSession(process.getAppForTesting(), handle)); // session revoked

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    // Decision 4: recent_token_reuse_behaviour=UNAUTHORISED reports RECENT_PREV as Unauthorised, but the session
    // is still revoked (the config alters reporting only, never enforcement).
    @Test
    public void recentPrevReportedAsUnauthorisedStillRevokes() throws Exception {
        Utils.setValueInConfig("refresh_token_rotation_grace_period", "0");
        Utils.setValueInConfig("recent_token_reuse_behaviour", "UNAUTHORISED");
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.startIsolatedProcess(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));
        Main main = process.getProcess();

        SessionInformationHolder s = createSession(main);
        String handle = s.session.handle;
        SessionSQLStorage storage = (SessionSQLStorage) StorageLayer.getStorage(main);

        refresh(main, s);
        Thread.sleep(10);

        try {
            refresh(main, s);
            fail("expected unauthorised");
        } catch (UnauthorisedException e) {
            assertEquals(RefreshTokenReuseSubtype.RECENT_PREV, e.reuseSubtype);
        }
        assertNull(storage.getSession(process.getAppForTesting(), handle)); // revoked regardless of reporting mode

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    // Decision 4: STALE_LINEAGE is always theft, even when recent_token_reuse_behaviour=UNAUTHORISED.
    @Test
    public void staleLineageIsAlwaysTheftEvenUnderUnauthorisedConfig() throws Exception {
        Utils.setValueInConfig("recent_token_reuse_behaviour", "UNAUTHORISED");
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.startIsolatedProcess(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));
        Main main = process.getProcess();

        SessionInformationHolder s = createSession(main);
        String handle = s.session.handle;
        SessionSQLStorage storage = (SessionSQLStorage) StorageLayer.getStorage(main);

        SessionInformationHolder r1 = refresh(main, s);   // prev = s
        refresh(main, r1);                                // prev = r1, current = r2 -> s is now stale lineage

        try {
            refresh(main, s);
            fail("expected token theft");
        } catch (TokenTheftDetectedException e) {
            assertEquals(RefreshTokenReuseSubtype.STALE_LINEAGE, e.reuseSubtype);
        }
        assertNull(storage.getSession(process.getAppForTesting(), handle)); // revoked

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }
}
