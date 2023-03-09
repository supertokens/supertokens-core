package io.supertokens.inmemorydb.queries;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import io.supertokens.inmemorydb.Start;
import io.supertokens.inmemorydb.config.Config;
import io.supertokens.inmemorydb.ConnectionWithLocks;
import io.supertokens.pluginInterface.RowMapper;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.exceptions.StorageTransactionLogicException;
import io.supertokens.pluginInterface.totp.TOTPDevice;
import io.supertokens.pluginInterface.totp.TOTPUsedCode;

import static io.supertokens.inmemorydb.QueryExecutorTemplate.execute;
import static io.supertokens.inmemorydb.QueryExecutorTemplate.update;

public class TOTPQueries {
    public static String getQueryToCreateUsersTable(Start start) {
        return "CREATE TABLE IF NOT EXISTS " + Config.getConfig(start).getTotpUsersTable() + " ("
                + "user_id VARCHAR(128) NOT NULL,"
                + "PRIMARY KEY (user_id))";
    }

    public static String getQueryToCreateUserDevicesTable(Start start) {
        return "CREATE TABLE IF NOT EXISTS " + Config.getConfig(start).getTotpUserDevicesTable() + " ("
                + "user_id VARCHAR(128) NOT NULL," + "device_name VARCHAR(256) NOT NULL,"
                + "secret_key VARCHAR(256) NOT NULL,"
                + "period INTEGER NOT NULL," + "skew INTEGER NOT NULL," + "verified BOOLEAN NOT NULL,"
                + "PRIMARY KEY (user_id, device_name)"
                + "FOREIGN KEY (user_id) REFERENCES " + Config.getConfig(start).getTotpUsersTable()
                + "(user_id) ON DELETE CASCADE);";
    }

    public static String getQueryToCreateUsedCodesTable(Start start) {
        return "CREATE TABLE IF NOT EXISTS " + Config.getConfig(start).getTotpUsedCodesTable() + " ("
                + "user_id VARCHAR(128) NOT NULL, "
                + "code VARCHAR(8) NOT NULL," + "is_valid BOOLEAN NOT NULL,"
                + "created_time_ms BIGINT UNSIGNED NOT NULL,"
                + "expiry_time_ms BIGINT UNSIGNED NOT NULL,"
                + "PRIMARY KEY (user_id, created_time_ms)"
                + "FOREIGN KEY (user_id) REFERENCES " + Config.getConfig(start).getTotpUsersTable()
                + "(user_id) ON DELETE CASCADE);";
    }

    public static String getQueryToCreateUsedCodesExpiryTimeIndex(Start start) {
        return "CREATE INDEX IF NOT EXISTS totp_used_codes_expiry_time_ms_index ON "
                + Config.getConfig(start).getTotpUsedCodesTable() + " (expiry_time_ms)";
    }

    private static int insertUser_Transaction(Start start, Connection con, String userId)
            throws SQLException, StorageQueryException {
        // Create user if not exists:
        String QUERY = "INSERT INTO " + Config.getConfig(start).getTotpUsersTable()
                + " (user_id) VALUES (?) ON CONFLICT DO NOTHING";

        return update(con, QUERY, pst -> pst.setString(1, userId));
    }

    private static int insertDevice_Transaction(Start start, Connection con, TOTPDevice device)
            throws SQLException, StorageQueryException {
        String QUERY = "INSERT INTO " + Config.getConfig(start).getTotpUserDevicesTable()
                + " (user_id, device_name, secret_key, period, skew, verified) VALUES (?, ?, ?, ?, ?, ?)";

        return update(con, QUERY, pst -> {
            pst.setString(1, device.userId);
            pst.setString(2, device.deviceName);
            pst.setString(3, device.secretKey);
            pst.setInt(4, device.period);
            pst.setInt(5, device.skew);
            pst.setBoolean(6, device.verified);
        });
    }

    public static void createDevice(Start start, TOTPDevice device)
            throws StorageQueryException, StorageTransactionLogicException {
        start.startTransaction(con -> {
            Connection sqlCon = (Connection) con.getConnection();

            try {
                insertUser_Transaction(start, sqlCon, device.userId);
                insertDevice_Transaction(start, sqlCon, device);
                sqlCon.commit();
            } catch (SQLException e) {
                throw new StorageTransactionLogicException(e);
            }

            return null;
        });
        return;
    }

    public static int markDeviceAsVerified(Start start, String userId, String deviceName)
            throws StorageQueryException, SQLException {
        String QUERY = "UPDATE " + Config.getConfig(start).getTotpUserDevicesTable()
                + " SET verified = true WHERE user_id = ? AND device_name = ?";
        return update(start, QUERY, pst -> {
            pst.setString(1, userId);
            pst.setString(2, deviceName);
        });
    }

    public static int deleteDevice_Transaction(Start start, Connection con, String userId, String deviceName)
            throws SQLException, StorageQueryException {
        String QUERY = "DELETE FROM " + Config.getConfig(start).getTotpUserDevicesTable()
                + " WHERE user_id = ? AND device_name = ?;";

        return update(con, QUERY, pst -> {
            pst.setString(1, userId);
            pst.setString(2, deviceName);
        });
    }

