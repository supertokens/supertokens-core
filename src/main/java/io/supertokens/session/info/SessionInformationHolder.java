/*
 *    Copyright (c) 2020, VRAI Labs and/or its affiliates. All rights reserved.
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

package io.supertokens.session.info;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import io.supertokens.utils.Utils;

public class SessionInformationHolder {

    @Nonnull
    public final SessionInfo session;

    @Nullable
    public final TokenInfo accessToken;

    @Nullable
    public final TokenInfo refreshToken;

    @Nullable
    public final TokenInfo idRefreshToken;

    @Nullable
    public final String antiCsrfToken;

    // CDI >= 5.6 stateless verify (PLAN-002 unit 6): a read-only flag set on a checkDatabase verify when the
    // stored session payload differs from the token's, so SDKs can background-refresh. null on every other path
    // (including all CDI <= 5.4 responses), so Gson omits it and those responses stay byte-identical.
    @Nullable
    public final Boolean payloadUpdateAvailable;

    public SessionInformationHolder(@Nonnull SessionInfo session, @Nullable TokenInfo accessToken,
                                    @Nullable TokenInfo refreshToken, @Nullable TokenInfo idRefreshToken,
                                    @Nullable String antiCsrfToken) {
        this(session, accessToken, refreshToken, idRefreshToken, antiCsrfToken, null);
    }

    public SessionInformationHolder(@Nonnull SessionInfo session, @Nullable TokenInfo accessToken,
                                    @Nullable TokenInfo refreshToken, @Nullable TokenInfo idRefreshToken,
                                    @Nullable String antiCsrfToken, @Nullable Boolean payloadUpdateAvailable) {
        this.session = session;
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.idRefreshToken = idRefreshToken;
        this.antiCsrfToken = antiCsrfToken;
        this.payloadUpdateAvailable = payloadUpdateAvailable;
    }

    public JsonObject toJsonObject() {
        JsonObject json = new Gson().toJsonTree(this).getAsJsonObject();
        json.add("session", Utils.toJsonTreeWithNulls(session));

        return json;
    }
}
