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
import io.supertokens.inmemorydb.queries.*;
import io.supertokens.pluginInterface.KeyValueInfo;
import io.supertokens.pluginInterface.RECIPE_ID;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.pluginInterface.authRecipe.AuthRecipeUserInfo;
import io.supertokens.pluginInterface.emailpassword.PasswordResetTokenInfo;
import io.supertokens.pluginInterface.emailpassword.UserInfo;
import io.supertokens.pluginInterface.emailpassword.exceptions.DuplicateEmailException;
import io.supertokens.pluginInterface.emailpassword.exceptions.DuplicatePasswordResetTokenException;
import io.supertokens.pluginInterface.emailpassword.exceptions.DuplicateUserIdException;
import io.supertokens.pluginInterface.emailpassword.exceptions.UnknownUserIdException;
import io.supertokens.pluginInterface.emailpassword.sqlStorage.EmailPasswordSQLStorage;
import io.supertokens.pluginInterface.emailverification.EmailVerificationTokenInfo;
import io.supertokens.pluginInterface.emailverification.exception.DuplicateEmailVerificationTokenException;
import io.supertokens.pluginInterface.emailverification.sqlStorage.EmailVerificationSQLStorage;
import io.supertokens.pluginInterface.exceptions.QuitProgramFromPluginException;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.exceptions.StorageTransactionLogicException;
import io.supertokens.pluginInterface.jwt.JWTSigningKeyInfo;
import io.supertokens.pluginInterface.jwt.exceptions.DuplicateKeyIdException;
import io.supertokens.pluginInterface.jwt.sqlstorage.JWTRecipeSQLStorage;
import io.supertokens.pluginInterface.passwordless.PasswordlessCode;
import io.supertokens.pluginInterface.passwordless.PasswordlessDevice;
import io.supertokens.pluginInterface.passwordless.exception.DuplicateCodeIdException;
import io.supertokens.pluginInterface.passwordless.exception.DuplicateDeviceIdHashException;
import io.supertokens.pluginInterface.passwordless.exception.DuplicateLinkCodeHashException;
import io.supertokens.pluginInterface.passwordless.exception.DuplicatePhoneNumberException;
import io.supertokens.pluginInterface.passwordless.exception.UnknownDeviceIdHash;
import io.supertokens.pluginInterface.passwordless.sqlStorage.PasswordlessSQLStorage;
import io.supertokens.pluginInterface.session.SessionInfo;
import io.supertokens.pluginInterface.session.sqlStorage.SessionSQLStorage;
import io.supertokens.pluginInterface.sqlStorage.TransactionConnection;
import io.supertokens.pluginInterface.thirdparty.exception.DuplicateThirdPartyUserException;
import io.supertokens.pluginInterface.thirdparty.sqlStorage.ThirdPartySQLStorage;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import javax.annotation.Nonnull;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLTransactionRollbackException;
import java.util.List;

