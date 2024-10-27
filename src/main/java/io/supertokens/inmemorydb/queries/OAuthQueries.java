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
import io.supertokens.inmemorydb.Utils;
import io.supertokens.inmemorydb.config.Config;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.multitenancy.AppIdentifier;
import io.supertokens.pluginInterface.oauth.OAuthClient;
import io.supertokens.pluginInterface.oauth.OAuthLogoutChallenge;
import org.jetbrains.annotations.NotNull;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static io.supertokens.inmemorydb.QueryExecutorTemplate.execute;
import static io.supertokens.inmemorydb.QueryExecutorTemplate.update;

public class OAuthQueries {

    public static String getQueryToCreateOAuthClientTable(Start start) {
        String oAuth2ClientTable = Config.getConfig(start).getOAuthClientsTable();
        // @formatter:off
        return "CREATE TABLE IF NOT EXISTS " + oAuth2ClientTable + " ("
                + "app_id VARCHAR(64),"
                + "client_id VARCHAR(255) NOT NULL,"
                + "client_secret TEXT,"
                + "enable_refresh_token_rotation BOOLEAN NOT NULL,"
                + "is_client_credentials_only BOOLEAN NOT NULL,"
                + " PRIMARY KEY (app_id, client_id),"
                + " FOREIGN KEY(app_id) REFERENCES " + Config.getConfig(start).getAppsTable() + "(app_id) ON DELETE CASCADE);";
        // @formatter:on
    }

    public static String getQueryToCreateOAuthSessionsTable(Start start) {
        String oAuthSessionsTable = Config.getConfig(start).getOAuthSessionsTable();
        // @formatter:off
        return "CREATE TABLE IF NOT EXISTS " + oAuthSessionsTable + " ("
                + "gid VARCHAR(255)," // needed for instrospect. It's much easier to find these records if we have a gid
                + "app_id VARCHAR(64) DEFAULT 'public',"
                + "client_id VARCHAR(255) NOT NULL,"
                + "session_handle VARCHAR(128),"
                + "external_refresh_token VARCHAR(255) UNIQUE,"
                + "internal_refresh_token VARCHAR(255) UNIQUE,"
                + "jti TEXT NOT NULL," // comma separated jti list
                + "exp BIGINT NOT NULL,"
                + "PRIMARY KEY (gid),"
                + "FOREIGN KEY(app_id, client_id) REFERENCES " + Config.getConfig(start).getOAuthClientsTable() + "(app_id, client_id) ON DELETE CASCADE);";
        // @formatter:on
    }

    public static String getQueryToCreateOAuthSessionsExpIndex(Start start) {
        String oAuth2SessionTable = Config.getConfig(start).getOAuthSessionsTable();
        return "CREATE INDEX IF NOT EXISTS oauth_session_exp_index ON "
                + oAuth2SessionTable + "(exp DESC);";
    }

    public static String getQueryToCreateOAuthSessionsExternalRefreshTokenIndex(Start start) {
        String oAuth2SessionTable = Config.getConfig(start).getOAuthSessionsTable();
        return "CREATE INDEX IF NOT EXISTS oauth_session_external_refresh_token_index ON "
                + oAuth2SessionTable + "(app_id, external_refresh_token DESC);";
    }

    public static String getQueryToCreateOAuthM2MTokensTable(Start start) {
        String oAuth2M2MTokensTable = Config.getConfig(start).getOAuthM2MTokensTable();
        // @formatter:off
        return "CREATE TABLE IF NOT EXISTS " + oAuth2M2MTokensTable + " ("
                + "app_id VARCHAR(64) DEFAULT 'public',"
                + "client_id VARCHAR(255) NOT NULL,"
                + "iat BIGINT NOT NULL,"
                + "exp BIGINT NOT NULL,"
                + "PRIMARY KEY (app_id, client_id, iat),"
                + "FOREIGN KEY(app_id, client_id)"
                + " REFERENCES " + Config.getConfig(start).getOAuthClientsTable() + "(app_id, client_id) ON DELETE CASCADE"
                + ");";
        // @formatter:on
    }

    public static String getQueryToCreateOAuthM2MTokenIatIndex(Start start) {
        String oAuth2M2MTokensTable = Config.getConfig(start).getOAuthM2MTokensTable();
        return "CREATE INDEX IF NOT EXISTS oauth_m2m_token_iat_index ON "
                + oAuth2M2MTokensTable + "(iat DESC, app_id DESC);";
    }

