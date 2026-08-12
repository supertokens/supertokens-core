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
import io.supertokens.config.Config;
import io.supertokens.exceptions.AccessTokenValidityOutOfRangeException;
import io.supertokens.session.Session;
import io.supertokens.session.accessToken.AccessToken;
import io.supertokens.session.info.SessionInformationHolder;
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
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Exercises the optional per-mint access token validity override (PLAN-002 decision 11, the CDI >= 5.6
 * {@code accessTokenValidity} parameter on session create and refresh): the issued access token uses
 * {@code param ?? configured validity}, shorten-only (0 < param <= configured, out-of-range is a hard
 * rejection not a clamp), nothing is persisted, and the refresh token validity is unaffected.
 *
 * The refresh-side override is driven through the direct
 * {@link Session#refreshSession(Main, String, String, boolean, AccessToken.VERSION, SemVer, Long)} test overload
 * because core does not yet advertise CDI 5.6 over HTTP (see the PR description). An honoured override mints the
 * access token at exactly {@code now + param} and is never jittered; only the configured-validity fallback is
 * subject to {@code access_token_validity_jitter}. The two cases that assert against the configured validity pin
 * the jitter off so the lifetime is exact.
 */
public class AccessTokenValidityParamTest {

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

    private static SessionInformationHolder createSession(Main main, Long accessTokenValidity) throws Exception {
        JsonObject jwt = new JsonObject();
        jwt.addProperty("k", "v");
        JsonObject db = new JsonObject();
        db.addProperty("k", "v");
        return Session.createNewSession(main, "userId", jwt, db, accessTokenValidity);
    }

    private static long accessTokenLifetime(SessionInformationHolder s) {
        return s.accessToken.expiry - s.accessToken.createdTime;
    }

    private static long configuredValidityMs(TestingProcessManager.TestingProcess process) throws Exception {
        return Config.getConfig(process.getAppForTesting(), process.getProcess()).getAccessTokenValidityInMillis();
    }

    // A shorter override shortens the minted access token to exactly `param`; the configured validity is longer.
    @Test
    public void createSessionOverrideShortensAccessTokenExpiry() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));
        Main main = process.getProcess();

        long configured = configuredValidityMs(process);
        long override = 600 * 1000L; // 10 min, well under the default 1h
        assertTrue(override < configured);

        SessionInformationHolder s = createSession(main, override);
        assertEquals(override, accessTokenLifetime(s));
        // nothing about the override is persisted: the refresh token still uses the configured refresh validity
        assertEquals(Config.getConfig(process.getAppForTesting(), main).getRefreshTokenValidityInMillis(),
                s.refreshToken.expiry - s.refreshToken.createdTime);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    // The boundary value (param == configured validity) is accepted (range is inclusive on the top end).
    @Test
    public void createSessionOverrideEqualToConfiguredIsAllowed() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));
        Main main = process.getProcess();

        long configured = configuredValidityMs(process);
        SessionInformationHolder s = createSession(main, configured);
        assertEquals(configured, accessTokenLifetime(s));

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    // A null override keeps the configured validity unchanged (byte-identical to not passing the parameter).
    @Test
    public void createSessionNullOverrideUsesConfiguredValidity() throws Exception {
        String[] args = {"../"};
        Utils.setValueInConfig("access_token_validity_jitter", "0"); // pin off so the configured lifetime is exact
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));
        Main main = process.getProcess();

        long configured = configuredValidityMs(process);
        SessionInformationHolder s = createSession(main, null);
        assertEquals(configured, accessTokenLifetime(s));

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    // Above the configured validity is rejected outright (a 400 over HTTP), never clamped.
    @Test
    public void createSessionOverrideAboveConfiguredThrows() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));
        Main main = process.getProcess();

        long configured = configuredValidityMs(process);
        try {
            createSession(main, configured + 1000L);
            fail("expected AccessTokenValidityOutOfRangeException");
        } catch (AccessTokenValidityOutOfRangeException expected) {
            // ok
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    // Zero and negative overrides are rejected (range is 0 < param).
    @Test
    public void createSessionOverrideZeroOrNegativeThrows() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));
        Main main = process.getProcess();

        for (long bad : new long[]{0L, -1000L}) {
            try {
                createSession(main, bad);
                fail("expected AccessTokenValidityOutOfRangeException for " + bad);
            } catch (AccessTokenValidityOutOfRangeException expected) {
                // ok
            }
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    // CDI >= 5.6 refresh honours the override on the rotation mint; the refresh token expiry is not overridable.
    @Test
    public void refreshWithOverrideShortensAccessTokenExpiry() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));
        Main main = process.getProcess();

        long configured = configuredValidityMs(process);
        long override = 300 * 1000L; // 5 min
        assertTrue(override < configured);

        SessionInformationHolder s = createSession(main, null);
        SessionInformationHolder refreshed = Session.refreshSession(main, s.refreshToken.token, s.antiCsrfToken,
                false, AccessToken.getLatestVersion(), SemVer.v5_6, override);

        assertNotNull(refreshed.accessToken);
        assertEquals(override, accessTokenLifetime(refreshed));
        // refresh token validity is not overridable
        assertEquals(Config.getConfig(process.getAppForTesting(), main).getRefreshTokenValidityInMillis(),
                refreshed.refreshToken.expiry - refreshed.refreshToken.createdTime);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    // An out-of-range override on refresh is rejected before the session is touched.
    @Test
    public void refreshOverrideAboveConfiguredThrows() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));
        Main main = process.getProcess();

        long configured = configuredValidityMs(process);
        SessionInformationHolder s = createSession(main, null);
        try {
            Session.refreshSession(main, s.refreshToken.token, s.antiCsrfToken, false,
                    AccessToken.getLatestVersion(), SemVer.v5_6, configured + 1000L);
            fail("expected AccessTokenValidityOutOfRangeException");
        } catch (AccessTokenValidityOutOfRangeException expected) {
            // ok
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    // Below CDI 5.6 the override is not applied: the legacy refresh mint uses the configured validity. Asserted
    // at CDI 5.4 (a legacy version below the gate): the webserver only forwards the parameter at CDI >= 5.6, and
    // this shows the mint path itself never honours it below the gate.
    @Test
    public void refreshOverrideIgnoredBelowCdi5_6() throws Exception {
        String[] args = {"../"};
        Utils.setValueInConfig("access_token_validity_jitter", "0"); // pin off so the configured lifetime is exact
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));
        Main main = process.getProcess();

        long configured = configuredValidityMs(process);
        long override = 300 * 1000L;
        assertTrue(override < configured);

        SessionInformationHolder s = createSession(main, null);
        SessionInformationHolder refreshed = Session.refreshSession(main, s.refreshToken.token, s.antiCsrfToken,
                false, AccessToken.getLatestVersion(), SemVer.v5_4, override);

        assertNotNull(refreshed.accessToken);
        assertEquals(configured, accessTokenLifetime(refreshed));

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }
}
