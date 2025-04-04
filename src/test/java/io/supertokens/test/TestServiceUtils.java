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
import java.util.Random;

public class TestServiceUtils {
    static {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                killServices();
            } catch (Exception e) {
                System.out.println(e.getMessage());
            }
        }));
    }

    public static void startServices() {
        try {
            OAuthProviderService.startService();
            PostgresqlService.startService();
            MysqlService.startService();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static void killServices() throws InterruptedException, IOException {
        OAuthProviderService.killService();
        PostgresqlService.killService();
        MysqlService.killService();
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
            System.out.println("Running query: " + query);
            return CmdHelper.runCommand(new String[] {
                    "docker", "exec", PG_SERVICE_NAME, "psql", "-U", "root", "postgres", "-c", query
            });
        }

        public static void startService() throws IOException, InterruptedException {
            if (System.getProperty("ST_PLUGIN_NAME", "").isBlank() || System.getProperty("ST_PLUGIN_NAME", "").equals("postgresql")) {
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
            System.out.println("Running query: " + query);
            return CmdHelper.runCommand(new String[] {
                    "docker", "exec", MYSQL_SERVICE_NAME, "mysql", "-uroot", "-proot", "-e", query
            });
        }

        public static void startService() throws IOException, InterruptedException {
            if (System.getProperty("ST_PLUGIN_NAME", "").isBlank() || System.getProperty("ST_PLUGIN_NAME", "").equals("mysql")) {
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
