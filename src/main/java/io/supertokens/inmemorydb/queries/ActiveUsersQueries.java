package io.supertokens.inmemorydb.queries;

import java.sql.SQLException;

import io.supertokens.inmemorydb.config.Config;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.inmemorydb.Start;

import static io.supertokens.inmemorydb.QueryExecutorTemplate.execute;
import static io.supertokens.inmemorydb.QueryExecutorTemplate.update;

public class ActiveUsersQueries {
    static String getQueryToCreateUserLastActiveTable(Start start) {
        return "CREATE TABLE IF NOT EXISTS " + Config.getConfig(start).getUserLastActiveTable() + " ("
                + "user_id VARCHAR(128),"
                + "last_active_time BIGINT UNSIGNED," + "PRIMARY KEY(user_id)" + " );";
    }

    public static int countUsersActiveSince(Start start, long sinceTime) throws SQLException, StorageQueryException {
        String QUERY = "SELECT COUNT(*) as total FROM " + Config.getConfig(start).getUserLastActiveTable()
                + " WHERE last_active_time >= ?";

        return execute(start, QUERY, pst -> pst.setLong(1, sinceTime), result -> {
            if (result.next()) {
                return result.getInt("total");
            }
            return 0;
        });
    }

    public static int countUsersEnabledTotp(Start start) throws SQLException, StorageQueryException {
        String QUERY = "SELECT COUNT(*) as total FROM " + Config.getConfig(start).getTotpUsersTable();

        return execute(start, QUERY, null, result -> {
            if (result.next()) {
                return result.getInt("total");
            }
            return 0;
        });
    }

    public static int countUsersEnabledTotpAndActiveSince(Start start, long sinceTime) throws SQLException, StorageQueryException {
        String QUERY = "SELECT COUNT(*) as total FROM " + Config.getConfig(start).getTotpUsersTable() + " AS totp_users "
                + "INNER JOIN " + Config.getConfig(start).getUserLastActiveTable() + " AS user_last_active "
                + "ON totp_users.user_id = user_last_active.user_id "
                + "WHERE user_last_active.last_active_time >= ?";

        return execute(start, QUERY, pst -> pst.setLong(1, sinceTime), result -> {
            if (result.next()) {
                return result.getInt("total");
            }
            return 0;
        });
    }

    public static int updateUserLastActive(Start start, String userId) throws SQLException, StorageQueryException {
        String QUERY = "INSERT INTO " + Config.getConfig(start).getUserLastActiveTable()
                + "(user_id, last_active_time) VALUES(?, ?) ON CONFLICT(user_id) DO UPDATE SET last_active_time = ?";

        long now = System.currentTimeMillis();
        return update(start, QUERY, pst -> {
            pst.setString(1, userId);
            pst.setLong(2, now);
            pst.setLong(3, now);
        });
    }

}
