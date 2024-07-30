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

package io.supertokens.inmemorydb.queries;

import io.supertokens.inmemorydb.Start;
import io.supertokens.inmemorydb.config.Config;

public class OAuthQueries {

    public static String getQueryToCreateOAuthClientTable(Start start) {
        String oAuth2ClientTable = Config.getConfig(start).getOAuthClientTable();
        // @formatter:off
        return "CREATE TABLE IF NOT EXISTS " + oAuth2ClientTable + " ("
                + "app_id VARCHAR(64) DEFAULT 'public',"
                + "client_id VARCHAR(128) NOT NULL,"
                + "name TEXT NOT NULL,"
                + "client_secret_hash  VARCHAR(128) NOT NULL,"
                + "redirect_uris  TEXT NOT NULL,"
                + "created_at_ms  BIGINT NOT NULL,"
                + "updated_at_ms BIGINT NOT NULL,"
                + " PRIMARY KEY (app_id, client_id),"
                + " FOREIGN KEY(app_id) REFERENCES " + Config.getConfig(start).getAppsTable() + "(app_id) ON DELETE CASCADE);";
        // @formatter:on
    }

    public static String getQueryToCreateOAuthScopesTable(Start start) {
        String oAuth2ScopesTable = Config.getConfig(start).getOAuthScopesTable();
        // @formatter:off
        return "CREATE TABLE IF NOT EXISTS " + oAuth2ScopesTable + " ("
                + "app_id VARCHAR(64) DEFAULT 'public',"
                + "scope TEXT NOT NULL,"
                + " PRIMARY KEY (app_id, scope),"
                + " FOREIGN KEY(app_id) REFERENCES " + Config.getConfig(start).getAppsTable() +
                " (app_id) ON DELETE CASCADE);";
        // @formatter:on
    }

    public static String getQueryToCreateOAuthClientAllowedScopesTable(Start start) {
        String oAuth2ClientAllowedScopesTable = Config.getConfig(start).getOAuthClientAllowedScopesTable();
        // @formatter:off
        return "CREATE TABLE IF NOT EXISTS " + oAuth2ClientAllowedScopesTable + " ("
                + "app_id VARCHAR(64) DEFAULT 'public',"
                + "client_id VARCHAR(128) NOT NULL,"
                + "scope TEXT NOT NULL,"
                + "requires_consent BOOLEAN NOT NULL,"
                + " PRIMARY KEY(app_id, client_id, scope),"
                + " FOREIGN KEY(app_id,client_id) REFERENCES " + Config.getConfig(start).getOAuthClientTable()
                + "(app_id, client_id) ON DELETE CASCADE,"
                + " FOREIGN KEY(app_id, scope) REFERENCES " + Config.getConfig(start).getOAuthScopesTable()
                + "(app_id, scope) ON DELETE CASCADE);";
        // @formatter:on
    }

    public static String getQueryToCreateOAuthAuthcodeTable(Start start) {
        String oAuth2AuthcodeTable = Config.getConfig(start).getOAuthAuthcodeTable();
        // @formatter:off
        return "CREATE TABLE IF NOT EXISTS " + oAuth2AuthcodeTable + " ("
                + "app_id VARCHAR(64) DEFAULT 'public',"
                + "tenant_id VARCHAR(64) DEFAULT 'public',"
                + "authorization_code_hash VARCHAR(128) NOT NULL, "
                + "session_handle VARCHAR(255) NOT NULL,"
                + "client_id VARCHAR(128) NOT NULL,"
                + "created_at_ms BIGINT NOT NULL,"
                + "expires_at_ms BIGINT NOT NULL,"
                + "scopes TEXT NOT NULL,"
                // In an ideal scenario, the 'scopes' field should be a foreign key referencing the 'scope' field in
                // the 'oauth2_scope' table,
                // containing an array of scopes. However, in this case, the scopes are not directly linked
                // to the 'oauth2_scope' table's 'scope' field.
                // This deliberate design choice ensures that any changes or deletions made to scopes in the 'oauth2_scope' table
                // do not affect existing OAuth2 codes or cause unexpected disruptions in users' sessions.
                + "redirect_uri TEXT NOT NULL,"
                + "access_type VARCHAR(10) NOT NULL,"
                + "code_challenge VARCHAR(128),"
                + "code_challenge_method VARCHAR(10),"
                + " PRIMARY KEY (app_id, tenant_id, authorization_code_hash),"
                + " FOREIGN KEY(app_id, client_id) REFERENCES " + Config.getConfig(start).getOAuthClientTable()
                + "(app_id, client_id) ON DELETE CASCADE,"
                + " FOREIGN KEY(app_id, tenant_id, session_handle) REFERENCES "
                + Config.getConfig(start).getSessionInfoTable() + "(app_id, tenant_id, session_handle) ON DELETE CASCADE );";
        // @formatter:on
    }

    public static String getQueryToCreateOAuthTokenTable(Start start) {
        String oAuth2TokenTable = Config.getConfig(start).getOAuthTokenTable();
        // @formatter:off
        return "CREATE TABLE IF NOT EXISTS " + oAuth2TokenTable + " ("
                + "id CHAR(36) NOT NULL,"
                + "app_id VARCHAR(64) DEFAULT 'public',"
                + "tenant_id VARCHAR(64) DEFAULT 'public',"
                + "client_id VARCHAR(128) NOT NULL,"
                + "session_handle VARCHAR(255),"
                + "scopes TEXT NOT NULL,"
                + "access_token_hash VARCHAR(128) NOT NULL, "
                + "refresh_token_hash VARCHAR(128), "
                + "created_at_ms BIGINT NOT NULL,"
                + "last_updated_at_ms BIGINT NOT NULL,"
                + "access_token_expires_at_ms BIGINT NOT NULL,"
                + "refresh_token_expires_at_ms BIGINT,"
                + " PRIMARY KEY (app_id, tenant_id, id),"
                + " FOREIGN KEY(app_id, client_id) REFERENCES " + Config.getConfig(start).getOAuthClientTable()
                + "(app_id, client_id) ON DELETE CASCADE,"
                + " FOREIGN KEY(app_id, tenant_id, session_handle) REFERENCES " + Config.getConfig(start).getSessionInfoTable()
                + "(app_id, tenant_id, session_handle) ON DELETE CASCADE);";
        // @formatter:on
    }

}
