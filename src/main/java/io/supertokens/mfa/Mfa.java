package io.supertokens.mfa;

import io.supertokens.Main;
import io.supertokens.featureflag.EE_FEATURES;
import io.supertokens.featureflag.FeatureFlag;
import io.supertokens.featureflag.exceptions.FeatureNotEnabledException;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.mfa.MfaStorage;
import io.supertokens.pluginInterface.multitenancy.AppIdentifier;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifierWithStorage;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;

public class Mfa {
    private static boolean isMfaEnabled(AppIdentifier appIdentifier, Main main)
            throws StorageQueryException, TenantOrAppNotFoundException {
        EE_FEATURES[] features = FeatureFlag.getInstance(main, appIdentifier).getEnabledFeatures();
        for (EE_FEATURES f : features) {
            if (f == EE_FEATURES.MFA) {
                return true;
            }
        }
        return false;
    }

    public static boolean enableFactor(TenantIdentifierWithStorage tenantIdentifierWithStorage, Main main, String userId, String factorId)
            throws
            StorageQueryException, FeatureNotEnabledException, TenantOrAppNotFoundException {
        if (!isMfaEnabled(tenantIdentifierWithStorage.toAppIdentifier(), main)) {
            throw new FeatureNotEnabledException(
                    "TOTP feature is not enabled. Please subscribe to a SuperTokens core license key to enable this " +
                            "feature.");
        }

        MfaStorage mfaStorage = tenantIdentifierWithStorage.getMfaStorage();
        return mfaStorage.enableFactor(tenantIdentifierWithStorage, userId, factorId);
    }

    public static String[] listFactors(TenantIdentifierWithStorage tenantIdentifierWithStorage, Main main, String userId)
            throws
            StorageQueryException, TenantOrAppNotFoundException, FeatureNotEnabledException {
        if (!isMfaEnabled(tenantIdentifierWithStorage.toAppIdentifier(), main)) {
            throw new FeatureNotEnabledException(
                    "TOTP feature is not enabled. Please subscribe to a SuperTokens core license key to enable this " +
                            "feature.");
        }

        MfaStorage mfaStorage = tenantIdentifierWithStorage.getMfaStorage();
        return mfaStorage.listFactors(tenantIdentifierWithStorage, userId);
    }

    public static boolean disableFactor(TenantIdentifierWithStorage tenantIdentifierWithStorage, Main main, String userId, String factorId) throws
            StorageQueryException {
        // No need to check for MFA feature flag here.
        MfaStorage mfaStorage = tenantIdentifierWithStorage.getMfaStorage();
        return mfaStorage.disableFactor(tenantIdentifierWithStorage, userId, factorId);
    }
}
