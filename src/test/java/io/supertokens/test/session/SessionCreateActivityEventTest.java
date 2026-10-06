/*
 *    Copyright (c) 2026, VRAI Labs and/or its affiliates. All rights reserved.
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
import io.supertokens.emailpassword.EmailPassword;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.pluginInterface.auditlog.ActivityLogStorage;
import io.supertokens.pluginInterface.auditlog.AuditLogEvent;
import io.supertokens.pluginInterface.authRecipe.AuthRecipeUserInfo;
import io.supertokens.pluginInterface.multitenancy.AppIdentifier;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.test.TestingProcessManager;
import io.supertokens.test.Utils;
import io.supertokens.test.httpRequest.HttpRequestForTesting;
import io.supertokens.useridmapping.UserIdMapping;
import io.supertokens.utils.SemVer;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * A session create writes exactly one activity-log row: an unthrottled {@code session_create} carrying the
 * session handle and the SuperTokens (not external) recipe and primary user ids. The former second row, the
 * untyped {@code session_created} audit event, is no longer written.
 */
public class SessionCreateActivityEventTest {

    private static final Set<String> SESSION_EVENT_TYPES = Set.of("session_create", "session_created");

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

    @Test
    public void eachSessionCreateWritesOneSessionCreateRow() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.startIsolatedProcess(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));
        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        long startTs = System.currentTimeMillis() - 1;
        String handle1 = createSession(process, "user-1");
        String handle2 = createSession(process, "user-1");

        List<AuditLogEvent> events = readSessionEvents(process, startTs);
        assertEquals(2, events.size());
        for (AuditLogEvent event : events) {
            assertEquals("session_create", event.eventType);
            assertEquals("user-1", event.recipeUserId);
            assertEquals("user-1", event.primaryOrRecipeUserId);
        }
        assertEquals(Set.of(handle1, handle2), events.stream().map(e -> e.identifier).collect(Collectors.toSet()));

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void sessionCreateRowCarriesSuperTokensIdsForMappedUser() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.startIsolatedProcess(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));
        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        AuthRecipeUserInfo user = EmailPassword.signUp(process.getProcess(), "mapped@example.com", "password");
        UserIdMapping.createUserIdMapping(process.getProcess(), user.getSupertokensUserId(), "external-1", null,
                false);

        long startTs = System.currentTimeMillis() - 1;
        String handle = createSession(process, "external-1");

        List<AuditLogEvent> events = readSessionEvents(process, startTs);
        assertEquals(1, events.size());
        AuditLogEvent event = events.get(0);
        assertEquals("session_create", event.eventType);
        assertEquals(handle, event.identifier);
        // The fold guards primary_or_recipe_user_id against app_id_to_user_id, which holds SuperTokens ids.
        assertEquals(user.getSupertokensUserId(), event.recipeUserId);
        assertEquals(user.getSupertokensUserId(), event.primaryOrRecipeUserId);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    private static String createSession(TestingProcessManager.TestingProcess process, String userId)
            throws Exception {
        JsonObject request = new JsonObject();
        request.addProperty("userId", userId);
        request.add("userDataInJWT", new JsonObject());
        request.add("userDataInDatabase", new JsonObject());
        request.addProperty("enableAntiCsrf", false);

        JsonObject response = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/session", request, 1000, 1000, null, SemVer.v5_0.get(),
                "session");
        assertEquals("OK", response.get("status").getAsString());
        assertEquals(userId, response.getAsJsonObject("session").get("userId").getAsString());
        return response.getAsJsonObject("session").get("handle").getAsString();
    }

    private static List<AuditLogEvent> readSessionEvents(TestingProcessManager.TestingProcess process, long fromTs)
            throws Exception {
        ActivityLogStorage storage = (ActivityLogStorage) StorageLayer.getStorage(process.getProcess());
        return storage.getActivityLogEntriesForApp(new AppIdentifier(null, null), SESSION_EVENT_TYPES, fromTs,
                System.currentTimeMillis() + 1000, 100);
    }
}
