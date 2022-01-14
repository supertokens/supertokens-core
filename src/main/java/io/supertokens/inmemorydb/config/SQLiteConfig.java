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

package io.supertokens.inmemorydb.config;

public class SQLiteConfig {

    public String getKeyValueTable() {
        return "key_value";
    }

    public String getUsersTable() {
        return "all_auth_recipe_users";
    }

    public String getAccessTokenSigningKeysTable() {
        return "session_access_token_signing_keys";
    }

    public String getSessionInfoTable() {
        return "session_info";
    }

    public String getEmailPasswordUsersTable() {
        return "emailpassword_users";
    }

    public String getPasswordResetTokensTable() {
        return "emailpassword_pswd_reset_tokens";
    }

    public String getEmailVerificationTokensTable() {
        return "emailverification_tokens";
    }

    public String getEmailVerificationTable() {
        return "emailverification_verified_emails";
    }

    public String getThirdPartyUsersTable() {
        return "thirdparty_users";
    }

    public String getJWTSigningKeysTable() {
        return "jwt_signing_keys";
    }

    public String getPasswordlessUsersTable() {
        return "passwordless_users";
    }

    public String getPasswordlessDevicesTable() {
        return "passwordless_devices";
    }

    public String getPasswordlessCodesTable() {
        return "passwordless_codes";
    }
}