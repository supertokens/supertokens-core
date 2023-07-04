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

import com.google.gson.JsonObject;

import javax.annotation.Nonnull;

public class SessionInfo {
    @Nonnull
    public final String handle;

    @Nonnull
    public final String userId;

    @Nonnull
    public final JsonObject userDataInJWT;

    @Nonnull
    public final String tenantId;

    public SessionInfo(@Nonnull String handle, @Nonnull String userId, @Nonnull JsonObject userDataInJWT, @Nonnull String tenantId) {
        this.handle = handle;
        this.userId = userId;
        this.userDataInJWT = userDataInJWT;
        this.tenantId = tenantId;
    }
}
