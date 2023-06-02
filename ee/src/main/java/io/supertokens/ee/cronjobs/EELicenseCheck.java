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

public class EELicenseCheck extends CronTask {

    public static final String RESOURCE_KEY = "io.supertokens.ee.cronjobs.EELicenseCheck";

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
            Integer interval = CronTaskTest.getInstance(main).getIntervalInSeconds(RESOURCE_KEY);
            if (interval != null) {
                return interval;
            }
        }
        return EEFeatureFlag.INTERVAL_BETWEEN_SERVER_SYNC; // We delay by one day cause we attempt a sync on core
        // startup anyway.
    }
}
