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

public class UnauthorisedException extends Exception {

    private static final long serialVersionUID = 1L;

    // Non-null only when this Unauthorised is a CDI >= 5.5 recent-refresh-token-reuse (RECENT_PREV /
    // ORPHANED_BRANCH) reported under recent_token_reuse_behaviour = UNAUTHORISED. The session is still
    // revoked in that case; this field lets the refresh path tell reuse-Unauthorised apart from ordinary
    // Unauthorised (session missing / expired) so it revokes only for the former.
    public final RefreshTokenReuseSubtype reuseSubtype;

    public UnauthorisedException(String err) {
        super(err);
        this.reuseSubtype = null;
    }

    public UnauthorisedException(String err, RefreshTokenReuseSubtype reuseSubtype) {
        super(err);
        this.reuseSubtype = reuseSubtype;
    }

    public UnauthorisedException(Exception e) throws UnauthorisedException {
        super(e);
        this.reuseSubtype = null;
        if (e instanceof UnauthorisedException) {
            throw (UnauthorisedException) e;
        }
    }
}