    public static String getQueryToCreateOAuthM2MTokenExpIndex(Start start) {
        String oAuth2M2MTokensTable = Config.getConfig(start).getOAuthM2MTokensTable();
        return "CREATE INDEX IF NOT EXISTS oauth_m2m_token_exp_index ON "
                + oAuth2M2MTokensTable + "(exp DESC);";
    }

    public static String getQueryToCreateOAuthLogoutChallengesTable(Start start) {
        String oAuth2LogoutChallengesTable = Config.getConfig(start).getOAuthLogoutChallengesTable();
        // @formatter:off
        return "CREATE TABLE IF NOT EXISTS " + oAuth2LogoutChallengesTable + " ("
                + "app_id VARCHAR(64) DEFAULT 'public',"
                + "challenge VARCHAR(128) NOT NULL,"
                + "client_id VARCHAR(255) NOT NULL,"
                + "post_logout_redirect_uri VARCHAR(1024),"
                + "session_handle VARCHAR(128),"
                + "state VARCHAR(128),"
                + "time_created BIGINT NOT NULL,"
                + "PRIMARY KEY (app_id, challenge),"
                + "FOREIGN KEY(app_id, client_id)"
                + " REFERENCES " + Config.getConfig(start).getOAuthClientsTable() + "(app_id, client_id) ON DELETE CASCADE"
                + ");";
        // @formatter:on
    }

    public static String getQueryToCreateOAuthLogoutChallengesTimeCreatedIndex(Start start) {
        String oAuth2LogoutChallengesTable = Config.getConfig(start).getOAuthLogoutChallengesTable();
        return "CREATE INDEX IF NOT EXISTS oauth_logout_challenges_time_created_index ON "
                + oAuth2LogoutChallengesTable + "(time_created DESC);";
    }

    public static OAuthClient getOAuthClientById(Start start, String clientId, AppIdentifier appIdentifier)
            throws SQLException, StorageQueryException {
        String QUERY = "SELECT client_secret, is_client_credentials_only, enable_refresh_token_rotation FROM " + Config.getConfig(start).getOAuthClientsTable() +
            " WHERE client_id = ? AND app_id = ?";

        return execute(start, QUERY, pst -> {
            pst.setString(1, clientId);
            pst.setString(2, appIdentifier.getAppId());
        }, (result) -> {
            if (result.next()) {
                return new OAuthClient(clientId, result.getString("client_secret"), result.getBoolean("is_client_credentials_only"), result.getBoolean("enable_refresh_token_rotation"));
            }
            return null;
        });
    }

    public static void createOrUpdateOAuthSession(Start start, AppIdentifier appIdentifier, @NotNull String gid, @NotNull String clientId,
                                                  String externalRefreshToken, String internalRefreshToken, String sessionHandle,
                                                  List<String> jtis, long exp)
            throws SQLException, StorageQueryException {
        String QUERY = "INSERT INTO " + Config.getConfig(start).getOAuthSessionsTable() +
                " (gid, client_id, app_id, external_refresh_token, internal_refresh_token, session_handle, jti, exp) VALUES (?, ?, ?, ?, ?, ?, ?, ?) " +
                "ON CONFLICT (gid) DO UPDATE SET external_refresh_token = ?, internal_refresh_token = ?, " +
                "session_handle = ? , jti = CONCAT(jti, ',' , ?), exp = ?";
        update(start, QUERY, pst -> {
            String jtiDbValue = jtis == null ? null : String.join(",", jtis);

            pst.setString(1, gid);
            pst.setString(2, clientId);
            pst.setString(3, appIdentifier.getAppId());
            pst.setString(4, externalRefreshToken);
            pst.setString(5, internalRefreshToken);
            pst.setString(6, sessionHandle);
            pst.setString(7, jtiDbValue);
            pst.setLong(8, exp);

            pst.setString(9, externalRefreshToken);
            pst.setString(10, internalRefreshToken);
            pst.setString(11, sessionHandle);
            pst.setString(12, jtiDbValue);
            pst.setLong(13, exp);
        });
    }

