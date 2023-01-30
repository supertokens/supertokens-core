package io.supertokens.ee.cronjobs;

import io.supertokens.Main;
import io.supertokens.ResourceDistributor;
import io.supertokens.cronjobs.CronTask;
import io.supertokens.cronjobs.CronTaskTest;
import io.supertokens.ee.EEFeatureFlag;
import io.supertokens.featureflag.FeatureFlag;
import io.supertokens.storageLayer.StorageLayer;

import java.util.ArrayList;
import java.util.List;

public class EELicenseCheck extends CronTask {

    public static final String RESOURCE_KEY = "io.supertokens.ee.cronjobs.EELicenseCheck";

    private EELicenseCheck(Main main, List<ResourceDistributor.KeyClass> tenants) {
        super("EELicenseCheck", main, tenants);
    }

    public static EELicenseCheck getInstance(Main main) {
        ResourceDistributor.SingletonResource instance = main.getResourceDistributor()
                .getResource(null, null, RESOURCE_KEY);
        if (instance == null) {
            List<ResourceDistributor.KeyClass> tenants = new ArrayList<>();
            tenants.add(new ResourceDistributor.KeyClass(null, null, StorageLayer.RESOURCE_KEY));
            instance = main.getResourceDistributor()
                    .setResource(null, null, RESOURCE_KEY, new EELicenseCheck(main, tenants));
        }
        return (EELicenseCheck) instance;
    }

    @Override
    protected void doTask(String connectionUriDomain, String tenantId) throws Exception {
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
