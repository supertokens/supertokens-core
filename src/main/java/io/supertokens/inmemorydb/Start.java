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

import com.google.gson.JsonObject;
import io.supertokens.ResourceDistributor;
import io.supertokens.inmemorydb.config.Config;
import io.supertokens.pluginInterface.KeyValueInfo;
import io.supertokens.pluginInterface.KeyValueInfoWithLastUpdated;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.pluginInterface.exceptions.QuitProgramFromPluginException;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.noSqlStorage.NoSQLStorage_1;
import io.supertokens.pluginInterface.sqlStorage.SQLStorage;
import io.supertokens.pluginInterface.tokenInfo.PastTokenInfo;

import java.sql.SQLException;

public class Start extends NoSQLStorage_1 {

    private static final Object appenderLock = new Object();
    private static boolean silent = false;
    private ResourceDistributor resourceDistributor = new ResourceDistributor();
    private String processId;
    private static final String APP_ID_KEY_NAME = "app_id";
    private static final String ACCESS_TOKEN_SIGNING_KEY_NAME = "access_token_signing_key";
    private static final String REFRESH_TOKEN_KEY_NAME = "refresh_token_key";
    public static boolean isTesting = false;
    private boolean enabled = true;

    public ResourceDistributor getResourceDistributor() {
        return resourceDistributor;
    }

    public String getProcessId() {
        return this.processId;
    }

    @Override
    public void constructor(String processId, boolean silent) {
        this.processId = processId;
        Start.silent = silent;
    }

    @Override
    public STORAGE_TYPE getType() {
        return STORAGE_TYPE.NOSQL_1;
    }

    @Override
    public void loadConfig(String ignored) {
        Config.loadConfig(this);
    }

    @Override
    public void initFileLogging(String infoLogPath, String errorLogPath) {
        // no op
    }

    @Override
    public void stopLogging() {
        // no op
    }

    @Override
    public void initStorage() {
        try {
            ConnectionPool.initPool(this);
            Queries.createTablesIfNotExists(this);
        } catch (SQLException e) {
            throw new QuitProgramFromPluginException(e);
        }
    }

    @Override
    public String getAppId() throws StorageQueryException {
        try {
            KeyValueInfo result = Queries.getKeyValue(this, APP_ID_KEY_NAME);
            if (result != null) {
                return result.value;
            }
            return null;
        } catch (SQLException e) {
            throw new StorageQueryException(e);
        }
    }

    @Override
    public void setAppId(String appId) throws StorageQueryException {
        try {
            KeyValueInfo keyInfo = new KeyValueInfo(appId, System.currentTimeMillis());
            Queries.setKeyValue(this, APP_ID_KEY_NAME, keyInfo);
        } catch (SQLException e) {
            throw new StorageQueryException(e);
        }
    }


    @Override
    public KeyValueInfoWithLastUpdated getAccessTokenSigningKey_Transaction() throws StorageQueryException {
        try {
            return Queries.getKeyValue_Transaction(this, ACCESS_TOKEN_SIGNING_KEY_NAME);
        } catch (SQLException e) {
            throw new StorageQueryException(e);
        }
    }

    @Override
    public boolean setAccessTokenSigningKey_Transaction(KeyValueInfoWithLastUpdated info) throws StorageQueryException {
        try {
            return Queries.setKeyValue_Transaction(this, ACCESS_TOKEN_SIGNING_KEY_NAME, info);
        } catch (SQLException e) {
            throw new StorageQueryException(e);
        }
    }

    @Override
    public KeyValueInfoWithLastUpdated getRefreshTokenSigningKey_Transaction() throws StorageQueryException {
        try {
            return Queries.getKeyValue_Transaction(this, REFRESH_TOKEN_KEY_NAME);
        } catch (SQLException e) {
            throw new StorageQueryException(e);
        }
    }

    @Override
    public boolean setRefreshTokenSigningKey_Transaction(KeyValueInfoWithLastUpdated info)
            throws StorageQueryException {
        try {
            return Queries.setKeyValue_Transaction(this, REFRESH_TOKEN_KEY_NAME, info);
        } catch (SQLException e) {
            throw new StorageQueryException(e);
        }
    }


    @Override
    public void deleteAllInformation() {
        /*no-op*/
    }

    @Override
    public void close() {
        ConnectionPool.close(this);
    }

    @Override
    public PastTokenInfo getPastTokenInfo(String refreshTokenHash2) throws StorageQueryException {
        try {
            return Queries.getPastTokenInfo(this, refreshTokenHash2);
        } catch (SQLException e) {
            throw new StorageQueryException(e);
        }
    }

