/*
 *    Copyright (c) 2023, VRAI Labs and/or its affiliates. All rights reserved.
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

package io.supertokens.test.accountlinking.api;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.supertokens.Main;
import io.supertokens.ProcessState;
import io.supertokens.authRecipe.AuthRecipe;
import io.supertokens.emailpassword.EmailPassword;
import io.supertokens.exceptions.UnauthorisedException;
import io.supertokens.featureflag.EE_FEATURES;
import io.supertokens.featureflag.FeatureFlagTestContent;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.pluginInterface.authRecipe.AuthRecipeUserInfo;
import io.supertokens.session.Session;
import io.supertokens.session.info.SessionInformationHolder;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.test.TestingProcessManager;
import io.supertokens.test.Utils;
import io.supertokens.test.httpRequest.HttpRequestForTesting;
import io.supertokens.test.httpRequest.HttpResponseException;
import io.supertokens.webserver.WebserverAPI;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;

public class SessionTests {
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

    private String[] getSessionsForUser(Main main, String userId, Boolean includeAllLinkedAccounts)
            throws HttpResponseException, IOException {
        Map<String, String> params = new HashMap<>();
        params.put("userId", userId);
        if (includeAllLinkedAccounts != null) {
            params.put("fetchSessionsForAllLinkedAccounts", includeAllLinkedAccounts.toString());
        }
        JsonObject response = HttpRequestForTesting.sendGETRequest(main, "",
                "http://localhost:3567/recipe/session/user", params, 1000, 1000, null,
                WebserverAPI.getLatestCDIVersion().get(), "");

        JsonArray sessionHandles = response.getAsJsonArray("sessionHandles");
        String[] result = new String[sessionHandles.size()];
        for (int i = 0; i < sessionHandles.size(); i++) {
            result[i] = sessionHandles.get(i).getAsString();
        }

        return result;
    }

    private void revokeSessionsForUser(Main main, String userId, Boolean includeAllLinkedAccounts)
            throws HttpResponseException, IOException {
        JsonObject params = new JsonObject();
        params.addProperty("userId", userId);
        if (includeAllLinkedAccounts != null) {
            params.addProperty("revokeSessionsForLinkedAccounts", includeAllLinkedAccounts);
        }

        JsonObject response = HttpRequestForTesting.sendJsonPOSTRequest(main, "",
                "http://localhost:3567/recipe/session/remove", params, 1000, 1000, null,
                WebserverAPI.getLatestCDIVersion().get(), "");
        assertEquals("OK", response.get("status").getAsString());
    }

    @Test
    public void testGetSessionForUserWithAndWithoutIncludingAllLinkedAccounts() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args, false);
        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{
                        EE_FEATURES.ACCOUNT_LINKING, EE_FEATURES.MULTI_TENANCY});
        process.startProcess();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        AuthRecipeUserInfo user1 = EmailPassword.signUp(process.getProcess(), "test1@example.com", "password");
        AuthRecipeUserInfo user2 = EmailPassword.signUp(process.getProcess(), "test2@example.com", "password");
        AuthRecipe.createPrimaryUser(process.getProcess(), user1.getSupertokensUserId());
        AuthRecipe.linkAccounts(process.getProcess(), user2.getSupertokensUserId(), user1.getSupertokensUserId());

        SessionInformationHolder session1 = Session.createNewSession(process.getProcess(), user1.getSupertokensUserId(),
                new JsonObject(), new JsonObject());
        SessionInformationHolder session2 = Session.createNewSession(process.getProcess(), user2.getSupertokensUserId(),
                new JsonObject(), new JsonObject());


        {
            String[] sessions = getSessionsForUser(process.getProcess(), user1.getSupertokensUserId(),
                    false);
            assertEquals(1, sessions.length);
            assertEquals(session1.session.handle, sessions[0]);
        }
        {
            String[] sessions = getSessionsForUser(process.getProcess(), user2.getSupertokensUserId(),
                    false);
            assertEquals(1, sessions.length);
            assertEquals(session2.session.handle, sessions[0]);
        }

        {
            String[] sessions = getSessionsForUser(process.getProcess(), user1.getSupertokensUserId(),
                    true);
            assertEquals(2, sessions.length);
        }
        {
            String[] sessions = getSessionsForUser(process.getProcess(), user2.getSupertokensUserId(),
                    true);
            assertEquals(2, sessions.length);
        }
        {
            String[] sessions = getSessionsForUser(process.getProcess(), user1.getSupertokensUserId(),
                    null);
            assertEquals(2, sessions.length);
        }
        {
            String[] sessions = getSessionsForUser(process.getProcess(), user2.getSupertokensUserId(),
                    null);
            assertEquals(2, sessions.length);
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testRevokeSessionsForUserWithAndWithoutIncludingAllLinkedAccounts() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args, false);
        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{
                        EE_FEATURES.ACCOUNT_LINKING, EE_FEATURES.MULTI_TENANCY});
        process.startProcess();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }


        AuthRecipeUserInfo user1 = EmailPassword.signUp(process.getProcess(), "test1@example.com", "password");
        AuthRecipeUserInfo user2 = EmailPassword.signUp(process.getProcess(), "test2@example.com", "password");
        AuthRecipe.createPrimaryUser(process.getProcess(), user1.getSupertokensUserId());
        AuthRecipe.linkAccounts(process.getProcess(), user2.getSupertokensUserId(), user1.getSupertokensUserId());

        {
            SessionInformationHolder session1 = Session.createNewSession(process.getProcess(),
                    user1.getSupertokensUserId(),
                    new JsonObject(), new JsonObject());
            SessionInformationHolder session2 = Session.createNewSession(process.getProcess(),
                    user2.getSupertokensUserId(),
                    new JsonObject(), new JsonObject());

            revokeSessionsForUser(process.getProcess(), user1.getSupertokensUserId(), true);

            try {
                Session.getSession(process.getProcess(), session1.session.handle);
                fail();
            } catch (UnauthorisedException e) {
                // ok
            }
            try {
                Session.getSession(process.getProcess(), session2.session.handle);
                fail();
            } catch (UnauthorisedException e) {
                // ok
            }
        }

        {
            SessionInformationHolder session1 = Session.createNewSession(process.getProcess(),
                    user1.getSupertokensUserId(),
                    new JsonObject(), new JsonObject());
            SessionInformationHolder session2 = Session.createNewSession(process.getProcess(),
                    user2.getSupertokensUserId(),
                    new JsonObject(), new JsonObject());

            revokeSessionsForUser(process.getProcess(), user2.getSupertokensUserId(), true);

            try {
                Session.getSession(process.getProcess(), session1.session.handle);
                fail();
            } catch (UnauthorisedException e) {
                // ok
            }
            try {
                Session.getSession(process.getProcess(), session2.session.handle);
                fail();
            } catch (UnauthorisedException e) {
                // ok
            }
        }

        {
            SessionInformationHolder session1 = Session.createNewSession(process.getProcess(),
                    user1.getSupertokensUserId(),
                    new JsonObject(), new JsonObject());
            SessionInformationHolder session2 = Session.createNewSession(process.getProcess(),
                    user2.getSupertokensUserId(),
                    new JsonObject(), new JsonObject());


            revokeSessionsForUser(process.getProcess(), user1.getSupertokensUserId(), null);

            try {
                Session.getSession(process.getProcess(), session1.session.handle);
                fail();
            } catch (UnauthorisedException e) {
                // ok
            }
            try {
                Session.getSession(process.getProcess(), session2.session.handle);
                fail();
            } catch (UnauthorisedException e) {
                // ok
            }
        }

        {
            SessionInformationHolder session1 = Session.createNewSession(process.getProcess(),
                    user1.getSupertokensUserId(),
                    new JsonObject(), new JsonObject());
            SessionInformationHolder session2 = Session.createNewSession(process.getProcess(),
                    user2.getSupertokensUserId(),
                    new JsonObject(), new JsonObject());

            revokeSessionsForUser(process.getProcess(), user2.getSupertokensUserId(), null);

            try {
                Session.getSession(process.getProcess(), session1.session.handle);
                fail();
            } catch (UnauthorisedException e) {
                // ok
            }
            try {
                Session.getSession(process.getProcess(), session2.session.handle);
                fail();
            } catch (UnauthorisedException e) {
                // ok
            }
        }

        {
            SessionInformationHolder session1 = Session.createNewSession(process.getProcess(),
                    user1.getSupertokensUserId(),
                    new JsonObject(), new JsonObject());
            SessionInformationHolder session2 = Session.createNewSession(process.getProcess(),
                    user2.getSupertokensUserId(),
                    new JsonObject(), new JsonObject());

            revokeSessionsForUser(process.getProcess(), user1.getSupertokensUserId(), false);

            try {
                Session.getSession(process.getProcess(), session1.session.handle);
                fail();
            } catch (UnauthorisedException e) {
                // ok
            }

            Session.getSession(process.getProcess(), session2.session.handle);
        }

        {
            SessionInformationHolder session1 = Session.createNewSession(process.getProcess(),
                    user1.getSupertokensUserId(),
                    new JsonObject(), new JsonObject());
            SessionInformationHolder session2 = Session.createNewSession(process.getProcess(),
                    user2.getSupertokensUserId(),
                    new JsonObject(), new JsonObject());

            revokeSessionsForUser(process.getProcess(), user2.getSupertokensUserId(), false);

            Session.getSession(process.getProcess(), session1.session.handle);

            try {
                Session.getSession(process.getProcess(), session2.session.handle);
                fail();
            } catch (UnauthorisedException e) {
                // ok
            }
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }
}
