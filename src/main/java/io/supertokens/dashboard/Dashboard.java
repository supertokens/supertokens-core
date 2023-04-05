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
import io.supertokens.pluginInterface.multitenancy.AppIdentifier;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.utils.Utils;
import jakarta.annotation.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

public class Dashboard {
    public static final int MAX_NUMBER_OF_FREE_DASHBOARD_USERS = 1;
    public static final long DASHBOARD_SESSION_DURATION = 2592000000L; // 30 days in milliseconds

    @TestOnly
    public static DashboardUser signUpDashboardUser(Main main, String email,
                                                    String password)
            throws StorageQueryException, DuplicateEmailException, FeatureNotEnabledException {
        try {
            return signUpDashboardUser(new AppIdentifier(null, null), main, email, password);
        } catch (TenantOrAppNotFoundException e) {
            throw new IllegalStateException(e);
        }
    }

    public static DashboardUser signUpDashboardUser(AppIdentifier appIdentifier, Main main, String email,
                                                    String password)
            throws StorageQueryException, DuplicateEmailException, FeatureNotEnabledException,
            TenantOrAppNotFoundException {

        if (StorageLayer.getDashboardStorage(appIdentifier, main).getDashboardUserByEmail(appIdentifier, email) !=
                null) {
            throw new DuplicateEmailException();
        }

        if (!isDashboardFeatureFlagEnabled(main, appIdentifier)) {
            DashboardUser[] users = StorageLayer.getDashboardStorage(appIdentifier, main)
                    .getAllDashboardUsers(appIdentifier);
            if (users.length >= MAX_NUMBER_OF_FREE_DASHBOARD_USERS) {
                throw new FeatureNotEnabledException(
                        "Free user limit reached. Please subscribe to a SuperTokens core license key to allow more " +
                                "users to access the dashboard.");
            }
        }

        String hashedPassword = PasswordHashing.getInstance(main).createHashWithSalt(appIdentifier, password);
        while (true) {

            String userId = Utils.getUUID();
            long timeJoined = System.currentTimeMillis();

            try {
                DashboardUser user = new DashboardUser(userId, email, hashedPassword, timeJoined);
                StorageLayer.getDashboardStorage(appIdentifier, main).createNewDashboardUser(appIdentifier, user);
                return user;
            } catch (DuplicateUserIdException ignored) {
                // we retry with a new userId (while loop)
            }
        }
    }

    @TestOnly
    public static DashboardUser[] getAllDashboardUsers(Main main)
            throws StorageQueryException {
        try {
            return getAllDashboardUsers(new AppIdentifier(null, null), main);
        } catch (TenantOrAppNotFoundException e) {
            throw new IllegalStateException(e);
        }
    }

    public static DashboardUser[] getAllDashboardUsers(AppIdentifier appIdentifier, Main main)
            throws StorageQueryException, TenantOrAppNotFoundException {

        DashboardUser[] dashboardUsers = StorageLayer.getDashboardStorage(appIdentifier, main)
                .getAllDashboardUsers(appIdentifier);
        if (isDashboardFeatureFlagEnabled(main, appIdentifier)) {
            return dashboardUsers;
        } else {
            List<DashboardUser> validDashboardUsers = new ArrayList<>();
            for (int i = 0; i < dashboardUsers.length && i < Dashboard.MAX_NUMBER_OF_FREE_DASHBOARD_USERS; i++) {
                validDashboardUsers.add(dashboardUsers[i]);
            }
            return validDashboardUsers.toArray(DashboardUser[]::new);
        }
    }

    @TestOnly
    public static String signInDashboardUser(Main main, String email, String password)
            throws StorageQueryException, UserSuspendedException {
        try {
            return signInDashboardUser(new AppIdentifier(null, null), main, email, password);
        } catch (TenantOrAppNotFoundException e) {
            throw new IllegalStateException(e);
        }
    }

    public static String signInDashboardUser(AppIdentifier appIdentifier, Main main, String email, String password)
            throws StorageQueryException, UserSuspendedException, TenantOrAppNotFoundException {
        DashboardUser user = StorageLayer.getDashboardStorage(appIdentifier, main)
                .getDashboardUserByEmail(appIdentifier, email);
        if (user != null) {
            if (isUserSuspended(appIdentifier, main, email, null)) {
                throw new UserSuspendedException();
            }
            if (PasswordHashing.getInstance(main).verifyPasswordWithHash(appIdentifier, password, user.passwordHash)) {
                // create a new session for the user
                try {
                    return createSessionForDashboardUser(appIdentifier, main, user);
                } catch (UserIdNotFoundException e) {
                    throw new IllegalStateException(e);
                }
            }
        }
        return null;
    }

