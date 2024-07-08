package io.supertokens.ee;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.auth0.jwt.interfaces.RSAKeyProvider;
import com.google.gson.*;
import io.supertokens.Main;
import io.supertokens.ProcessState;
import io.supertokens.cronjobs.Cronjobs;
import io.supertokens.cronjobs.telemetry.Telemetry;
import io.supertokens.ee.cronjobs.EELicenseCheck;
import io.supertokens.featureflag.EE_FEATURES;
import io.supertokens.featureflag.FeatureFlag;
import io.supertokens.featureflag.exceptions.InvalidLicenseKeyException;
import io.supertokens.featureflag.exceptions.NoLicenseKeyFoundException;
import io.supertokens.httpRequest.HttpRequest;
import io.supertokens.httpRequest.HttpResponseException;
import io.supertokens.multitenancy.Multitenancy;
import io.supertokens.output.Logging;
import io.supertokens.pluginInterface.ActiveUsersStorage;
import io.supertokens.pluginInterface.KeyValueInfo;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.pluginInterface.Storage;
import io.supertokens.pluginInterface.authRecipe.AuthRecipeStorage;
import io.supertokens.pluginInterface.dashboard.sqlStorage.DashboardSQLStorage;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.multitenancy.AppIdentifier;
import io.supertokens.pluginInterface.multitenancy.TenantConfig;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifier;
import io.supertokens.pluginInterface.multitenancy.ThirdPartyConfig;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.pluginInterface.session.sqlStorage.SessionSQLStorage;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.utils.Utils;
import io.supertokens.version.Version;
import org.jetbrains.annotations.TestOnly;

import java.io.IOException;
import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.RSAPublicKeySpec;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;

public class EEFeatureFlag implements io.supertokens.featureflag.EEFeatureFlagInterface {
    public static final int INTERVAL_BETWEEN_SERVER_SYNC = 3600 * 24; // 1 day (in seconds).
    private static final long INTERVAL_BETWEEN_DB_READS = (long) 1000 * 3600 * 4; // 4 hour (in millis).
    public static final String REQUEST_ID = "licensecheck";

    public static final String FEATURE_FLAG_KEY_IN_DB = "FEATURE_FLAG";
    public static final String LICENSE_KEY_IN_DB = "LICENSE_KEY";

    private static List<JsonObject> licenseCheckRequests = new ArrayList<>();

    private static final String[] ENTERPRISE_THIRD_PARTY_IDS = new String[]{
            "google-workspaces",
            "okta",
            "active-directory",
            "boxy-saml",
    };

    private static final String JWT_PUBLIC_KEY_N = "yDzeKQFJMtc4" +
            "-Z4BkLvlHVTEW8DEu31onyslJ2fg48hWYlesBkb2UTLT2t7dZw9CCmqtuyYxxHIQ3iy" +
            "-TkEMKroZzQjnMNmapKNQ8H6bx5h5nGnp_xmHSF" +
            "-4ajF5XWrdVaXi2PDY6cd9LdXNRW6AC6WeXew47Ou_xJt9HSY24bpMVFa2_YwTJVwu0Wq6smu0XaHsd2Q2fDKB_Q05hYCat4FZni897en9j3qwJyU0ajE7wUWADySlKIXwnmWKG85Bh7ZhyXHRjnHskylGOCDBUao-3FObTHpD99sKxpxDKbKy_RB6n_5P_plN_gX0d-X5i1otKiyKoNKc2rSSzDOOaw";
    private static final String JWT_PUBLIC_KEY_E = "AQAB";

    // the license key in the db will be set to this if we are explicitly removing
    // it because we do not have a remove key value function in the storage yet, and this
    // works well enough anyway.
    private static final String LICENSE_KEY_IN_DB_NOT_PRESENT_VALUE = "NOT_PRESENT";

    private Boolean isLicenseKeyPresent = null;

    private long enabledFeaturesValueReadFromDbTime = -1;
    private EE_FEATURES[] enabledFeaturesFromDb = null;

    private Main main;

    private AppIdentifier appIdentifier;

