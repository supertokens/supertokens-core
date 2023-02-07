package io.supertokens.dashboard;

import java.util.regex.Pattern;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import io.supertokens.Main;
import io.supertokens.emailpassword.PasswordHashing;
import io.supertokens.pluginInterface.dashboard.DashboardUser;
import io.supertokens.pluginInterface.dashboard.exceptions.DuplicateEmailException;
import io.supertokens.pluginInterface.dashboard.exceptions.DuplicateUserIdException;
import io.supertokens.pluginInterface.dashboard.sqlStorage.DashboardSQLStorage;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.exceptions.StorageTransactionLogicException;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.utils.Utils;
import jakarta.annotation.Nullable;

public class Dashboard {
    public static final int MAX_NUMBER_OF_FREE_DASHBOARD_USERS = 1;

    public static void signUpDashboardUser(Main main, String email, String password)
            throws StorageQueryException, DuplicateEmailException {

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
        if (isDashboardFeatureFlagEnabled()) {
            for (int i = 0; i < dashboardUsers.length; i++) {
                JsonObject user = new JsonObject();
                user.addProperty("email", dashboardUsers[i].email);
                user.addProperty("userId", dashboardUsers[i].id);
                user.addProperty("isSuspended", false);
                jsonArrayOfDashboardUsers.add(user);
            }
        } else {
            for (int i = 0; i < dashboardUsers.length; i++) {
                JsonObject user = new JsonObject();
                user.addProperty("email", dashboardUsers[i].email);
                user.addProperty("userId", dashboardUsers[i].id);
                user.addProperty("isSuspended", !((i + 1) <= Dashboard.MAX_NUMBER_OF_FREE_DASHBOARD_USERS));
                jsonArrayOfDashboardUsers.add(user);
            }
        }

        return jsonArrayOfDashboardUsers;
    }

    public static String signInDashboardUser(Main main, String email, String password) throws StorageQueryException {
        DashboardUser user = StorageLayer.getDashboardStorage(main).getDashboardUserByEmail(email);
        if (user != null) {
            String hashedPassword = PasswordHashing.getInstance(main).createHashWithSalt(password);
            if (user.passwordHash.equals(hashedPassword)) {
                // create a new session for the user
                return createSessionForDashboardUser(user);
            }
        }
        return null;
    }

    public static boolean deleteUserWithEmail(Main main, String email) throws StorageQueryException {
        return StorageLayer.getDashboardStorage(main).deleteDashboardUserWithEmail(email);
    }

    public static boolean deleteUserWithUserId(Main main, String userId) throws StorageQueryException {
        return StorageLayer.getDashboardStorage(main).deleteDashboardUserWithUserId(userId);
    }

    public static void updateUsersCredentialsWithEmail(Main main, String email, @Nullable String newEmail,
            @Nullable String newPassword)
            throws StorageQueryException, DuplicateEmailException, StorageTransactionLogicException {
        DashboardSQLStorage storage = StorageLayer.getDashboardStorage(main);
        try {
            storage.startTransaction(transaction -> {
                if (newEmail != null) {
                    try {
                        storage.updateDashboardUsersEmailWithEmail_Transaction(transaction, email, newEmail);
                    } catch (DuplicateEmailException e) {
                        throw new StorageTransactionLogicException(e);
                    }
                }

                if (newPassword != null) {
                    storage.updateDashboardUsersPasswordWithEmail_Transaction(transaction, email, newPassword);
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

        if (!isDashboardFeatureFlagEnabled()) {
            // retrieve current dashboard users
            DashboardUser[] users = StorageLayer.getDashboardStorage(main).getAllDashboardUsers();
            // check if current dashboard users count is under the threshold
            return users.length >= MAX_NUMBER_OF_FREE_DASHBOARD_USERS;
        }
        return true;
    }

    private static boolean isDashboardFeatureFlagEnabled() {
        // TODO: check if the feature is enabled
        return false;
    }

    public static String createSessionForDashboardUser(DashboardUser user) {
        // TODO:
        return "";
    }

    public static boolean isValidEmail(String email) {
        // Regex pattern checks
        String regexPatternForEmail = "^(.+)@(.+)$";
        return patternMatcher(email, regexPatternForEmail);
    }

    public static boolean isStrongPassword(String password) {
        String regexPatternForPassowrd = "(?=.*[A-Z])(?=.*[!@#$&*])(?=.*[0-9])(?=.*[a-z]).{8,}";
        return patternMatcher(password, regexPatternForPassowrd);
    }

    public static boolean isValidDashboardUserSession(String sessionId) {
        // TODO: check that the input sessionId is valid
        return false;
    }

    private static boolean patternMatcher(String input, String pattern) {
        return Pattern.compile(pattern).matcher(input).matches();
    }
}
