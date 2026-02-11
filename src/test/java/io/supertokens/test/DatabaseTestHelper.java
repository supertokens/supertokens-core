/*
 *    Copyright (c) 2020, VRAI Labs and/or its affiliates. All rights reserved.
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
 *
 */

package io.supertokens.test;

import java.io.File;
import java.io.FileWriter;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Helper class for managing test-specific PostgreSQL databases.
 * Each worker gets its own isolated database to prevent interference during parallel execution.
 * Within a worker, tests share the same database (SharedProcess handles data cleanup).
 */
public class DatabaseTestHelper {

    private static final AtomicInteger testCounter = new AtomicInteger(0);

    // Thread-local storage for the current test's database name
    private static final ThreadLocal<String> currentTestDatabase = new ThreadLocal<>();

    // Static storage for per-worker database (created once per worker)
    private static volatile String workerDatabase = null;
    private static volatile boolean workerDatabaseInitialized = false;
    private static final Object workerDbLock = new Object();

    // PostgreSQL connection details - read from environment or use defaults
    private static final String PG_HOST = getConfigValue("TEST_PG_HOST", "pg");
    private static final String PG_PORT = getConfigValue("TEST_PG_PORT", "5432");
    private static final String PG_USER = getConfigValue("TEST_PG_USER", "root");
    private static final String PG_PASSWORD = getConfigValue("TEST_PG_PASSWORD", "root");
    private static final String PG_ADMIN_DATABASE = "postgres"; // Database to connect to for admin operations

    private static String getConfigValue(String envName, String defaultValue) {
        String value = System.getenv(envName);
        if (value == null || value.isEmpty()) {
            value = System.getProperty(envName);
        }
        return (value != null && !value.isEmpty()) ? value : defaultValue;
    }

    /**
     * Generate a unique database name for the current test.
     * Format: test_w{workerId}_t{timestamp}_{counter}
     */
    public static String generateTestDatabaseName() {
        String workerId = System.getProperty("org.gradle.test.worker", "0");
        long timestamp = System.currentTimeMillis();
        int counter = testCounter.incrementAndGet();

        // PostgreSQL database names must be lowercase and can't start with a number
        // Max length is 63 characters
        String dbName = String.format("test_w%s_%d_%d", workerId, timestamp % 1000000, counter);
        return dbName.toLowerCase();
    }

    /**
     * Get or create the test database for this worker.
     * The database is created once per worker and reused across tests.
     * SharedProcess handles data cleanup between tests via createAppForTesting().
     *
     * Note: Auxiliary databases (st1_w*, st2_w*, st3_w*) for multitenancy tests are now
     * created directly by the PostgreSQL plugin in modifyConfigToAddANewUserPoolForTesting().
     */
    public static String createTestDatabase() {
        // Check if we already have a main database for this worker
        synchronized (workerDbLock) {
            if (workerDatabaseInitialized && workerDatabase != null) {
                // Return the existing worker database
                currentTestDatabase.set(workerDatabase);
                return workerDatabase;
            }
        }

        String dbName = generateTestDatabaseName();

        try {
            Class.forName("org.postgresql.Driver");
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("PostgreSQL driver not found", e);
        }

        String adminUrl = String.format("jdbc:postgresql://%s:%s/%s", PG_HOST, PG_PORT, PG_ADMIN_DATABASE);

        try (Connection conn = DriverManager.getConnection(adminUrl, PG_USER, PG_PASSWORD);
             Statement stmt = conn.createStatement()) {

            // Create the main test database
            stmt.executeUpdate("CREATE DATABASE " + dbName);
            // System.out.println("[DatabaseTestHelper] Created test database: " + dbName);

            // Store as the worker's database (created once, reused for all tests in this worker)
            synchronized (workerDbLock) {
                workerDatabase = dbName;
                workerDatabaseInitialized = true;
            }
            currentTestDatabase.set(dbName);
            return dbName;

        } catch (SQLException e) {
            System.err.println("[DatabaseTestHelper] Failed to create database " + dbName + ": " + e.getMessage());
            throw new RuntimeException("Failed to create test database: " + dbName, e);
        }
    }

    // Note: Auxiliary database creation has been moved to the PostgreSQL plugin's
    // modifyConfigToAddANewUserPoolForTesting() method. This centralizes the database
    // creation logic and removes the need for test infrastructure to pre-create databases.

