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

import io.supertokens.auditlog.lifecycle.ActivityEventType;
import io.supertokens.auditlog.lifecycle.LastActiveFoldEvents;
import org.junit.Test;

import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The semantic activity-event vocabulary: the {@code event_type} strings and the last-active fold set (the six
 * activity events plus user_creation and account_linking; user_import and the retired user_last_active
 * excluded).
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
    public void foldSetIsTheSixActivityEventsPlusUserCreationAndAccountLinking() {
        assertEquals(
                Set.of("sign_in", "token_refresh", "session_create", "sign_out", "oauth_token_exchange",
                        "oauth_authorize", "user_creation", "account_linking"),
                LastActiveFoldEvents.FOLD_EVENT_TYPES);

        // The retired synthetic event and imported users are not part of the fold.
        assertFalse(LastActiveFoldEvents.FOLD_EVENT_TYPES.contains("user_last_active"));
        assertFalse(LastActiveFoldEvents.FOLD_EVENT_TYPES.contains("user_import"));
    }

    @Test
    public void sqlInListQuotesEveryFoldType() {
        String inList = LastActiveFoldEvents.sqlInList();
        for (String type : LastActiveFoldEvents.FOLD_EVENT_TYPES) {
            assertTrue("expected '" + type + "' in " + inList, inList.contains("'" + type + "'"));
        }
        assertFalse(inList.contains("user_last_active"));
    }
}