    @Override
    @TestOnly
    public void updateEnabledFeaturesValueReadFromDbTime(long newTime) {
        this.enabledFeaturesValueReadFromDbTime = newTime;
    }

    @Override
    public void constructor(Main main, AppIdentifier appIdentifier) {
        this.main = main;
        this.appIdentifier = appIdentifier;

        // EELicenseCheck.init does not create a new CronTask each time, it creates for the first time and
        // returns the same instance from there on.
        Cronjobs.addCronjob(main, EELicenseCheck.init(main, StorageLayer.getTenantsWithUniqueUserPoolId(main)));

        try {
            this.syncFeatureFlagWithLicenseKey();
        } catch (HttpResponseException | IOException e) {
            Logging.error(main, appIdentifier.getAsPublicTenantIdentifier(), "API Error during constructor sync", false,
                    e);
            // server request failed. we ignore for now as later on it will sync up anyway.
        } catch (InvalidLicenseKeyException ignored) {
            // the license key that was in the db was invalid. If this error is thrown,
            // it means that the key was removed from the db anyway.. so we can just ignore
            // here.
        } catch (Throwable ignored) {
        }
    }

    @Override
    public EE_FEATURES[] getEnabledFeatures() throws StorageQueryException, TenantOrAppNotFoundException {
        return this.getEnabledEEFeaturesFromDbOrCache();
    }

    @Override
    public void removeLicenseKeyAndSyncFeatures()
            throws HttpResponseException, IOException, StorageQueryException, TenantOrAppNotFoundException {
        this.removeLicenseKeyFromDb();
        try {
            this.syncFeatureFlagWithLicenseKey();
        } catch (InvalidLicenseKeyException ignored) {
            // should never come here, because we are removing the license key.
        }
    }

    @Override
    public void setLicenseKeyAndSyncFeatures(String key)
            throws HttpResponseException, IOException, StorageQueryException, InvalidLicenseKeyException,
            TenantOrAppNotFoundException {
        // if the key is not valid, we will not affect the current running of things.
        // This would cause 2 calls to the supertokens server if the key is opaque, but that's OK.
        verifyLicenseKey(key);
        this.setLicenseKeyInDb(key);
        this.syncFeatureFlagWithLicenseKey();
    }

    @Override
    public void syncFeatureFlagWithLicenseKey()
            throws HttpResponseException, IOException, StorageQueryException, InvalidLicenseKeyException,
            TenantOrAppNotFoundException {
        Logging.debug(main, appIdentifier.getAsPublicTenantIdentifier(), "Syncing feature flag with license key");
        String licenseKey;
        try {
            licenseKey = this.getLicenseKeyFromDb();
            this.isLicenseKeyPresent = true;
        } catch (NoLicenseKeyFoundException ex) {
            try {
                licenseKey = this.getRootLicenseKeyFromDb();
                verifyLicenseKey(licenseKey); // also sends paid user stats for the app
            } catch (NoLicenseKeyFoundException | InvalidLicenseKeyException ex2) {
                // follow through below
            }
            this.isLicenseKeyPresent = false;
            this.setEnabledEEFeaturesInDb(new EE_FEATURES[]{});
            return;
        }
        try {
            this.setEnabledEEFeaturesInDb(verifyLicenseKey(licenseKey));
        } catch (InvalidLicenseKeyException e) {
            this.removeLicenseKeyAndSyncFeatures();
            throw e;
        }
    }

    @Override
    @TestOnly
    public Boolean getIsLicenseKeyPresent() {
        return isLicenseKeyPresent;
    }

    private JsonObject getDashboardLoginStats() throws TenantOrAppNotFoundException, StorageQueryException {
        JsonObject stats = new JsonObject();
        int userCount = ((DashboardSQLStorage) StorageLayer.getStorage(this.appIdentifier.getAsPublicTenantIdentifier(),
                main))
                .getAllDashboardUsers(this.appIdentifier).length;
        stats.addProperty("user_count", userCount);
        return stats;
    }

