package io.supertokens.ee.cronjobs;

import io.supertokens.Main;
import io.supertokens.cronjobs.CronTask;
import io.supertokens.cronjobs.CronTaskTest;
import io.supertokens.cronjobs.Cronjobs;
import io.supertokens.cronjobs.deleteExpiredAccessTokenSigningKeys.DeleteExpiredAccessTokenSigningKeys;
import io.supertokens.ee.EEFeatureFlag;
import io.supertokens.featureflag.FeatureFlag;
import io.supertokens.pluginInterface.multitenancy.AppIdentifier;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifier;
import io.supertokens.storageLayer.StorageLayer;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class EELicenseCheck extends CronTask {

    public static final String RESOURCE_KEY = "io.supertokens.ee.cronjobs.EELicenseCheck";

    // Initial delay before the first (and now the only startup) license sync: 30s + up to 30s of jitter,
    // i.e. a value in [30, 60) seconds. See getInitialWaitTimeSeconds() for why it is jittered.
    static final int INITIAL_WAIT_TIME_SECONDS_BASE = 30;
    static final int INITIAL_WAIT_TIME_SECONDS_JITTER = 30;

    private EELicenseCheck(Main main, List<List<TenantIdentifier>> tenantsInfo) {
        super("EELicenseCheck", main, tenantsInfo, true);
    }

    public static EELicenseCheck init(Main main, List<List<TenantIdentifier>> tenantsInfo) {
        return (EELicenseCheck) main.getResourceDistributor()
                .setResource(new TenantIdentifier(null, null, null), RESOURCE_KEY,
                        new EELicenseCheck(main, tenantsInfo));
    }

    @Override
    protected void doTaskPerApp(AppIdentifier app) throws Exception {
        FeatureFlag.getInstance(main, app).syncFeatureFlagWithLicenseKey();
    }

    @Override
    public int getIntervalTimeSeconds() {
        if (Main.isTesting) {
            Integer interval = CronTaskTest.getInstance(main).getIntervalInSeconds(RESOURCE_KEY);
            if (interval != null) {
                return interval;
            }
        }
        return EEFeatureFlag.INTERVAL_BETWEEN_SERVER_SYNC;
    }

    @Override
    public int getInitialWaitTimeSeconds() {
        if (Main.isTesting) {
            // Prefer the dedicated initial-wait seam (as BackfillReservationTables / ProcessBulkImportUsers /
            // RollupUserLastActive do) so a test can pin the first-run delay without also collapsing the daily
            // interval (which would make the cron loop every few seconds, each loop a live license-server call).
            Integer waitTime = CronTaskTest.getInstance(main).getInitialWaitTimeInSeconds(RESOURCE_KEY);
            if (waitTime != null) {
                return waitTime;
            }
            // Backwards-compatible fallback: tests that only pin the interval still shorten the initial delay
            // (many existing EE tests rely on setIntervalInSeconds(RESOURCE_KEY, 1) to fire the first sync fast).
            Integer interval = CronTaskTest.getInstance(main).getIntervalInSeconds(RESOURCE_KEY);
            if (interval != null) {
                return interval;
            }
        }
        // The license sync used to run synchronously in EEFeatureFlag's constructor on core startup, so this
        // cron delayed its first run by a full day. It now owns the initial sync too, hence a short delay. It
        // is jittered so a fleet of cores restarting together does not stampede the license server all at once.
        // The daily interval (getIntervalTimeSeconds) is unchanged.
        return INITIAL_WAIT_TIME_SECONDS_BASE + ThreadLocalRandom.current().nextInt(INITIAL_WAIT_TIME_SECONDS_JITTER);
    }
}
