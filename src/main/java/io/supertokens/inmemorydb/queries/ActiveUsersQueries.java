package io.supertokens.inmemorydb.queries;

import java.sql.Connection;
import java.sql.SQLException;

import io.supertokens.inmemorydb.config.Config;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.inmemorydb.Start;
import io.supertokens.pluginInterface.multitenancy.AppIdentifier;

import static io.supertokens.inmemorydb.QueryExecutorTemplate.execute;
import static io.supertokens.inmemorydb.QueryExecutorTemplate.update;

public class ActiveUsersQueries {
    static String getQueryToCreateUserLastActiveTable(Start start) {
        return "CREATE TABLE IF NOT EXISTS " + Config.getConfig(start).getUserLastActiveTable() + " ("
                + "app_id VARCHAR(64) DEFAULT 'public',"
                + "user_id VARCHAR(128),"
                + "last_active_time BIGINT UNSIGNED,"
                + "PRIMARY KEY(app_id, user_id),"
                + "FOREIGN KEY (app_id) REFERENCES "
                + Config.getConfig(start).getAppsTable() + "(app_id) ON DELETE CASCADE"
                + " );";
    }

    public static int countUsersActiveSince(Start start, AppIdentifier appIdentifier, long sinceTime)
            throws SQLException, StorageQueryException {
        String QUERY = "SELECT COUNT(*) as total FROM " + Config.getConfig(start).getUserLastActiveTable()
                + " WHERE app_id = ? AND last_active_time >= ?";

        return execute(start, QUERY, pst -> {
            pst.setString(1, appIdentifier.getAppId());
            pst.setLong(2, sinceTime);
        }, result -> {
            if (result.next()) {
                return result.getInt("total");
            }
            return 0;
        });
    }

    public static int countUsersActiveSinceAndHasMoreThanOneLoginMethod(Start start, AppIdentifier appIdentifier,
                                                                        long sinceTime)
            throws SQLException, StorageQueryException {
        // TODO: Active users are present only on public tenant and MFA users may be present on different storages
        String QUERY = "SELECT count(1) as c FROM ("
                + "  SELECT count(user_id) as num_login_methods, app_id, primary_or_recipe_user_id"
                + "  FROM " + Config.getConfig(start).getUsersTable()
                + "  WHERE primary_or_recipe_user_id IN ("
                + "    SELECT user_id FROM " + Config.getConfig(start).getUserLastActiveTable()
                + "    WHERE app_id = ? AND last_active_time >= ?"
                + "  )"
                + "  GROUP BY app_id, primary_or_recipe_user_id"
                + ") uc WHERE num_login_methods > 1";
        return execute(start, QUERY, pst -> {
            pst.setString(1, appIdentifier.getAppId());
            pst.setLong(2, sinceTime);
        }, result -> {
            if (result.next()) {
                return result.getInt("c");
            }
            return 0;
        });
    }

    public static int updateUserLastActive(Start start, AppIdentifier appIdentifier, String userId)
            throws SQLException, StorageQueryException {
        String QUERY = "INSERT INTO " + Config.getConfig(start).getUserLastActiveTable()
                +
                "(app_id, user_id, last_active_time) VALUES(?, ?, ?) ON CONFLICT(app_id, user_id) DO UPDATE SET " +
                "last_active_time = ?";

        long now = System.currentTimeMillis();
        return update(start, QUERY, pst -> {
            pst.setString(1, appIdentifier.getAppId());
            pst.setString(2, userId);
            pst.setLong(3, now);
            pst.setLong(4, now);
        });
    }

    public static void deleteUserActive_Transaction(Connection con, Start start, AppIdentifier appIdentifier,
                                                    String userId)
            throws StorageQueryException, SQLException {
        String QUERY = "DELETE FROM " + Config.getConfig(start).getUserLastActiveTable()
                + " WHERE app_id = ? AND user_id = ?";

        update(con, QUERY, pst -> {
            pst.setString(1, appIdentifier.getAppId());
            pst.setString(2, userId);
        });
    }

    public static int countUsersThatHaveMoreThanOneLoginMethodOrTOTPEnabledAndActiveSince(Start start,
                                                                                          AppIdentifier appIdentifier,
                                                                                          long sinceTime)
            throws SQLException, StorageQueryException {
        // TODO: Active users are present only on public tenant and MFA users may be present on different storages
        String QUERY =
                "SELECT COUNT (DISTINCT user_id) as c FROM ("
                        + "  " // users with more than one login method
                        + "    SELECT primary_or_recipe_user_id AS user_id FROM ("
                        + "      SELECT COUNT(user_id) as num_login_methods, app_id, primary_or_recipe_user_id"
                        + "      FROM " + Config.getConfig(start).getAppIdToUserIdTable()
                        + "      WHERE app_id = ? AND primary_or_recipe_user_id IN ("
                        + "        SELECT user_id FROM " + Config.getConfig(start).getUserLastActiveTable()
                        + "        WHERE app_id = ? AND last_active_time >= ?"
                        + "      )"
                        + "      GROUP BY app_id, primary_or_recipe_user_id"
                        + "    ) AS nloginmethods"
                        + "    WHERE num_login_methods > 1"
                        + "  UNION" // TOTP users
                        + "    SELECT user_id FROM " + Config.getConfig(start).getTotpUsersTable()
                        + "    WHERE app_id = ? AND user_id IN ("
                        + "      SELECT user_id FROM " + Config.getConfig(start).getUserLastActiveTable()
                        + "      WHERE app_id = ? AND last_active_time >= ?"
                        + "    )"
                        + "  "
                        + ") AS all_users";

        return execute(start, QUERY, pst -> {
            pst.setString(1, appIdentifier.getAppId());
            pst.setString(2, appIdentifier.getAppId());
            pst.setLong(3, sinceTime);
            pst.setString(4, appIdentifier.getAppId());
            pst.setString(5, appIdentifier.getAppId());
            pst.setLong(6, sinceTime);
        }, result -> {
            return result.next() ? result.getInt("c") : 0;
        });
    }
}