    public static List<OAuthClient> getOAuthClients(Start start, AppIdentifier appIdentifier, List<String> clientIds)
            throws SQLException, StorageQueryException {
        String QUERY = "SELECT * FROM " + Config.getConfig(start).getOAuthClientsTable()
                + " WHERE app_id = ? AND client_id IN ("
                + Utils.generateCommaSeperatedQuestionMarks(clientIds.size())
                + ")";
        return execute(start, QUERY, pst -> {
            pst.setString(1, appIdentifier.getAppId());
            for (int i = 0; i < clientIds.size(); i++) {
                pst.setString(i + 2, clientIds.get(i));
            }
        }, (result) -> {
            List<OAuthClient> res = new ArrayList<>();
            while (result.next()) {
                res.add(new OAuthClient(result.getString("client_id"), result.getString("client_secret"), result.getBoolean("is_client_credentials_only"), result.getBoolean("enable_refresh_token_rotation")));
            }
            return res;
        });
    }

    public static void addOrUpdateOauthClient(Start start, AppIdentifier appIdentifier, String clientId, String clientSecret,
                                            boolean isClientCredentialsOnly, boolean enableRefreshTokenRotation)
            throws SQLException, StorageQueryException {
        String INSERT = "INSERT INTO " + Config.getConfig(start).getOAuthClientsTable()
                + "(app_id, client_id, client_secret, is_client_credentials_only, enable_refresh_token_rotation) VALUES(?, ?, ?, ?, ?) "
                + "ON CONFLICT (app_id, client_id) DO UPDATE SET client_secret = ?, is_client_credentials_only = ?, enable_refresh_token_rotation = ?";
        update(start, INSERT, pst -> {
            pst.setString(1, appIdentifier.getAppId());
            pst.setString(2, clientId);
            pst.setString(3, clientSecret);
            pst.setBoolean(4, isClientCredentialsOnly);
            pst.setBoolean(5, enableRefreshTokenRotation);
            pst.setString(6, clientSecret);
            pst.setBoolean(7, isClientCredentialsOnly);
            pst.setBoolean(8, enableRefreshTokenRotation);
        });
    }

    public static boolean deleteOAuthClient(Start start, String clientId, AppIdentifier appIdentifier)
            throws SQLException, StorageQueryException {
        String DELETE = "DELETE FROM " + Config.getConfig(start).getOAuthClientsTable()
                + " WHERE app_id = ? AND client_id = ?";
        int numberOfRow = update(start, DELETE, pst -> {
            pst.setString(1, appIdentifier.getAppId());
            pst.setString(2, clientId);
        });
        return numberOfRow > 0;
    }

    public static boolean deleteOAuthSessionByGID(Start start, AppIdentifier appIdentifier, String gid)
            throws SQLException, StorageQueryException {
        String DELETE = "DELETE FROM " + Config.getConfig(start).getOAuthSessionsTable()
                        + " WHERE gid = ? and app_id = ?;";
        int numberOfRows = update(start, DELETE, pst -> {
            pst.setString(1, gid);
            pst.setString(2, appIdentifier.getAppId());
        });
        return numberOfRows > 0;
    }

    public static boolean deleteOAuthSessionByClientId(Start start, AppIdentifier appIdentifier, String clientId)
            throws SQLException, StorageQueryException {
        String DELETE = "DELETE FROM " + Config.getConfig(start).getOAuthSessionsTable()
                + " WHERE app_id = ? and client_id = ?;";
        int numberOfRows = update(start, DELETE, pst -> {
            pst.setString(1, appIdentifier.getAppId());
            pst.setString(2, clientId);
        });
        return numberOfRows > 0;
    }

    public static boolean deleteOAuthSessionBySessionHandle(Start start, AppIdentifier appIdentifier, String sessionHandle)
            throws SQLException, StorageQueryException {
        String DELETE = "DELETE FROM " + Config.getConfig(start).getOAuthSessionsTable()
                + " WHERE app_id = ? and session_handle = ?";
        int numberOfRows = update(start, DELETE, pst -> {
            pst.setString(1, appIdentifier.getAppId());
            pst.setString(2, sessionHandle);
        });
        return numberOfRows > 0;
    }

