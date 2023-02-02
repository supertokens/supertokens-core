package io.supertokens.inmemorydb.queries;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import io.supertokens.inmemorydb.ResultSetValueExtractor;
import io.supertokens.inmemorydb.Start;
import io.supertokens.inmemorydb.config.Config;
import io.supertokens.pluginInterface.RowMapper;
import io.supertokens.pluginInterface.dashboard.DashboardUser;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import static io.supertokens.inmemorydb.QueryExecutorTemplate.execute;
import static io.supertokens.inmemorydb.QueryExecutorTemplate.update;

public class DashboardQueries {
    public static String getQueryToCreateUserIdMappingTable(Start start) {
        String tableName = Config.getConfig(start).getDashboardEmailPasswordUsersTable();
        // @formatter:off
        return "CREATE TABLE IF NOT EXISTS " + tableName + " ("
                + "id CHAR(36) NOT NULL,"
                + "email VARCHAR(256) NOT NULL UNIQUE,"
                + "password_hash VARCHAR(256) NOT NULL,"
                + "is_suspended TINYINT,"
                + "time_joined BIGINT UNSIGNED NOT NULL,"
                + "PRIMARY KEY(id));";
        // @formatter:on
    }

    public static void createDashboardUser(Start start, String userId, String email, String passwordHash,
            boolean isSuspended, long timeJoined) throws SQLException, StorageQueryException {
        // convert boolean to int since sqlite does not support boolean type
        int isSuspendedAsInt = isSuspended ? 1 : 0;

        String QUERY = "INSERT INTO " + Config.getConfig(start).getDashboardEmailPasswordUsersTable()
                + "(id, email, password_hash, is_suspended, time_joined)" + " VALUES(?, ?, ?, ?, ?)";
        update(start, QUERY, pst -> {
            pst.setString(1, userId);
            pst.setString(2, email);
            pst.setString(3, passwordHash);
            pst.setInt(4, isSuspendedAsInt);
            pst.setLong(5, timeJoined);
        });
    }

    public static DashboardUser[] getAllDashBoardUsers(Start start) throws SQLException, StorageQueryException {
        String QUERY = "SELECT * FROM "
                + Config.getConfig(start).getDashboardEmailPasswordUsersTable();
        return execute(start, QUERY, null, new DashboardUserInfoResultExtractor());
    }

    public static boolean deleteDashboardUserWithEmail(Start start, String email)
            throws SQLException, StorageQueryException {
        String QUERY = "DELETE FROM " + Config.getConfig(start).getDashboardEmailPasswordUsersTable()
                + " WHERE email = ?";
        // store the number of rows updated
        int rowUpdatedCount = update(start, QUERY, pst -> pst.setString(1, email));

        return rowUpdatedCount > 0;
    }

    public static boolean deleteDashboardUserWithUserId(Start start, String userId)
            throws SQLException, StorageQueryException {
        String QUERY = "DELETE FROM " + Config.getConfig(start).getDashboardEmailPasswordUsersTable() + " WHERE id = ?";
        // store the number of rows updated
        int rowUpdatedCount = update(start, QUERY, pst -> pst.setString(1, userId));

        return rowUpdatedCount > 0;
    }

    public static DashboardUser getDashboardUserByEmail(Start start, String email)
            throws SQLException, StorageQueryException {
        String QUERY = "SELECT * FROM "
                + Config.getConfig(start).getDashboardEmailPasswordUsersTable() + " WHERE email = ?";
        return execute(start, QUERY, pst -> pst.setString(1, email), result -> {
            if (result.next()) {
                return DashboardInfoMapper.getInstance().mapOrThrow(result);
            }
            return null;
        });
    }

    public static void updateDashboardUsersEmailWithEmail(Start start, String email, String newEmail) throws SQLException, StorageQueryException{
        String QUERY = "UPDATE " + Config.getConfig(start).getEmailPasswordUsersTable() + " SET email = ? WHERE email = ?";
        update(start, QUERY, pst -> {
            pst.setString(1, newEmail);
            pst.setString(2, email);
        });
    }

    public static  void updateDashboardUsersPasswordWithEmail(Start start, String email, String newPassword) throws SQLException, StorageQueryException{
        String QUERY = "UPDATE " + Config.getConfig(start).getEmailPasswordUsersTable() + " SET password = ? WHERE email = ?";
        update(start, QUERY, pst -> {
            pst.setString(1, newPassword);
            pst.setString(2, email);
        });
    }

    public static void updateDashboardUsersEmailWithUserId(Start start, String userId, String newEmail) throws SQLException, StorageQueryException{
        String QUERY = "UPDATE " + Config.getConfig(start).getEmailPasswordUsersTable() + " SET email = ? WHERE id = ?";
        update(start, QUERY, pst -> {
            pst.setString(1, newEmail);
            pst.setString(2, userId);
        });
    }

    public static void updateDashboardUsersPasswordWithUserId(Start start, String userId, String newPassword) throws SQLException, StorageQueryException{
        String QUERY = "UPDATE " + Config.getConfig(start).getEmailPasswordUsersTable() + " SET password = ? WHERE id = ?";
        update(start, QUERY, pst -> {
            pst.setString(1, newPassword);
            pst.setString(2, userId);
        });
    }

    private static class DashboardInfoMapper implements RowMapper<DashboardUser, ResultSet> {
        private static final DashboardInfoMapper INSTANCE = new DashboardInfoMapper();

        private DashboardInfoMapper() {
        }

        private static DashboardInfoMapper getInstance() {
            return INSTANCE;
        }

        @Override
        public DashboardUser map(ResultSet rs) throws Exception {

            return new DashboardUser(rs.getString("id"), rs.getString("email"), rs.getString("password_hash"),
                    rs.getLong("time_joined"), rs.getInt("is_suspended") == 1 ? true : false);
        }
    }

    private static class DashboardUserInfoResultExtractor implements ResultSetValueExtractor<DashboardUser[]> {
        @Override
        public DashboardUser[] extract(ResultSet result) throws SQLException, StorageQueryException {
            List<DashboardUser> temp = new ArrayList<>();
            while (result.next()) {
                temp.add(DashboardInfoMapper.getInstance().mapOrThrow(result));
            }
            return temp.toArray(DashboardUser[]::new);
        }
    }
}