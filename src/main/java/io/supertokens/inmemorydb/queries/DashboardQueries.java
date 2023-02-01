package io.supertokens.inmemorydb.queries;

import java.sql.SQLException;

import io.supertokens.inmemorydb.QueryExecutorTemplate;
import io.supertokens.inmemorydb.Start;
import io.supertokens.inmemorydb.config.Config;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;

public class DashboardQueries {
    public static String getQueryToCreateUserIdMappingTable(Start start) {
        String tableName = Config.getConfig(start).getUserIdMappingTable();
        // @formatter:off
        return "CREATE TABLE IF NOT EXISTS " + tableName + " ("
                + "user_id CHAR(36) NOT NULL,"
                + "email VARCHAR(256) NOT NULL UNIQUE,"
                + "password_hash VARCHAR(256) NOT NULL,"
                + "is_suspended TINYINT,"
                + "time_joined BIGINT UNSIGNED NOT NULL,"
                + "PRIMARY KEY(user_id)";

        // @formatter:on
    }

    public static void createDashboardUser(Start start, String userId, String email, String passwordHash,
            boolean isSuspended, long timeJoined) throws SQLException, StorageQueryException {
        // convert boolean to int since sqlite does not support boolean type
        int isSuspendedAsInt = isSuspended ? 1 : 0;

        String QUERY = "INSERT INTO " + Config.getConfig(start).getDashboardEmailPasswordUsersTable()
                + "(id, email, is_suspended, time_joined, password_hash)" + " VALUES(?, ?, ?, ?, ?)";
        QueryExecutorTemplate.update(start, QUERY, pst -> {
            pst.setString(1, userId);
            pst.setString(2, email);
            pst.setInt(3, isSuspendedAsInt);
            pst.setLong(4, timeJoined);
            pst.setString(5, passwordHash);
        });
    }
}