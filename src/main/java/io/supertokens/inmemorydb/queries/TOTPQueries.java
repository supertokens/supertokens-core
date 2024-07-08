package io.supertokens.inmemorydb.queries;

import io.supertokens.inmemorydb.ConnectionWithLocks;
import io.supertokens.inmemorydb.Start;
import io.supertokens.inmemorydb.config.Config;
import io.supertokens.pluginInterface.RowMapper;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.exceptions.StorageTransactionLogicException;
import io.supertokens.pluginInterface.multitenancy.AppIdentifier;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifier;
import io.supertokens.pluginInterface.totp.TOTPDevice;
import io.supertokens.pluginInterface.totp.TOTPUsedCode;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import static io.supertokens.inmemorydb.QueryExecutorTemplate.execute;
import static io.supertokens.inmemorydb.QueryExecutorTemplate.update;

public class TOTPQueries {
    public static String getQueryToCreateUsersTable(Start start) {
        return "CREATE TABLE IF NOT EXISTS " + Config.getConfig(start).getTotpUsersTable() + " ("
                + "app_id VARCHAR(64) DEFAULT 'public',"
                + "user_id VARCHAR(128) NOT NULL,"
                + "PRIMARY KEY (app_id, user_id),"
                + "FOREIGN KEY (app_id) REFERENCES " + Config.getConfig(start).getAppsTable()
                + " (app_id) ON DELETE CASCADE"
                + ")";
    }

    public static String getQueryToCreateUserDevicesTable(Start start) {
        return "CREATE TABLE IF NOT EXISTS " + Config.getConfig(start).getTotpUserDevicesTable() + " ("
                + "app_id VARCHAR(64) DEFAULT 'public',"
                + "user_id VARCHAR(128) NOT NULL,"
                + "device_name VARCHAR(256) NOT NULL,"
                + "secret_key VARCHAR(256) NOT NULL,"
                + "period INTEGER NOT NULL,"
                + "skew INTEGER NOT NULL,"
                + "verified BOOLEAN NOT NULL,"
                + "created_at BIGINT UNSIGNED NOT NULL,"
                + "PRIMARY KEY (app_id, user_id, device_name),"
                + "FOREIGN KEY (app_id, user_id) REFERENCES " + Config.getConfig(start).getTotpUsersTable()
                + " (app_id, user_id) ON DELETE CASCADE"
                + ");";
    }

    public static String getQueryToCreateUsedCodesTable(Start start) {
        return "CREATE TABLE IF NOT EXISTS " + Config.getConfig(start).getTotpUsedCodesTable() + " ("
                + "app_id VARCHAR(64) DEFAULT 'public',"
                + "tenant_id VARCHAR(64) DEFAULT 'public',"
                + "user_id VARCHAR(128) NOT NULL, "
                // SQLite doesn't follow VARCHAR length by default
                // But we can add a check constraint to make sure the length is <= 8
                + "code VARCHAR(8) NOT NULL CHECK(LENGTH(code) <= 8),"
                + "is_valid BOOLEAN NOT NULL,"
                + "expiry_time_ms BIGINT UNSIGNED NOT NULL,"
                + "created_time_ms BIGINT UNSIGNED NOT NULL,"
                + "PRIMARY KEY (app_id, tenant_id, user_id, created_time_ms),"
                + "FOREIGN KEY (app_id, user_id) REFERENCES " + Config.getConfig(start).getTotpUsersTable()
                + " (app_id, user_id) ON DELETE CASCADE,"
                + "FOREIGN KEY (app_id, tenant_id) REFERENCES " + Config.getConfig(start).getTenantsTable()
                + " (app_id, tenant_id) ON DELETE CASCADE"
                + ");";
    }

    public static String getQueryToCreateUsedCodesExpiryTimeIndex(Start start) {
        return "CREATE INDEX IF NOT EXISTS totp_used_codes_expiry_time_ms_index ON "
                + Config.getConfig(start).getTotpUsedCodesTable() + " (app_id, tenant_id, expiry_time_ms)";
    }

    private static int insertUser_Transaction(Start start, Connection con, AppIdentifier appIdentifier, String userId)
            throws SQLException, StorageQueryException {
        // Create user if not exists:
        String QUERY = "INSERT INTO " + Config.getConfig(start).getTotpUsersTable()
                + " (app_id, user_id) VALUES (?, ?) ON CONFLICT DO NOTHING";

        return update(con, QUERY, pst -> {
            pst.setString(1, appIdentifier.getAppId());
            pst.setString(2, userId);
        });
    }

