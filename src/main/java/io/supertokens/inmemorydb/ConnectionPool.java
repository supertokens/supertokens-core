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
 */

package io.supertokens.inmemorydb;

import org.sqlite.SQLiteConfig;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class ConnectionPool extends ResourceDistributor.SingletonResource {

    private static final String RESOURCE_KEY = "io.supertokens.inmemorydb.ConnectionPool";
    private static String URL = "jdbc:sqlite:file::memory:?cache=shared";

    // we use this to keep all the information in memory across requests.
    private Connection alwaysAlive = null;
    private Lock lock = new Lock();

    public ConnectionPool() throws SQLException {
        SQLiteConfig config = new SQLiteConfig();
        config.enforceForeignKeys(true);
        this.alwaysAlive = DriverManager.getConnection(URL, config.toProperties());
    }

    static boolean isAlreadyInitialised(Start start) {
        return getInstance(start) != null;
    }

    static void initPool(Start start, boolean ignored) throws SQLException {
        start.getResourceDistributor()
                .setResource(RESOURCE_KEY, new ConnectionPool());
    }

    public static Connection getConnection(Start start) throws SQLException {
        if (!start.enabled) {
            throw new SQLException("Storage layer disabled");
        }
        SQLiteConfig config = new SQLiteConfig();
        config.enforceForeignKeys(true);
        return new ConnectionWithLocks(DriverManager.getConnection(URL, config.toProperties()),
                ConnectionPool.getInstance(start));
    }

    private static ConnectionPool getInstance(Start start) {
        return (ConnectionPool) start.getResourceDistributor()
                .getResource(RESOURCE_KEY);
    }

    static void close(Start start) {
        if (getInstance(start) == null) {
            return;
        }
        try {
            getInstance(start).alwaysAlive.close();
        } catch (Exception ignored) {
        }
    }

    public void lock(String key) {
        this.lock.lock(key);
    }

    public void unlock(String key) {
        this.lock.unlock(key);
    }

}
