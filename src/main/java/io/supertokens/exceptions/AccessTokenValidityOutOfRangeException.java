/*
 *    Copyright (c) 2024, VRAI Labs and/or its affiliates. All rights reserved.
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

// Thrown when a per-mint access token validity override (the optional accessTokenValidity parameter on
// session create / refresh, CDI >= 5.6) is outside the allowed shorten-only range
// (0 < param <= effective configured access_token_validity). The webserver maps it to a 400, never a clamp.
public class AccessTokenValidityOutOfRangeException extends Exception {

    private static final long serialVersionUID = 1L;

    public AccessTokenValidityOutOfRangeException(String err) {
        super(err);
    }
}
