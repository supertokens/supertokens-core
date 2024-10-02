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

import com.google.gson.JsonObject;
import io.supertokens.ProcessState;
import io.supertokens.exceptions.TryRefreshTokenException;
import io.supertokens.exceptions.UnauthorisedException;
import io.supertokens.pluginInterface.multitenancy.AppIdentifier;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifier;
import io.supertokens.pluginInterface.session.SessionStorage;
import io.supertokens.session.Session;
import io.supertokens.session.accessToken.AccessToken;
import io.supertokens.session.info.SessionInformationHolder;
import io.supertokens.session.jwt.JWT;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.test.TestingProcessManager;
import io.supertokens.test.Utils;
import org.junit.*;
import org.junit.rules.TestRule;

import static junit.framework.TestCase.*;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

public class SessionTest6 {

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
    public void createRefreshSwitchVerify() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        String userId = "userId";
        JsonObject userDataInJWT = new JsonObject();
        userDataInJWT.addProperty("key", "value");
        JsonObject userDataInDatabase = new JsonObject();
        userDataInDatabase.addProperty("key", "value");

        SessionInformationHolder sessionInfo = Session.createNewSession(process.getProcess(), userId, userDataInJWT,
                userDataInDatabase, false, AccessToken.getLatestVersion(), false);
        checkIfUsingStaticKey(sessionInfo, false);

        sessionInfo = Session.refreshSession(new AppIdentifier(null, null), process.getProcess(),
                sessionInfo.refreshToken.token,
                sessionInfo.antiCsrfToken, false, AccessToken.getLatestVersion(), true);
        assert sessionInfo.refreshToken != null;
        assert sessionInfo.accessToken != null;

        checkIfUsingStaticKey(sessionInfo, true);

        SessionInformationHolder verifiedSession = Session.getSession(process.getProcess(),
                sessionInfo.accessToken.token,
                sessionInfo.antiCsrfToken, false, true, false);

        checkIfUsingStaticKey(verifiedSession, true);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void createRefreshSwitchRegen() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        String userId = "userId";
        JsonObject userDataInJWT = new JsonObject();
        userDataInJWT.addProperty("key", "value");
        JsonObject userDataInDatabase = new JsonObject();
        userDataInDatabase.addProperty("key", "value");

        SessionInformationHolder sessionInfo = Session.createNewSession(process.getProcess(), userId, userDataInJWT,
                userDataInDatabase, false, AccessToken.getLatestVersion(), false);
        checkIfUsingStaticKey(sessionInfo, false);

        sessionInfo = Session.refreshSession(new AppIdentifier(null, null), process.getProcess(),
                sessionInfo.refreshToken.token,
                sessionInfo.antiCsrfToken, false, AccessToken.getLatestVersion(), true);
        assert sessionInfo.refreshToken != null;
        assert sessionInfo.accessToken != null;
        checkIfUsingStaticKey(sessionInfo, true);

        SessionInformationHolder newSessionInfo = Session.regenerateToken(process.getProcess(),
                sessionInfo.accessToken.token, userDataInJWT);
        checkIfUsingStaticKey(newSessionInfo, true);

        SessionInformationHolder getSessionResponse = Session.getSession(process.getProcess(),
                newSessionInfo.accessToken.token, sessionInfo.antiCsrfToken, false, true, false);
        checkIfUsingStaticKey(getSessionResponse, true);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void createRefreshRefreshSwitchVerify() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        String userId = "userId";
        JsonObject userDataInJWT = new JsonObject();
        userDataInJWT.addProperty("key", "value");
        JsonObject userDataInDatabase = new JsonObject();
        userDataInDatabase.addProperty("key", "value");

        SessionInformationHolder sessionInfo = Session.createNewSession(process.getProcess(), userId, userDataInJWT,
                userDataInDatabase, false, AccessToken.getLatestVersion(), false);
        checkIfUsingStaticKey(sessionInfo, false);

        sessionInfo = Session.refreshSession(new AppIdentifier(null, null), process.getProcess(),
                sessionInfo.refreshToken.token,
                sessionInfo.antiCsrfToken, false, AccessToken.getLatestVersion(), false);

        sessionInfo = Session.refreshSession(new AppIdentifier(null, null), process.getProcess(),
                sessionInfo.refreshToken.token,
                sessionInfo.antiCsrfToken, false, AccessToken.getLatestVersion(), true);
        assert sessionInfo.refreshToken != null;
        assert sessionInfo.accessToken != null;

        checkIfUsingStaticKey(sessionInfo, true);

        SessionInformationHolder verifiedSession = Session.getSession(process.getProcess(),
                sessionInfo.accessToken.token,
                sessionInfo.antiCsrfToken, false, true, false);

        checkIfUsingStaticKey(verifiedSession, true);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void createRefreshRefreshSwitchRegen() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        String userId = "userId";
        JsonObject userDataInJWT = new JsonObject();
        userDataInJWT.addProperty("key", "value");
        JsonObject userDataInDatabase = new JsonObject();
        userDataInDatabase.addProperty("key", "value");

        SessionInformationHolder sessionInfo = Session.createNewSession(process.getProcess(), userId, userDataInJWT,
                userDataInDatabase, false, AccessToken.getLatestVersion(), false);
        checkIfUsingStaticKey(sessionInfo, false);

        sessionInfo = Session.refreshSession(new AppIdentifier(null, null), process.getProcess(),
                sessionInfo.refreshToken.token,
                sessionInfo.antiCsrfToken, false, AccessToken.getLatestVersion(), false);

        sessionInfo = Session.refreshSession(new AppIdentifier(null, null), process.getProcess(),
                sessionInfo.refreshToken.token,
                sessionInfo.antiCsrfToken, false, AccessToken.getLatestVersion(), true);
        assert sessionInfo.refreshToken != null;
        assert sessionInfo.accessToken != null;
        checkIfUsingStaticKey(sessionInfo, true);

        SessionInformationHolder newSessionInfo = Session.regenerateToken(process.getProcess(),
                sessionInfo.accessToken.token, userDataInJWT);
        checkIfUsingStaticKey(newSessionInfo, true);

        SessionInformationHolder getSessionResponse = Session.getSession(process.getProcess(),
                newSessionInfo.accessToken.token, sessionInfo.antiCsrfToken, false, true, false);
        checkIfUsingStaticKey(getSessionResponse, true);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    private static void checkIfUsingStaticKey(SessionInformationHolder info, boolean shouldBeStatic)
            throws JWT.JWTException {
        assert info.accessToken != null;
        JWT.JWTPreParseInfo tokenInfo = JWT.preParseJWTInfo(info.accessToken.token);
        assert tokenInfo.kid != null;
        if (shouldBeStatic) {
            assert tokenInfo.kid.startsWith("s-");
        } else {
            assert tokenInfo.kid.startsWith("d-");
        }
    }

}