    private static int insertDevice_Transaction(Start start, Connection con, AppIdentifier appIdentifier,
                                                TOTPDevice device)
            throws SQLException, StorageQueryException {
        String QUERY = "INSERT INTO " + Config.getConfig(start).getTotpUserDevicesTable()
                +
                " (app_id, user_id, device_name, secret_key, period, skew, verified, created_at) VALUES (?, ?, ?, ?, " +
                "?, ?, ?, ?)";

        return update(con, QUERY, pst -> {
            pst.setString(1, appIdentifier.getAppId());
            pst.setString(2, device.userId);
            pst.setString(3, device.deviceName);
            pst.setString(4, device.secretKey);
            pst.setInt(5, device.period);
            pst.setInt(6, device.skew);
            pst.setBoolean(7, device.verified);
            pst.setLong(8, device.createdAt);
        });
    }

    public static void createDevice_Transaction(Start start, Connection sqlCon, AppIdentifier appIdentifier,
                                                TOTPDevice device)
            throws StorageQueryException, SQLException {
        insertUser_Transaction(start, sqlCon, appIdentifier, device.userId);
        insertDevice_Transaction(start, sqlCon, appIdentifier, device);
    }

    public static TOTPDevice getDeviceByName_Transaction(Start start, Connection sqlCon, AppIdentifier appIdentifier,
                                                         String userId, String deviceName)
            throws SQLException, StorageQueryException {

        ((ConnectionWithLocks) sqlCon).lock(
                appIdentifier.getAppId() + "~" + userId + "~" + deviceName +
                        Config.getConfig(start).getTotpUserDevicesTable());

        String QUERY = "SELECT * FROM " + Config.getConfig(start).getTotpUserDevicesTable()
                + " WHERE app_id = ? AND user_id = ? AND device_name = ?;";

        return execute(sqlCon, QUERY, pst -> {
            pst.setString(1, appIdentifier.getAppId());
            pst.setString(2, userId);
            pst.setString(3, deviceName);
        }, result -> {
            if (result.next()) {
                return TOTPDeviceRowMapper.getInstance().map(result);
            }
            return null;
        });
    }

    public static int markDeviceAsVerified(Start start, AppIdentifier appIdentifier, String userId, String deviceName)
            throws StorageQueryException, SQLException {
        String QUERY = "UPDATE " + Config.getConfig(start).getTotpUserDevicesTable()
                + " SET verified = true WHERE app_id = ? AND user_id = ? AND device_name = ?";
        return update(start, QUERY, pst -> {
            pst.setString(1, appIdentifier.getAppId());
            pst.setString(2, userId);
            pst.setString(3, deviceName);
        });
    }

    public static int deleteDevice_Transaction(Start start, Connection con, AppIdentifier appIdentifier, String userId,
                                               String deviceName)
            throws SQLException, StorageQueryException {
        String QUERY = "DELETE FROM " + Config.getConfig(start).getTotpUserDevicesTable()
                + " WHERE app_id = ? AND user_id = ? AND device_name = ?;";

        return update(con, QUERY, pst -> {
            pst.setString(1, appIdentifier.getAppId());
            pst.setString(2, userId);
            pst.setString(3, deviceName);
        });
    }

    public static int removeUser_Transaction(Start start, Connection con, AppIdentifier appIdentifier, String userId)
            throws SQLException, StorageQueryException {
        String QUERY = "DELETE FROM " + Config.getConfig(start).getTotpUsersTable()
                + " WHERE app_id = ? AND user_id = ?;";
        int removedUsersCount = update(con, QUERY, pst -> {
            pst.setString(1, appIdentifier.getAppId());
            pst.setString(2, userId);
        });

        return removedUsersCount;
    }

    public static boolean removeUser(Start start, TenantIdentifier tenantIdentifier, String userId)
            throws SQLException, StorageQueryException {
        String QUERY = "DELETE FROM " + Config.getConfig(start).getTotpUsedCodesTable()
                + " WHERE app_id = ? AND tenant_id = ? AND user_id = ?;";
        int removedUsersCount = update(start, QUERY, pst -> {
            pst.setString(1, tenantIdentifier.getAppId());
            pst.setString(2, tenantIdentifier.getTenantId());
            pst.setString(3, userId);
        });

        return removedUsersCount > 0;
    }

    public static int updateDeviceName(Start start, AppIdentifier appIdentifier, String userId, String oldDeviceName,
                                       String newDeviceName)
            throws StorageQueryException, SQLException {
        String QUERY = "UPDATE " + Config.getConfig(start).getTotpUserDevicesTable()
                + " SET device_name = ? WHERE app_id = ? AND user_id = ? AND device_name = ?;";

        return update(start, QUERY, pst -> {
            pst.setString(1, newDeviceName);
            pst.setString(2, appIdentifier.getAppId());
            pst.setString(3, userId);
            pst.setString(4, oldDeviceName);
        });
    }