    private boolean isEnterpriseThirdPartyId(String thirdPartyId) {
        for (String enterpriseThirdPartyId : ENTERPRISE_THIRD_PARTY_IDS) {
            if (thirdPartyId.startsWith(enterpriseThirdPartyId)) {
                return true;
            }
        }
        return false;
    }

    private JsonObject getMFAStats() throws StorageQueryException, TenantOrAppNotFoundException {
        // TODO: Active users are present only on public tenant and MFA users may be
        // present on different storages
        JsonObject result = new JsonObject();
        Storage[] storages = StorageLayer.getStoragesForApp(main, this.appIdentifier);

        int totalUserCountWithMoreThanOneLoginMethod = 0;
        int[] maus = new int[31];

        long now = System.currentTimeMillis();

        for (Storage storage : storages) {
            totalUserCountWithMoreThanOneLoginMethod += ((AuthRecipeStorage) storage)
                    .getUsersCountWithMoreThanOneLoginMethodOrTOTPEnabled(this.appIdentifier);

            for (int i = 1; i <= 31; i++) {
                long timestamp = now - (i * 24 * 60 * 60 * 1000L);

                // `maus[i-1]` since i starts from 1
                maus[i - 1] += ((ActiveUsersStorage) storage)
                        .countUsersThatHaveMoreThanOneLoginMethodOrTOTPEnabledAndActiveSince(appIdentifier, timestamp);
            }
        }

        result.addProperty("totalUserCountWithMoreThanOneLoginMethodOrTOTPEnabled",
                totalUserCountWithMoreThanOneLoginMethod);
        result.add("mauWithMoreThanOneLoginMethodOrTOTPEnabled", new Gson().toJsonTree(maus));
        return result;
    }

    private JsonObject getMultiTenancyStats()
            throws TenantOrAppNotFoundException, StorageQueryException {
        JsonObject stats = new JsonObject();

        stats.addProperty("connectionUriDomain", this.appIdentifier.getConnectionUriDomain());
        stats.addProperty("appId", this.appIdentifier.getAppId());

        JsonArray tenantStats = new JsonArray();

        TenantConfig[] tenantConfigs = Multitenancy.getAllTenantsForApp(this.appIdentifier, main);
        for (TenantConfig tenantConfig : tenantConfigs) {
            JsonObject tenantStat = new JsonObject();
            tenantStat.addProperty("tenantId", tenantConfig.tenantIdentifier.getTenantId());

            {
                Storage storage = StorageLayer.getStorage(tenantConfig.tenantIdentifier, main);
                long usersCount = ((AuthRecipeStorage) storage).getUsersCount(tenantConfig.tenantIdentifier, null);
                boolean hasUsersOrSessions = (usersCount > 0);
                hasUsersOrSessions = hasUsersOrSessions ||
                        ((SessionSQLStorage) storage).getNumberOfSessions(tenantConfig.tenantIdentifier) > 0;
                tenantStat.addProperty("usersCount", usersCount);
                tenantStat.addProperty("hasUsersOrSessions", hasUsersOrSessions);
                if (tenantConfig.firstFactors != null) {
                    JsonArray firstFactors = new JsonArray();
                    for (String firstFactor : tenantConfig.firstFactors) {
                        firstFactors.add(new JsonPrimitive(firstFactor));
                    }
                    tenantStat.add("firstFactors", firstFactors);
                }

                if (tenantConfig.requiredSecondaryFactors != null) {
                    JsonArray requiredSecondaryFactors = new JsonArray();
                    for (String requiredSecondaryFactor : tenantConfig.requiredSecondaryFactors) {
                        requiredSecondaryFactors.add(new JsonPrimitive(requiredSecondaryFactor));
                    }
                    tenantStat.add("requiredSecondaryFactors", requiredSecondaryFactors);
                }

                try {
                    tenantStat.addProperty("userPoolId", Utils.hashSHA256(storage.getUserPoolId()));
                } catch (NoSuchAlgorithmException e) {
                    throw new RuntimeException(e); // should not come here
                }
            }
            {
                boolean hasEnterpriseLogin = false;
                if (tenantConfig.thirdPartyConfig.providers != null) {
                    for (ThirdPartyConfig.Provider provider : tenantConfig.thirdPartyConfig.providers) {
                        if (isEnterpriseThirdPartyId(provider.thirdPartyId)) {
                            hasEnterpriseLogin = true;
                            break;
                        }
                    }
                }

                tenantStat.addProperty("hasEnterpriseLogin", hasEnterpriseLogin);
            }

            tenantStats.add(tenantStat);
        }

        stats.add("tenants", tenantStats);

        return stats;
    }

