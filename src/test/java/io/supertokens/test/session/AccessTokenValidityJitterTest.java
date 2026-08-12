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
import io.supertokens.ProcessState;
import io.supertokens.config.Config;
import io.supertokens.session.Session;
import io.supertokens.session.accessToken.AccessToken;
import io.supertokens.session.info.SessionInformationHolder;
import io.supertokens.test.TestingProcessManager;
import io.supertokens.test.Utils;
import org.junit.*;
import org.junit.rules.TestRule;

import static org.junit.Assert.*;

/**
 * Behaviour tests for {@code access_token_validity_jitter} (PLAN-002 unit 7). The jitter shortens
 * a freshly minted access token's validity by up to {@code jitter * validity}, subtract-only, at
 * session creation and refresh. Regenerate is exempt (covered by RegenerateTokenTest) and verify
 * does not re-roll it.
 */
public class AccessTokenValidityJitterTest {

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

    private SessionInformationHolder createSession(TestingProcessManager.TestingProcess process) throws Exception {
        JsonObject userDataInJWT = new JsonObject();
        userDataInJWT.addProperty("key", "value");
        return Session.createNewSession(process.getProcess(), "userId", userDataInJWT, new JsonObject());
    }

    // jitter = 0 disables the feature: the token lifetime is exactly the configured validity.
    @Test
    public void jitterZeroKeepsFullValidity() throws Exception {
        Utils.setValueInConfig("access_token_validity_jitter", "0");

        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        long validity = Config.getConfig(process.getProcess()).getAccessTokenValidityInMillis();

        for (int i = 0; i < 20; i++) {
            SessionInformationHolder sessionInfo = createSession(process);
            assertEquals(validity, sessionInfo.accessToken.expiry - sessionInfo.accessToken.createdTime);
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    // jitter > 0 shortens the token at session creation, only ever within [validity*(1-jitter), validity].
    @Test
    public void jitterShortensWithinBandAtSessionCreation() throws Exception {
        Utils.setValueInConfig("access_token_validity_jitter", "0.25");

        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        double jitter = Config.getConfig(process.getProcess()).getAccessTokenValidityJitter();
        long validity = Config.getConfig(process.getProcess()).getAccessTokenValidityInMillis();
        assertEquals(0.25, jitter, 0);

        long minLife = Long.MAX_VALUE;
        long maxLife = Long.MIN_VALUE;
        for (int i = 0; i < 100; i++) {
            SessionInformationHolder sessionInfo = createSession(process);
            long life = sessionInfo.accessToken.expiry - sessionInfo.accessToken.createdTime;
            // subtract-only, with 1s slack for the second-truncation of expiry and createdTime
            assertTrue("token lengthened beyond validity: " + life,
                    life <= validity + 1000);
            assertTrue("token shortened beyond the jitter band: " + life,
                    life >= (long) (validity * (1 - jitter)) - 1000);
            minLife = Math.min(minLife, life);
            maxLife = Math.max(maxLife, life);
        }
        // the jitter is actually applied and randomized: at least one token was shortened well past
        // half the maximum jitter, and the sampled lifetimes vary.
        assertTrue("jitter never shortened the token: " + minLife,
                minLife < (long) (validity * (1 - jitter / 2)));
        assertTrue("jitter produced no variation across mints", maxLife > minLife);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    // the jitter is applied on the refresh path too.
    @Test
    public void jitterAppliedAtRefresh() throws Exception {
        Utils.setValueInConfig("access_token_validity_jitter", "0.25");

        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        double jitter = Config.getConfig(process.getProcess()).getAccessTokenValidityJitter();
        long validity = Config.getConfig(process.getProcess()).getAccessTokenValidityInMillis();

        SessionInformationHolder sessionInfo = createSession(process);
        String refreshToken = sessionInfo.refreshToken.token;

        long minLife = Long.MAX_VALUE;
        long maxLife = Long.MIN_VALUE;
        for (int i = 0; i < 100; i++) {
            SessionInformationHolder refreshed = Session.refreshSession(process.getProcess(), refreshToken, null,
                    false, AccessToken.getLatestVersion());
            long life = refreshed.accessToken.expiry - refreshed.accessToken.createdTime;
            assertTrue("token lengthened beyond validity: " + life, life <= validity + 1000);
            assertTrue("token shortened beyond the jitter band: " + life,
                    life >= (long) (validity * (1 - jitter)) - 1000);
            minLife = Math.min(minLife, life);
            maxLife = Math.max(maxLife, life);
            refreshToken = refreshed.refreshToken.token;
        }
        assertTrue("jitter never shortened the refreshed token: " + minLife,
                minLife < (long) (validity * (1 - jitter / 2)));
        assertTrue("jitter produced no variation across refreshes", maxLife > minLife);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    // the default configuration (0.05) has the jitter enabled out of the box.
    @Test
    public void defaultConfigAppliesJitter() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        double jitter = Config.getConfig(process.getProcess()).getAccessTokenValidityJitter();
        long validity = Config.getConfig(process.getProcess()).getAccessTokenValidityInMillis();
        assertEquals(0.05, jitter, 0);

        long minLife = Long.MAX_VALUE;
        for (int i = 0; i < 100; i++) {
            SessionInformationHolder sessionInfo = createSession(process);
            long life = sessionInfo.accessToken.expiry - sessionInfo.accessToken.createdTime;
            assertTrue("token lengthened beyond validity: " + life, life <= validity + 1000);
            assertTrue("token shortened beyond the jitter band: " + life,
                    life >= (long) (validity * (1 - jitter)) - 1000);
            minLife = Math.min(minLife, life);
        }
        assertTrue("default jitter never shortened the token: " + minLife,
                minLife < (long) (validity * (1 - jitter / 2)));

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }
}
