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

import java.sql.*;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.Executor;

public class ConnectionWithLocks implements Connection {

    private Connection con;
    private ConnectionPool connectionPool;
    private Set<String> lockedKeys = new HashSet<String>();

    public synchronized void lock(String key) {
        if (!this.lockedKeys.contains(key)) {
            this.lockedKeys.add(key);
            connectionPool.lock(key);
        }
    }

    private synchronized void unlockAllLocks() {
        for (String key : lockedKeys) {
            connectionPool.unlock(key);
        }
        this.lockedKeys.clear();
    }

    public ConnectionWithLocks(Connection con, ConnectionPool connectionPool) {
        this.con = con;
        this.connectionPool = connectionPool;
    }

    @Override
    public Statement createStatement() throws SQLException {
        return con.createStatement();
    }

    @Override
    public PreparedStatement prepareStatement(String sql) throws SQLException {
        return con.prepareStatement(sql);
    }

    @Override
    public CallableStatement prepareCall(String sql) throws SQLException {
        return con.prepareCall(sql);
    }

    @Override
    public String nativeSQL(String sql) throws SQLException {
        return con.nativeSQL(sql);
    }

    @Override
    public void setAutoCommit(boolean autoCommit) throws SQLException {
        con.setAutoCommit(autoCommit);
    }

    @Override
    public boolean getAutoCommit() throws SQLException {
        return con.getAutoCommit();
    }

    @Override
    public void commit() throws SQLException {
        try {
            con.commit();
        } finally {
            this.unlockAllLocks();
        }
    }

    @Override
    public void rollback() throws SQLException {
        try {
            con.rollback();
        } finally {
            this.unlockAllLocks();
        }
    }

    @Override
    public void close() throws SQLException {
        try {
            con.close();
        } finally {
            this.unlockAllLocks();
        }
    }

    @Override
    public boolean isClosed() throws SQLException {
        return con.isClosed();
    }

    @Override
    public DatabaseMetaData getMetaData() throws SQLException {
        return con.getMetaData();
    }

    @Override
    public void setReadOnly(boolean readOnly) throws SQLException {
        con.setReadOnly(readOnly);
    }

    @Override
    public boolean isReadOnly() throws SQLException {
        return con.isReadOnly();
    }

    @Override
    public void setCatalog(String catalog) throws SQLException {
        con.setCatalog(catalog);
    }

    @Override
    public String getCatalog() throws SQLException {
        return con.getCatalog();
    }

    @Override
    public void setTransactionIsolation(int level) throws SQLException {
        con.setTransactionIsolation(level);
    }

    @Override
    public int getTransactionIsolation() throws SQLException {
        return con.getTransactionIsolation();
    }

    @Override
    public SQLWarning getWarnings() throws SQLException {
        return con.getWarnings();
    }

    @Override
    public void clearWarnings() throws SQLException {
        con.clearWarnings();
    }

    @Override
    public Statement createStatement(int resultSetType, int resultSetConcurrency) throws SQLException {
        return con.createStatement();
    }

    @Override
    public PreparedStatement prepareStatement(String sql, int resultSetType, int resultSetConcurrency)
            throws SQLException {
        return con.prepareStatement(sql, resultSetType, resultSetConcurrency);
    }

    @Override
    public CallableStatement prepareCall(String sql, int resultSetType, int resultSetConcurrency) throws SQLException {
        return con.prepareCall(sql, resultSetType, resultSetConcurrency);
    }

    @Override
    public Map<String, Class<?>> getTypeMap() throws SQLException {
        return con.getTypeMap();
    }

    @Override
    public void setTypeMap(Map<String, Class<?>> map) throws SQLException {
        con.setTypeMap(map);
    }

    @Override
    public void setHoldability(int holdability) throws SQLException {
        con.setHoldability(holdability);
    }

    @Override
    public int getHoldability() throws SQLException {
        return con.getHoldability();
    }

    @Override
    public Savepoint setSavepoint() throws SQLException {
        return con.setSavepoint();
    }

    @Override
    public Savepoint setSavepoint(String name) throws SQLException {
        return con.setSavepoint();
    }

    @Override
    public void rollback(Savepoint savepoint) throws SQLException {
        con.rollback();
    }

    @Override
    public void releaseSavepoint(Savepoint savepoint) throws SQLException {
        con.releaseSavepoint(savepoint);
    }

    @Override
    public Statement createStatement(int resultSetType, int resultSetConcurrency, int resultSetHoldability)
            throws SQLException {
        return con.createStatement(resultSetType, resultSetConcurrency, resultSetHoldability);
    }

    @Override
    public PreparedStatement prepareStatement(String sql, int resultSetType, int resultSetConcurrency,
                                              int resultSetHoldability) throws SQLException {
        return con.prepareStatement(sql, resultSetType, resultSetConcurrency, resultSetHoldability);
    }

    @Override
    public CallableStatement prepareCall(String sql, int resultSetType, int resultSetConcurrency,
                                         int resultSetHoldability) throws SQLException {
        return con.prepareCall(sql, resultSetType, resultSetConcurrency, resultSetHoldability);
    }

    @Override
    public PreparedStatement prepareStatement(String sql, int autoGeneratedKeys) throws SQLException {
        return con.prepareStatement(sql, autoGeneratedKeys);
    }

    @Override
    public PreparedStatement prepareStatement(String sql, int[] columnIndexes) throws SQLException {
        return con.prepareStatement(sql, columnIndexes);
    }

    @Override
    public PreparedStatement prepareStatement(String sql, String[] columnNames) throws SQLException {
        return con.prepareStatement(sql, columnNames);
    }

    @Override
    public Clob createClob() throws SQLException {
        return con.createClob();
    }

    @Override
    public Blob createBlob() throws SQLException {
        return con.createBlob();
    }

    @Override
    public NClob createNClob() throws SQLException {
        return con.createNClob();
    }

    @Override
    public SQLXML createSQLXML() throws SQLException {
        return con.createSQLXML();
    }

    @Override
    public boolean isValid(int timeout) throws SQLException {
        return con.isValid(timeout);
    }

    @Override
    public void setClientInfo(String name, String value) throws SQLClientInfoException {
        con.setClientInfo(name, value);
    }

    @Override
    public void setClientInfo(Properties properties) throws SQLClientInfoException {
        con.setClientInfo(properties);
    }

    @Override
    public String getClientInfo(String name) throws SQLException {
        return con.getClientInfo(name);
    }

    @Override
    public Properties getClientInfo() throws SQLException {
        return con.getClientInfo();
    }

    @Override
    public Array createArrayOf(String typeName, Object[] elements) throws SQLException {
        return con.createArrayOf(typeName, elements);
    }

    @Override
    public Struct createStruct(String typeName, Object[] attributes) throws SQLException {
        return con.createStruct(typeName, attributes);
    }

    @Override
    public void setSchema(String schema) throws SQLException {
        con.setSchema(schema);
    }

    @Override
    public String getSchema() throws SQLException {
        return con.getSchema();
    }

    @Override
    public void abort(Executor executor) throws SQLException {
        con.abort(executor);
    }

    @Override
    public void setNetworkTimeout(Executor executor, int milliseconds) throws SQLException {
        con.setNetworkTimeout(executor, milliseconds);
    }

    @Override
    public int getNetworkTimeout() throws SQLException {
        return con.getNetworkTimeout();
    }

    @Override
    public <T> T unwrap(Class<T> iface) throws SQLException {
        return con.unwrap(iface);
    }

    @Override
    public boolean isWrapperFor(Class<?> iface) throws SQLException {
        return con.isWrapperFor(iface);
    }
}
