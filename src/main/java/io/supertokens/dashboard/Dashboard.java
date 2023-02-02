package io.supertokens.dashboard;

import io.supertokens.Main;
import io.supertokens.emailpassword.PasswordHashing;
import io.supertokens.pluginInterface.dashboard.DashboardUser;
import io.supertokens.pluginInterface.emailpassword.exceptions.DuplicateEmailException;
import io.supertokens.pluginInterface.emailpassword.exceptions.DuplicateUserIdException;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.utils.Utils;

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
        if (user != null){
            String hashedPassword = PasswordHashing.getInstance(main).createHashWithSalt(password);
            if(user.passwordHash.equals(hashedPassword)){
                // TODO: generate JWT
                return "JWT";
            }
        }
        return null;
    }
    

    public static void updateUsersCredentialsWithEmail(Main main, String email, String newEmail, String newPassword) throws StorageQueryException, DuplicateEmailException {
        StorageLayer.getDashboardStorage(main).updateDashboardUserWithEmail(email, newEmail, newPassword);
    }

    public static void updateUsersCredentialsWithUserId(Main main, String userId, String newEmail, String newPassword) throws StorageQueryException, DuplicateEmailException {
        StorageLayer.getDashboardStorage(main).updateDashboardUserWithUserId(userId, newEmail, newPassword);
    }

    public static boolean isDashboardFeatureFlagEnabled(){
        // TODO: check that dashboard is enabled in the feature flag

        return false;
    }
}
