package io.supertokens.ee.cronjobs;

import io.supertokens.Main;
import io.supertokens.cronjobs.CronTask;
import io.supertokens.cronjobs.CronTaskTest;
import io.supertokens.ee.EEFeatureFlag;
import io.supertokens.featureflag.FeatureFlag;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifier;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;

import java.util.ArrayList;
import java.util.List;

public class EELicenseCheck extends CronTask {

    public static final String RESOURCE_KEY = "io.supertokens.ee.cronjobs.EELicenseCheck";

    private EELicenseCheck(Main main, List<List<TenantIdentifier>> tenants, TenantIdentifier targetTenant) {
        super("EELicenseCheck", main, tenants, targetTenant);
    }

    public static EELicenseCheck getInstance(Main main, TenantIdentifier tenantIdentifier) {
        try {
            return (EELicenseCheck) main.getResourceDistributor()
                    .getResource(tenantIdentifier, RESOURCE_KEY);
        } catch (TenantOrAppNotFoundException e) {
            List<TenantIdentifier> tenants = new ArrayList<>();
            tenants.add(tenantIdentifier);
            List<List<TenantIdentifier>> finalList = new ArrayList<>();
            finalList.add(tenants);
            return (EELicenseCheck) main.getResourceDistributor()
                    .setResource(tenantIdentifier, RESOURCE_KEY,
                            new EELicenseCheck(main, finalList, tenantIdentifier));
        }
    }

    @Override
    protected void doTask(List<TenantIdentifier> tenantIdentifier) throws Exception {
        // this cronjob is for one tenant only (the targetTenant provided in the constructor)
        assert this.targetTenant != null;
        FeatureFlag.getInstance(main, this.targetTenant.toAppIdentifier()).syncFeatureFlagWithLicenseKey();
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