    private JsonObject getAccountLinkingStats() throws StorageQueryException, TenantOrAppNotFoundException {
        JsonObject result = new JsonObject();
        Storage[] storages = StorageLayer.getStoragesForApp(main, this.appIdentifier);
        boolean usesAccountLinking = false;

        for (Storage storage : storages) {
            if (((AuthRecipeStorage) storage).checkIfUsesAccountLinking(this.appIdentifier)) {
                usesAccountLinking = true;
                break;
            }
        }

        result.addProperty("usesAccountLinking", usesAccountLinking);
        if (!usesAccountLinking) {
            result.addProperty("totalUserCountWithMoreThanOneLoginMethod", 0);
            JsonArray mauArray = new JsonArray();
            for (int i = 0; i < 31; i++) {
                mauArray.add(new JsonPrimitive(0));
            }
            result.add("mauWithMoreThanOneLoginMethod", mauArray);
            return result;
        }

        int totalUserCountWithMoreThanOneLoginMethod = 0;
        int[] maus = new int[31];

        long now = System.currentTimeMillis();

        for (Storage storage : storages) {
            totalUserCountWithMoreThanOneLoginMethod += ((AuthRecipeStorage) storage).getUsersCountWithMoreThanOneLoginMethod(
                    this.appIdentifier);

            for (int i = 1; i <= 31; i++) {
                long timestamp = now - (i * 24 * 60 * 60 * 1000L);

                // `maus[i-1]` because i starts from 1
                maus[i - 1] += ((ActiveUsersStorage) storage).countUsersThatHaveMoreThanOneLoginMethodAndActiveSince(
                        appIdentifier, timestamp);
            }
        }

        result.addProperty("totalUserCountWithMoreThanOneLoginMethod", totalUserCountWithMoreThanOneLoginMethod);
        result.add("mauWithMoreThanOneLoginMethod", new Gson().toJsonTree(maus));
        return result;
    }

    private JsonArray getMAUs() throws StorageQueryException, TenantOrAppNotFoundException {
        JsonArray mauArr = new JsonArray();
        long now = System.currentTimeMillis();

        for (int i = 1; i <= 31; i++) {
            long timestamp = now - (i * 24 * 60 * 60 * 1000L);
            ActiveUsersStorage activeUsersStorage = (ActiveUsersStorage) StorageLayer.getStorage(
                    this.appIdentifier.getAsPublicTenantIdentifier(), main);
            int mau = activeUsersStorage.countUsersActiveSince(this.appIdentifier, timestamp);
            mauArr.add(new JsonPrimitive(mau));
        }
        return mauArr;
    }

