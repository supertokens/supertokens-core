package io.supertokens.cronjobs.cleanupOAuthRevokeList;

import java.util.List;

import io.supertokens.Main;
import io.supertokens.cronjobs.CronTask;
import io.supertokens.cronjobs.CronTaskTest;
import io.supertokens.pluginInterface.Storage;
import io.supertokens.pluginInterface.StorageUtils;
import io.supertokens.pluginInterface.multitenancy.AppIdentifier;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifier;
import io.supertokens.pluginInterface.oauth.OAuthStorage;
import io.supertokens.storageLayer.StorageLayer;

public class CleanupOAuthRevokeList extends CronTask {

    public static final String RESOURCE_KEY = "io.supertokens.cronjobs.cleanupOAuthRevokeList" +
            ".CleanupOAuthRevokeList";

    private CleanupOAuthRevokeList(Main main, List<List<TenantIdentifier>> tenantsInfo) {
        super("CleanupOAuthRevokeList", main, tenantsInfo, true);
    }

    public static CleanupOAuthRevokeList init(Main main, List<List<TenantIdentifier>> tenantsInfo) {
        return (CleanupOAuthRevokeList) main.getResourceDistributor()
                .setResource(new TenantIdentifier(null, null, null), RESOURCE_KEY,
                        new CleanupOAuthRevokeList(main, tenantsInfo));
    }

    @Override
    protected void doTaskPerApp(AppIdentifier app) throws Exception {
        Storage storage = StorageLayer.getStorage(app.getAsPublicTenantIdentifier(), main);
        OAuthStorage oauthStorage = StorageUtils.getOAuthStorage(storage);
        oauthStorage.cleanUpExpiredAndRevokedTokens(app);
    }

    @Override
    public int getIntervalTimeSeconds() {
        if (Main.isTesting) {
            Integer interval = CronTaskTest.getInstance(main).getIntervalInSeconds(RESOURCE_KEY);
            if (interval != null) {
                return interval;
            }
        }
        // Every 24 hours.
        return 24 * 3600;
    }

    @Override
    public int getInitialWaitTimeSeconds() {
        if (!Main.isTesting) {
            return getIntervalTimeSeconds();
        } else {
            return 0;
        }
    }
}
