package io.supertokens.inmemorydb.queries;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import io.supertokens.inmemorydb.ConnectionWithLocks;
import io.supertokens.inmemorydb.PreparedStatementValueSetter;
import io.supertokens.inmemorydb.Start;
import io.supertokens.inmemorydb.config.Config;
import io.supertokens.pluginInterface.RowMapper;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.exceptions.StorageTransactionLogicException;
import io.supertokens.pluginInterface.sqlStorage.SQLStorage.TransactionIsolationLevel;

import io.supertokens.pluginInterface.totp.TOTPDevice;
import io.supertokens.pluginInterface.totp.TOTPUsedCode;

import static io.supertokens.inmemorydb.QueryExecutorTemplate.execute;
import static io.supertokens.inmemorydb.QueryExecutorTemplate.update;

public class TOTPQueries {
    public static String getQueryToCreateUserDevicesTable(Start start) {
        // Todo: verify if "DEFAULT FALSE" is correct
        // TODO: Verify all queries using SQLite
        return "CREATE TABLE IF NOT EXISTS" + Config.getConfig(start).getTotpUserDevicesTable() + " ("
                + "user_id VARCHAR(128) NOT NULL," + "device_name VARCHAR(256) NOT NULL,"
                + "secret_key VARCHAR(256) NOT NULL,"
                + "period INTEGER NOT NULL," + "skew INTEGER NOT NULL," + "verified BOOLEAN NOT NULL,"
                + "PRIMARY KEY (user_id, device_name)";
    }

    public static String getQueryToCreateUsedCodesTable(Start start) {
        return "CREATE TABLE IF NOT EXISTS" + Config.getConfig(start).getTotpUsedCodesTable() + " ("
                + "user_id VARCHAR(128) NOT NULL," + "code VARCHAR(6) NOT NULL," + "is_valid_code BOOLEAN NOT NULL,"
                + "expiry_time BIGINT NOT NULL," + "PRIMARY KEY (user_id, code),"
                + "FOREIGN KEY (user_id) REFERENCES" + Config.getConfig(start).getTotpUserDevicesTable()
                + "(user_id) ON DELETE CASCADE";
    }

    public static String getQueryToCreateUsedCodesIndex(Start start) {
        return "CREATE INDEX IF NOT EXISTS totp_used_codes_expiry_time_index ON "
                + Config.getConfig(start).getTotpUsedCodesTable() + " (expiry_time)";
    }

    public static void createDevice(Start start, TOTPDevice device)
            throws StorageQueryException, StorageTransactionLogicException, SQLException {
        String QUERY = "INSERT INTO " + Config.getConfig(start).getTotpUserDevicesTable()
                + " (deviceName, userId, secretKey, period, skew, verified) VALUES (?, ?, ?, ?, ?, ?)";

        update(start, QUERY, pst -> {
            pst.setString(1, device.deviceName);
            pst.setString(2, device.userId);
            pst.setString(3, device.secretKey);
            pst.setInt(4, device.period);
            pst.setInt(5, device.skew);
            pst.setBoolean(6, device.verified);
        });
    }

    public static void markDeviceAsVerified(Start start, String userId, String deviceName)
            throws StorageTransactionLogicException, StorageQueryException, SQLException {
        String QUERY = "UPDATE " + Config.getConfig(start).getTotpUserDevicesTable()
                + " SET verified = true WHERE user_id = ? AND device_name = ?;"; // What if device is already
                                                                                 // verified?
        update(start, QUERY, pst -> {
            pst.setString(1, userId);
            pst.setString(2, deviceName);
        });
    }

    public static void deleteDevice(Start start, String userId, String deviceName)
            throws StorageTransactionLogicException, StorageQueryException, SQLException {
        String QUERY = "DELETE FROM " + Config.getConfig(start).getTotpUserDevicesTable()
                + " WHERE user_id = ? AND device_name = ?;";

        update(start, QUERY, pst -> {
            pst.setString(1, userId);
            pst.setString(2, deviceName);
        });
    }

    public static void updateDeviceName(Start start, String userId, String oldDeviceName, String newDeviceName)
            throws StorageTransactionLogicException, StorageQueryException, SQLException {
        String QUERY = "UPDATE " + Config.getConfig(start).getTotpUserDevicesTable()
                + " SET device_name = ? WHERE user_id = ? AND device_name = ?;";

        update(start, QUERY, pst -> {
            pst.setString(1, newDeviceName);
            pst.setString(2, userId);
            pst.setString(3, oldDeviceName);
        });
    }

    public static TOTPDevice[] getDevices(Start start, String userId)
            throws StorageTransactionLogicException, StorageQueryException, SQLException {
        String QUERY = "SELECT * FROM " + Config.getConfig(start).getTotpUserDevicesTable()
                + " WHERE user_id = ?;";

        return execute(start, QUERY, pst -> pst.setString(1, userId), result -> {
            List<TOTPDevice> devices = new ArrayList<>();
            while (result.next()) {
                devices.add(TOTPDeviceRowMapper.getInstance().map(result));
            }

            return devices.toArray(new TOTPDevice[0]);
        });
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
                    result.getString("device_name"),
                    result.getString("user_id"),
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
                    result.getBoolean("is_valid_code"),
                    result.getLong("expiry_time"));
        }
    }

    public static boolean insertUsedCode(Start start, TOTPUsedCode code) throws SQLException, StorageQueryException {
        String QUERY = "INSERT INTO " + Config.getConfig(start).getTotpUsedCodesTable()
                + " (user_id, code, is_valid_code, expiry_time) VALUES (?, ?, ?, ?);";

        update(start, QUERY, pst -> {
            pst.setString(1, code.userId);
            pst.setString(2, code.code);
            pst.setBoolean(3, code.isValidCode);
            pst.setLong(4, code.expiryTime);
        });
        return true; // FIXME: This is not correct. We should check if the code was inserted or not.
    }

    public static TOTPUsedCode[] getUsedCodes(Start start, String userId) throws SQLException, StorageQueryException {
        String QUERY = "SELECT * FROM " +
                Config.getConfig(start).getTotpUsedCodesTable()
                + " WHERE user_id = ?;";
        return execute(start, QUERY, pst -> pst.setString(1, userId), result -> {
            List<TOTPUsedCode> codes = new ArrayList<>();
            while (result.next()) {
                codes.add(TOTPUsedCodeRowMapper.getInstance().map(result));
            }

            return codes.toArray(new TOTPUsedCode[0]);
        });
    }

    public static void removeExpiredCodes(Start start)
            throws StorageTransactionLogicException, StorageQueryException, SQLException {
        String QUERY = "DELETE FROM " + Config.getConfig(start).getTotpUsedCodesTable()
                + " WHERE expiry_time < ?;";

        update(start, QUERY, pst -> pst.setLong(1, System.currentTimeMillis()));
    }
}