    @Override
    public JsonObject getPaidFeatureStats() throws StorageQueryException, TenantOrAppNotFoundException {
        JsonObject usageStats = new JsonObject();

        if (StorageLayer.getStorage(this.appIdentifier.getAsPublicTenantIdentifier(), main).getType() !=
                STORAGE_TYPE.SQL) {
            return usageStats;
        }

        EE_FEATURES[] features = getEnabledEEFeaturesFromDbOrCache();

        if (!this.appIdentifier.equals(new AppIdentifier(null, null)) && !Arrays.asList(features)
                .contains(EE_FEATURES.MULTI_TENANCY)) { // Check for multitenancy on the base app
            EE_FEATURES[] baseFeatures = FeatureFlag.getInstance(main, new AppIdentifier(null, null))
                    .getEnabledFeatures();
            for (EE_FEATURES feature : baseFeatures) {
                if (feature == EE_FEATURES.MULTI_TENANCY) {
                    features = Arrays.copyOf(features, features.length + 1);
                    features[features.length - 1] = EE_FEATURES.MULTI_TENANCY;
                }
            }
        }

        for (EE_FEATURES feature : features) {
            if (feature == EE_FEATURES.DASHBOARD_LOGIN) {
                usageStats.add(EE_FEATURES.DASHBOARD_LOGIN.toString(), getDashboardLoginStats());
            }

            if (feature == EE_FEATURES.MFA) {
                usageStats.add(EE_FEATURES.MFA.toString(), getMFAStats());
            }

            if (feature == EE_FEATURES.MULTI_TENANCY) {
                usageStats.add(EE_FEATURES.MULTI_TENANCY.toString(), getMultiTenancyStats());
            }

            if (feature == EE_FEATURES.ACCOUNT_LINKING) {
                usageStats.add(EE_FEATURES.ACCOUNT_LINKING.toString(), getAccountLinkingStats());
            }
        }

        usageStats.add("maus", getMAUs());

        return usageStats;
    }

    private EE_FEATURES[] verifyLicenseKey(String licenseKey)
            throws StorageQueryException, InvalidLicenseKeyException, HttpResponseException, IOException,
            TenantOrAppNotFoundException {
        if (doesLicenseKeyRequireServerQuery(licenseKey)) {
            return doServerCall(licenseKey);
        } else {
            return decodeLicenseKeyToGetFeatures(licenseKey);
        }
    }

    private boolean doesLicenseKeyRequireServerQuery(String licenseKey) {
        return licenseKey.split("\\.").length != 3;
    }

    private EE_FEATURES[] decodeLicenseKeyToGetFeatures(String licenseKey) throws InvalidLicenseKeyException {
        Logging.debug(main, appIdentifier.getAsPublicTenantIdentifier(), "Decoding JWT license key");
        try {
            BigInteger modulus = new BigInteger(1, Base64.getUrlDecoder().decode(JWT_PUBLIC_KEY_N));
            BigInteger exponent = new BigInteger(1, Base64.getUrlDecoder().decode(JWT_PUBLIC_KEY_E));

            RSAPublicKey publicKey = (RSAPublicKey) KeyFactory.getInstance("RSA")
                    .generatePublic(new RSAPublicKeySpec(modulus, exponent));
            Algorithm verificationAlgorithm = Algorithm.RSA256(new RSAKeyProvider() {
                @Override
                public RSAPublicKey getPublicKeyById(String keyId) {
                    return publicKey;
                }

                @Override
                public RSAPrivateKey getPrivateKey() {
                    return null;
                }

                @Override
                public String getPrivateKeyId() {
                    return null;
                }
            });

            JWTVerifier verifier = JWT.require(verificationAlgorithm).ignoreIssuedAt().build();
            DecodedJWT decoded = verifier.verify(licenseKey);
            String[] enabledFeaturesFromClaim = decoded.getClaim("enabledFeatures").asArray(String.class);
            List<EE_FEATURES> enabledFeatures = new ArrayList<>();
            Arrays.stream(enabledFeaturesFromClaim).forEach(featureStr -> {
                EE_FEATURES feature = EE_FEATURES.getEnumFromString(featureStr);
                if (feature != null) { // this check cause maybe the core is of an older version
                    enabledFeatures.add(feature);
                }
            });
            return enabledFeatures.toArray(EE_FEATURES[]::new);
        } catch (JWTVerificationException e) {
            ProcessState.getInstance(main).addState(ProcessState.PROCESS_STATE.INVALID_LICENSE_KEY, null);
            Logging.debug(main, appIdentifier.getAsPublicTenantIdentifier(), "Invalid license key: " + licenseKey);
            Logging.error(main, appIdentifier.getAsPublicTenantIdentifier(), "Invalid license key", false, e);
            throw new InvalidLicenseKeyException(e);
        } catch (InvalidKeySpecException | NoSuchAlgorithmException e) {
            // should never come here...
            throw new RuntimeException(e);
        }
    }

