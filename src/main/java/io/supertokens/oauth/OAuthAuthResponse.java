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

package io.supertokens.oauth;

import java.util.List;

public class OAuthAuthResponse {
    public final String redirectTo;
    public final List<String> cookies;

    public OAuthAuthResponse(String redirectTo, List<String> cookies) {
        this.redirectTo = redirectTo;
        this.cookies = cookies;
    }

    @Override
    public String toString() {
        return "redirectTo: " + redirectTo;
    }
}
