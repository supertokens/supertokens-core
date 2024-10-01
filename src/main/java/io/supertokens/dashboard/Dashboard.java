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
import io.supertokens.pluginInterface.Storage;
import io.supertokens.pluginInterface.StorageUtils;
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
    public static final int MAX_NUMBER_OF_FREE_DASHBOARD_USERS = 3;
    public static final long DASHBOARD_SESSION_DURATION = 2592000000L; // 30 days in milliseconds

    @TestOnly
    public static DashboardUser signUpDashboardUser(Main main, String email,
                                                    String password)
            throws StorageQueryException, DuplicateEmailException, FeatureNotEnabledException {
        try {
            Storage storage = StorageLayer.getStorage(main);
            return signUpDashboardUser(new AppIdentifier(null, null), storage,
                    main, email, password);
        } catch (TenantOrAppNotFoundException e) {
            throw new IllegalStateException(e);
        }
    }

    public static DashboardUser signUpDashboardUser(AppIdentifier appIdentifier, Storage storage, Main main,
                                                    String email,
                                                    String password)
            throws StorageQueryException, DuplicateEmailException, FeatureNotEnabledException,
            TenantOrAppNotFoundException {

        if (StorageUtils.getDashboardStorage(storage).getDashboardUserByEmail(appIdentifier, email) !=
                null) {
            throw new DuplicateEmailException();
        }

        if (!isDashboardFeatureFlagEnabled(main, appIdentifier)) {
            DashboardUser[] users = StorageUtils.getDashboardStorage(storage)
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
                StorageUtils.getDashboardStorage(storage).createNewDashboardUser(appIdentifier, user);
                return user;
            } catch (DuplicateUserIdException ignored) {
                // we retry with a new userId (while loop)
            }
        }
    }

    @TestOnly
    public static DashboardUser[] getAllDashboardUsers(Main main)
            throws StorageQueryException {
        Storage storage = StorageLayer.getStorage(main);
        return getAllDashboardUsers(new AppIdentifier(null, null), storage, main);
    }

    public static DashboardUser[] getAllDashboardUsers(AppIdentifier appIdentifier, Storage storage, Main main)
            throws StorageQueryException {

        DashboardUser[] dashboardUsers = StorageUtils.getDashboardStorage(storage)
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
            Storage storage = StorageLayer.getStorage(main);
            return signInDashboardUser(new AppIdentifier(null, null), storage,
                    main, email, password);
        } catch (TenantOrAppNotFoundException e) {
            throw new IllegalStateException(e);
        }
    }

    public static String signInDashboardUser(AppIdentifier appIdentifier, Storage storage, Main main,
                                             String email, String password)
            throws StorageQueryException, UserSuspendedException, TenantOrAppNotFoundException {
        DashboardUser user = StorageUtils.getDashboardStorage(storage)
                .getDashboardUserByEmail(appIdentifier, email);
        if (user != null) {
            if (isUserSuspended(appIdentifier, storage, main, email, null)) {
                throw new UserSuspendedException();
            }
            if (PasswordHashing.getInstance(main).verifyPasswordWithHash(appIdentifier, password, user.passwordHash)) {
                // create a new session for the user
                try {
                    return createSessionForDashboardUser(appIdentifier, storage, user);
                } catch (UserIdNotFoundException e) {
                    throw new IllegalStateException(e);
                }
            }
        }
        return null;
    }

    @TestOnly
    public static boolean deleteUserWithUserId(Main main, String userId)
            throws StorageQueryException {
        Storage storage = StorageLayer.getStorage(main);
        return deleteUserWithUserId(new AppIdentifier(null, null), storage, userId);
    }

    public static boolean deleteUserWithUserId(AppIdentifier appIdentifier, Storage storage, String userId)
            throws StorageQueryException {
        return StorageUtils.getDashboardStorage(storage)
                .deleteDashboardUserWithUserId(appIdentifier, userId);
    }

    private static boolean isUserSuspended(AppIdentifier appIdentifier, Storage storage, Main main,
                                           @Nullable String email,
                                           @Nullable String userId)
            throws StorageQueryException {
        if (!isDashboardFeatureFlagEnabled(main, appIdentifier)) {
            DashboardUser[] users = StorageUtils.getDashboardStorage(storage)
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
        Storage storage = StorageLayer.getStorage(main);
        return deleteUserWithEmail(new AppIdentifier(null, null), storage, email);
    }

    public static boolean deleteUserWithEmail(AppIdentifier appIdentifier, Storage storage, String email)
            throws StorageQueryException {
        DashboardUser user = StorageUtils.getDashboardStorage(storage)
                .getDashboardUserByEmail(appIdentifier, email);
        if (user != null) {
            return deleteUserWithUserId(appIdentifier, storage, user.userId);
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
            Storage storage = StorageLayer.getStorage(main);
            return updateUsersCredentialsWithUserId(
                    new AppIdentifier(null, null), storage, main, userId,
                    newEmail, newPassword);
        } catch (TenantOrAppNotFoundException e) {
            throw new IllegalStateException(e);
        }
    }

    public static DashboardUser updateUsersCredentialsWithUserId(AppIdentifier appIdentifier, Storage storage,
                                                                 Main main, String userId,
                                                                 String newEmail,
                                                                 String newPassword)
            throws StorageQueryException, DuplicateEmailException, UserIdNotFoundException,
            StorageTransactionLogicException, TenantOrAppNotFoundException {
        DashboardSQLStorage dashboardStorage = StorageUtils.getDashboardStorage(storage);
        try {
            dashboardStorage.startTransaction(transaction -> {
                if (newEmail != null) {
                    try {
                        dashboardStorage.updateDashboardUsersEmailWithUserId_Transaction(appIdentifier, transaction,
                                userId,
                                newEmail);
                    } catch (DuplicateEmailException | UserIdNotFoundException e) {
                        throw new StorageTransactionLogicException(e);
                    }
                }

                if (newPassword != null) {
                    try {
                        String hashedPassword = PasswordHashing.getInstance(main)
                                .createHashWithSalt(appIdentifier, newPassword);
                        dashboardStorage.updateDashboardUsersPasswordWithUserId_Transaction(appIdentifier, transaction,
                                userId,
                                hashedPassword);
                    } catch (UserIdNotFoundException | TenantOrAppNotFoundException e) {
                        throw new StorageTransactionLogicException(e);
                    }
                }
                dashboardStorage.commitTransaction(transaction);
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
        DashboardSessionInfo[] sessionInfo = Dashboard.getAllDashboardSessionsForUser(appIdentifier, storage, userId);
        for (int i = 0; i < sessionInfo.length; i++) {
            StorageUtils.getDashboardStorage(storage)
                    .revokeSessionWithSessionId(appIdentifier, sessionInfo[i].sessionId);
        }

        return StorageUtils.getDashboardStorage(storage)
                .getDashboardUserByUserId(appIdentifier, userId);
    }

    @TestOnly
    public static DashboardUser getDashboardUserByEmail(Main main, String email)
            throws StorageQueryException {
        Storage storage = StorageLayer.getStorage(main);
        return getDashboardUserByEmail(new AppIdentifier(null, null), storage, email);
    }

    public static DashboardUser getDashboardUserByEmail(AppIdentifier appIdentifier, Storage storage, String email)
            throws StorageQueryException {

        return StorageUtils.getDashboardStorage(storage)
                .getDashboardUserByEmail(appIdentifier, email);
    }

    @TestOnly
    public static boolean revokeSessionWithSessionId(Main main, String sessionId)
            throws StorageQueryException {
        Storage storage = StorageLayer.getStorage(main);
        return revokeSessionWithSessionId(new AppIdentifier(null, null), storage, sessionId);
    }

    public static boolean revokeSessionWithSessionId(AppIdentifier appIdentifier, Storage storage, String sessionId)
            throws StorageQueryException {
        return StorageUtils.getDashboardStorage(storage)
                .revokeSessionWithSessionId(appIdentifier, sessionId);
    }

    @TestOnly
    public static DashboardSessionInfo[] getAllDashboardSessionsForUser(Main main,
                                                                        String userId)
            throws StorageQueryException {
        Storage storage = StorageLayer.getStorage(main);
        return getAllDashboardSessionsForUser(
                new AppIdentifier(null, null), storage, userId);
    }

    public static DashboardSessionInfo[] getAllDashboardSessionsForUser(AppIdentifier appIdentifier, Storage storage,
                                                                        String userId)
            throws StorageQueryException {
        return StorageUtils.getDashboardStorage(storage)
                .getAllSessionsForUserId(appIdentifier, userId);
    }

    private static boolean isDashboardFeatureFlagEnabled(Main main, AppIdentifier appIdentifier) {
        try {
            return Arrays.stream(FeatureFlag.getInstance(main, appIdentifier).getEnabledFeatures())
                    .anyMatch(t -> t == EE_FEATURES.DASHBOARD_LOGIN);
        } catch (Exception e) {
            return false;
        }
    }

    private static String createSessionForDashboardUser(AppIdentifier appIdentifier, Storage storage,
                                                        DashboardUser user)
            throws StorageQueryException, UserIdNotFoundException {
        String sessionId = UUID.randomUUID().toString();
        long timeCreated = System.currentTimeMillis();
        long expiry = timeCreated + DASHBOARD_SESSION_DURATION;
        StorageUtils.getDashboardStorage(storage)
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
        Storage storage = StorageLayer.getStorage(main);
        return isValidUserSession(new AppIdentifier(null, null), storage, main, sessionId);
    }

    public static boolean isValidUserSession(AppIdentifier appIdentifier, Storage storage, Main main, String sessionId)
            throws StorageQueryException, UserSuspendedException {
        DashboardSessionInfo sessionInfo = StorageUtils.getDashboardStorage(storage)
                .getSessionInfoWithSessionId(appIdentifier, sessionId);
        if (sessionInfo != null) {
            // check if user is suspended
            if (isUserSuspended(appIdentifier, storage, main, null, sessionInfo.userId)) {
                throw new UserSuspendedException();
            }
            return true;
        }
        return false;
    }

    public static String getEmailFromSessionId(AppIdentifier appIdentifier, Storage storage, String sessionId)
            throws StorageQueryException {
        DashboardSessionInfo sessionInfo = StorageUtils.getDashboardStorage(storage)
                .getSessionInfoWithSessionId(appIdentifier, sessionId);

        if (sessionInfo != null) {
            String userId = sessionInfo.userId;

            DashboardUser user = StorageUtils.getDashboardStorage(storage)
                    .getDashboardUserByUserId(appIdentifier, userId);

            if (user != null) {
                return user.email;
            }
        }

        return null;
    }

    private static boolean patternMatcher(String input, String pattern) {
        return Pattern.compile(pattern).matcher(input).matches();
    }
}
