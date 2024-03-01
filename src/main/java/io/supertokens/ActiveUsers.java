package io.supertokens;

import io.supertokens.pluginInterface.Storage;
import io.supertokens.pluginInterface.StorageUtils;
import io.supertokens.pluginInterface.authRecipe.sqlStorage.AuthRecipeSQLStorage;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.exceptions.StorageTransactionLogicException;
import io.supertokens.pluginInterface.multitenancy.AppIdentifier;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.storageLayer.StorageLayer;
import org.jetbrains.annotations.TestOnly;

public class ActiveUsers {

    public static void updateLastActive(AppIdentifier appIdentifier, Storage storage, String userId)
            throws TenantOrAppNotFoundException {
        try {
            StorageUtils.getActiveUsersStorage(storage).updateLastActive(appIdentifier, userId);
        } catch (StorageQueryException ignored) {
        }
    }

    @TestOnly
    public static void updateLastActive(Main main, String userId) {
        try {
            ActiveUsers.updateLastActive(new AppIdentifier(null, null),
                    StorageLayer.getStorage(main), userId);
        } catch (TenantOrAppNotFoundException e) {
            throw new IllegalStateException(e);
        }
    }

    public static int countUsersActiveSince(AppIdentifier appIdentifier, Storage storage, long time)
            throws StorageQueryException, TenantOrAppNotFoundException {
        return StorageUtils.getActiveUsersStorage(storage).countUsersActiveSince(appIdentifier, time);
    }

    @TestOnly
    public static int countUsersActiveSince(Main main, long time)
            throws StorageQueryException, TenantOrAppNotFoundException {
        return countUsersActiveSince(new AppIdentifier(null, null),
                StorageLayer.getStorage(main), time);
    }

    public static void removeActiveUser(AppIdentifier appIdentifier, Storage storage, String userId)
            throws StorageQueryException {
        try {
            ((AuthRecipeSQLStorage) StorageUtils.getActiveUsersStorage(storage)).startTransaction(con -> {
                StorageUtils.getActiveUsersStorage(storage).deleteUserActive_Transaction(con, appIdentifier, userId);
                ((AuthRecipeSQLStorage) StorageUtils.getActiveUsersStorage(storage)).commitTransaction(con);
                return null;
            });

        } catch (StorageTransactionLogicException e) {
            throw new StorageQueryException(e.actualException);
        }
    }
}
