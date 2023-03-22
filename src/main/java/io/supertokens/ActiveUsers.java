package io.supertokens;

import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.storageLayer.StorageLayer;

public class ActiveUsers {

    public static void updateLastActive(Main main, String userId) {
        try {
            StorageLayer.getActiveUsersStorage(main).updateLastActive(userId);
        } catch (StorageQueryException ignored) {
        }
    }

    public static int countUsersActiveSince(Main main, long time) throws StorageQueryException {
        return StorageLayer.getActiveUsersStorage(main).countUsersActiveSince(time);
    }
}
