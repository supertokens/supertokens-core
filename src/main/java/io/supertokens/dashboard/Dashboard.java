package io.supertokens.dashboard;

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
                DashboardUser user = new DashboardUser(userId, email, hashedPassword, timeJoined, false);
                StorageLayer.getDashboardStorage(main).createNewDashboardUser(user);
                return;
            } catch (DuplicateUserIdException ignored) {
                // we retry with a new userId (while loop)
            }
        }
    }

    public static DashboardUser[] getAllDashboardUsers(Main main) throws StorageQueryException {

        return StorageLayer.getDashboardStorage(main).getAllDashboardUsers();
    }

    public static String signInDashboardUser(Main main, String email, String password) throws StorageQueryException {
        DashboardUser user = StorageLayer.getDashboardStorage(main).getDashboardUserByEmail(email);
        if (user != null) {
            String hashedPassword = PasswordHashing.getInstance(main).createHashWithSalt(password);
            if (user.passwordHash.equals(hashedPassword)) {
                // TODO: generate JWT
                return "JWT";
            }
        }
        return null;
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
                        storage.updateDashboardUsersEmailWithUserId_Transaction(transaction, userId, newEmail);;
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

    public static boolean isDashboardFeatureFlagEnabled() {
        // TODO: check that dashboard is enabled in the feature flag
        return false;
    }

    public static boolean isValidEmail(String email) {
        // TODO: check that input email is in valid format

        return false;
    }

    public static boolean isStrongPassword(String password) {
        // TODO: check that input password is strong
        return false;
    }
}
