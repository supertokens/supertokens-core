package io.supertokens.cronjobs.deleteExpiredTotpTokens;

import io.supertokens.Main;
import io.supertokens.config.Config;
import io.supertokens.cronjobs.CronTask;
import io.supertokens.cronjobs.CronTaskTest;
import io.supertokens.output.Logging;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifier;
import io.supertokens.pluginInterface.totp.sqlStorage.TOTPSQLStorage;
import io.supertokens.storageLayer.StorageLayer;
import org.jetbrains.annotations.TestOnly;

import java.util.List;

public class DeleteExpiredTotpTokens extends CronTask {

    public static final String RESOURCE_KEY = "io.supertokens.cronjobs.deleteExpiredTotpTokens.DeleteExpiredTotpTokens";

    private DeleteExpiredTotpTokens(Main main, List<List<TenantIdentifier>> tenantsInfo) {
        super("DeleteExpiredTotpTokens", main, tenantsInfo, false);
    }

    public static DeleteExpiredTotpTokens init(Main main,
                                               List<List<TenantIdentifier>> tenantsInfo) {
        return (DeleteExpiredTotpTokens) main.getResourceDistributor()
                .setResource(new TenantIdentifier(null, null, null), RESOURCE_KEY,
                        new DeleteExpiredTotpTokens(main, tenantsInfo));
    }

    @TestOnly
    public static DeleteExpiredTotpTokens getInstance(Main main) {
        return (DeleteExpiredTotpTokens) main.getResourceDistributor().getResource(RESOURCE_KEY);
    }

    @Override
    protected void doTaskPerTenant(TenantIdentifier tenantIdentifier) throws Exception {
        if (StorageLayer.getStorage(tenantIdentifier, this.main).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        TOTPSQLStorage storage = (TOTPSQLStorage) StorageLayer.getStorage(tenantIdentifier, this.main);

        long rateLimitResetInMs =
                Config.getConfig(tenantIdentifier, this.main).getTotpRateLimitCooldownTimeSec() * 1000L;
        long expiredBefore = System.currentTimeMillis() - rateLimitResetInMs;

        // We will only remove expired codes that have been expired for longer
        // than rate limiting duration. This ensures that this DB query
        // doesn't delete totp codes that keep the rate limiting active for
        // the expected cooldown duration.
        int deletedCount = storage.removeExpiredCodes(tenantIdentifier, expiredBefore);
        Logging.debug(this.main, tenantIdentifier,
                "Cron DeleteExpiredTotpTokens deleted " + deletedCount + " expired TOTP codes");
    }

    @Override
    public int getIntervalTimeSeconds() {
        if (Main.isTesting) {
            Integer interval = CronTaskTest.getInstance(main).getIntervalInSeconds(RESOURCE_KEY);
            if (interval != null) {
                return interval;
            }
        }

        return 3600; // every hour
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
