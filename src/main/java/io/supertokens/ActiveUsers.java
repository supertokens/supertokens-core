package io.supertokens;

import io.supertokens.pluginInterface.authRecipe.sqlStorage.AuthRecipeSQLStorage;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.exceptions.StorageTransactionLogicException;
import io.supertokens.pluginInterface.multitenancy.AppIdentifierWithStorage;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.storageLayer.StorageLayer;
import org.jetbrains.annotations.TestOnly;

public class ActiveUsers {

    public static void updateLastActive(AppIdentifierWithStorage appIdentifierWithStorage, Main main, String userId)
            throws TenantOrAppNotFoundException {
        try {
            appIdentifierWithStorage.getActiveUsersStorage().updateLastActive(appIdentifierWithStorage, userId);
        } catch (StorageQueryException ignored) {
        }
    }

    @TestOnly
    public static void updateLastActive(Main main, String userId) {
        try {
            ActiveUsers.updateLastActive(new AppIdentifierWithStorage(null, null, StorageLayer.getStorage(main)), main,
                    userId);
        } catch (TenantOrAppNotFoundException e) {
            throw new IllegalStateException(e);
        }
    }

    public static int countUsersActiveSince(AppIdentifierWithStorage appIdentifierWithStorage, Main main, long time)
            throws StorageQueryException, TenantOrAppNotFoundException {
        return appIdentifierWithStorage.getActiveUsersStorage().countUsersActiveSince(appIdentifierWithStorage, time);
    }

    @TestOnly
    public static int countUsersActiveSince(Main main, long time)
            throws StorageQueryException, TenantOrAppNotFoundException {
        return countUsersActiveSince(new AppIdentifierWithStorage(null, null, StorageLayer.getStorage(main)), main,
                time);
    }

    public static void removeActiveUser(AppIdentifierWithStorage appIdentifierWithStorage, String userId)
            throws StorageQueryException {
        try {
            ((AuthRecipeSQLStorage) appIdentifierWithStorage.getActiveUsersStorage()).startTransaction(con -> {
                appIdentifierWithStorage.getActiveUsersStorage().deleteUserActive_Transaction(con, appIdentifierWithStorage, userId);
                ((AuthRecipeSQLStorage) appIdentifierWithStorage.getActiveUsersStorage()).commitTransaction(con);
                return null;
            });

        } catch (StorageTransactionLogicException e) {
            throw new StorageQueryException(e.actualException);
        }
    }
}