    /**
     * Clear the current test database reference.
     * Note: We don't actually drop the database between tests because SharedProcess
     * needs to maintain its connection. SharedProcess handles data cleanup via
     * createAppForTesting(). The database will be reused for subsequent tests.
     */
    public static void dropCurrentTestDatabase() {
        // Don't drop - just clear the thread-local reference
        // The database persists and is reused by SharedProcess
        currentTestDatabase.remove();
    }

    /**
     * Drop a specific test database.
     * Uses graceful drop without terminating connections to avoid interfering with parallel tests.
     */
    public static void dropTestDatabase(String dbName) {
        if (dbName == null || dbName.isEmpty()) {
            return;
        }

        // Don't drop the admin database or any non-test databases
        if (!dbName.startsWith("test_")) {
            System.err.println("[DatabaseTestHelper] Refusing to drop non-test database: " + dbName);
            return;
        }

        String adminUrl = String.format("jdbc:postgresql://%s:%s/%s", PG_HOST, PG_PORT, PG_ADMIN_DATABASE);

        try (Connection conn = DriverManager.getConnection(adminUrl, PG_USER, PG_PASSWORD);
             Statement stmt = conn.createStatement()) {

            // Try to drop the database gracefully without terminating connections.
            // This avoids interfering with parallel tests that might be sharing connections.
            // If the drop fails due to active connections, we just log and continue -
            // the database will be cleaned up by cleanupStaleTestDatabases later.
            stmt.executeUpdate("DROP DATABASE IF EXISTS " + dbName);
            // System.out.println("[DatabaseTestHelper] Dropped test database: " + dbName);

        } catch (SQLException e) {
            // Log but don't fail - database might have active connections or already be dropped
            // These will be cleaned up later by cleanupStaleTestDatabases
            System.err.println("[DatabaseTestHelper] Warning: Could not drop database " + dbName + " (will be cleaned later): " + e.getMessage());
        }
    }

    /**
     * Get the current test database name.
     */
    public static String getCurrentTestDatabase() {
        return currentTestDatabase.get();
    }

    /**
     * Set the current test database name (for cases where it's set externally).
     */
    public static void setCurrentTestDatabase(String dbName) {
        currentTestDatabase.set(dbName);
    }

    /**
     * Clean up any stale test databases that might have been left from previous runs.
     * This can be called at the start of a test run.
     * Unlike dropTestDatabase, this method forcefully terminates connections since
     * stale databases should not have any active connections from the current test run.
     */
    public static void cleanupStaleTestDatabases() {
        String adminUrl = String.format("jdbc:postgresql://%s:%s/%s", PG_HOST, PG_PORT, PG_ADMIN_DATABASE);

        try (Connection conn = DriverManager.getConnection(adminUrl, PG_USER, PG_PASSWORD);
             Statement stmt = conn.createStatement()) {

            // Find all test databases
            var rs = stmt.executeQuery(
                "SELECT datname FROM pg_database WHERE datname LIKE 'test_%'"
            );

            while (rs.next()) {
                String dbName = rs.getString(1);
                // System.out.println("[DatabaseTestHelper] Cleaning up stale database: " + dbName);
                forceDropTestDatabase(dbName, conn);
            }

        } catch (SQLException e) {
            System.err.println("[DatabaseTestHelper] Warning: Could not cleanup stale databases: " + e.getMessage());
        }
    }

    /**
     * Force drop a test database by terminating all connections first.
     * Only used for cleanup of stale databases, not during parallel test execution.
     */
    private static void forceDropTestDatabase(String dbName, Connection adminConn) {
        if (dbName == null || dbName.isEmpty() || !dbName.startsWith("test_")) {
            return;
        }

        try (Statement stmt = adminConn.createStatement()) {
            // Terminate all connections to the database
            stmt.executeUpdate(
                "SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname = '" + dbName + "'"
            );

            // Drop the database
            stmt.executeUpdate("DROP DATABASE IF EXISTS " + dbName);
            // System.out.println("[DatabaseTestHelper] Force dropped stale database: " + dbName);

        } catch (SQLException e) {
            System.err.println("[DatabaseTestHelper] Warning: Could not force drop database " + dbName + ": " + e.getMessage());
        }
    }

