package io.supertokens.cronjobs.cleanupSAMLCodes;

import java.util.List;

import io.supertokens.Main;
import io.supertokens.cronjobs.CronTask;
import io.supertokens.cronjobs.CronTaskTest;
import io.supertokens.pluginInterface.Storage;
import io.supertokens.pluginInterface.StorageUtils;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifier;
import io.supertokens.pluginInterface.saml.SAMLStorage;

public class CleanupSAMLCodes extends CronTask {
    public static final String RESOURCE_KEY = "io.supertokens.cronjobs.cleanupSAMLCodes" +
            ".CleanupSAMLCodes";
    
    private CleanupSAMLCodes(Main main, List<List<TenantIdentifier>> tenantsInfo) {
        super("CleanupOAuthSessionsAndChallenges", main, tenantsInfo, false);
    }

    public static CleanupSAMLCodes init(Main main, List<List<TenantIdentifier>> tenantsInfo) {
        return (CleanupSAMLCodes) main.getResourceDistributor()
                .setResource(new TenantIdentifier(null, null, null), RESOURCE_KEY,
                        new CleanupSAMLCodes(main, tenantsInfo));
    }

    @Override
    protected void doTaskPerStorage(Storage storage) throws Exception {
        SAMLStorage samlStorage = StorageUtils.getSAMLStorage(storage);
        samlStorage.removeExpiredSAMLCodesAndRelayStates();
    }

    @Override
    public int getIntervalTimeSeconds() {
        if (Main.isTesting) {
            Integer interval = CronTaskTest.getInstance(main).getIntervalInSeconds(RESOURCE_KEY);
            if (interval != null) {
                return interval;
            }
        }
        // Every hour
        return 3600;
    }

    @Override
    public int getInitialWaitTimeSeconds() {
        if (!Main.isTesting) {
            return getIntervalTimeSeconds();
        } else {
            return 3600;
        }
    }
}
