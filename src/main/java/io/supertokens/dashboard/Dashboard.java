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

    public static DashboardUser signUpDashboardUser(Main main, String email, String password)
            throws StorageQueryException, DuplicateEmailException, FeatureNotEnabledException {

        if (StorageLayer.getDashboardStorage(main).getDashboardUserByEmail(email) != null) {
            throw new DuplicateEmailException();
        }

        if (!isDashboardFeatureFlagEnabled(main)) {
            DashboardUser[] users = StorageLayer.getDashboardStorage(main).getAllDashboardUsers();
            if (users.length >= MAX_NUMBER_OF_FREE_DASHBOARD_USERS) {
                throw new FeatureNotEnabledException(
                        "Free user limit reached. Please subscribe to a SuperTokens core license key to allow more users to access the dashboard.");
            }
        }

        String hashedPassword = PasswordHashing.getInstance(main).createHashWithSalt(password);
        while (true) {

            String userId = Utils.getUUID();
            long timeJoined = System.currentTimeMillis();

            try {
                DashboardUser user = new DashboardUser(userId, email, hashedPassword, timeJoined);
                StorageLayer.getDashboardStorage(main).createNewDashboardUser(user);
                return user;
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
            return validDashboardUsers.toArray(DashboardUser[]::new);
        }
    }

    public static String signInDashboardUser(Main main, String email, String password)
            throws StorageQueryException, UserSuspendedException {
        DashboardUser user = StorageLayer.getDashboardStorage(main).getDashboardUserByEmail(email);
        if (user != null) {
            if (isUserSuspended(main, email, null)) {
                throw new UserSuspendedException();
            }
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

    private static boolean isUserSuspended(Main main, @Nullable String email, @Nullable String userId)
            throws StorageQueryException {
        if (!isDashboardFeatureFlagEnabled(main)) {
            DashboardUser[] users = StorageLayer.getDashboardStorage(main).getAllDashboardUsers();

            if (email != null) {
                for (int i = 0; i < MAX_NUMBER_OF_FREE_DASHBOARD_USERS; i++) {
                    if (email.equals(users[i].email)) {
                        return false;
                    }
                }
            } else if (userId != null) {
                for (int i = 0; i < MAX_NUMBER_OF_FREE_DASHBOARD_USERS; i++) {
                    if (userId.equals(users[i].userId)) {
                        return false;
                    }
                }
            } else {
                throw new IllegalStateException("Should never come here");
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

    public static DashboardUser updateUsersCredentialsWithUserId(Main main, String userId, String newEmail, String newPassword)
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
        
        // revoke sessions for the user
        DashboardSessionInfo[] sessionInfo = Dashboard.getAllDashboardSessionsForUser(main, userId);
        for(int i = 0; i < sessionInfo.length; i ++){
            StorageLayer.getDashboardStorage(main).revokeSessionWithSessionId(sessionInfo[i].sessionId);
        }

        return StorageLayer.getDashboardStorage(main).getDashboardUserByUserId(userId);
    }

    public static DashboardUser getDashboardUserByEmail(Main main, String email) throws StorageQueryException{

        return StorageLayer.getDashboardStorage(main).getDashboardUserByEmail(email);
    }

    public static boolean isUserCountUnderThreshold(DashboardUser[] users) {
        return users.length < MAX_NUMBER_OF_FREE_DASHBOARD_USERS;
    }

    public static boolean revokeSessionWithSessionId(Main main, String sessionId) throws StorageQueryException {
        return StorageLayer.getDashboardStorage(main).revokeSessionWithSessionId(sessionId);
    }

    public static DashboardSessionInfo [] getAllDashboardSessionsForUser(Main main, String userId) throws StorageQueryException {
        return StorageLayer.getDashboardStorage(main).getAllSessionsForUserId(userId);
    }

    private static boolean isDashboardFeatureFlagEnabled(Main main) throws StorageQueryException {
        try {
            return Arrays.stream(FeatureFlag.getInstance(main).getEnabledFeatures())
                .anyMatch(t -> t == EE_FEATURES.DASHBOARD_LOGIN);
        } catch (Exception e) {
           return false;
        }
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

        // length >= 8 && < 100
        // must have a number and a character
        // as per
        // https://github.com/supertokens/supertokens-auth-react/issues/5#issuecomment-709512438

        if (password.length() < 8) {
            return "Password must contain at least 8 characters, including a number";
        }

        if (password.length() >= 100) {
            return "Password's length must be lesser than 100 characters";
        }

        if (!patternMatcher(password, "(.*[A-Za-z]+.*)")) {
            return "Password must contain at least one alphabet";
        }

        if (!patternMatcher(password, "(.*[0-9]+.*)")) {
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
            if (isUserSuspended(main, null, sessionInfo.userId)) {
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
