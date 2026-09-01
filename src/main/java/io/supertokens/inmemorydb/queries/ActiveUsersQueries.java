package io.supertokens.inmemorydb.queries;

import java.sql.Connection;
import java.sql.SQLException;

import io.supertokens.inmemorydb.config.Config;
import io.supertokens.pluginInterface.auditlog.RollupEventTypes;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.inmemorydb.Start;
import io.supertokens.pluginInterface.multitenancy.AppIdentifier;
import org.jetbrains.annotations.TestOnly;

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

    static String getQueryToCreateLastActiveTimeIndexForUserLastActiveTable(Start start) {
        return "CREATE INDEX user_last_active_last_active_time_index ON "
                + Config.getConfig(start).getUserLastActiveTable() + "(last_active_time DESC, app_id DESC);";
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
        if (Config.getConfig(start).getMigrationMode().readsFromNewTables()) {
            return countUsersActiveSinceAndHasMoreThanOneLoginMethod_new(start, appIdentifier, sinceTime);
        }
        return countUsersActiveSinceAndHasMoreThanOneLoginMethod_legacy(start, appIdentifier, sinceTime);
    }

    private static int countUsersActiveSinceAndHasMoreThanOneLoginMethod_legacy(Start start,
                                                                                 AppIdentifier appIdentifier,
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

    private static int countUsersActiveSinceAndHasMoreThanOneLoginMethod_new(Start start,
                                                                              AppIdentifier appIdentifier,
                                                                              long sinceTime)
            throws SQLException, StorageQueryException {
        // TODO: Active users are present only on public tenant and MFA users may be present on different storages
        String QUERY = "SELECT count(1) as c FROM ("
                + "  SELECT count(user_id) as num_login_methods, app_id, primary_or_recipe_user_id"
                + "  FROM " + Config.getConfig(start).getAppIdToUserIdTable()
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

    @TestOnly
    public static int updateUserLastActive(Start start, AppIdentifier appIdentifier, String userId, long timestamp)
            throws SQLException, StorageQueryException {
        String QUERY = "INSERT INTO " + Config.getConfig(start).getUserLastActiveTable()
                +
                "(app_id, user_id, last_active_time) VALUES(?, ?, ?) ON CONFLICT(app_id, user_id) DO UPDATE SET " +
                "last_active_time = ?";

        return update(start, QUERY, pst -> {
            pst.setString(1, appIdentifier.getAppId());
            pst.setString(2, userId);
            pst.setLong(3, timestamp);
            pst.setLong(4, timestamp);
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

    /**
     * Derives {@code user_last_active} from the activity log over {@code [windowStartMillis, now]}, on the
     * caller's transaction connection. Mirrors the PostgreSQL implementation with two idempotent statements:
     * <ol>
     *   <li><b>Fold</b> — upsert each user's most recent fold-relevant activity (see
     *       {@code RollupEventTypes#FOLD_SET}) into the projection, monotonically ({@code MAX(stored, new)} never
     *       lowers a stored timestamp).</li>
     *   <li><b>Reconcile</b> — delete projection rows for users linked away within the same window
     *       ({@code account_linking} events, matched on {@code app_id} + {@code recipe_user_id}).</li>
     * </ol>
     * No advisory lock — the in-memory store is single-instance, so there is no concurrent pass to
     * deduplicate and the fold never skips; this always returns {@code true} to match the SQLStorage
     * contract. SQLite lacks the {@code DELETE ... USING} join, so the reconcile is expressed as a
     * correlated {@code EXISTS} sub-select (result-identical).
     */
    public static boolean rollupLastActiveFromActivityLog_Transaction(Start start, Connection con,
                                                                   long windowStartMillis)
            throws StorageQueryException, SQLException {
        String userLastActiveTable = Config.getConfig(start).getUserLastActiveTable();
        String activityLogTable = Config.getConfig(start).getActivityLogTable();
        String appsTable = Config.getConfig(start).getAppsTable();

        // SQLite's two-argument max() is the scalar GREATEST, so the upsert stays monotonic.
        // The fold set is the semantic activity events plus the two lifecycle events that imply activity
        // (user_creation, account_linking); see RollupEventTypes.FOLD_SET. account_linking credits the primary
        // user here (primary_or_recipe_user_id); the reconcile below separately drops the recipe user's row.
        // The apps guard skips activity for apps deleted within the window: activity_log rows are
        // intentionally retained after an app is deleted (no app_id cascade), but user_last_active
        // cascades on app delete, so folding a since-deleted app's rows would violate the
        // user_last_active -> apps foreign key. EXISTS keeps the fold set to still-existing apps only.
        String FOLD_QUERY = "INSERT INTO " + userLastActiveTable + " (app_id, user_id, last_active_time)"
                + " SELECT app_id, primary_or_recipe_user_id, MAX(created_at) FROM " + activityLogTable + " al"
                + " WHERE event_type IN (" + RollupEventTypes.sqlInList() + ") AND created_at >= ?"
                + " AND EXISTS (SELECT 1 FROM " + appsTable + " a WHERE a.app_id = al.app_id)"
                + " GROUP BY app_id, primary_or_recipe_user_id"
                + " ON CONFLICT (app_id, user_id) DO UPDATE"
                + " SET last_active_time = MAX(" + userLastActiveTable + ".last_active_time,"
                + " excluded.last_active_time)";
        update(con, FOLD_QUERY, pst -> pst.setLong(1, windowStartMillis));

        String RECONCILE_QUERY = "DELETE FROM " + userLastActiveTable
                + " WHERE EXISTS (SELECT 1 FROM " + activityLogTable + " al"
                + " WHERE al.event_type = 'account_linking' AND al.created_at >= ?"
                + " AND al.app_id = " + userLastActiveTable + ".app_id"
                + " AND al.recipe_user_id = " + userLastActiveTable + ".user_id)";
        update(con, RECONCILE_QUERY, pst -> pst.setLong(1, windowStartMillis));
        return true;
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