    /**
     * Get the JDBC URL for the current test database.
     */
    public static String getTestDatabaseUrl() {
        String dbName = currentTestDatabase.get();
        if (dbName == null) {
            throw new IllegalStateException("No test database has been created. Call createTestDatabase() first.");
        }
        return String.format("jdbc:postgresql://%s:%s/%s", PG_HOST, PG_PORT, dbName);
    }

    /**
     * Get the PostgreSQL host.
     */
    public static String getHost() {
        return PG_HOST;
    }

    /**
     * Get the PostgreSQL port.
     */
    public static String getPort() {
        return PG_PORT;
    }

    /**
     * Get the PostgreSQL user.
     */
    public static String getUser() {
        return PG_USER;
    }

    /**
     * Get the PostgreSQL password.
     */
    public static String getPassword() {
        return PG_PASSWORD;
    }

    /**
     * Take a snapshot of pg_stat_monitor numeric counters for a given database.
     * Returns a map of queryid -> (column_name -> cumulative_value), aggregated across buckets.
     * Used as the "before" baseline so that collectPgStatMonitorData can compute per-test deltas.
     */
    public static Map<String, Map<String, Double>> takePgStatMonitorSnapshot(String datname) {
        Map<String, Map<String, Double>> snapshot = new HashMap<>();
        if (datname == null || datname.isEmpty()) return snapshot;

        String collectEnv = System.getenv("COLLECT_PG_STAT_MONITOR");
        if (!"true".equalsIgnoreCase(collectEnv)) return snapshot;

        String adminUrl = String.format("jdbc:postgresql://%s:%s/%s", PG_HOST, PG_PORT, PG_ADMIN_DATABASE);

        try (Connection conn = DriverManager.getConnection(adminUrl, PG_USER, PG_PASSWORD);
             PreparedStatement pstmt = conn.prepareStatement(
                     "SELECT * FROM pg_stat_monitor WHERE datname = ?")) {

            pstmt.setString(1, datname);
            ResultSet rs = pstmt.executeQuery();
            ResultSetMetaData meta = rs.getMetaData();
            int columnCount = meta.getColumnCount();

            int queryidCol = findColumnIndex(meta, columnCount, "queryid");

            while (rs.next()) {
                String queryid = queryidCol > 0
                        ? String.valueOf(rs.getObject(queryidCol)) : "row_" + rs.getRow();
                Map<String, Double> row = snapshot.computeIfAbsent(queryid, k -> new HashMap<>());
                for (int i = 1; i <= columnCount; i++) {
                    Object value = rs.getObject(i);
                    if (value instanceof Number) {
                        row.merge(meta.getColumnName(i), ((Number) value).doubleValue(), Double::sum);
                    }
                }
            }

        } catch (Exception e) {
            System.err.println(
                    "[PgStatMonitor] Warning: Could not take snapshot for " + datname + ": "
                            + e.getMessage());
        }
        return snapshot;
    }

