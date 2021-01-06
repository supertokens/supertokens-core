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
import io.supertokens.Main;
import io.supertokens.ProcessState;
import io.supertokens.ResourceDistributor;
import io.supertokens.inmemorydb.config.Config;
import io.supertokens.inmemorydb.queries.EmailPasswordQueries;
import io.supertokens.inmemorydb.queries.GeneralQueries;
import io.supertokens.inmemorydb.queries.SessionQueries;
import io.supertokens.pluginInterface.KeyValueInfo;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.pluginInterface.emailpassword.EmailVerificationTokenInfo;
import io.supertokens.pluginInterface.emailpassword.PasswordResetTokenInfo;
import io.supertokens.pluginInterface.emailpassword.UserInfo;
import io.supertokens.pluginInterface.emailpassword.exceptions.*;
import io.supertokens.pluginInterface.emailpassword.sqlStorage.EmailPasswordSQLStorage;
import io.supertokens.pluginInterface.exceptions.QuitProgramFromPluginException;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.exceptions.StorageTransactionLogicException;
import io.supertokens.pluginInterface.session.SessionInfo;
import io.supertokens.pluginInterface.session.sqlStorage.SessionSQLStorage;
import io.supertokens.pluginInterface.sqlStorage.TransactionConnection;

import javax.annotation.Nullable;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLTransactionRollbackException;

public class Start implements SessionSQLStorage, EmailPasswordSQLStorage {

    private static final Object appenderLock = new Object();
    private static boolean silent = false;
    private ResourceDistributor resourceDistributor = new ResourceDistributor();
    private String processId;
    private static final String APP_ID_KEY_NAME = "app_id";
    private static final String ACCESS_TOKEN_SIGNING_KEY_NAME = "access_token_signing_key";
    private static final String REFRESH_TOKEN_KEY_NAME = "refresh_token_key";
    public static boolean isTesting = false;
    boolean enabled = true;
    private Main main;

