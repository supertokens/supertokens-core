package io.supertokens.ee.cronjobs;

import io.supertokens.Main;
import io.supertokens.cronjobs.CronTask;
import io.supertokens.cronjobs.CronTaskTest;
import io.supertokens.ee.EEFeatureFlag;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.featureflag.FeatureFlag;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifier;

import java.util.ArrayList;
import java.util.List;

public class EELicenseCheck extends CronTask {

    public static final String RESOURCE_KEY = "io.supertokens.ee.cronjobs.EELicenseCheck";

    private EELicenseCheck(Main main, List<TenantIdentifier> tenants) {
        super("EELicenseCheck", main, tenants);
    }

    public static EELicenseCheck getInstance(Main main) {
        try {
            return (EELicenseCheck) main.getResourceDistributor()
                    .getResource(new TenantIdentifier(null, null, null), RESOURCE_KEY);
        } catch (TenantOrAppNotFoundException e) {
            List<TenantIdentifier> tenants = new ArrayList<>();
            tenants.add(new TenantIdentifier(null, null, null));
            return (EELicenseCheck) main.getResourceDistributor()
                    .setResource(new TenantIdentifier(null, null, null), RESOURCE_KEY,
                            new EELicenseCheck(main, tenants));
        }
    }

    @Override
    protected void doTask(TenantIdentifier tenantIdentifier) throws Exception {
        FeatureFlag.getInstance(main).syncFeatureFlagWithLicenseKey();
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
