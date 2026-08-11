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

package io.supertokens.exceptions;

public class TokenTheftDetectedException extends Exception {

    private static final long serialVersionUID = -7964000536695705071L;

    public final String sessionHandle;
    public final String recipeUserId;
    public final String primaryUserId;
    // Reuse classification for CDI >= 5.6 refresh-time detection (PLAN-002 decision 4). null for legacy
    // (CDI <= 5.4) theft, which has no subtype - keeping old behaviour byte-identical.
    public final RefreshTokenReuseSubtype reuseSubtype;

    public TokenTheftDetectedException(String sessionHandle, String recipeUserId, String primaryUserId) {
        this(sessionHandle, recipeUserId, primaryUserId, null);
    }

    public TokenTheftDetectedException(String sessionHandle, String recipeUserId, String primaryUserId,
                                       RefreshTokenReuseSubtype reuseSubtype) {
        this.sessionHandle = sessionHandle;
        this.recipeUserId = recipeUserId;
        this.primaryUserId = primaryUserId;
        this.reuseSubtype = reuseSubtype;
    }
}