    public Start(Main main) {
        this.main = main;
    }

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
        return STORAGE_TYPE.SQL;
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
            GeneralQueries.createTablesIfNotExists(this, this.main);
        } catch (SQLException e) {
            throw new QuitProgramFromPluginException(e);
        }
    }

    @Override
    public <T> T startTransaction(TransactionLogic<T> logic)
            throws StorageTransactionLogicException, StorageQueryException {
        int tries = 0;
        while (true) {
            tries++;
            try {
                return startTransactionHelper(logic);
            } catch (SQLException | StorageQueryException e) {
                if ((e instanceof SQLTransactionRollbackException ||
                        e.getMessage().toLowerCase().contains("deadlock")) &&
                        tries < 3) {
                    ProcessState.getInstance(this.main).addState(ProcessState.PROCESS_STATE.DEADLOCK_FOUND, e);
                    continue;   // this because deadlocks are not necessarily a result of faulty logic. They can happen
                }
                if (e instanceof StorageQueryException) {
                    throw (StorageQueryException) e;
                }
                throw new StorageQueryException(e);
            }
        }
    }

    private <T> T startTransactionHelper(TransactionLogic<T> logic)
            throws StorageQueryException, StorageTransactionLogicException, SQLException {
        Connection con = null;
        try {
            con = ConnectionPool.getConnection(this);
            con.setAutoCommit(false);
            return logic.mainLogicAndCommit(new TransactionConnection(con));
        } catch (Exception e) {
            if (con != null) {
                con.rollback();
            }
            throw e;
        } finally {
            if (con != null) {
                con.setAutoCommit(true);
                con.close();
            }
        }
    }

    @Override
    public void commitTransaction(TransactionConnection con) throws StorageQueryException {
        Connection sqlCon = (Connection) con.getConnection();
        try {
            sqlCon.commit();
        } catch (SQLException e) {
            throw new StorageQueryException(e);
        }

    }

    @Override
    public KeyValueInfo getAccessTokenSigningKey_Transaction(TransactionConnection con) throws StorageQueryException {
        Connection sqlCon = (Connection) con.getConnection();
        try {
            return GeneralQueries.getKeyValue_Transaction(this, sqlCon, ACCESS_TOKEN_SIGNING_KEY_NAME);
        } catch (SQLException e) {
            throw new StorageQueryException(e);
        }
    }

    @Override
    public void setAccessTokenSigningKey_Transaction(TransactionConnection con, KeyValueInfo info)
            throws StorageQueryException {
        Connection sqlCon = (Connection) con.getConnection();
        try {
            GeneralQueries.setKeyValue_Transaction(this, sqlCon, ACCESS_TOKEN_SIGNING_KEY_NAME, info);
        } catch (SQLException e) {
            throw new StorageQueryException(e);
        }
    }

    @Override
    public KeyValueInfo getRefreshTokenSigningKey_Transaction(TransactionConnection con) throws StorageQueryException {
        Connection sqlCon = (Connection) con.getConnection();
        try {
            return GeneralQueries.getKeyValue_Transaction(this, sqlCon, REFRESH_TOKEN_KEY_NAME);
        } catch (SQLException e) {
            throw new StorageQueryException(e);
        }
    }

    @Override
    public void setRefreshTokenSigningKey_Transaction(TransactionConnection con, KeyValueInfo info)
            throws StorageQueryException {
        Connection sqlCon = (Connection) con.getConnection();
        try {
            GeneralQueries.setKeyValue_Transaction(this, sqlCon, REFRESH_TOKEN_KEY_NAME, info);
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
    public void createNewSession(String sessionHandle, String userId, String refreshTokenHash2,
                                 JsonObject userDataInDatabase, long expiry, JsonObject userDataInJWT,
                                 long createdAtTime)
            throws StorageQueryException {
        try {
            SessionQueries.createNewSession(this, sessionHandle, userId, refreshTokenHash2, userDataInDatabase, expiry,
                    userDataInJWT, createdAtTime);
        } catch (SQLException e) {
            throw new StorageQueryException(e);
        }
    }

    @Override
    public int getNumberOfSessions() throws StorageQueryException {
        try {
            return SessionQueries.getNumberOfSessions(this);
        } catch (SQLException e) {
            throw new StorageQueryException(e);
        }
    }

    @Override
    public int deleteSession(String[] sessionHandles) throws StorageQueryException {
        try {
            return SessionQueries.deleteSession(this, sessionHandles);
        } catch (SQLException e) {
            throw new StorageQueryException(e);
        }
    }

    @Override
    public String[] getAllSessionHandlesForUser(String userId) throws StorageQueryException {
        try {
            return SessionQueries.getAllSessionHandlesForUser(this, userId);
        } catch (SQLException e) {
            throw new StorageQueryException(e);
        }
    }

    @Override
    public void deleteAllExpiredSessions() throws StorageQueryException {
        try {
            SessionQueries.deleteAllExpiredSessions(this);
        } catch (SQLException e) {
            throw new StorageQueryException(e);
        }
    }

    @Override
    public KeyValueInfo getKeyValue(String key) throws StorageQueryException {
        try {
            return GeneralQueries.getKeyValue(this, key);
        } catch (SQLException e) {
            throw new StorageQueryException(e);
        }
    }

    @Override
    public void setKeyValue(String key, KeyValueInfo info) throws StorageQueryException {
        try {
            GeneralQueries.setKeyValue(this, key, info);
        } catch (SQLException e) {
            throw new StorageQueryException(e);
        }
    }

    @Override
    public void setStorageLayerEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public SessionInfo getSession(String sessionHandle) throws StorageQueryException {
        try {
            return SessionQueries.getSession(this, sessionHandle);
        } catch (SQLException e) {
            throw new StorageQueryException(e);
        }
    }

    @Override
    public int updateSession(String sessionHandle, @Nullable JsonObject sessionData, @Nullable JsonObject jwtPayload)
            throws StorageQueryException {
        try {
            return SessionQueries.updateSession(this, sessionHandle, sessionData, jwtPayload);
        } catch (SQLException e) {
            throw new StorageQueryException(e);
        }
    }

    @Override
    public boolean canBeUsed(String configFilePath) {
        return true;
    }

    @Override
    public SessionInfo getSessionInfo_Transaction(TransactionConnection con, String sessionHandle)
            throws StorageQueryException {
        Connection sqlCon = (Connection) con.getConnection();
        try {
            return SessionQueries.getSessionInfo_Transaction(this, sqlCon, sessionHandle);
        } catch (SQLException e) {
            throw new StorageQueryException(e);
        }
    }

    @Override
    public void updateSessionInfo_Transaction(TransactionConnection con, String sessionHandle,
                                              String refreshTokenHash2, long expiry) throws StorageQueryException {
        Connection sqlCon = (Connection) con.getConnection();
        try {
            SessionQueries.updateSessionInfo_Transaction(this, sqlCon, sessionHandle, refreshTokenHash2, expiry);
        } catch (SQLException e) {
            throw new StorageQueryException(e);
        }
    }

    @Override
    public void setKeyValue_Transaction(TransactionConnection con, String key, KeyValueInfo info)
            throws StorageQueryException {
        Connection sqlCon = (Connection) con.getConnection();
        try {
            GeneralQueries.setKeyValue_Transaction(this, sqlCon, key, info);
        } catch (SQLException e) {
            throw new StorageQueryException(e);
        }
    }

    @Override
    public KeyValueInfo getKeyValue_Transaction(TransactionConnection con, String key) throws StorageQueryException {
        Connection sqlCon = (Connection) con.getConnection();
        try {
            return GeneralQueries.getKeyValue_Transaction(this, sqlCon, key);
        } catch (SQLException e) {
            throw new StorageQueryException(e);
        }
    }

    @Override
    public void signUp(UserInfo userInfo)
            throws StorageQueryException, DuplicateUserIdException, DuplicateEmailException {
        try {
            EmailPasswordQueries.signUp(this, userInfo.id, userInfo.email, userInfo.passwordHash, userInfo.timeJoined,
                    userInfo.isEmailVerified);
        } catch (SQLException e) {
            if (e.getMessage()
                    .equals("[SQLITE_CONSTRAINT]  Abort due to constraint violation (UNIQUE constraint failed: " +
                            Config.getConfig(this).getUsersTable() + ".email)"
                    )) {
                throw new DuplicateEmailException();
            } else if (e.getMessage()
                    .equals("[SQLITE_CONSTRAINT]  Abort due to constraint violation (UNIQUE constraint failed: " +
                            Config.getConfig(this).getUsersTable() + ".user_id)"
                    )) {
                throw new DuplicateUserIdException();
            }
            throw new StorageQueryException(e);
        }
    }

    @Override
    public UserInfo getUserInfoUsingId(String id) throws StorageQueryException {
        try {
            return EmailPasswordQueries.getUserInfoUsingId(this, id);
        } catch (SQLException e) {
            throw new StorageQueryException(e);
        }
    }

    @Override
    public UserInfo getUserInfoUsingEmail(String email) throws StorageQueryException {
        try {
            return EmailPasswordQueries.getUserInfoUsingEmail(this, email);
        } catch (SQLException e) {
            throw new StorageQueryException(e);
        }
    }

    @Override
    public void addPasswordResetToken(PasswordResetTokenInfo passwordResetTokenInfo)
            throws StorageQueryException, UnknownUserIdException, DuplicatePasswordResetTokenException {
        try {
            // SQLite is not compiled with foreign key constraint and so we must check for the userId manually
            if (this.getUserInfoUsingId(passwordResetTokenInfo.userId) == null) {
                throw new UnknownUserIdException();
            }

            EmailPasswordQueries.addPasswordResetToken(this, passwordResetTokenInfo.userId,
                    passwordResetTokenInfo.token, passwordResetTokenInfo.tokenExpiry);
        } catch (SQLException e) {
            if (e.getMessage()
                    .equals("[SQLITE_CONSTRAINT]  Abort due to constraint violation (UNIQUE constraint failed: " +
                            Config.getConfig(this).getPasswordResetTokensTable() +
                            ".user_id, " + Config.getConfig(this).getPasswordResetTokensTable() + ".token)")) {
                throw new DuplicatePasswordResetTokenException();
            }
            throw new StorageQueryException(e);
        }
    }

    @Override
    public PasswordResetTokenInfo getPasswordResetTokenInfo(String token) throws StorageQueryException {
        try {
            return EmailPasswordQueries.getPasswordResetTokenInfo(this, token);
        } catch (SQLException e) {
            throw new StorageQueryException(e);
        }
    }

    @Override
    public PasswordResetTokenInfo[] getAllPasswordResetTokenInfoForUser(String userId) throws StorageQueryException {
        try {
            return EmailPasswordQueries.getAllPasswordResetTokenInfoForUser(this, userId);
        } catch (SQLException e) {
            throw new StorageQueryException(e);
        }
    }

    @Override
    public void addEmailVerificationToken(EmailVerificationTokenInfo emailVerificationInfo)
            throws StorageQueryException, UnknownUserIdException, DuplicateEmailVerificationTokenException {
        try {
            // SQLite is not compiled with foreign key constraint and so we must check for the userId manually
            if (this.getUserInfoUsingId(emailVerificationInfo.userId) == null) {
                throw new UnknownUserIdException();
            }

            EmailPasswordQueries.addEmailVerificationToken(this, emailVerificationInfo.userId,
                    emailVerificationInfo.token, emailVerificationInfo.tokenExpiry, emailVerificationInfo.email);
        } catch (SQLException e) {
            if (e.getMessage()
                    .equals("[SQLITE_CONSTRAINT]  Abort due to constraint violation (UNIQUE constraint failed: " +
                            Config.getConfig(this).getEmailVerificationTokensTable() +
                            ".user_id, " + Config.getConfig(this).getEmailVerificationTokensTable() + ".token)")) {
                throw new DuplicateEmailVerificationTokenException();
            }
            throw new StorageQueryException(e);
        }
    }

    @Override
    public PasswordResetTokenInfo[] getAllPasswordResetTokenInfoForUser_Transaction(TransactionConnection con,
                                                                                    String userId)
            throws StorageQueryException {
        Connection sqlCon = (Connection) con.getConnection();
        try {
            return EmailPasswordQueries.getAllPasswordResetTokenInfoForUser_Transaction(this, sqlCon, userId);
        } catch (SQLException e) {
            throw new StorageQueryException(e);
        }
    }

    @Override
    public void deleteAllPasswordResetTokensForUser_Transaction(TransactionConnection con, String userId)
            throws StorageQueryException {
        Connection sqlCon = (Connection) con.getConnection();
        try {
            EmailPasswordQueries.deleteAllPasswordResetTokensForUser_Transaction(this, sqlCon, userId);
        } catch (SQLException e) {
            throw new StorageQueryException(e);
        }
    }

    @Override
    public void updateUsersPassword_Transaction(TransactionConnection con, String userId, String newPassword)
            throws StorageQueryException {
        Connection sqlCon = (Connection) con.getConnection();
        try {
            EmailPasswordQueries.updateUsersPassword_Transaction(this, sqlCon, userId, newPassword);
        } catch (SQLException e) {
            throw new StorageQueryException(e);
        }
    }

    @Override
    public EmailVerificationTokenInfo[] getAllEmailVerificationTokenInfoForUser_Transaction(TransactionConnection con,
                                                                                            String userId)
            throws StorageQueryException {
        Connection sqlCon = (Connection) con.getConnection();
        try {
            return EmailPasswordQueries.getAllEmailVerificationTokenInfoForUser_Transaction(this, sqlCon, userId);
        } catch (SQLException e) {
            throw new StorageQueryException(e);
        }
    }

    @Override
    public void deleteAllEmailVerificationTokensForUser_Transaction(TransactionConnection con, String userId)
            throws StorageQueryException {
        Connection sqlCon = (Connection) con.getConnection();
        try {
            EmailPasswordQueries.deleteAllEmailVerificationTokensForUser_Transaction(this, sqlCon, userId);
        } catch (SQLException e) {
            throw new StorageQueryException(e);
        }
    }

    @Override
    public void updateUsersIsEmailVerified_Transaction(TransactionConnection con, String userId,
                                                       boolean isEmailVerified) throws StorageQueryException {
        Connection sqlCon = (Connection) con.getConnection();
        try {
            EmailPasswordQueries.updateUsersIsEmailVerified_Transaction(this, sqlCon, userId, isEmailVerified);
        } catch (SQLException e) {
            throw new StorageQueryException(e);
        }
    }

    @Override
    public UserInfo getUserInfoUsingId_Transaction(TransactionConnection con, String userId)
            throws StorageQueryException {
        Connection sqlCon = (Connection) con.getConnection();
        try {
            return EmailPasswordQueries.getUserInfoUsingId_Transaction(this, sqlCon, userId);
        } catch (SQLException e) {
            throw new StorageQueryException(e);
        }
    }

    @Override
    public EmailVerificationTokenInfo getEmailVerificationTokenInfo(String token) throws StorageQueryException {
        try {
            return EmailPasswordQueries.getEmailVerificationTokenInfo(this, token);
        } catch (SQLException e) {
            throw new StorageQueryException(e);
        }
    }

    @Override
    public void deleteExpiredEmailVerificationTokens() throws StorageQueryException {
        try {
            EmailPasswordQueries.deleteExpiredEmailVerificationTokens(this);
        } catch (SQLException e) {
            throw new StorageQueryException(e);
        }
    }

    @Override
    public EmailVerificationTokenInfo[] getAllEmailVerificationTokenInfoForUser(String userId)
            throws StorageQueryException {
        try {
            return EmailPasswordQueries.getAllEmailVerificationTokenInfoForUser(this, userId);
        } catch (SQLException e) {
            throw new StorageQueryException(e);
        }
    }

    @Override
    public void deleteExpiredPasswordResetTokens() throws StorageQueryException {
        try {
            EmailPasswordQueries.deleteExpiredPasswordResetTokens(this);
        } catch (SQLException e) {
            throw new StorageQueryException(e);
        }
    }
}
