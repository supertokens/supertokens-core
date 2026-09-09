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

package io.supertokens.test;

import io.supertokens.ActiveUsers;
import io.supertokens.pluginInterface.auditlog.ActivityEventType;
import io.supertokens.pluginInterface.auditlog.RollupEventTypes;
import org.junit.Test;

import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The semantic activity-event vocabulary that core consumes from the shared plugin-interface
 * ({@link ActivityEventType}, {@link RollupEventTypes}) — the {@code event_type} strings and the last-active
 * fold set (the six activity events plus user_creation and account_linking; user_import and the retired
 * user_last_active excluded) — plus core's own throttle classification ({@link ActiveUsers#isThrottled}:
 * sign_in / sign_out unthrottled, the rest throttled). The plugin-interface has no test-running CI, so these
 * pin the exact contract core's fold and throttle depend on.
 */
public class ActivityEventTypeTest {

    @Test
    public void eventTypeValuesAreStable() {
        assertEquals("sign_in", ActivityEventType.SIGN_IN.getValue());
        assertEquals("sign_out", ActivityEventType.SIGN_OUT.getValue());
        assertEquals("token_refresh", ActivityEventType.TOKEN_REFRESH.getValue());
        assertEquals("session_create", ActivityEventType.SESSION_CREATE.getValue());
        assertEquals("oauth_token_exchange", ActivityEventType.OAUTH_TOKEN_EXCHANGE.getValue());
        assertEquals("oauth_authorize", ActivityEventType.OAUTH_AUTHORIZE.getValue());
    }

    @Test
    public void signInAndSignOutAreUnthrottledEverythingElseThrottled() {
        assertFalse(ActiveUsers.isThrottled(ActivityEventType.SIGN_IN));
        assertFalse(ActiveUsers.isThrottled(ActivityEventType.SIGN_OUT));
        assertTrue(ActiveUsers.isThrottled(ActivityEventType.TOKEN_REFRESH));
        assertTrue(ActiveUsers.isThrottled(ActivityEventType.SESSION_CREATE));
        assertTrue(ActiveUsers.isThrottled(ActivityEventType.OAUTH_TOKEN_EXCHANGE));
        assertTrue(ActiveUsers.isThrottled(ActivityEventType.OAUTH_AUTHORIZE));
    }

    @Test
    public void foldSetIsTheSixActivityEventsPlusUserCreationAndAccountLinking() {
        assertEquals(
                Set.of("sign_in", "token_refresh", "session_create", "sign_out", "oauth_token_exchange",
                        "oauth_authorize", "user_creation", "account_linking"),
                RollupEventTypes.FOLD_SET);

        // The retired synthetic event and imported users are not part of the fold.
        assertFalse(RollupEventTypes.FOLD_SET.contains("user_last_active"));
        assertFalse(RollupEventTypes.FOLD_SET.contains("user_import"));
    }

    @Test
    public void sqlInListQuotesEveryFoldType() {
        String inList = RollupEventTypes.sqlInList();
        for (String type : RollupEventTypes.FOLD_SET) {
            assertTrue("expected '" + type + "' in " + inList, inList.contains("'" + type + "'"));
        }
        assertFalse(inList.contains("user_last_active"));
    }
}
