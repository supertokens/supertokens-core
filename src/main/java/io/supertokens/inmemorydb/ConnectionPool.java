/*
 *    Copyright (c) 2020, VRAI Labs and/or its affiliates. All rights reserved.
 *
 *    This program is licensed under the SuperTokens Community License (the
 *    "License") as published by VRAI Labs. You may not use this file except in
 *    compliance with the License. You are not permitted to transfer or
 *    redistribute this file without express written permission from VRAI Labs.
 *
 *    A copy of the License is available in the file titled
 *    "SuperTokensLicense.pdf" inside this repository or included with your copy of
 *    the software or its source code. If you have not received a copy of the
 *    License, please write to VRAI Labs at team@supertokens.io.
 *
 *    Please read the License carefully before accessing, downloading, copying,
 *    using, modifying, merging, transferring or sharing this software. By
 *    undertaking any of these activities, you indicate your agreement to the terms
 *    of the License.
 *
 *    This program is distributed with certain software that is licensed under
 *    separate terms, as designated in a particular file or component or in
 *    included license documentation. VRAI Labs hereby grants you an additional
 *    permission to link the program and your derivative works with the separately
 *    licensed software that they have included with this program, however if you
 *    modify this program, you shall be solely liable to ensure compliance of the
 *    modified program with the terms of licensing of the separately licensed
 *    software.
 *
 *    Unless required by applicable law or agreed to in writing, this program is
 *    distributed under the License on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 *    CONDITIONS OF ANY KIND, either express or implied. See the License for the
 *    specific language governing permissions and limitations under the License.
 *
 */

package io.supertokens.inmemorydb;

import io.supertokens.ResourceDistributor;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

class ConnectionPool extends ResourceDistributor.SingletonResource {

    private static final String RESOURCE_KEY = "io.supertokens.inmemorydb.ConnectionPool";
    private static String URL = "jdbc:sqlite:file::memory:?cache=shared";

    // we use this to keep all the information in memory across requests.
    private Connection alwaysAlive = null;

    public ConnectionPool() throws SQLException {
        this.alwaysAlive = DriverManager.getConnection(URL);
    }

    static void initPool(Start start) throws SQLException {
        start.getResourceDistributor().setResource(RESOURCE_KEY, new ConnectionPool());
    }

    static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(URL);
    }

    private static ConnectionPool getInstance(Start start) {
        return (ConnectionPool) start.getResourceDistributor().getResource(RESOURCE_KEY);
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

}
