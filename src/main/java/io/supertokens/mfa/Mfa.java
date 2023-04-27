package io.supertokens.mfa;

import io.supertokens.Main;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.mfa.MfaStorage;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifierWithStorage;

public class Mfa {
    public static boolean enableFactor(TenantIdentifierWithStorage tenantIdentifierWithStorage, Main main, String userId, String factorId) throws
            StorageQueryException {
        // TODO: Throw FeatureNotEnabledException if MFA is not enabled.
        MfaStorage mfaStorage = tenantIdentifierWithStorage.getMfaStorage();
        return mfaStorage.enableFactor(tenantIdentifierWithStorage, userId, factorId);
    }

    public static String[] listFactors(TenantIdentifierWithStorage tenantIdentifierWithStorage, Main main, String userId) throws
            StorageQueryException {
        // TODO: Throw FeatureNotEnabledException if MFA is not enabled.
        MfaStorage mfaStorage = tenantIdentifierWithStorage.getMfaStorage();
        return mfaStorage.listFactors(tenantIdentifierWithStorage, userId);
    }

    public static boolean disableFactor(TenantIdentifierWithStorage tenantIdentifierWithStorage, Main main, String userId, String factorId) throws
            StorageQueryException {
        // TODO: Throw FeatureNotEnabledException if MFA is not enabled.
        MfaStorage mfaStorage = tenantIdentifierWithStorage.getMfaStorage();
        return mfaStorage.disableFactor(tenantIdentifierWithStorage, userId, factorId);
    }
}
