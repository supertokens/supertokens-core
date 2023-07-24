/*
 *    Copyright (c) 2022, VRAI Labs and/or its affiliates. All rights reserved.
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

package io.supertokens.featureflag;

public enum EE_FEATURES {
    ACCOUNT_LINKING("account_linking"), MULTI_TENANCY("multi_tenancy"), TEST("test"),
    DASHBOARD_LOGIN("dashboard_login"),
    TOTP("totp");

    private final String name;

    EE_FEATURES(String s) {
        name = s;
    }

    @Override
    public String toString() {
        return this.name;
    }

    public static EE_FEATURES getEnumFromString(String s) {
        for (EE_FEATURES b : EE_FEATURES.values()) {
            if (b.toString().equalsIgnoreCase(s)) {
                return b;
            }
        }
        return null;
    }
}
