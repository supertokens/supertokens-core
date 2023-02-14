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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
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

        if (StorageLayer.getDashboardStorage(main).getDashboardUserByEmail(email) != null) {
            throw new DuplicateEmailException();
        }

        DashboardUser[] users = StorageLayer.getDashboardStorage(main).getAllDashboardUsers();

        if (!isFeatureFlagEnabledOrUserCountIsUnderThreshold(main, users)) {
            // TODO: update message
            throw new FeatureNotEnabledException(
                    "Free user limit reached. Please subscribe to a SuperTokens core license key to allow more users to access the dashboard.");
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

    public static DashboardUser[] getAllDashboardUsers(Main main) throws StorageQueryException {

        DashboardUser[] dashboardUsers = StorageLayer.getDashboardStorage(main).getAllDashboardUsers();
        if (isDashboardFeatureFlagEnabled(main)) {
            return dashboardUsers;
        } else {
            List<DashboardUser> validDashboardUsers = new ArrayList<>();
            for (int i = 0; i < dashboardUsers.length && i < Dashboard.MAX_NUMBER_OF_FREE_DASHBOARD_USERS; i++) {
                validDashboardUsers.add(dashboardUsers[i]);
            }
            return (DashboardUser[]) validDashboardUsers.toArray();
        }
    }

    public static String signInDashboardUser(Main main, String email, String password)
            throws StorageQueryException, UserSuspendedException {
        if (isUserSuspendedCheckWithEmailCheck(main, email)) {
            throw new UserSuspendedException();

        }
        DashboardUser user = StorageLayer.getDashboardStorage(main).getDashboardUserByEmail(email);
        if (user != null) {
            if (PasswordHashing.getInstance(main).verifyPasswordWithHash(password, user.passwordHash)) {
                // create a new session for the user
                try {
                    return createSessionForDashboardUser(main, user);
                } catch (UserIdNotFoundException e) {
                    throw new IllegalStateException(e);
                }
            }
        }
        return null;
    }

    public static boolean deleteUserWithUserId(Main main, String userId) throws StorageQueryException {
        return StorageLayer.getDashboardStorage(main).deleteDashboardUserWithUserId(userId);
    }

    private static boolean isUserSuspendedCheckWithEmailCheck(Main main, String email) throws StorageQueryException {
        DashboardUser[] users = StorageLayer.getDashboardStorage(main).getAllDashboardUsers();
        if (!isFeatureFlagEnabledOrUserCountIsUnderThreshold(main, users)) {
            for (int i = 0; i < MAX_NUMBER_OF_FREE_DASHBOARD_USERS; i++) {
                if (email.equals(users[i].email)) {
                    return false;
                }
            }
            return true;
        }

        return false;
    }

    private static boolean isUserSuspendedCheckWithUserIdCheck(Main main, String userId) throws StorageQueryException {
        DashboardUser[] users = StorageLayer.getDashboardStorage(main).getAllDashboardUsers();
        if (!isFeatureFlagEnabledOrUserCountIsUnderThreshold(main, users)) {
            for (int i = 0; i < MAX_NUMBER_OF_FREE_DASHBOARD_USERS; i++) {
                if (userId.equals(users[i].userId)) {
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
            throws StorageQueryException, DuplicateEmailException, StorageTransactionLogicException,
            UserIdNotFoundException {

        DashboardSQLStorage storage = StorageLayer.getDashboardStorage(main);

        DashboardUser user = storage.getDashboardUserByEmail(email);
        if (user != null) {
            updateUsersCredentialsWithUserId(main, user.userId, newEmail, newPassword);
        }
        throw new UserIdNotFoundException();
    }

    public static void updateUsersCredentialsWithUserId(Main main, String userId, String newEmail, String newPassword)
            throws StorageQueryException, DuplicateEmailException, UserIdNotFoundException,
            StorageTransactionLogicException {
        DashboardSQLStorage storage = StorageLayer.getDashboardStorage(main);
        try {
            storage.startTransaction(transaction -> {
                if (newEmail != null) {
                    try {
                        storage.updateDashboardUsersEmailWithUserId_Transaction(transaction, userId, newEmail);
                    } catch (DuplicateEmailException e) {
                        throw new StorageTransactionLogicException(e);
                    } catch (UserIdNotFoundException e) {
                        throw new StorageTransactionLogicException(e);
                    }
                }

                if (newPassword != null) {
                    String hashedPassword = PasswordHashing.getInstance(main).createHashWithSalt(newPassword);
                    try {
                        storage.updateDashboardUsersPasswordWithUserId_Transaction(transaction, userId, hashedPassword);
                    } catch (UserIdNotFoundException e) {
                        throw new StorageTransactionLogicException(e);
                    }
                }
                storage.commitTransaction(transaction);
                return null;
            });
        } catch (StorageTransactionLogicException e) {
            if (e.actualException instanceof DuplicateEmailException) {
                throw (DuplicateEmailException) e.actualException;
            }
            if (e.actualException instanceof UserIdNotFoundException) {
                throw (UserIdNotFoundException) e.actualException;
            }
            throw e;
        }
    }

    public static boolean isFeatureFlagEnabledOrUserCountIsUnderThreshold(Main main, DashboardUser[] users)
            throws StorageQueryException {
        if (!isDashboardFeatureFlagEnabled(main)) {
            // check if current dashboard users count is under the threshold
            return users.length < MAX_NUMBER_OF_FREE_DASHBOARD_USERS;
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

    public static String validatePassword(String password) {

        // as per
        // https://github.com/supertokens/supertokens-auth-react/issues/5#issuecomment-709512438

        if (password.length() < 8) {
            return "Password must contain at least 8 characters, including a number";
        }

        if (password.length() >= 100) {
            return "Password's length must be lesser than 100 characters";
        }

        if (patternMatcher("(?=.*[A-Za-z])", password)) {
            return "Password must contain at least one alphabet";
        }

        if (patternMatcher("(?=.*[0-9])", password)) {
            return "Password must contain at least one number";
        }

        return null;
    }

    public static boolean isValidUserSession(Main main, String sessionId)
            throws StorageQueryException, UserSuspendedException {
        DashboardSessionInfo sessionInfo = StorageLayer.getDashboardStorage(main)
                .getSessionInfoWithSessionId(sessionId);
        if (sessionInfo != null) {
            // check if user is suspended
            if (isUserSuspendedCheckWithUserIdCheck(main, sessionInfo.userId)) {
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