public class Start implements SessionSQLStorage, EmailPasswordSQLStorage, EmailVerificationSQLStorage,
        ThirdPartySQLStorage, JWTRecipeSQLStorage, PasswordlessSQLStorage {

    private static final Object appenderLock = new Object();
    private static final String APP_ID_KEY_NAME = "app_id";
    private static final String ACCESS_TOKEN_SIGNING_KEY_NAME = "access_token_signing_key";
    private static final String REFRESH_TOKEN_KEY_NAME = "refresh_token_key";
    public static boolean isTesting = false;
    private static boolean silent = false;
    boolean enabled = true;
    private ResourceDistributor resourceDistributor = new ResourceDistributor();
    private String processId;
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
                if ((e instanceof SQLTransactionRollbackException || e.getMessage().toLowerCase().contains("deadlock"))
                        && tries < 3) {
                    ProcessState.getInstance(this.main).addState(ProcessState.PROCESS_STATE.DEADLOCK_FOUND, e);
                    continue; // this because deadlocks are not necessarily a result of faulty logic. They can
                    // happen
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
    public KeyValueInfo getLegacyAccessTokenSigningKey_Transaction(TransactionConnection con)
            throws StorageQueryException {
        Connection sqlCon = (Connection) con.getConnection();
        try {
            return GeneralQueries.getKeyValue_Transaction(this, sqlCon, ACCESS_TOKEN_SIGNING_KEY_NAME);
        } catch (SQLException e) {
            throw new StorageQueryException(e);
        }
    }

    @Override
    public void removeLegacyAccessTokenSigningKey_Transaction(TransactionConnection con) throws StorageQueryException {
        Connection sqlCon = (Connection) con.getConnection();
        try {
            GeneralQueries.deleteKeyValue_Transaction(this, sqlCon, ACCESS_TOKEN_SIGNING_KEY_NAME);
        } catch (SQLException e) {
            throw new StorageQueryException(e);
        }
    }

    @Override
    public KeyValueInfo[] getAccessTokenSigningKeys_Transaction(TransactionConnection con)
            throws StorageQueryException {
        Connection sqlCon = (Connection) con.getConnection();
        try {
            return SessionQueries.getAccessTokenSigningKeys_Transaction(this, sqlCon);
        } catch (SQLException e) {
            throw new StorageQueryException(e);
        }
    }

    @Override
    public void addAccessTokenSigningKey_Transaction(TransactionConnection con, KeyValueInfo info)
            throws StorageQueryException {
        Connection sqlCon = (Connection) con.getConnection();
        try {
            SessionQueries.addAccessTokenSigningKey_Transaction(this, sqlCon, info.createdAtTime, info.value);
        } catch (SQLException e) {
            throw new StorageQueryException(e);
        }
    }

    @Override
    public void removeAccessTokenSigningKeysBefore(long time) throws StorageQueryException {
        try {
            SessionQueries.removeAccessTokenSigningKeysBefore(this, time);
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
        /* no-op */
    }

    @Override
    public void close() {
        ConnectionPool.close(this);
    }

    @Override
    public void createNewSession(String sessionHandle, String userId, String refreshTokenHash2,
            JsonObject userDataInDatabase, long expiry, JsonObject userDataInJWT, long createdAtTime)
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
    public long getUsersCount(RECIPE_ID[] includeRecipeIds) throws StorageQueryException {
        try {
            return GeneralQueries.getUsersCount(this, includeRecipeIds);
        } catch (SQLException e) {
            throw new StorageQueryException(e);
        }
    }

    @Override
    public AuthRecipeUserInfo[] getUsers(@NotNull Integer limit, @NotNull String timeJoinedOrder,
            @Nullable RECIPE_ID[] includeRecipeIds, @Nullable String userId, @Nullable Long timeJoined)
            throws StorageQueryException {
        try {
            return GeneralQueries.getUsers(this, limit, timeJoinedOrder, includeRecipeIds, userId, timeJoined);
        } catch (SQLException e) {
            throw new StorageQueryException(e);
        }
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
    public void updateSessionInfo_Transaction(TransactionConnection con, String sessionHandle, String refreshTokenHash2,
            long expiry) throws StorageQueryException {
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
            EmailPasswordQueries.signUp(this, userInfo.id, userInfo.email, userInfo.passwordHash, userInfo.timeJoined);
        } catch (StorageTransactionLogicException eTemp) {
            Exception e = eTemp.actualException;
            if (e.getMessage()
                    .equals("[SQLITE_CONSTRAINT]  Abort due to constraint violation (UNIQUE constraint failed: "
                            + Config.getConfig(this).getEmailPasswordUsersTable() + ".email)")) {
                throw new DuplicateEmailException();
            } else if (e.getMessage()
                    .equals("[SQLITE_CONSTRAINT]  Abort due to constraint violation (UNIQUE constraint failed: "
                            + Config.getConfig(this).getEmailPasswordUsersTable() + ".user_id)")
                    || e.getMessage()
                            .equals("[SQLITE_CONSTRAINT]  Abort due to constraint violation (UNIQUE constraint failed: "
                                    + Config.getConfig(this).getUsersTable() + ".user_id)")) {
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
            // SQLite is not compiled with foreign key constraint and so we must check for
            // the userId manually
            if (this.getUserInfoUsingId(passwordResetTokenInfo.userId) == null) {
                throw new UnknownUserIdException();
            }

            EmailPasswordQueries.addPasswordResetToken(this, passwordResetTokenInfo.userId,
                    passwordResetTokenInfo.token, passwordResetTokenInfo.tokenExpiry);
        } catch (SQLException e) {
            if (e.getMessage()
                    .equals("[SQLITE_CONSTRAINT]  Abort due to constraint violation (UNIQUE constraint failed: "
                            + Config.getConfig(this).getPasswordResetTokensTable() + ".user_id, "
                            + Config.getConfig(this).getPasswordResetTokensTable() + ".token)")) {
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
    public PasswordResetTokenInfo[] getAllPasswordResetTokenInfoForUser_Transaction(TransactionConnection con,
            String userId) throws StorageQueryException {
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
    public void updateUsersEmail_Transaction(TransactionConnection conn, String userId, String email)
            throws StorageQueryException, DuplicateEmailException {
        Connection sqlConn = (Connection) conn.getConnection();

        try {
            EmailPasswordQueries.updateUsersEmail_Transaction(this, sqlConn, userId, email);
        } catch (SQLException e) {
            if (e.getMessage()
                    .equals("[SQLITE_CONSTRAINT]  Abort due to constraint violation (UNIQUE constraint failed: "
                            + Config.getConfig(this).getEmailPasswordUsersTable() + ".email)")) {
                throw new DuplicateEmailException();
            }
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
    @Deprecated
    public UserInfo[] getUsers(@NotNull String userId, @NotNull Long timeJoined, @NotNull Integer limit,
            @NotNull String timeJoinedOrder) throws StorageQueryException {
        try {
            return EmailPasswordQueries.getUsersInfo(this, userId, timeJoined, limit, timeJoinedOrder);
        } catch (SQLException e) {
            throw new StorageQueryException(e);
        }
    }

    @Override
    @Deprecated
    public UserInfo[] getUsers(@NotNull Integer limit, @NotNull String timeJoinedOrder) throws StorageQueryException {
        try {
            return EmailPasswordQueries.getUsersInfo(this, limit, timeJoinedOrder);
        } catch (SQLException e) {
            throw new StorageQueryException(e);
        }
    }

    @Override
    @Deprecated
    public long getUsersCount() throws StorageQueryException {
        try {
            return EmailPasswordQueries.getUsersCount(this);
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

    @Override
    public EmailVerificationTokenInfo[] getAllEmailVerificationTokenInfoForUser_Transaction(TransactionConnection con,
            String userId, String email) throws StorageQueryException {
        Connection sqlCon = (Connection) con.getConnection();
        try {
            return EmailVerificationQueries.getAllEmailVerificationTokenInfoForUser_Transaction(this, sqlCon, userId,
                    email);
        } catch (SQLException e) {
            throw new StorageQueryException(e);
        }
    }

    @Override
    public void addEmailVerificationToken(EmailVerificationTokenInfo emailVerificationInfo)
            throws StorageQueryException, DuplicateEmailVerificationTokenException {
        try {
            EmailVerificationQueries.addEmailVerificationToken(this, emailVerificationInfo.userId,
                    emailVerificationInfo.token, emailVerificationInfo.tokenExpiry, emailVerificationInfo.email);
        } catch (SQLException e) {
            if (e.getMessage()
                    .equals("[SQLITE_CONSTRAINT]  Abort due to constraint violation (UNIQUE constraint failed: "
                            + Config.getConfig(this).getEmailVerificationTokensTable() + ".user_id, "
                            + Config.getConfig(this).getEmailVerificationTokensTable() + ".email, "
                            + Config.getConfig(this).getEmailVerificationTokensTable() + ".token)")) {
                throw new DuplicateEmailVerificationTokenException();
            }
            throw new StorageQueryException(e);
        }
    }

    @Override
    public void deleteAllEmailVerificationTokensForUser_Transaction(TransactionConnection con, String userId,
            String email) throws StorageQueryException {
        Connection sqlCon = (Connection) con.getConnection();
        try {
            EmailVerificationQueries.deleteAllEmailVerificationTokensForUser_Transaction(this, sqlCon, userId, email);
        } catch (SQLException e) {
            throw new StorageQueryException(e);
        }
    }

    @Override
    public void updateIsEmailVerified_Transaction(TransactionConnection con, String userId, String email,
            boolean isEmailVerified) throws StorageQueryException {
        Connection sqlCon = (Connection) con.getConnection();
        try {
            EmailVerificationQueries.updateUsersIsEmailVerified_Transaction(this, sqlCon, userId, email,
                    isEmailVerified);
        } catch (SQLException e) {
            if (!isEmailVerified || !e.getMessage()
                    .equals("[SQLITE_CONSTRAINT]  Abort due to constraint violation (UNIQUE constraint failed: "
                            + Config.getConfig(this).getEmailVerificationTable() + ".user_id, "
                            + Config.getConfig(this).getEmailVerificationTable() + ".email)")) {
                throw new StorageQueryException(e);
            }
            // we do not throw an error since the email is already verified
        }
    }

    @Override
    public EmailVerificationTokenInfo getEmailVerificationTokenInfo(String token) throws StorageQueryException {
        try {
            return EmailVerificationQueries.getEmailVerificationTokenInfo(this, token);
        } catch (SQLException e) {
            throw new StorageQueryException(e);
        }
    }

    @Override
    public void revokeAllTokens(String userId, String email) throws StorageQueryException {
        try {
            EmailVerificationQueries.revokeAllTokens(this, userId, email);
        } catch (SQLException e) {
            throw new StorageQueryException(e);
        }
    }

    @Override
    public void unverifyEmail(String userId, String email) throws StorageQueryException {
        try {
            EmailVerificationQueries.unverifyEmail(this, userId, email);
        } catch (SQLException e) {
            throw new StorageQueryException(e);
        }
    }

    @Override
    public void deleteExpiredEmailVerificationTokens() throws StorageQueryException {
        try {
            EmailVerificationQueries.deleteExpiredEmailVerificationTokens(this);
        } catch (SQLException e) {
            throw new StorageQueryException(e);
        }
    }

    @Override
    public EmailVerificationTokenInfo[] getAllEmailVerificationTokenInfoForUser(String userId, String email)
            throws StorageQueryException {
        try {
            return EmailVerificationQueries.getAllEmailVerificationTokenInfoForUser(this, userId, email);
        } catch (SQLException e) {
            throw new StorageQueryException(e);
        }
    }

    @Override
    public boolean isEmailVerified(String userId, String email) throws StorageQueryException {
        try {
            return EmailVerificationQueries.isEmailVerified(this, userId, email);
        } catch (SQLException e) {
            throw new StorageQueryException(e);
        }
    }

    @Override
    public io.supertokens.pluginInterface.thirdparty.UserInfo getUserInfoUsingId_Transaction(TransactionConnection con,
            String thirdPartyId, String thirdPartyUserId) throws StorageQueryException {
        Connection sqlCon = (Connection) con.getConnection();
        try {
            return ThirdPartyQueries.getUserInfoUsingId_Transaction(this, sqlCon, thirdPartyId, thirdPartyUserId);
        } catch (SQLException e) {
            throw new StorageQueryException(e);
        }
    }

    @Override
    public void updateUserEmail_Transaction(TransactionConnection con, String thirdPartyId, String thirdPartyUserId,
            String newEmail) throws StorageQueryException {
        Connection sqlCon = (Connection) con.getConnection();
        try {
            ThirdPartyQueries.updateUserEmail_Transaction(this, sqlCon, thirdPartyId, thirdPartyUserId, newEmail);
        } catch (SQLException e) {
            throw new StorageQueryException(e);
        }
    }

    @Override
    public void signUp(io.supertokens.pluginInterface.thirdparty.UserInfo userInfo)
            throws StorageQueryException, io.supertokens.pluginInterface.thirdparty.exception.DuplicateUserIdException,
            DuplicateThirdPartyUserException {
        try {
            ThirdPartyQueries.signUp(this, userInfo);
        } catch (StorageTransactionLogicException eTemp) {
            Exception e = eTemp.actualException;
            if (e.getMessage()
                    .equals("[SQLITE_CONSTRAINT]  Abort due to constraint violation (UNIQUE constraint failed: "
                            + Config.getConfig(this).getThirdPartyUsersTable() + ".third_party_id, "
                            + Config.getConfig(this).getThirdPartyUsersTable() + ".third_party_user_id)")) {
                throw new DuplicateThirdPartyUserException();
            } else if (e.getMessage()
                    .equals("[SQLITE_CONSTRAINT]  Abort due to constraint violation (UNIQUE constraint failed: "
                            + Config.getConfig(this).getThirdPartyUsersTable() + ".user_id)")
                    || e.getMessage()
                            .equals("[SQLITE_CONSTRAINT]  Abort due to constraint violation (UNIQUE constraint failed: "
                                    + Config.getConfig(this).getUsersTable() + ".user_id)")) {
                throw new io.supertokens.pluginInterface.thirdparty.exception.DuplicateUserIdException();
            }
            throw new StorageQueryException(e);
        }
    }

    @Override
    public io.supertokens.pluginInterface.thirdparty.UserInfo getThirdPartyUserInfoUsingId(String thirdPartyId,
            String thirdPartyUserId) throws StorageQueryException {
        try {
            return ThirdPartyQueries.getThirdPartyUserInfoUsingId(this, thirdPartyId, thirdPartyUserId);
        } catch (SQLException e) {
            throw new StorageQueryException(e);
        }
    }

    @Override
    public io.supertokens.pluginInterface.thirdparty.UserInfo getThirdPartyUserInfoUsingId(String id)
            throws StorageQueryException {
        try {
            return ThirdPartyQueries.getThirdPartyUserInfoUsingId(this, id);
        } catch (SQLException e) {
            throw new StorageQueryException(e);
        }
    }

    @Override
    @Deprecated
    public io.supertokens.pluginInterface.thirdparty.UserInfo[] getThirdPartyUsers(@NotNull String userId,
            @NotNull Long timeJoined, @NotNull Integer limit, @NotNull String timeJoinedOrder)
            throws StorageQueryException {
        try {
            return ThirdPartyQueries.getThirdPartyUsers(this, userId, timeJoined, limit, timeJoinedOrder);
        } catch (SQLException e) {
            throw new StorageQueryException(e);
        }
    }

    @Override
    @Deprecated
    public io.supertokens.pluginInterface.thirdparty.UserInfo[] getThirdPartyUsers(@NotNull Integer limit,
            @NotNull String timeJoinedOrder) throws StorageQueryException {
        try {
            return ThirdPartyQueries.getThirdPartyUsers(this, limit, timeJoinedOrder);
        } catch (SQLException e) {
            throw new StorageQueryException(e);
        }
    }

    @Override
    public io.supertokens.pluginInterface.thirdparty.UserInfo[] getThirdPartyUsersByEmail(@NotNull String email)
            throws StorageQueryException {
        try {
            return ThirdPartyQueries.getThirdPartyUsersByEmail(this, email);
        } catch (SQLException e) {
            throw new StorageQueryException(e);
        }
    }

    @Override
    @Deprecated
    public long getThirdPartyUsersCount() throws StorageQueryException {
        try {
            return ThirdPartyQueries.getUsersCount(this);
        } catch (SQLException e) {
            throw new StorageQueryException(e);
        }
    }

    @Override
    public List<JWTSigningKeyInfo> getJWTSigningKeys_Transaction(TransactionConnection con)
            throws StorageQueryException {
        Connection sqlCon = (Connection) con.getConnection();
        try {
            return JWTSigningQueries.getJWTSigningKeys_Transaction(this, sqlCon);
        } catch (SQLException e) {
            throw new StorageQueryException(e);
        }
    }

    @Override
    public void setJWTSigningKey_Transaction(TransactionConnection con, JWTSigningKeyInfo info)
            throws StorageQueryException, DuplicateKeyIdException {
        Connection sqlCon = (Connection) con.getConnection();
        try {
            JWTSigningQueries.setJWTSigningKeyInfo_Transaction(this, sqlCon, info);
        } catch (SQLException e) {

            if (e.getMessage()
                    .equals("[SQLITE_CONSTRAINT]  Abort due to constraint violation (UNIQUE constraint failed: "
                            + Config.getConfig(this).getJWTSigningKeysTable() + ".key_id)")) {
                throw new DuplicateKeyIdException();
            }

            throw new StorageQueryException(e);
        }
    }

    @Override
    public PasswordlessDevice getDevice(String deviceIdHash) throws StorageQueryException {
        try {
            return PasswordlessQueries.getDevice(this, deviceIdHash);
        } catch (SQLException e) {
            throw new StorageQueryException(e);
        }
    }

    @Override
    public PasswordlessDevice[] getDevicesByEmail(String email) throws StorageQueryException {
        try {
            return PasswordlessQueries.getDevicesByEmail(this, email);
        } catch (SQLException e) {
            throw new StorageQueryException(e);
        }
    }

    @Override
    public PasswordlessDevice[] getDevicesByPhoneNumber(String phoneNumber) throws StorageQueryException {
        try {
            return PasswordlessQueries.getDevicesByPhoneNumber(this, phoneNumber);
        } catch (SQLException e) {
            throw new StorageQueryException(e);
        }
    }

    @Override
    public PasswordlessCode[] getCodesOfDevice(String deviceIdHash) throws StorageQueryException {
        try {
            return PasswordlessQueries.getCodesOfDevice(this, deviceIdHash);
        } catch (SQLException e) {
            throw new StorageQueryException(e);
        }
    }

    @Override
    public PasswordlessCode[] getCodesBefore(long time) throws StorageQueryException {
        try {
            return PasswordlessQueries.getCodesBefore(this, time);
        } catch (SQLException e) {
            throw new StorageQueryException(e);
        }
    }

    @Override
    public PasswordlessCode getCode(String codeId) throws StorageQueryException {
        try {
            return PasswordlessQueries.getCode(this, codeId);
        } catch (SQLException e) {
            throw new StorageQueryException(e);
        }
    }

    @Override
    public PasswordlessCode getCodeByLinkCodeHash(String linkCodeHash) throws StorageQueryException {
        try {
            return PasswordlessQueries.getCodeByLinkCodeHash(this, linkCodeHash);
        } catch (SQLException e) {
            throw new StorageQueryException(e);
        }
    }

    @Override
    public io.supertokens.pluginInterface.passwordless.UserInfo getUserById(String userId)
            throws StorageQueryException {
        try {
            return PasswordlessQueries.getUserById(this, userId);
        } catch (SQLException e) {
            throw new StorageQueryException(e);
        }
    }

    @Override
    public io.supertokens.pluginInterface.passwordless.UserInfo getUserByEmail(String email)
            throws StorageQueryException {
        try {
            return PasswordlessQueries.getUserByEmail(this, email);
        } catch (SQLException e) {
            throw new StorageQueryException(e);
        }
    }

    @Override
    public io.supertokens.pluginInterface.passwordless.UserInfo getUserByPhoneNumber(String phoneNumber)
            throws StorageQueryException {
        try {
            return PasswordlessQueries.getUserByPhoneNumber(this, phoneNumber);
        } catch (SQLException e) {
            throw new StorageQueryException(e);
        }
    }

    @Override
    public void createDeviceWithCode(String deviceIdHash, @Nullable String email, @Nullable String phoneNumber,
            String codeId, String linkCodeHash, long createdAt) throws StorageQueryException,
            DuplicateDeviceIdHashException, DuplicateCodeIdException, DuplicateLinkCodeHashException {
        if (email == null && phoneNumber == null) {
            throw new IllegalArgumentException("Both email and phoneNumber can't be null");
        }
        try {
            PasswordlessQueries.createDeviceWithCode(this, deviceIdHash, email, phoneNumber, codeId, linkCodeHash,
                    createdAt);
        } catch (StorageTransactionLogicException e) {
            String message = e.actualException.getMessage();
            if (message.equals("[SQLITE_CONSTRAINT]  Abort due to constraint violation (UNIQUE constraint failed: "
                    + Config.getConfig(this).getPasswordlessDevicesTable() + ".device_id_hash)")) {
                throw new DuplicateDeviceIdHashException();
            }
            if (message.equals("[SQLITE_CONSTRAINT]  Abort due to constraint violation (UNIQUE constraint failed: "
                    + Config.getConfig(this).getPasswordlessCodesTable() + ".code_id)")) {
                throw new DuplicateCodeIdException();
            }

            if (message.equals("[SQLITE_CONSTRAINT]  Abort due to constraint violation (UNIQUE constraint failed: "
                    + Config.getConfig(this).getPasswordlessCodesTable() + ".link_code_hash)")) {
                throw new DuplicateLinkCodeHashException();
            }

            throw new StorageQueryException(e);
        }
    }

    @Override
    public PasswordlessDevice getDevice_Transaction(TransactionConnection con, String deviceIdHash)
            throws StorageQueryException {
        Connection sqlCon = (Connection) con.getConnection();
        try {
            return PasswordlessQueries.getDevice_Transaction(this, sqlCon, deviceIdHash);
        } catch (SQLException e) {
            throw new StorageQueryException(e);
        }
    }

    @Override
    public void incrementDeviceFailedAttemptCount_Transaction(TransactionConnection con, String deviceIdHash)
            throws StorageQueryException {
        Connection sqlCon = (Connection) con.getConnection();
        try {
            PasswordlessQueries.incrementDeviceFailedAttemptCount_Transaction(this, sqlCon, deviceIdHash);
        } catch (SQLException e) {
            throw new StorageQueryException(e);
        }

    }

    @Override
    public PasswordlessCode[] getCodesOfDevice_Transaction(TransactionConnection con, String deviceIdHash)
            throws StorageQueryException {
        Connection sqlCon = (Connection) con.getConnection();
        try {
            return PasswordlessQueries.getCodesOfDevice_Transaction(this, sqlCon, deviceIdHash);
        } catch (SQLException e) {
            throw new StorageQueryException(e);
        }
    }

    @Override
    public void deleteDevice_Transaction(TransactionConnection con, String deviceIdHash) throws StorageQueryException {
        Connection sqlCon = (Connection) con.getConnection();
        try {
            PasswordlessQueries.deleteDevice_Transaction(this, sqlCon, deviceIdHash);
        } catch (SQLException e) {
            throw new StorageQueryException(e);
        }

    }

    @Override
    public void deleteDevicesByPhoneNumber_Transaction(TransactionConnection con, @Nonnull String phoneNumber)
            throws StorageQueryException {
        Connection sqlCon = (Connection) con.getConnection();
        try {
            PasswordlessQueries.deleteDevicesByPhoneNumber_Transaction(this, sqlCon, phoneNumber);
        } catch (SQLException e) {
            throw new StorageQueryException(e);
        }
    }

    @Override
    public void deleteDevicesByEmail_Transaction(TransactionConnection con, @Nonnull String email)
            throws StorageQueryException {
        Connection sqlCon = (Connection) con.getConnection();
        try {
            PasswordlessQueries.deleteDevicesByEmail_Transaction(this, sqlCon, email);
        } catch (SQLException e) {
            throw new StorageQueryException(e);
        }
    }

    @Override
    public void createCode(String codeId, String deviceIdHash, String linkCodeHash, long createdAt)
            throws StorageQueryException, UnknownDeviceIdHash, DuplicateCodeIdException,
            DuplicateLinkCodeHashException {

        try {
            this.startTransaction(con -> {
                Connection sqlCon = (Connection) con.getConnection();
                // SQLite is not compiled with foreign key constraint and so we must check for
                // the deviceIdHash manually
                if (this.getDevice_Transaction(con, deviceIdHash) == null) {
                    throw new StorageTransactionLogicException(new UnknownDeviceIdHash());
                }
                try {
                    PasswordlessQueries.createCode_Transaction(this, sqlCon, codeId, deviceIdHash, linkCodeHash,
                            createdAt);
                    sqlCon.commit();
                } catch (SQLException e) {
                    throw new StorageTransactionLogicException(e);
                }
                return null;
            });
        } catch (StorageTransactionLogicException e) {
            if (e.actualException instanceof UnknownDeviceIdHash) {
                throw (UnknownDeviceIdHash) e.actualException;
            }
            String message = e.actualException.getMessage();
            if (message.equals("[SQLITE_CONSTRAINT]  Abort due to constraint violation (UNIQUE constraint failed: "
                    + Config.getConfig(this).getPasswordlessCodesTable() + ".code_id)")) {
                throw new DuplicateCodeIdException();
            }

            if (message.equals("[SQLITE_CONSTRAINT]  Abort due to constraint violation (UNIQUE constraint failed: "
                    + Config.getConfig(this).getPasswordlessCodesTable() + ".link_code_hash)")) {
                throw new DuplicateLinkCodeHashException();
            }
            throw new StorageQueryException(e.actualException);
        }
    }

    @Override
    public PasswordlessCode getCodeByLinkCodeHash_Transaction(TransactionConnection con, String linkCodeHash)
            throws StorageQueryException {
        Connection sqlCon = (Connection) con.getConnection();
        try {
            return PasswordlessQueries.getCodeByLinkCodeHash_Transaction(this, sqlCon, linkCodeHash);
        } catch (SQLException e) {
            throw new StorageQueryException(e);
        }
    }

    @Override
    public void deleteCode_Transaction(TransactionConnection con, String deviceIdHash) throws StorageQueryException {
        Connection sqlCon = (Connection) con.getConnection();
        try {
            PasswordlessQueries.deleteCode_Transaction(this, sqlCon, deviceIdHash);
        } catch (SQLException e) {
            throw new StorageQueryException(e);
        }
    }

    @Override
    public void deleteCodesOfDeviceBefore_Transaction(TransactionConnection con, String deviceIdHash, long time)
            throws StorageQueryException {
        Connection sqlCon = (Connection) con.getConnection();
        try {
            PasswordlessQueries.deleteCodesOfDeviceBefore_Transaction(this, sqlCon, deviceIdHash, time);
        } catch (SQLException e) {
            throw new StorageQueryException(e);
        }
    }

    @Override
    public void createUser(String userId, String email, String phoneNumber, long timeJoined)
            throws StorageQueryException, DuplicateEmailException, DuplicatePhoneNumberException,
            DuplicateUserIdException {
        if (email == null && phoneNumber == null) {
            throw new IllegalArgumentException("Both email and phoneNumber can't be null");
        }

        try {
            PasswordlessQueries.createUser(this, userId, email, phoneNumber, timeJoined);
        } catch (StorageTransactionLogicException e) {
            String message = e.actualException.getMessage();
            if (message
                    .equals("[SQLITE_CONSTRAINT]  Abort due to constraint violation (UNIQUE constraint failed: "
                            + Config.getConfig(this).getPasswordlessUsersTable() + ".user_id)")
                    || message
                            .equals("[SQLITE_CONSTRAINT]  Abort due to constraint violation (UNIQUE constraint failed: "
                                    + Config.getConfig(this).getUsersTable() + ".user_id)")) {
                throw new DuplicateUserIdException();
            }

            if (message.equals("[SQLITE_CONSTRAINT]  Abort due to constraint violation (UNIQUE constraint failed: "
                    + Config.getConfig(this).getPasswordlessUsersTable() + ".email)")) {
                throw new DuplicateEmailException();
            }

            if (message.equals("[SQLITE_CONSTRAINT]  Abort due to constraint violation (UNIQUE constraint failed: "
                    + Config.getConfig(this).getPasswordlessUsersTable() + ".phone_number)")) {
                throw new DuplicatePhoneNumberException();
            }

            throw new StorageQueryException(e.actualException);
        }
    }

    @Override
    public void updateUser_Transaction(TransactionConnection con, String userId, String email, String phoneNumber)
            throws StorageQueryException, DuplicateEmailException, DuplicatePhoneNumberException,
            UnknownUserIdException {
        if (email == null && phoneNumber == null) {
            throw new IllegalArgumentException("Both email and phoneNumber can't be null");
        }

        Connection sqlCon = (Connection) con.getConnection();
        try {
            // SQLite is not compiled with foreign key constraint and so we must check for
            // the userId manually
            if (this.getUserById(userId) == null) {
                throw new UnknownUserIdException();
            }

            PasswordlessQueries.updateUser_Transaction(this, sqlCon, userId, email, phoneNumber);
        } catch (SQLException e) {
            if (e.getMessage()
                    .equals("[SQLITE_CONSTRAINT]  Abort due to constraint violation (UNIQUE constraint failed: "
                            + Config.getConfig(this).getPasswordlessUsersTable() + ".phone_number)")) {
                throw new DuplicatePhoneNumberException();
            }

            if (e.getMessage()
                    .equals("[SQLITE_CONSTRAINT]  Abort due to constraint violation (UNIQUE constraint failed: "
                            + Config.getConfig(this).getPasswordlessUsersTable() + ".email)")) {
                throw new DuplicateEmailException();
            }

            throw new StorageQueryException(e);
        }
    }
}