    @Override
    public void insertPastToken(PastTokenInfo info) throws StorageQueryException {
        try {
            Queries.insertPastTokenInfo(this, info);
        } catch (SQLException e) {
            throw new StorageQueryException(e);
        }
    }

    @Override
    public int getNumberOfPastTokens() throws StorageQueryException {
        try {
            return Queries.getNumberOfPastTokens(this);
        } catch (SQLException e) {
            throw new StorageQueryException(e);
        }
    }

    @Override
    public void createNewSession(String sessionHandle, String userId, String refreshTokenHash2,
                                 JsonObject userDataInDatabase, long expiry, JsonObject userDataInJWT,
                                 long createdAtTime)
            throws StorageQueryException {
        try {
            Queries.createNewSession(this, sessionHandle, userId, refreshTokenHash2, userDataInDatabase, expiry,
                    userDataInJWT, createdAtTime);
        } catch (SQLException e) {
            throw new StorageQueryException(e);
        }
    }

    @Override
    public int getNumberOfSessions() throws StorageQueryException {
        try {
            return Queries.getNumberOfSessions(this);
        } catch (SQLException e) {
            throw new StorageQueryException(e);
        }
    }

    @Override
    public int deleteSession(String[] sessionHandles) throws StorageQueryException {
        try {
            return Queries.deleteSession(this, sessionHandles);
        } catch (SQLException e) {
            throw new StorageQueryException(e);
        }
    }

    @Override
    public String[] getAllSessionHandlesForUser(String userId) throws StorageQueryException {
        try {
            return Queries.getAllSessionHandlesForUser(this, userId);
        } catch (SQLException e) {
            throw new StorageQueryException(e);
        }
    }


    @Override
    public void deleteAllExpiredSessions() throws StorageQueryException {
        try {
            Queries.deleteAllExpiredSessions(this);
        } catch (SQLException e) {
            throw new StorageQueryException(e);
        }
    }

    @Override
    public void deletePastOrphanedTokens(long createdBefore) throws StorageQueryException {
        try {
            Queries.deletePastOrphanedTokens(this, createdBefore);
        } catch (SQLException e) {
            throw new StorageQueryException(e);
        }
    }

    @Override
    public KeyValueInfo getKeyValue(String key) throws StorageQueryException {
        try {
            return Queries.getKeyValue(this, key);
        } catch (SQLException e) {
            throw new StorageQueryException(e);
        }
    }

    @Override
    public void setKeyValue(String key, KeyValueInfo info) throws StorageQueryException {
        try {
            Queries.setKeyValue(this, key, info);
        } catch (SQLException e) {
            throw new StorageQueryException(e);
        }
    }

    @Override
    public void setStorageLayerEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public SQLStorage.SessionInfo getSession(String sessionHandle) throws StorageQueryException {
        try {
            return Queries.getSession(this, sessionHandle);
        } catch (SQLException e) {
            throw new StorageQueryException(e);
        }
    }

    @Override
    public int updateSession(String sessionHandle, JsonObject sessionData, JsonObject jwtPayload)
            throws StorageQueryException {
        try {
            return Queries.updateSession(this, sessionHandle, sessionData, jwtPayload);
        } catch (SQLException e) {
            throw new StorageQueryException(e);
        }

    }

    @Override
    public boolean canBeUsed(String configFilePath) {
        return true;
    }

    @Override
    public SessionInfoWithLastUpdated getSessionInfo_Transaction(String sessionHandle) throws StorageQueryException {
        try {
            return Queries.getSessionInfo_Transaction(this, sessionHandle);
        } catch (SQLException e) {
            throw new StorageQueryException(e);
        }
    }


    @Override
    public boolean updateSessionInfo_Transaction(String sessionHandle, String refreshTokenHash2, long expiry,
                                                 String lastUpdatedSign) throws StorageQueryException {
        try {
            return Queries.updateSessionInfo_Transaction(this, sessionHandle, refreshTokenHash2, expiry,
                    lastUpdatedSign);
        } catch (SQLException e) {
            throw new StorageQueryException(e);
        }
    }

    @Override
    public boolean setKeyValue_Transaction(String key, KeyValueInfoWithLastUpdated info) throws StorageQueryException {
        try {
            return Queries.setKeyValue_Transaction(this, key, info);
        } catch (SQLException e) {
            throw new StorageQueryException(e);
        }
    }

    @Override
    public KeyValueInfoWithLastUpdated getKeyValue_Transaction(String key) throws StorageQueryException {
        try {
            return Queries.getKeyValue_Transaction(this, key);
        } catch (SQLException e) {
            throw new StorageQueryException(e);
        }
    }

}