    public static boolean deleteJTIFromOAuthSession(Start start, AppIdentifier appIdentifier, String gid, String jti)
            throws SQLException, StorageQueryException {
        //jti is a comma separated list. When deleting a jti, just have to delete from the list
        String DELETE = "UPDATE " + Config.getConfig(start).getOAuthSessionsTable()
                + " SET jti = REPLACE(jti, ?, '')" // deletion means replacing the jti with empty char
                + " WHERE app_id = ? and gid = ?";
        int numberOfRows = update(start, DELETE, pst -> {
            pst.setString(1, jti);
            pst.setString(2, appIdentifier.getAppId());
            pst.setString(3, gid);
        });
        return numberOfRows > 0;
    }

    public static int countTotalNumberOfClients(Start start, AppIdentifier appIdentifier,
            boolean filterByClientCredentialsOnly) throws SQLException, StorageQueryException {
        if (filterByClientCredentialsOnly) {
            String QUERY = "SELECT COUNT(*) as c FROM " + Config.getConfig(start).getOAuthClientsTable() +
                    " WHERE app_id = ? AND is_client_credentials_only = ?";
            return execute(start, QUERY, pst -> {
                pst.setString(1, appIdentifier.getAppId());
                pst.setBoolean(2, true);
            }, result -> {
                if (result.next()) {
                    return result.getInt("c");
                }
                return 0;
            });
        } else {
            String QUERY = "SELECT COUNT(*) as c FROM " + Config.getConfig(start).getOAuthClientsTable() +
                    " WHERE app_id = ?";
            return execute(start, QUERY, pst -> {
                pst.setString(1, appIdentifier.getAppId());
            }, result -> {
                if (result.next()) {
                    return result.getInt("c");
                }
                return 0;
            });
        }
    }

    public static int countTotalNumberOfOAuthM2MTokensAlive(Start start, AppIdentifier appIdentifier)
            throws SQLException, StorageQueryException {
        String QUERY = "SELECT COUNT(*) as c FROM " + Config.getConfig(start).getOAuthM2MTokensTable() +
                " WHERE app_id = ? AND exp > ?";
        return execute(start, QUERY, pst -> {
            pst.setString(1, appIdentifier.getAppId());
            pst.setLong(2, System.currentTimeMillis()/1000);
        }, result -> {
            if (result.next()) {
                return result.getInt("c");
            }
            return 0;
        });
    }

    public static int countTotalNumberOfOAuthM2MTokensCreatedSince(Start start, AppIdentifier appIdentifier, long since)
            throws SQLException, StorageQueryException {
        String QUERY = "SELECT COUNT(*) as c FROM " + Config.getConfig(start).getOAuthM2MTokensTable() +
                " WHERE app_id = ? AND iat >= ?";
        return execute(start, QUERY, pst -> {
            pst.setString(1, appIdentifier.getAppId());
            pst.setLong(2, since / 1000);
        }, result -> {
            if (result.next()) {
                return result.getInt("c");
            }
            return 0;
        });
    }

    public static void addOAuthM2MTokenForStats(Start start, AppIdentifier appIdentifier, String clientId, long iat, long exp)
            throws SQLException, StorageQueryException {
        String QUERY = "INSERT INTO " + Config.getConfig(start).getOAuthM2MTokensTable() +
                " (app_id, client_id, iat, exp) VALUES (?, ?, ?, ?) ON CONFLICT DO NOTHING";
        update(start, QUERY, pst -> {
            pst.setString(1, appIdentifier.getAppId());
            pst.setString(2, clientId);
            pst.setLong(3, iat);
            pst.setLong(4, exp);
        });
    }

    public static void addOAuthLogoutChallenge(Start start, AppIdentifier appIdentifier, String challenge, String clientId,
            String postLogoutRedirectionUri, String sessionHandle, String state, long timeCreated) throws SQLException, StorageQueryException {
        String QUERY = "INSERT INTO " + Config.getConfig(start).getOAuthLogoutChallengesTable() +
                " (app_id, challenge, client_id, post_logout_redirect_uri, session_handle, state, time_created) VALUES (?, ?, ?, ?, ?, ?, ?)";
        update(start, QUERY, pst -> {
            pst.setString(1, appIdentifier.getAppId());
            pst.setString(2, challenge);
            pst.setString(3, clientId);
            pst.setString(4, postLogoutRedirectionUri);
            pst.setString(5, sessionHandle);
            pst.setString(6, state);
            pst.setLong(7, timeCreated);
        });
    }

