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
    private static final String USER_DEV_PRODUCTION_MODE_NAME = "user_dev_production_mode";
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
    public String getUserDevProductionMode() throws StorageQueryException {
        try {
            KeyValueInfo result = Queries.getKeyValue(this, USER_DEV_PRODUCTION_MODE_NAME);
            if (result != null) {
                return result.value;
            }
            return null;
        } catch (SQLException e) {
            throw new StorageQueryException(e);
        }
    }

    @Override
    public void setUserDevProductionMode(String mode) throws StorageQueryException {
        try {
            KeyValueInfo keyInfo = new KeyValueInfo(mode, System.currentTimeMillis());
            Queries.setKeyValue(this, USER_DEV_PRODUCTION_MODE_NAME, keyInfo);
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
