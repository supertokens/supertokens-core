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
}
