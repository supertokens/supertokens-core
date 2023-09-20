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

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.supertokens.Main;
import io.supertokens.ProcessState;
import io.supertokens.exceptions.TokenTheftDetectedException;
import io.supertokens.exceptions.UnauthorisedException;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifier;
import io.supertokens.pluginInterface.session.SessionStorage;
import io.supertokens.session.Session;
import io.supertokens.session.accessToken.AccessToken;
import io.supertokens.session.info.SessionInformationHolder;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.test.TestingProcessManager;
import io.supertokens.test.Utils;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import static junit.framework.TestCase.assertEquals;
import static org.junit.Assert.*;

public class SessionTest2 {

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
    public void tokenTheft_S1_R1_S2_R1() throws Exception {

        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        Main main = process.getProcess();

        String userId = "userId";
        JsonObject userDataInJWT = new JsonObject();
        userDataInJWT.addProperty("key", "value");
        JsonObject userDataInDatabase = new JsonObject();
        userDataInDatabase.addProperty("key", "value");

        SessionInformationHolder sessionInfo = Session.createNewSession(main, userId, userDataInJWT,
                userDataInDatabase);
        assert sessionInfo.refreshToken != null;
        assert sessionInfo.accessToken != null;

        SessionInformationHolder newRefreshedSession = Session.refreshSession(main, sessionInfo.refreshToken.token,
                sessionInfo.antiCsrfToken, false, AccessToken.getLatestVersion());
        assert newRefreshedSession.refreshToken != null;
        assert newRefreshedSession.accessToken != null;

        SessionInformationHolder sessionObj = Session.getSession(main, newRefreshedSession.accessToken.token,
                newRefreshedSession.antiCsrfToken, false, true, false);
        assert sessionObj.accessToken != null;
        assertNotEquals(sessionObj.accessToken.token, newRefreshedSession.accessToken.token);

        try {
            Session.refreshSession(main, sessionInfo.refreshToken.token, sessionInfo.antiCsrfToken, false,
                    AccessToken.getLatestVersion());
        } catch (TokenTheftDetectedException e) {
            assertEquals(e.sessionHandle, sessionInfo.session.handle);
            assertEquals(e.recipeUserId, sessionInfo.session.userId);
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));

    }

    @Test
    public void tokenTheft_S1_R1_R2_R1() throws Exception {

        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        Main main = process.getProcess();

        String userId = "userId";
        JsonObject userDataInJWT = new JsonObject();
        userDataInJWT.addProperty("key", "value");
        JsonObject userDataInDatabase = new JsonObject();
        userDataInDatabase.addProperty("key", "value");

        SessionInformationHolder sessionInfo = Session.createNewSession(main, userId, userDataInJWT,
                userDataInDatabase);
        assert sessionInfo.refreshToken != null;
        assert sessionInfo.accessToken != null;

        SessionInformationHolder newRefreshedSession1 = Session.refreshSession(main, sessionInfo.refreshToken.token,
                sessionInfo.antiCsrfToken, false, AccessToken.getLatestVersion());
        assert newRefreshedSession1.refreshToken != null;
        assert newRefreshedSession1.accessToken != null;

        SessionInformationHolder newRefreshedSession2 = Session.refreshSession(main,
                newRefreshedSession1.refreshToken.token, newRefreshedSession1.antiCsrfToken, false,
                AccessToken.getLatestVersion());
        assert newRefreshedSession2.refreshToken != null;
        assert newRefreshedSession2.accessToken != null;

        try {
            Session.refreshSession(main, sessionInfo.refreshToken.token, sessionInfo.antiCsrfToken, false,
                    AccessToken.getLatestVersion());
        } catch (TokenTheftDetectedException e) {
            assertEquals(e.sessionHandle, sessionInfo.session.handle);
            assertEquals(e.recipeUserId, sessionInfo.session.userId);
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));

    }

    @Test
    public void updateSessionInfo() throws Exception {

        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        String userId = "userId";
        JsonObject userDataInJWT = new JsonObject();
        userDataInJWT.addProperty("key", "value");
        JsonObject userDataInDatabase = new JsonObject();
        userDataInDatabase.addProperty("key", "value");

        SessionInformationHolder sessionInfo = Session.createNewSession(process.getProcess(), userId, userDataInJWT,
                userDataInDatabase);

        JsonObject sessionDataBeforeUpdate = Session.getSessionData(process.getProcess(), sessionInfo.session.handle);
        assertEquals(userDataInDatabase.toString(), sessionDataBeforeUpdate.toString());

        JsonObject userDataInDatabase2 = new JsonObject();
        userDataInDatabase2.addProperty("key1", "value1");
        userDataInDatabase2.addProperty("key2", 1);
        JsonArray arr = new JsonArray();
        userDataInDatabase2.add("key3", arr);

        Session.updateSession(process.getProcess(), sessionInfo.session.handle, userDataInDatabase2, null,
                AccessToken.getLatestVersion());

        JsonObject sessionDataAfterUpdate = Session.getSessionData(process.getProcess(), sessionInfo.session.handle);
        assertEquals(userDataInDatabase2.toString(), sessionDataAfterUpdate.toString());
        assertNotEquals(sessionDataBeforeUpdate.toString(), sessionDataAfterUpdate.toString());

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));

    }

    @Test
    public void revokeSessionWithoutBlacklisting() throws Exception {

        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        String userId = "userId";
        JsonObject userDataInJWT = new JsonObject();
        userDataInJWT.addProperty("key", "value");
        JsonObject userDataInDatabase = new JsonObject();
        userDataInDatabase.addProperty("key", "value");

        SessionInformationHolder sessionInfo = Session.createNewSession(process.getProcess(), userId, userDataInJWT,
                userDataInDatabase);
        assert sessionInfo.refreshToken != null;
        assert sessionInfo.accessToken != null;

        Session.createNewSession(process.getProcess(), userId, userDataInJWT, userDataInDatabase);

        assertEquals(((SessionStorage) StorageLayer.getStorage(process.getProcess()))
                .getNumberOfSessions(new TenantIdentifier(null, null, null)), 2);

        Session.revokeSessionUsingSessionHandles(process.getProcess(), new String[]{sessionInfo.session.handle});
        assertEquals(((SessionStorage) StorageLayer.getStorage(process.getProcess()))
                .getNumberOfSessions(new TenantIdentifier(null, null, null)), 1);

        try {
            Session.refreshSession(process.getProcess(), sessionInfo.refreshToken.token, sessionInfo.antiCsrfToken,
                    false, AccessToken.getLatestVersion());
            fail();
        } catch (UnauthorisedException e) {

        }

        SessionInformationHolder verifiedSession = Session.getSession(process.getProcess(),
                sessionInfo.accessToken.token, sessionInfo.antiCsrfToken, false, true, false);
        assertEquals(verifiedSession.session.userId, sessionInfo.session.userId);
        assertEquals(verifiedSession.session.userDataInJWT.toString(), sessionInfo.session.userDataInJWT.toString());

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));

    }

}