    private EE_FEATURES[] doServerCall(String licenseKey)
            throws StorageQueryException, HttpResponseException, IOException, InvalidLicenseKeyException,
            TenantOrAppNotFoundException {
        try {
            Logging.debug(main, appIdentifier.getAsPublicTenantIdentifier(),
                    "Making API call to server with licenseKey: " + licenseKey);
            JsonObject json = new JsonObject();
            KeyValueInfo info = Telemetry.getTelemetryId(main, this.appIdentifier);
            String telemetryId = info == null ? null : info.value;
            if (telemetryId != null) {
                // this can be null if we are using in mem db right now.
                json.addProperty("telemetryId", telemetryId);
            }
            json.addProperty("licenseKey", licenseKey);
            json.addProperty("superTokensVersion", Version.getVersion(main).getCoreVersion());
            json.add("paidFeatureUsageStats", this.getPaidFeatureStats());
            if (Main.isTesting) {
                licenseCheckRequests.add(json);
            }
            ProcessState.getInstance(main)
                    .addState(ProcessState.PROCESS_STATE.LICENSE_KEY_CHECK_NETWORK_CALL, null, json);
            JsonObject licenseCheckResponse = HttpRequest.sendJsonPOSTRequest(this.main, REQUEST_ID,
                    "https://api.supertokens.io/0/st/license/check",
                    json, 10000, 10000, 0);
            if (licenseCheckResponse.get("status").getAsString().equalsIgnoreCase("OK")) {
                Logging.debug(main, appIdentifier.getAsPublicTenantIdentifier(), "API returned OK");
                JsonArray enabledFeaturesJSON = licenseCheckResponse.getAsJsonArray("enabledFeatures");
                List<EE_FEATURES> enabledFeatures = new ArrayList<>();
                enabledFeaturesJSON.forEach(jsonElement -> {
                    EE_FEATURES feature = EE_FEATURES.getEnumFromString(jsonElement.getAsString());
                    if (feature != null) { // this check cause maybe the core is of an older version
                        enabledFeatures.add(feature);
                    }
                });
                return enabledFeatures.toArray(EE_FEATURES[]::new);
            } else if (licenseCheckResponse.get("status").getAsString().equalsIgnoreCase("INVALID_LICENSE_KEY")) {
                Logging.debug(main, appIdentifier.getAsPublicTenantIdentifier(), "Invalid license key: " + licenseKey);
                ProcessState.getInstance(main).addState(ProcessState.PROCESS_STATE.INVALID_LICENSE_KEY, null);
                throw new InvalidLicenseKeyException();
            }
            throw new RuntimeException("Should never come here");
        } catch (HttpResponseException | IOException e) {
            ProcessState.getInstance(main)
                    .addState(ProcessState.PROCESS_STATE.SERVER_ERROR_DURING_LICENSE_KEY_CHECK_FAIL, e);
            throw e;
        }
    }

    private void setEnabledEEFeaturesInDb(EE_FEATURES[] features)
            throws StorageQueryException, TenantOrAppNotFoundException {
        JsonArray json = new JsonArray();
        Arrays.stream(features).forEach(ee_features -> json.add(new JsonPrimitive(ee_features.toString())));
        Logging.debug(main, appIdentifier.getAsPublicTenantIdentifier(),
                "Saving new feature flag in database: " + json);
        StorageLayer.getStorage(this.appIdentifier.getAsPublicTenantIdentifier(), main)
                .setKeyValue(this.appIdentifier.getAsPublicTenantIdentifier(), FEATURE_FLAG_KEY_IN_DB,
                        new KeyValueInfo(json.toString()));
        this.enabledFeaturesValueReadFromDbTime = System.currentTimeMillis();
        this.enabledFeaturesFromDb = features;
    }

