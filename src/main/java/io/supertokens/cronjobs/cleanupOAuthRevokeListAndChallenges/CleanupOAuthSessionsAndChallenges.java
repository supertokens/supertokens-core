package io.supertokens.cronjobs.cleanupOAuthRevokeListAndChallenges;

import io.supertokens.Main;
import io.supertokens.cronjobs.CronTask;
import io.supertokens.cronjobs.CronTaskTest;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.pluginInterface.Storage;
import io.supertokens.pluginInterface.StorageUtils;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifier;
import io.supertokens.pluginInterface.oauth.OAuthStorage;

import java.util.List;

public class CleanupOAuthSessionsAndChallenges extends CronTask {

    public static final String RESOURCE_KEY = "io.supertokens.cronjobs.cleanupOAuthRevokeListAndChallenges" +
            ".CleanupOAuthRevokeListAndChallenges";

    private CleanupOAuthSessionsAndChallenges(Main main, List<List<TenantIdentifier>> tenantsInfo) {
        super("CleanupOAuthRevokeList", main, tenantsInfo, true);
    }

    public static CleanupOAuthSessionsAndChallenges init(Main main, List<List<TenantIdentifier>> tenantsInfo) {
        return (CleanupOAuthSessionsAndChallenges) main.getResourceDistributor()
                .setResource(new TenantIdentifier(null, null, null), RESOURCE_KEY,
                        new CleanupOAuthSessionsAndChallenges(main, tenantsInfo));
    }

    @Override
    protected void doTaskPerStorage(Storage storage) throws Exception {
        if (storage.getType() != STORAGE_TYPE.SQL) {
            return;
        }

        OAuthStorage oauthStorage = StorageUtils.getOAuthStorage(storage);
        long monthAgo = System.currentTimeMillis() / 1000 - 31 * 24 * 3600;
        oauthStorage.deleteExpiredOAuthSessions(monthAgo);
        oauthStorage.deleteExpiredOAuthM2MTokens(monthAgo);

        oauthStorage.deleteOAuthLogoutChallengesBefore(System.currentTimeMillis() - 1000 * 60 * 60 * 48); // 48 hours
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