    /**
     * Collect pg_stat_monitor data for a given database and write per-test delta to a JSON file.
     * Only runs if COLLECT_PG_STAT_MONITOR environment variable is set to "true".
     * Connects to the "postgres" database where pg_stat_monitor extension is installed.
     *
     * @param beforeSnapshot snapshot taken before the test started (from takePgStatMonitorSnapshot)
     */
    public static void collectPgStatMonitorData(String datname, String testName,
                                                 Map<String, Map<String, Double>> beforeSnapshot) {
        if (datname == null || datname.isEmpty()) return;

        String collectEnv = System.getenv("COLLECT_PG_STAT_MONITOR");
        if (!"true".equalsIgnoreCase(collectEnv)) return;

        if (beforeSnapshot == null) beforeSnapshot = Collections.emptyMap();

        String adminUrl = String.format("jdbc:postgresql://%s:%s/%s", PG_HOST, PG_PORT, PG_ADMIN_DATABASE);

        try (Connection conn = DriverManager.getConnection(adminUrl, PG_USER, PG_PASSWORD);
             PreparedStatement pstmt = conn.prepareStatement(
                     "SELECT * FROM pg_stat_monitor WHERE datname = ?")) {

            pstmt.setString(1, datname);
            ResultSet rs = pstmt.executeQuery();
            ResultSetMetaData meta = rs.getMetaData();
            int columnCount = meta.getColumnCount();
            int queryidCol = findColumnIndex(meta, columnCount, "queryid");

            // Aggregate rows by queryid (across time buckets)
            Map<String, Map<String, Double>> afterNumeric = new LinkedHashMap<>();
            Map<String, Map<String, String>> afterStrings = new LinkedHashMap<>();

            while (rs.next()) {
                String queryid = queryidCol > 0
                        ? String.valueOf(rs.getObject(queryidCol)) : "row_" + rs.getRow();
                Map<String, Double> numRow = afterNumeric.computeIfAbsent(
                        queryid, k -> new LinkedHashMap<>());
                Map<String, String> strRow = afterStrings.computeIfAbsent(
                        queryid, k -> new LinkedHashMap<>());
                for (int i = 1; i <= columnCount; i++) {
                    String colName = meta.getColumnName(i);
                    Object value = rs.getObject(i);
                    if (value instanceof Number) {
                        numRow.merge(colName, ((Number) value).doubleValue(), Double::sum);
                    } else if (!strRow.containsKey(colName)) {
                        strRow.put(colName, value != null ? value.toString() : null);
                    }
                }
            }

            // Build JSON with delta values
            StringBuilder json = new StringBuilder();
            json.append("[\n");
            boolean firstRow = true;
            int rowCount = 0;

            for (String queryid : afterNumeric.keySet()) {
                Map<String, Double> afterVals = afterNumeric.get(queryid);
                Map<String, Double> beforeVals = beforeSnapshot.getOrDefault(
                        queryid, Collections.emptyMap());

                // Skip queries with no new activity
                boolean hasActivity = false;
                for (Map.Entry<String, Double> col : afterVals.entrySet()) {
                    if (col.getValue() - beforeVals.getOrDefault(col.getKey(), 0.0) > 0) {
                        hasActivity = true;
                        break;
                    }
                }
                if (!hasActivity) continue;

                if (!firstRow) json.append(",\n");
                firstRow = false;
                rowCount++;

                json.append("  {");
                boolean firstCol = true;

                // Non-numeric columns (query text, datname, etc.)
                for (Map.Entry<String, String> col : afterStrings.getOrDefault(
                        queryid, Collections.emptyMap()).entrySet()) {
                    if (!firstCol) json.append(", ");
                    firstCol = false;
                    json.append("\"").append(escapeJsonString(col.getKey())).append("\": ");
                    if (col.getValue() == null) {
                        json.append("null");
                    } else {
                        json.append("\"").append(escapeJsonString(col.getValue())).append("\"");
                    }
                }

                // Numeric columns as deltas
                for (Map.Entry<String, Double> col : afterVals.entrySet()) {
                    if (!firstCol) json.append(", ");
                    firstCol = false;
                    double delta = col.getValue() - beforeVals.getOrDefault(col.getKey(), 0.0);
                    json.append("\"").append(escapeJsonString(col.getKey())).append("\": ");
                    if (delta == Math.floor(delta) && !Double.isInfinite(delta)) {
                        json.append((long) delta);
                    } else {
                        json.append(delta);
                    }
                }
                json.append("}");
            }
            json.append("\n]");

            // Write to file
            String outputDir = System.getenv("PG_STAT_MONITOR_OUTPUT_DIR");
            if (outputDir == null || outputDir.isEmpty()) {
                outputDir = "pg_stat_monitor_output";
            }
            File dir = new File(outputDir);
            dir.mkdirs();

            String safeName = (testName != null ? testName : "unknown")
                    .replaceAll("[^a-zA-Z0-9._-]", "_");
            String filename = datname + "_" + safeName + "_" + System.currentTimeMillis() + ".json";
            File outputFile = new File(dir, filename);
            try (FileWriter fw = new FileWriter(outputFile)) {
                fw.write(json.toString());
            }

            System.out.println(
                    "[PgStatMonitor] Collected " + rowCount + " query stats for " + datname
                            + " (" + testName + ") -> " + outputFile.getAbsolutePath());

        } catch (Exception e) {
            System.err.println(
                    "[PgStatMonitor] Warning: Could not collect pg_stat_monitor data for " + datname
                            + ": " + e.getMessage());
        }
    }

    private static int findColumnIndex(ResultSetMetaData meta, int columnCount, String name)
            throws SQLException {
        for (int i = 1; i <= columnCount; i++) {
            if (name.equals(meta.getColumnName(i))) return i;
        }
        return -1;
    }

    private static String escapeJsonString(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