    private EE_FEATURES[] getEnabledEEFeaturesFromDbOrCache()
            throws StorageQueryException, TenantOrAppNotFoundException {
        if (this.enabledFeaturesValueReadFromDbTime == -1
                || (System.currentTimeMillis()
                - this.enabledFeaturesValueReadFromDbTime > INTERVAL_BETWEEN_DB_READS)) {
            Logging.debug(main, appIdentifier.getAsPublicTenantIdentifier(), "Reading feature flag from database");
            KeyValueInfo keyValueInfo = StorageLayer.getStorage(this.appIdentifier.getAsPublicTenantIdentifier(), main)
                    .getKeyValue(this.appIdentifier.getAsPublicTenantIdentifier(), FEATURE_FLAG_KEY_IN_DB);
            if (keyValueInfo == null) {
                Logging.debug(main, appIdentifier.getAsPublicTenantIdentifier(), "No feature flag set in db");
                return new EE_FEATURES[]{};
            }
            JsonArray featuresArrayJson = new JsonParser().parse(keyValueInfo.value).getAsJsonArray();
            List<EE_FEATURES> enabledFeatures = new ArrayList<>();
            for (int i = 0; i < featuresArrayJson.size(); i++) {
                EE_FEATURES feature = EE_FEATURES.getEnumFromString(featuresArrayJson.get(i).getAsString());
                if (feature != null) {
                    enabledFeatures.add(feature);
                }
            }
            this.enabledFeaturesFromDb = enabledFeatures.toArray(EE_FEATURES[]::new);
            this.enabledFeaturesValueReadFromDbTime = System.currentTimeMillis();
        } else {
            Logging.debug(main, appIdentifier.getAsPublicTenantIdentifier(), "Returning feature flag from cache");
        }
        return this.enabledFeaturesFromDb;
    }

    private void setLicenseKeyInDb(String key) throws StorageQueryException, TenantOrAppNotFoundException {
        Logging.debug(main, appIdentifier.getAsPublicTenantIdentifier(), "Setting license key in db: " + key);
        StorageLayer.getStorage(this.appIdentifier.getAsPublicTenantIdentifier(), main)
                .setKeyValue(this.appIdentifier.getAsPublicTenantIdentifier(), LICENSE_KEY_IN_DB,
                        new KeyValueInfo(key));
    }

    private void removeLicenseKeyFromDb() throws StorageQueryException, TenantOrAppNotFoundException {
        Logging.debug(main, appIdentifier.getAsPublicTenantIdentifier(), "Removing license key from db");
        StorageLayer.getStorage(this.appIdentifier.getAsPublicTenantIdentifier(), main)
                .setKeyValue(this.appIdentifier.getAsPublicTenantIdentifier(), LICENSE_KEY_IN_DB,
                        new KeyValueInfo(LICENSE_KEY_IN_DB_NOT_PRESENT_VALUE));
    }

    private String getLicenseKeyInDb(TenantIdentifier tenantIdentifier)
            throws TenantOrAppNotFoundException, StorageQueryException, NoLicenseKeyFoundException {
        Logging.debug(main, tenantIdentifier, "Attempting to fetch license key from db");
        KeyValueInfo info = StorageLayer.getStorage(tenantIdentifier, main)
                .getKeyValue(tenantIdentifier, LICENSE_KEY_IN_DB);
        if (info == null || info.value.equals(LICENSE_KEY_IN_DB_NOT_PRESENT_VALUE)) {
            Logging.debug(main, tenantIdentifier, "No license key found in db");
            throw new NoLicenseKeyFoundException();
        }
        Logging.debug(main, tenantIdentifier, "Fetched license key from db: " + info.value);
        return info.value;
    }

    @Override
    public String getLicenseKeyFromDb()
            throws NoLicenseKeyFoundException, StorageQueryException, TenantOrAppNotFoundException {
        return getLicenseKeyInDb(appIdentifier.getAsPublicTenantIdentifier());
    }

    private String getRootLicenseKeyFromDb()
            throws TenantOrAppNotFoundException, StorageQueryException, NoLicenseKeyFoundException {
        return getLicenseKeyInDb(TenantIdentifier.BASE_TENANT);
    }

    @TestOnly
    public static List<JsonObject> getLicenseCheckRequests() {
        assert (Main.isTesting);
        return licenseCheckRequests;
    }

    @TestOnly
    public static void resetLisenseCheckRequests() {
        licenseCheckRequests = new ArrayList<>();
    }


}