    public static OAuthLogoutChallenge getOAuthLogoutChallenge(Start start, AppIdentifier appIdentifier, String challenge) throws SQLException, StorageQueryException {
        String QUERY = "SELECT challenge, client_id, post_logout_redirect_uri, session_handle, state, time_created FROM " +
                Config.getConfig(start).getOAuthLogoutChallengesTable() +
                " WHERE app_id = ? AND challenge = ?";
        
        return execute(start, QUERY, pst -> {
            pst.setString(1, appIdentifier.getAppId());
            pst.setString(2, challenge);
        }, result -> {
            if (result.next()) {
                return new OAuthLogoutChallenge(
                    result.getString("challenge"),
                    result.getString("client_id"),
                    result.getString("post_logout_redirect_uri"),
                    result.getString("session_handle"),
                    result.getString("state"),
                    result.getLong("time_created")
                );
            }
            return null;
        });
    }

    public static void deleteOAuthLogoutChallenge(Start start, AppIdentifier appIdentifier, String challenge) throws SQLException, StorageQueryException {
        String QUERY = "DELETE FROM " + Config.getConfig(start).getOAuthLogoutChallengesTable() +
                " WHERE app_id = ? AND challenge = ?";
        update(start, QUERY, pst -> {
            pst.setString(1, appIdentifier.getAppId());
            pst.setString(2, challenge);
        });
    }

    public static void deleteOAuthLogoutChallengesBefore(Start start, long time) throws SQLException, StorageQueryException {
        String QUERY = "DELETE FROM " + Config.getConfig(start).getOAuthLogoutChallengesTable() +
                " WHERE time_created < ?";
        update(start, QUERY, pst -> {
            pst.setLong(1, time);
        });
    }

    public static String getRefreshTokenMapping(Start start, AppIdentifier appIdentifier, String externalRefreshToken) throws SQLException, StorageQueryException {
        String QUERY = "SELECT internal_refresh_token FROM " + Config.getConfig(start).getOAuthSessionsTable() +
                " WHERE app_id = ? AND external_refresh_token = ?";
        return execute(start, QUERY, pst -> {
            pst.setString(1, appIdentifier.getAppId());
            pst.setString(2, externalRefreshToken);
        }, result -> {
            if (result.next()) {
                return result.getString("internal_refresh_token");
            }
            return null;
        });
    }

    public static void deleteExpiredOAuthSessions(Start start, long exp) throws SQLException, StorageQueryException {
        // delete expired M2M tokens
        String QUERY = "DELETE FROM " + Config.getConfig(start).getOAuthSessionsTable() +
                " WHERE exp < ?";

        update(start, QUERY, pst -> {
            pst.setLong(1, exp);
        });
    }

    public static void deleteExpiredOAuthM2MTokens(Start start, long exp) throws SQLException, StorageQueryException {
        // delete expired M2M tokens
        String QUERY = "DELETE FROM " + Config.getConfig(start).getOAuthM2MTokensTable() +
                " WHERE exp < ?";
        update(start, QUERY, pst -> {
            pst.setLong(1, exp);
        });
    }

    public static boolean isOAuthSessionExistsByJTI(Start start, AppIdentifier appIdentifier, String gid, String jti)
            throws SQLException, StorageQueryException {
        String SELECT = "SELECT jti FROM " + Config.getConfig(start).getOAuthSessionsTable()
                + " WHERE app_id = ? and gid = ?;";
        return execute(start, SELECT, pst -> {
            pst.setString(1, appIdentifier.getAppId());
            pst.setString(2, gid);
        }, result -> {
            if(result.next()){
                List<String> jtis = Arrays.stream(result.getString(1).split(",")).filter(s -> !s.isEmpty()).collect(
                        Collectors.toList());
                return jtis.contains(jti);
            }
            return false;
        });
    }

    public static boolean isOAuthSessionExistsByGID(Start start, AppIdentifier appIdentifier, String gid)
            throws SQLException, StorageQueryException {
        String SELECT = "SELECT count(*) FROM " + Config.getConfig(start).getOAuthSessionsTable()
                + " WHERE app_id = ? and gid = ?;";
        return execute(start, SELECT, pst -> {
            pst.setString(1, appIdentifier.getAppId());
            pst.setString(2, gid);
        }, result -> {
            if(result.next()){
                return result.getInt(1) > 0;
            }
            return false;
        });
    }

}