    public static TOTPDevice[] getDevices(Start start, AppIdentifier appIdentifier, String userId)
            throws StorageQueryException, SQLException {
        String QUERY = "SELECT * FROM " + Config.getConfig(start).getTotpUserDevicesTable()
                + " WHERE app_id = ? AND user_id = ?;";

        return execute(start, QUERY, pst -> {
            pst.setString(1, appIdentifier.getAppId());
            pst.setString(2, userId);
        }, result -> {
            List<TOTPDevice> devices = new ArrayList<>();
            while (result.next()) {
                devices.add(TOTPDeviceRowMapper.getInstance().map(result));
            }

            return devices.toArray(TOTPDevice[]::new);
        });
    }

    public static TOTPDevice[] getDevices_Transaction(Start start, Connection con, AppIdentifier appIdentifier,
                                                      String userId)
            throws StorageQueryException, SQLException {

        ((ConnectionWithLocks) con).lock(
                appIdentifier.getAppId() + "~" + userId + Config.getConfig(start).getTotpUserDevicesTable());

        String QUERY = "SELECT * FROM " + Config.getConfig(start).getTotpUserDevicesTable()
                + " WHERE app_id = ? AND user_id = ?;";

        return execute(con, QUERY, pst -> {
            pst.setString(1, appIdentifier.getAppId());
            pst.setString(2, userId);
        }, result -> {
            List<TOTPDevice> devices = new ArrayList<>();
            while (result.next()) {
                devices.add(TOTPDeviceRowMapper.getInstance().map(result));
            }

            return devices.toArray(TOTPDevice[]::new);
        });

    }

    public static int insertUsedCode_Transaction(Start start, Connection con, TenantIdentifier tenantIdentifier,
                                                 TOTPUsedCode code)
            throws SQLException, StorageQueryException {
        String QUERY = "INSERT INTO " + Config.getConfig(start).getTotpUsedCodesTable()
                +
                " (app_id, tenant_id, user_id, code, is_valid, expiry_time_ms, created_time_ms) VALUES (?, ?, ?, ?, " +
                "?, ?, ?);";

        return update(con, QUERY, pst -> {
            pst.setString(1, tenantIdentifier.getAppId());
            pst.setString(2, tenantIdentifier.getTenantId());
            pst.setString(3, code.userId);
            pst.setString(4, code.code);
            pst.setBoolean(5, code.isValid);
            pst.setLong(6, code.expiryTime);
            pst.setLong(7, code.createdTime);
        });
    }

    /**
     * Query to get all used codes (expired/non-expired) for a user in descending
     * order of creation time.
     */
    public static TOTPUsedCode[] getAllUsedCodesDescOrder_Transaction(Start start, Connection con,
                                                                      TenantIdentifier tenantIdentifier, String userId)
            throws SQLException, StorageQueryException {
        // Take a lock based on the user id:
        ((ConnectionWithLocks) con).lock(
                tenantIdentifier.getAppId() + "~" + tenantIdentifier.getTenantId() + "~" + userId +
                        Config.getConfig(start).getTotpUsedCodesTable());

        String QUERY = "SELECT * FROM " +
                Config.getConfig(start).getTotpUsedCodesTable()
                + " WHERE app_id = ? AND tenant_id = ? AND user_id = ? ORDER BY created_time_ms DESC;";
        return execute(con, QUERY, pst -> {
            pst.setString(1, tenantIdentifier.getAppId());
            pst.setString(2, tenantIdentifier.getTenantId());
            pst.setString(3, userId);
        }, result -> {
            List<TOTPUsedCode> codes = new ArrayList<>();
            while (result.next()) {
                codes.add(TOTPUsedCodeRowMapper.getInstance().map(result));
            }

            return codes.toArray(TOTPUsedCode[]::new);
        });
    }

    public static int removeExpiredCodes(Start start, TenantIdentifier tenantIdentifier, long expiredBefore)
            throws StorageQueryException, SQLException {
        String QUERY = "DELETE FROM " + Config.getConfig(start).getTotpUsedCodesTable()
                + " WHERE app_id = ? AND tenant_id = ? AND expiry_time_ms < ?;";

        return update(start, QUERY, pst -> {
            pst.setString(1, tenantIdentifier.getAppId());
            pst.setString(2, tenantIdentifier.getTenantId());
            pst.setLong(3, expiredBefore);
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
                    result.getString("user_id"),
                    result.getString("device_name"),
                    result.getString("secret_key"),
                    result.getInt("period"),
                    result.getInt("skew"),
                    result.getBoolean("verified"),
                    result.getLong("created_at"));
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
        }
    }
}
