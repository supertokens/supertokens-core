package io.supertokens.mfa;

import io.supertokens.Main;
import io.supertokens.featureflag.EE_FEATURES;
import io.supertokens.featureflag.FeatureFlag;
import io.supertokens.featureflag.exceptions.FeatureNotEnabledException;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.multitenancy.AppIdentifier;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;

public class Mfa {
    public static void checkForMFAFeature(AppIdentifier appIdentifier, Main main)
            throws StorageQueryException, TenantOrAppNotFoundException, FeatureNotEnabledException {
        EE_FEATURES[] features = FeatureFlag.getInstance(main, appIdentifier).getEnabledFeatures();
        for (EE_FEATURES f : features) {
            if (f == EE_FEATURES.MFA) {
                return;
            }
        }
        throw new FeatureNotEnabledException(
                "MFA feature is not enabled. Please subscribe to a SuperTokens core license key to enable this " +
                        "feature.");
    }
}
