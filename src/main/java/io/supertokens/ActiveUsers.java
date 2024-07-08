package io.supertokens;

import io.supertokens.pluginInterface.ActiveUsersSQLStorage;
import io.supertokens.pluginInterface.Storage;
import io.supertokens.pluginInterface.StorageUtils;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.exceptions.StorageTransactionLogicException;
import io.supertokens.pluginInterface.multitenancy.AppIdentifier;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.storageLayer.StorageLayer;
import org.jetbrains.annotations.TestOnly;

public class ActiveUsers {

    public static void updateLastActive(AppIdentifier appIdentifier, Main main, String userId)
            throws TenantOrAppNotFoundException {
        Storage storage = StorageLayer.getStorage(appIdentifier.getAsPublicTenantIdentifier(), main);
        try {
            StorageUtils.getActiveUsersStorage(storage).updateLastActive(appIdentifier, userId);
        } catch (StorageQueryException ignored) {
        }
    }

    @TestOnly
    public static void updateLastActive(Main main, String userId) {
        try {
            ActiveUsers.updateLastActive(new AppIdentifier(null, null),
                    main, userId);
        } catch (TenantOrAppNotFoundException e) {
            throw new IllegalStateException(e);
        }
    }

    public static int countUsersActiveSince(Main main, AppIdentifier appIdentifier, long time)
            throws StorageQueryException, TenantOrAppNotFoundException {
        Storage storage = StorageLayer.getStorage(appIdentifier.getAsPublicTenantIdentifier(), main);
        return StorageUtils.getActiveUsersStorage(storage).countUsersActiveSince(appIdentifier, time);
    }

    public static void updateLastActiveAfterLinking(Main main, AppIdentifier appIdentifier, String primaryUserId,
                                                    String recipeUserId)
            throws StorageQueryException, TenantOrAppNotFoundException, StorageTransactionLogicException {
        ActiveUsersSQLStorage activeUsersStorage =
                (ActiveUsersSQLStorage) StorageUtils.getActiveUsersStorage(
                        StorageLayer.getStorage(appIdentifier.getAsPublicTenantIdentifier(), main));

        activeUsersStorage.startTransaction(con -> {
            activeUsersStorage.deleteUserActive_Transaction(con, appIdentifier, recipeUserId);
            return null;
        });

        updateLastActive(appIdentifier, main, primaryUserId);
    }

    @TestOnly
    public static int countUsersActiveSince(Main main, long time)
            throws StorageQueryException, TenantOrAppNotFoundException {
        return countUsersActiveSince(main, new AppIdentifier(null, null), time);
    }
}
