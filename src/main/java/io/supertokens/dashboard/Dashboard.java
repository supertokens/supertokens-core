/*
 *    Copyright (c) 2023, VRAI Labs and/or its affiliates. All rights reserved.
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

package io.supertokens.dashboard;

import java.util.Arrays;
import java.util.UUID;
import java.util.regex.Pattern;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import io.supertokens.Main;
import io.supertokens.dashboard.exceptions.UserSuspendedException;
import io.supertokens.emailpassword.PasswordHashing;
import io.supertokens.featureflag.EE_FEATURES;
import io.supertokens.featureflag.FeatureFlag;
import io.supertokens.featureflag.exceptions.FeatureNotEnabledException;
import io.supertokens.pluginInterface.dashboard.DashboardSessionInfo;
import io.supertokens.pluginInterface.dashboard.DashboardUser;
import io.supertokens.pluginInterface.dashboard.exceptions.DuplicateEmailException;
import io.supertokens.pluginInterface.dashboard.exceptions.DuplicateUserIdException;
import io.supertokens.pluginInterface.dashboard.exceptions.UserIdNotFoundException;
import io.supertokens.pluginInterface.dashboard.sqlStorage.DashboardSQLStorage;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.exceptions.StorageTransactionLogicException;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.utils.Utils;
import jakarta.annotation.Nullable;

public class Dashboard {
    public static final int MAX_NUMBER_OF_FREE_DASHBOARD_USERS = 1;
    public static final long DASHBOARD_SESSION_DURATION = 2592000000L; // 30 days in milliseconds

    public static void signUpDashboardUser(Main main, String email, String password)
            throws StorageQueryException, DuplicateEmailException, FeatureNotEnabledException {

        if (!isFeatureFlagEnabledOrUserCountIsUnderThreshold(main)) {
            // TODO: update message
            throw new FeatureNotEnabledException("Free user limit reached, please enable the dashboard feature");
        }

        String hashedPassword = PasswordHashing.getInstance(main).createHashWithSalt(password);
        while (true) {

            String userId = Utils.getUUID();
            long timeJoined = System.currentTimeMillis();

            try {
                DashboardUser user = new DashboardUser(userId, email, hashedPassword, timeJoined);
                StorageLayer.getDashboardStorage(main).createNewDashboardUser(user);
                return;
            } catch (DuplicateUserIdException ignored) {
                // we retry with a new userId (while loop)
            }
        }
    }

    public static JsonArray getAllDashboardUsers(Main main) throws StorageQueryException {

        DashboardUser[] dashboardUsers = StorageLayer.getDashboardStorage(main).getAllDashboardUsers();
        JsonArray jsonArrayOfDashboardUsers = new JsonArray();
        if (isDashboardFeatureFlagEnabled(main)) {
            for (int i = 0; i < dashboardUsers.length; i++) {
                JsonObject user = new JsonObject();
                user.addProperty("email", dashboardUsers[i].email);
                user.addProperty("userId", dashboardUsers[i].userId);
                user.addProperty("isSuspended", false);
                jsonArrayOfDashboardUsers.add(user);
            }
        } else {
            for (int i = 0; i < dashboardUsers.length; i++) {
                JsonObject user = new JsonObject();
                user.addProperty("email", dashboardUsers[i].email);
                user.addProperty("userId", dashboardUsers[i].userId);
                user.addProperty("isSuspended", !((i + 1) <= Dashboard.MAX_NUMBER_OF_FREE_DASHBOARD_USERS));
                jsonArrayOfDashboardUsers.add(user);
            }
        }

        return jsonArrayOfDashboardUsers;
    }

    public static String signInDashboardUser(Main main, String email, String password)
            throws StorageQueryException, UserSuspendedException {
        if (isUserSuspended(main, email)) {
            throw new UserSuspendedException();

        }
        DashboardUser user = StorageLayer.getDashboardStorage(main).getDashboardUserByEmail(email);
        if (user != null) {
            if (PasswordHashing.getInstance(main).verifyPasswordWithHash(password, user.passwordHash)) {
                // create a new session for the user
                try {
                    return createSessionForDashboardUser(main, user);
                } catch (UserIdNotFoundException ignore) {
                    // should not come here since user exists
                }
            }
        }
        return null;
    }

    public static boolean deleteUserWithUserId(Main main, String userId) throws StorageQueryException {
        return StorageLayer.getDashboardStorage(main).deleteDashboardUserWithUserId(userId);
    }

    private static boolean isUserSuspended(Main main, String email) throws StorageQueryException {
        if (!isFeatureFlagEnabledOrUserCountIsUnderThreshold(main)) {
            DashboardUser[] users = StorageLayer.getDashboardStorage(main).getAllDashboardUsers();
            for (int i = 0; i < MAX_NUMBER_OF_FREE_DASHBOARD_USERS; i++) {
                if (email.equals(users[i].email)) {
                    return false;
                }
            }
            return true;
        }

        return false;
    }

    public static boolean deleteUserWithEmail(Main main, String email) throws StorageQueryException {
        DashboardUser user = StorageLayer.getDashboardStorage(main).getDashboardUserByEmail(email);
        if (user != null) {
            return deleteUserWithUserId(main, user.userId);
        }
        return false;
    }

    public static void updateUsersCredentialsWithEmail(Main main, String email, @Nullable String newEmail,
            @Nullable String newPassword)
            throws StorageQueryException, DuplicateEmailException, StorageTransactionLogicException {

        DashboardSQLStorage storage = StorageLayer.getDashboardStorage(main);

        DashboardUser user = storage.getDashboardUserByEmail(email);
        if (user != null) {
            updateUsersCredentialsWithUserId(main, user.userId, newEmail, newPassword);
        }
    }

    public static void updateUsersCredentialsWithUserId(Main main, String userId, String newEmail, String newPassword)
            throws StorageQueryException, DuplicateEmailException, StorageTransactionLogicException {
        DashboardSQLStorage storage = StorageLayer.getDashboardStorage(main);
        try {
            storage.startTransaction(transaction -> {
                if (newEmail != null) {
                    try {
                        storage.updateDashboardUsersEmailWithUserId_Transaction(transaction, userId, newEmail);
                    } catch (DuplicateEmailException e) {
                        throw new StorageTransactionLogicException(e);
                    }
                }

                if (newPassword != null) {
                    String hashedPassword = PasswordHashing.getInstance(main).createHashWithSalt(newPassword);
                    storage.updateDashboardUsersPasswordWithUserId_Transaction(transaction, userId, hashedPassword);
                }
                storage.commitTransaction(transaction);
                return null;
            });
        } catch (StorageTransactionLogicException e) {
            if (e.actualException instanceof DuplicateEmailException) {
                throw (DuplicateEmailException) e.actualException;
            }
            throw e;
        }
    }

    public static boolean isFeatureFlagEnabledOrUserCountIsUnderThreshold(Main main) throws StorageQueryException {
        if (!isDashboardFeatureFlagEnabled(main)) {
            // retrieve current dashboard users
            DashboardUser[] users = StorageLayer.getDashboardStorage(main).getAllDashboardUsers();
            // check if current dashboard users count is under the threshold
            return users.length <= MAX_NUMBER_OF_FREE_DASHBOARD_USERS;
        }
        return true;
    }

    public static boolean revokeSessionWithSessionId(Main main, String sessionId) throws StorageQueryException {
        return StorageLayer.getDashboardStorage(main).revokeSessionWithSessionId(sessionId);
    }

    private static boolean isDashboardFeatureFlagEnabled(Main main) throws StorageQueryException {
        return Arrays.stream(FeatureFlag.getInstance(main).getEnabledFeatures())
                .anyMatch(t -> t == EE_FEATURES.DASHBOARD_LOGIN);
    }

    private static String createSessionForDashboardUser(Main main, DashboardUser user)
            throws StorageQueryException, UserIdNotFoundException {
        String sessionId = UUID.randomUUID().toString();
        long timeCreated = System.currentTimeMillis();
        long expiry = timeCreated + DASHBOARD_SESSION_DURATION;
        StorageLayer.getDashboardStorage(main).createNewDashboardUserSession(user.userId, sessionId, timeCreated,
                expiry);
        return sessionId;
    }

    public static boolean isValidEmail(String email) {
        // We use the same regex as the backend SDK
        // https://github.com/supertokens/supertokens-node/blob/master/lib/ts/recipe/emailpassword/utils.ts#L250
        String regexPatternForEmail = "((^<>()[].,;:@]+(.^<>()[].,;:@]+)*)|(.+))@(([[0-9]{1,3}.[0-9]{1,3}.[0-9]{1,3}.[0-9]{1,3}])|(([a-zA-Z-0-9]+.)+[a-zA-Z]{2,}))$";
        return patternMatcher(email, regexPatternForEmail);
    }

    public static boolean isStrongPassword(String password) {
        // TODO: this function should be updated to return null if it passes the regex,
        // or a string depending on which part of the regex fails
        String regexPatternForPassword = "(?=.*[A-Za-z])(?=.*[0-9]).{8,100}";
        return patternMatcher(password, regexPatternForPassword);
    }

    public static boolean isValidUserSession(Main main, String sessionId)
            throws StorageQueryException, UserSuspendedException {
        DashboardSessionInfo sessionInfo = StorageLayer.getDashboardStorage(main)
                .getSessionInfoWithSessionId(sessionId);
        if (sessionInfo != null) {
            // check if user is suspended
            DashboardUser user = StorageLayer.getDashboardStorage(main).getDashboardUserByUserId(sessionInfo.userId);
            if (isUserSuspended(main, user.email)) {
                throw new UserSuspendedException();
            }
            return true;
        }
        return false;
    }

    private static boolean patternMatcher(String input, String pattern) {
        return Pattern.compile(pattern).matcher(input).matches();
    }
}