    public static int removeUser_Transaction(Start start, Connection con, String userId)
            throws SQLException, StorageQueryException {
        String QUERY = "DELETE FROM " + Config.getConfig(start).getTotpUsersTable()
                + " WHERE user_id = ?;";
        int removedUsersCount = update(con, QUERY, pst -> pst.setString(1, userId));

        return removedUsersCount;
    }

    public static int updateDeviceName(Start start, String userId, String oldDeviceName, String newDeviceName)
            throws StorageQueryException, SQLException {
        String QUERY = "UPDATE " + Config.getConfig(start).getTotpUserDevicesTable()
                + " SET device_name = ? WHERE user_id = ? AND device_name = ?;";

        return update(start, QUERY, pst -> {
            pst.setString(1, newDeviceName);
            pst.setString(2, userId);
            pst.setString(3, oldDeviceName);
        });
    }

    public static TOTPDevice[] getDevices(Start start, String userId)
            throws StorageQueryException, SQLException {
        String QUERY = "SELECT * FROM " + Config.getConfig(start).getTotpUserDevicesTable()
                + " WHERE user_id = ?;";

        return execute(start, QUERY, pst -> pst.setString(1, userId), result -> {
            List<TOTPDevice> devices = new ArrayList<>();
            while (result.next()) {
                devices.add(TOTPDeviceRowMapper.getInstance().map(result));
            }

            return devices.toArray(TOTPDevice[]::new);
        });
    }

    public static int getDevicesCount_Transaction(Start start, Connection con, String userId)
            throws StorageQueryException, SQLException {
        ((ConnectionWithLocks) con).lock(userId + Config.getConfig(start).getTotpUserDevicesTable());

        String QUERY = "SELECT COUNT(*) as count FROM " + Config.getConfig(start).getTotpUserDevicesTable()
                + " WHERE user_id = ?;";

        return execute(con, QUERY, pst -> pst.setString(1, userId), result -> {
            return result.getInt("count");
        });
    }

    public static int insertUsedCode_Transaction(Start start, Connection con, TOTPUsedCode code)
            throws SQLException, StorageQueryException {
        String QUERY = "INSERT INTO " + Config.getConfig(start).getTotpUsedCodesTable()
                + " (user_id, code, is_valid, expiry_time_ms, created_time_ms) VALUES (?, ?, ?, ?, ?);";

        return update(con, QUERY, pst -> {
            pst.setString(1, code.userId);
            pst.setString(2, code.code);
            pst.setBoolean(3, code.isValid);
            pst.setLong(4, code.expiryTime);
            pst.setLong(5, code.createdTime);
        });
    }

    /**
     * Query to get all used codes (expired/non-expired) for a user in descending
     * order of creation time.
     */
    public static TOTPUsedCode[] getAllUsedCodesDescOrderAndLockByUser_Transaction(Start start, Connection con,
            String userId)
            throws SQLException, StorageQueryException {
        // Take a lock based on the user id:
        ((ConnectionWithLocks) con).lock(userId + Config.getConfig(start).getTotpUsedCodesTable());

        String QUERY = "SELECT * FROM " +
                Config.getConfig(start).getTotpUsedCodesTable()
                + " WHERE user_id = ? ORDER BY created_time_ms DESC";
        return execute(con, QUERY, pst -> {
            pst.setString(1, userId);
        }, result -> {
            List<TOTPUsedCode> codes = new ArrayList<>();
            while (result.next()) {
                codes.add(TOTPUsedCodeRowMapper.getInstance().map(result));
            }

            return codes.toArray(TOTPUsedCode[]::new);
        });
    }

    public static int removeExpiredCodes(Start start, long expiredBefore)
            throws StorageQueryException, SQLException {
        String QUERY = "DELETE FROM " + Config.getConfig(start).getTotpUsedCodesTable()
                + " WHERE expiry_time_ms < ?;";

        return update(start, QUERY, pst -> pst.setLong(1, expiredBefore));
    }

    private static class TOTPDeviceRowMapper implements RowMapper<TOTPDevice, ResultSet> {
        private static final TOTPDeviceRowMapper INSTANCE = new TOTPDeviceRowMapper();

        private TOTPDeviceRowMapper() {
        }

        private static TOTPDeviceRowMapper getInstance() {
            return INSTANCE;
        }

        @Override
        public TOTPDevice map(ResultSet result) throws SQLException {
            return new TOTPDevice(
                    result.getString("user_id"),
                    result.getString("device_name"),
                    result.getString("secret_key"),
                    result.getInt("period"),
                    result.getInt("skew"),
                    result.getBoolean("verified"));
        }
    }

    private static class TOTPUsedCodeRowMapper implements RowMapper<TOTPUsedCode, ResultSet> {
        private static final TOTPUsedCodeRowMapper INSTANCE = new TOTPUsedCodeRowMapper();

        private TOTPUsedCodeRowMapper() {
        }

        private static TOTPUsedCodeRowMapper getInstance() {
            return INSTANCE;
        }

        @Override
        public TOTPUsedCode map(ResultSet result) throws SQLException {
            return new TOTPUsedCode(
                    result.getString("user_id"),
                    result.getString("code"),
                    result.getBoolean("is_valid"),
                    result.getLong("expiry_time_ms"),
                    result.getLong("created_time_ms"));
            // FIXME: Put created time first, then expiry time.
        }
    }
}