    @TestOnly
    public static boolean deleteUserWithUserId(Main main, String userId)
            throws StorageQueryException, TenantOrAppNotFoundException {
        try {
            return deleteUserWithUserId(new AppIdentifier(null, null), main, userId);
        } catch (TenantOrAppNotFoundException e) {
            throw new IllegalStateException(e);
        }
    }

    public static boolean deleteUserWithUserId(AppIdentifier appIdentifier, Main main, String userId)
            throws StorageQueryException, TenantOrAppNotFoundException {
        return StorageLayer.getDashboardStorage(appIdentifier, main)
                .deleteDashboardUserWithUserId(appIdentifier, userId);
    }

    private static boolean isUserSuspended(AppIdentifier appIdentifier, Main main, @Nullable String email,
                                           @Nullable String userId)
            throws StorageQueryException, TenantOrAppNotFoundException {
        if (!isDashboardFeatureFlagEnabled(main, appIdentifier)) {
            DashboardUser[] users = StorageLayer.getDashboardStorage(appIdentifier, main)
                    .getAllDashboardUsers(appIdentifier);

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

    @TestOnly
    public static boolean deleteUserWithEmail(Main main, String email)
            throws StorageQueryException {
        try {
            return deleteUserWithEmail(new AppIdentifier(null, null), main, email);
        } catch (TenantOrAppNotFoundException e) {
            throw new IllegalStateException(e);
        }
    }

    public static boolean deleteUserWithEmail(AppIdentifier appIdentifier, Main main, String email)
            throws StorageQueryException, TenantOrAppNotFoundException {
        DashboardUser user = StorageLayer.getDashboardStorage(appIdentifier, main)
                .getDashboardUserByEmail(appIdentifier, email);
        if (user != null) {
            return deleteUserWithUserId(appIdentifier, main, user.userId);
        }
        return false;
    }

    @TestOnly
    public static DashboardUser updateUsersCredentialsWithUserId(Main main, String userId,
                                                                 String newEmail,
                                                                 String newPassword)
            throws StorageQueryException, DuplicateEmailException, UserIdNotFoundException,
            StorageTransactionLogicException {
        try {
            return updateUsersCredentialsWithUserId(new AppIdentifier(null, null), main, userId, newEmail, newPassword);
        } catch (TenantOrAppNotFoundException e) {
            throw new IllegalStateException(e);
        }
    }

    public static DashboardUser updateUsersCredentialsWithUserId(AppIdentifier appIdentifier, Main main, String userId,
                                                                 String newEmail,
                                                                 String newPassword)
            throws StorageQueryException, DuplicateEmailException, UserIdNotFoundException,
            StorageTransactionLogicException, TenantOrAppNotFoundException {
        DashboardSQLStorage storage = StorageLayer.getDashboardStorage(appIdentifier, main);
        try {
            storage.startTransaction(transaction -> {
                if (newEmail != null) {
                    try {
                        storage.updateDashboardUsersEmailWithUserId_Transaction(appIdentifier, transaction, userId,
                                newEmail);
                    } catch (DuplicateEmailException | UserIdNotFoundException e) {
                        throw new StorageTransactionLogicException(e);
                    }
                }

                if (newPassword != null) {
                    try {
                        String hashedPassword = PasswordHashing.getInstance(main)
                                .createHashWithSalt(appIdentifier, newPassword);
                        storage.updateDashboardUsersPasswordWithUserId_Transaction(appIdentifier, transaction, userId,
                                hashedPassword);
                    } catch (UserIdNotFoundException | TenantOrAppNotFoundException e) {
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
            if (e.actualException instanceof TenantOrAppNotFoundException) {
                throw (TenantOrAppNotFoundException) e.actualException;
            }
            throw e;
        }

        // revoke sessions for the user
        DashboardSessionInfo[] sessionInfo = Dashboard.getAllDashboardSessionsForUser(appIdentifier, main, userId);
        for (int i = 0; i < sessionInfo.length; i++) {
            StorageLayer.getDashboardStorage(appIdentifier, main)
                    .revokeSessionWithSessionId(appIdentifier, sessionInfo[i].sessionId);
        }

        return StorageLayer.getDashboardStorage(appIdentifier, main)
                .getDashboardUserByUserId(appIdentifier, userId);
    }

    @TestOnly
    public static DashboardUser getDashboardUserByEmail(Main main, String email)
            throws StorageQueryException {
        try {
            return getDashboardUserByEmail(new AppIdentifier(null, null), main, email);
        } catch (TenantOrAppNotFoundException e) {
            throw new IllegalStateException(e);
        }
    }

    public static DashboardUser getDashboardUserByEmail(AppIdentifier appIdentifier, Main main, String
            email)
            throws StorageQueryException, TenantOrAppNotFoundException {

        return StorageLayer.getDashboardStorage(appIdentifier, main)
                .getDashboardUserByEmail(appIdentifier, email);
    }

    @TestOnly
    public static boolean revokeSessionWithSessionId(Main main, String sessionId)
            throws StorageQueryException {
        try {
            return revokeSessionWithSessionId(new AppIdentifier(null, null), main, sessionId);
        } catch (TenantOrAppNotFoundException e) {
            throw new IllegalStateException(e);
        }
    }

    public static boolean revokeSessionWithSessionId(AppIdentifier appIdentifier, Main main, String
            sessionId)
            throws StorageQueryException, TenantOrAppNotFoundException {
        return StorageLayer.getDashboardStorage(appIdentifier, main)
                .revokeSessionWithSessionId(appIdentifier, sessionId);
    }

    @TestOnly
    public static DashboardSessionInfo[] getAllDashboardSessionsForUser(Main main,
                                                                        String userId)
            throws StorageQueryException {
        try {
            return getAllDashboardSessionsForUser(new AppIdentifier(null, null), main, userId);
        } catch (TenantOrAppNotFoundException e) {
            throw new IllegalStateException(e);
        }
    }

    public static DashboardSessionInfo[] getAllDashboardSessionsForUser(AppIdentifier appIdentifier, Main
            main,
                                                                        String userId)
            throws StorageQueryException, TenantOrAppNotFoundException {
        return StorageLayer.getDashboardStorage(appIdentifier, main)
                .getAllSessionsForUserId(appIdentifier, userId);
    }

    private static boolean isDashboardFeatureFlagEnabled(Main main, AppIdentifier appIdentifier)
            throws StorageQueryException {
        try {
            return Arrays.stream(FeatureFlag.getInstance(main, appIdentifier).getEnabledFeatures())
                    .anyMatch(t -> t == EE_FEATURES.DASHBOARD_LOGIN);
        } catch (Exception e) {
            return false;
        }
    }

    private static String createSessionForDashboardUser(AppIdentifier appIdentifier, Main
            main, DashboardUser user)
            throws StorageQueryException, UserIdNotFoundException, TenantOrAppNotFoundException {
        String sessionId = UUID.randomUUID().toString();
        long timeCreated = System.currentTimeMillis();
        long expiry = timeCreated + DASHBOARD_SESSION_DURATION;
        StorageLayer.getDashboardStorage(appIdentifier, main)
                .createNewDashboardUserSession(appIdentifier, user.userId, sessionId, timeCreated,
                        expiry);
        return sessionId;
    }

    public static boolean isValidEmail(String email) {
        // We use the same regex as the backend SDK
        // https://github.com/supertokens/supertokens-node/blob/master/lib/ts/recipe/emailpassword/utils.ts#L250
        String regexPatternForEmail =
                "((^<>()[].,;:@]+(.^<>()[].,;:@]+)*)|(.+))@(([[0-9]{1,3}.[0-9]{1,3}.[0-9]{1,3}" +
                        ".[0-9]{1,3}])|(([a-zA-Z-0-9]+.)+[a-zA-Z]{2,}))$";
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

    @TestOnly
    public static boolean isValidUserSession(Main main, String sessionId)
            throws StorageQueryException, UserSuspendedException {
        try {
            return isValidUserSession(new AppIdentifier(null, null), main, sessionId);
        } catch (TenantOrAppNotFoundException e) {
            throw new IllegalStateException(e);
        }
    }

    public static boolean isValidUserSession(AppIdentifier appIdentifier, Main main, String sessionId)
            throws StorageQueryException, UserSuspendedException, TenantOrAppNotFoundException {
        DashboardSessionInfo sessionInfo = StorageLayer.getDashboardStorage(appIdentifier, main)
                .getSessionInfoWithSessionId(appIdentifier, sessionId);
        if (sessionInfo != null) {
            // check if user is suspended
            if (isUserSuspended(appIdentifier, main, null, sessionInfo.userId)) {
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
