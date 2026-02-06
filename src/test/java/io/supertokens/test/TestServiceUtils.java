/*
 *    Copyright (c) 2025, VRAI Labs and/or its affiliates. All rights reserved.
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

package io.supertokens.test;

import java.io.IOException;
import java.net.Socket;
import java.util.Random;

public class TestServiceUtils {
    // Environment variable names for external service configuration
    private static final String ENV_PG_HOST = "TEST_PG_HOST";
    private static final String ENV_PG_PORT = "TEST_PG_PORT";
    private static final String ENV_PG_DB = "TEST_PG_DB";
    private static final String ENV_PG_USER = "TEST_PG_USER";
    private static final String ENV_PG_PASSWORD = "TEST_PG_PASSWORD";
    private static final String ENV_OAUTH_HOST = "TEST_OAUTH_HOST";
    private static final String ENV_OAUTH_PUBLIC_PORT = "TEST_OAUTH_PUBLIC_PORT";
    private static final String ENV_OAUTH_ADMIN_PORT = "TEST_OAUTH_ADMIN_PORT";

    // Flags to track if we're using external services
    private static boolean useExternalPostgres = false;
    private static boolean useExternalOAuth = false;

    static {
        // Check if external services are configured via environment variables
        checkExternalServicesConfiguration();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                killServices();
            } catch (Exception e) {
                System.out.println(e.getMessage());
            }
        }));
    }

    /**
     * Gets a configuration value from either environment variable or system property.
     * Environment variables take precedence.
     */
    private static String getConfigValue(String name) {
        String value = System.getenv(name);
        if (value == null) {
            value = System.getProperty(name);
        }
        return value;
    }

    private static void checkExternalServicesConfiguration() {
        // System.out.println("[TestServiceUtils] Checking for external services configuration...");

        // Check PostgreSQL external configuration
        String pgHost = getConfigValue(ENV_PG_HOST);
        String pgPort = getConfigValue(ENV_PG_PORT);

        // System.out.println("[TestServiceUtils] " + ENV_PG_HOST + " = " + pgHost);
        // System.out.println("[TestServiceUtils] " + ENV_PG_PORT + " = " + pgPort);

        if (pgHost != null && pgPort != null) {
            useExternalPostgres = true;
            // System.out.println("[TestServiceUtils] External PostgreSQL configured: " + pgHost + ":" + pgPort);
            // Set system properties for the PostgreSQL plugin
            System.setProperty("ST_POSTGRESQL_PLUGIN_SERVER_HOST", pgHost);
            System.setProperty("ST_POSTGRESQL_PLUGIN_SERVER_PORT", pgPort);
        } else {
            // System.out.println("[TestServiceUtils] External PostgreSQL NOT configured - will use Docker containers");
        }

        // Check OAuth external configuration
        String oauthHost = getConfigValue(ENV_OAUTH_HOST);
        String oauthPublicPort = getConfigValue(ENV_OAUTH_PUBLIC_PORT);
        String oauthAdminPort = getConfigValue(ENV_OAUTH_ADMIN_PORT);

        // System.out.println("[TestServiceUtils] " + ENV_OAUTH_HOST + " = " + oauthHost);
        // System.out.println("[TestServiceUtils] " + ENV_OAUTH_PUBLIC_PORT + " = " + oauthPublicPort);
        // System.out.println("[TestServiceUtils] " + ENV_OAUTH_ADMIN_PORT + " = " + oauthAdminPort);

        if (oauthHost != null && oauthPublicPort != null && oauthAdminPort != null) {
            useExternalOAuth = true;
            // System.out.println("[TestServiceUtils] External OAuth configured: " + oauthHost + ":" + oauthPublicPort + "/" + oauthAdminPort);
            // Set system properties for OAuth
            System.setProperty("ST_OAUTH_PROVIDER_SERVICE_HOST", oauthHost);
            System.setProperty("ST_OAUTH_PROVIDER_SERVICE_PORT", oauthPublicPort);
            System.setProperty("ST_OAUTH_PROVIDER_ADMIN_PORT", oauthAdminPort);
        } else {
            // System.out.println("[TestServiceUtils] External OAuth NOT configured - will use Docker containers");
        }
    }

    public static void startServices() {
        try {
            if (useExternalOAuth) {
                // System.out.println("[TestServiceUtils] Using external OAuth service, skipping container startup");
                verifyOAuthConnectivity();
            } else {
                OAuthProviderService.startService();
            }

            if (useExternalPostgres) {
                // System.out.println("[TestServiceUtils] Using external PostgreSQL service, skipping container startup");
                verifyPostgresConnectivity();
            } else {
                PostgresqlService.startService();
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static void killServices() throws InterruptedException, IOException {
        if (!useExternalOAuth) {
            OAuthProviderService.killService();
        }
        if (!useExternalPostgres) {
            PostgresqlService.killService();
        }
    }

    private static void verifyPostgresConnectivity() throws IOException {
        String host = getConfigValue(ENV_PG_HOST);
        int port = Integer.parseInt(getConfigValue(ENV_PG_PORT));
        // System.out.println("[TestServiceUtils] Verifying PostgreSQL connectivity to " + host + ":" + port);

        int maxAttempts = 30;
        for (int i = 0; i < maxAttempts; i++) {
            try (Socket socket = new Socket(host, port)) {
                // System.out.println("[TestServiceUtils] Successfully connected to PostgreSQL at " + host + ":" + port);
                return;
            } catch (IOException e) {
                if (i < maxAttempts - 1) {
                    // System.out.println("[TestServiceUtils] PostgreSQL not ready, attempt " + (i + 1) + "/" + maxAttempts);
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new IOException("Interrupted while waiting for PostgreSQL", ie);
                    }
                }
            }
        }
        throw new IOException("Failed to connect to PostgreSQL at " + host + ":" + port + " after " + maxAttempts + " attempts");
    }

    private static void verifyOAuthConnectivity() throws IOException {
        String host = getConfigValue(ENV_OAUTH_HOST);
        int publicPort = Integer.parseInt(getConfigValue(ENV_OAUTH_PUBLIC_PORT));
        int adminPort = Integer.parseInt(getConfigValue(ENV_OAUTH_ADMIN_PORT));
        // System.out.println("[TestServiceUtils] Verifying OAuth connectivity to " + host + ":" + publicPort + " and " + host + ":" + adminPort);

        int maxAttempts = 30;
        // Verify public port
        for (int i = 0; i < maxAttempts; i++) {
            try (Socket socket = new Socket(host, publicPort)) {
                // System.out.println("[TestServiceUtils] Successfully connected to OAuth public port at " + host + ":" + publicPort);
                break;
            } catch (IOException e) {
                if (i == maxAttempts - 1) {
                    throw new IOException("Failed to connect to OAuth public port at " + host + ":" + publicPort + " after " + maxAttempts + " attempts");
                }
                // System.out.println("[TestServiceUtils] OAuth public port not ready, attempt " + (i + 1) + "/" + maxAttempts);
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Interrupted while waiting for OAuth", ie);
                }
            }
        }

        // Verify admin port
        for (int i = 0; i < maxAttempts; i++) {
            try (Socket socket = new Socket(host, adminPort)) {
                // System.out.println("[TestServiceUtils] Successfully connected to OAuth admin port at " + host + ":" + adminPort);
                return;
            } catch (IOException e) {
                if (i == maxAttempts - 1) {
                    throw new IOException("Failed to connect to OAuth admin port at " + host + ":" + adminPort + " after " + maxAttempts + " attempts");
                }
                // System.out.println("[TestServiceUtils] OAuth admin port not ready, attempt " + (i + 1) + "/" + maxAttempts);
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Interrupted while waiting for OAuth", ie);
                }
            }
        }
    }

    private static class CmdHelper {
        public static int runCommand(String[] command) throws InterruptedException, IOException {
            ProcessBuilder processBuilder = new ProcessBuilder();
            processBuilder.command(command);
            Process process = processBuilder.start();
            process.waitFor();
            return process.exitValue();
        }
    }

    private static class PostgresqlService {
        private static final String PG_SERVICE_NAME = "postgres_" + System.getProperty("org.gradle.test.worker", "base");

        private static final int PG_DB_PORT = new Random().nextInt(5000) + 15000;

        static {
            System.setProperty("ST_POSTGRESQL_PLUGIN_SERVER_PORT", "" + PG_DB_PORT);
        }

        private static int runQuery(String query) throws InterruptedException, IOException {
            // System.out.println("Running query: " + query);
            return CmdHelper.runCommand(new String[] {
                    "docker", "exec", PG_SERVICE_NAME, "psql", "-U", "root", "postgres", "-c", query
            });
        }

        public static void startService() throws IOException, InterruptedException {
            if (!System.getenv().containsKey("ST_PLUGIN_NAME") || System.getenv("ST_PLUGIN_NAME").equals("postgresql")) {
                int exitCode = CmdHelper.runCommand(new String[] {
                        "docker", "run", "--rm", "--name", PG_SERVICE_NAME,
                        "-e", "POSTGRES_USER=root",
                        "-e", "POSTGRES_PASSWORD=root",
                        "-d", "-p", PG_DB_PORT + ":5432",
                        "postgres",
                        "-c", "max_connections=1000"
                });

                if (exitCode != 0) {
                    throw new RuntimeException("Failed to start PostgreSQL service");
                }

                for (int i = 0; i < 1000; i++) {
                    exitCode = CmdHelper.runCommand(new String[] {
                            "docker", "exec", PG_SERVICE_NAME, "pg_isready", "-U", "root"
                    });
                    if (exitCode == 0) {
                        break;
                    }
                    Thread.sleep(200);
                }

                while (runQuery("CREATE DATABASE supertokens;") != 0) {
                    Thread.sleep(200);
                }

                for (int i = 0; i <= 10; i++) {
                    while (runQuery("CREATE DATABASE st" + i + ";") != 0) {
                        Thread.sleep(200);
                    }
                }
            }
        }

        public static void killService() throws IOException, InterruptedException {
            CmdHelper.runCommand(new String[] {
                    "docker", "stop", PG_SERVICE_NAME
            });
        }
    }

    private static class MysqlService {
        private static final String MYSQL_SERVICE_NAME = "mysql_" + System.getProperty("org.gradle.test.worker", "base");
        private static final int MYSQL_DB_PORT = new Random().nextInt(5000) + 20000;

        static {
            System.setProperty("ST_MYSQL_PLUGIN_SERVER_PORT", "" + MYSQL_DB_PORT);
        }

        private static int runQuery(String query) throws InterruptedException, IOException {
            // System.out.println("Running query: " + query);
            return CmdHelper.runCommand(new String[] {
                    "docker", "exec", MYSQL_SERVICE_NAME, "mysql", "-uroot", "-proot", "-e", query
            });
        }

        public static void startService() throws IOException, InterruptedException {
            if (!System.getenv().containsKey("ST_PLUGIN_NAME") || System.getenv("ST_PLUGIN_NAME").equals("mysql")) {
                int exitCode = CmdHelper.runCommand(new String[] {
                        "docker", "run", "--rm", "--name", MYSQL_SERVICE_NAME,
                        "-e", "MYSQL_ROOT_PASSWORD=root",
                        "-d", "-p", MYSQL_DB_PORT + ":3306",
                        "mysql"
                });

                if (exitCode != 0) {
                    throw new RuntimeException("Failed to start MySQL service");
                }

                for (int i = 0; i < 1000; i++) {
                    exitCode = CmdHelper.runCommand(new String[] {
                            "docker", "exec", MYSQL_SERVICE_NAME, "mysqladmin", "ping", "-uroot", "-proot", "-h", "localhost"
                    });
                    if (exitCode == 0) {
                        break;
                    }
                    Thread.sleep(200);
                }

                while (runQuery("CREATE DATABASE supertokens;") != 0) {
                    Thread.sleep(200);
                }

                for (int i = 0; i <= 10; i++) {
                    while (runQuery("CREATE DATABASE st" + i + ";") != 0) {
                        Thread.sleep(200);
                    }
                }
            }
        }

        public static void killService() throws IOException, InterruptedException {
            CmdHelper.runCommand(new String[] {
                    "docker", "stop", MYSQL_SERVICE_NAME
            });
        }
    }

    private static class MongodbService {
        private static final String MONGODB_SERVICE_NAME = "mongodb_" + System.getProperty("org.gradle.test.worker", "base");
        private static final int MONGODB_PORT = new Random().nextInt(5000) + 30000;

        static {
            System.setProperty("ST_MONGODB_PLUGIN_SERVER_PORT", "" + MONGODB_PORT);
        }

        private static int runCommand(String command) throws InterruptedException, IOException {
            // System.out.println("Running command: " + command);
            return CmdHelper.runCommand(new String[] {
                    "docker", "exec", MONGODB_SERVICE_NAME, "mongosh", "--eval", command
            });
        }

        public static void startService() throws IOException, InterruptedException {
            if (!System.getenv().containsKey("ST_PLUGIN_NAME") || System.getenv("ST_PLUGIN_NAME").equals("mongodb")) {
                int exitCode = CmdHelper.runCommand(new String[] {
                        "docker", "run", "--rm", "--name", MONGODB_SERVICE_NAME,
                        "-d", "-p", MONGODB_PORT + ":27017",
                        "-e", "MONGO_INITDB_ROOT_USERNAME=root",
                        "-e", "MONGO_INITDB_ROOT_PASSWORD=root",
                        "mongo:latest"
                });

                if (exitCode != 0) {
                    throw new RuntimeException("Failed to start MongoDB service");
                }

                // Wait for MongoDB to be ready
                for (int i = 0; i < 1000; i++) {
                    exitCode = CmdHelper.runCommand(new String[] {
                            "docker", "exec", MONGODB_SERVICE_NAME, "mongosh", "--eval", "db.version()"
                    });
                    if (exitCode == 0) {
                        break;
                    }
                    Thread.sleep(200);
                }

                // Create databases
                while (runCommand("use supertokens") != 0) {
                    Thread.sleep(200);
                }
            }
        }

        public static void killService() throws IOException, InterruptedException {
            CmdHelper.runCommand(new String[] {
                    "docker", "stop", MONGODB_SERVICE_NAME
            });
        }
    }

    private static class OAuthProviderService {
        private static final int SVC_PORT1 = new Random().nextInt(2500) * 2 + 25000;
        private static final int SVC_PORT2 = SVC_PORT1 + 1;
        private static final String SVC_NAME = "oauth-cicd-" + System.getProperty("org.gradle.test.worker", "base");

        static {
            System.setProperty("ST_OAUTH_PROVIDER_SERVICE_PORT", "" + SVC_PORT1);
            System.setProperty("ST_OAUTH_PROVIDER_ADMIN_PORT", "" + SVC_PORT2);
        }

        public static void startService() throws IOException, InterruptedException {
            // docker run -p 4444:4444 -p 4445:4445 -d --rm --name hydra-cicd rishabhpoddar/oauth-server-cicd
            CmdHelper.runCommand(new String[] {
                "docker", "run", "-p", SVC_PORT1 + ":4444", "-p", SVC_PORT2 + ":4445", "-d", "--rm", "--name", SVC_NAME, "rishabhpoddar/oauth-server-cicd"
            });
        }

        public static void killService() throws InterruptedException, IOException {
            CmdHelper.runCommand(new String[] {
                "docker", "stop", SVC_NAME
            });
        }
    }
}
