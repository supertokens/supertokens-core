package io.supertokens.ee;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.auth0.jwt.interfaces.RSAKeyProvider;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import io.supertokens.Main;
import io.supertokens.ProcessState;
import io.supertokens.cronjobs.Cronjobs;
import io.supertokens.cronjobs.telemetry.Telemetry;
import io.supertokens.ee.cronjobs.EELicenseCheck;
import io.supertokens.featureflag.EE_FEATURES;
import io.supertokens.featureflag.exceptions.InvalidLicenseKeyException;
import io.supertokens.featureflag.exceptions.NoLicenseKeyFoundException;
import io.supertokens.httpRequest.HttpRequest;
import io.supertokens.httpRequest.HttpResponseException;
import io.supertokens.output.Logging;
import io.supertokens.pluginInterface.KeyValueInfo;
import io.supertokens.pluginInterface.Storage;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.storageLayer.StorageLayer;
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
    public static final int INTERVAL_BETWEEN_SERVER_SYNC = 1000 * 3600 * 24; // 1 day.
    private static final long INTERVAL_BETWEEN_DB_READS = (long) 1000 * 3600 * 4; // 4 hour.
    public static final String REQUEST_ID = "licensecheck";

    public static final String FEATURE_FLAG_KEY_IN_DB = "FEATURE_FLAG";
    public static final String LICENSE_KEY_IN_DB = "LICENSE_KEY";

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

    @Override
    @TestOnly
    public void updateEnabledFeaturesValueReadFromDbTime(long newTime) {
        this.enabledFeaturesValueReadFromDbTime = newTime;
    }

    @Override
    public void constructor(Main main) {
        this.main = main;
        Cronjobs.addCronjob(main, EELicenseCheck.getInstance(main));
        try {
            this.syncFeatureFlagWithLicenseKey();
        } catch (HttpResponseException | IOException e) {
            Logging.error(main, "API Error during constructor sync", false, e);
            // server request failed. we ignore for now as later on it will sync up anyway.
        } catch (InvalidLicenseKeyException ignored) {
            // the license key that was in the db was invalid. If this error is thrown,
            // it means that the key was removed from the db anyway.. so we can just ignore
            // here.
        } catch (Throwable ignored) {
        }
    }

    @Override
    public EE_FEATURES[] getEnabledFeatures() throws StorageQueryException {
        return this.getEnabledEEFeaturesFromDbOrCache();
    }

    @Override
    public void removeLicenseKeyAndSyncFeatures() throws HttpResponseException, IOException, StorageQueryException {
        this.removeLicenseKeyFromDb();
        try {
            this.syncFeatureFlagWithLicenseKey();
        } catch (InvalidLicenseKeyException ignored) {
            // should never come here, because we are removing the license key.
        }
    }

    @Override
    public void setLicenseKeyAndSyncFeatures(String key)
            throws HttpResponseException, IOException, StorageQueryException, InvalidLicenseKeyException {
        // if the key is not valid, we will not affect the current running of things.
        // This would cause 2 calls to the supertokens server if the key is opaque, but that's OK.
        verifyLicenseKey(key);
        this.setLicenseKeyInDb(key);
        this.syncFeatureFlagWithLicenseKey();
    }

    @Override
    public void syncFeatureFlagWithLicenseKey()
            throws HttpResponseException, IOException, StorageQueryException, InvalidLicenseKeyException {
        Logging.debug(main, "Syncing feature flag with license key");
        String licenseKey;
        try {
            licenseKey = this.getLicenseKeyFromDb();
            this.isLicenseKeyPresent = true;
        } catch (NoLicenseKeyFoundException ex) {
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

    @Override
    public JsonObject getPaidFeatureStats() throws StorageQueryException {
        JsonObject result = new JsonObject();
        EE_FEATURES[] features = getEnabledEEFeaturesFromDbOrCache();
        if (Arrays.stream(features).anyMatch(t -> t == EE_FEATURES.DASHBOARD_LOGIN)) {
            JsonObject stats = new JsonObject();
            int userCount = StorageLayer.getDashboardStorage(main).getAllDashboardUsers().length;
            stats.addProperty("user_count", userCount);
            result.add(EE_FEATURES.DASHBOARD_LOGIN.toString(), stats);
        }
        return result;
    }

    private EE_FEATURES[] verifyLicenseKey(String licenseKey)
            throws StorageQueryException, InvalidLicenseKeyException, HttpResponseException, IOException {
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
        Logging.debug(main, "Decoding JWT license key");
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
            Logging.debug(main, "Invalid license key: " + licenseKey);
            Logging.error(main, "Invalid license key", false, e);
            throw new InvalidLicenseKeyException(e);
        } catch (InvalidKeySpecException | NoSuchAlgorithmException e) {
            // should never come here...
            throw new RuntimeException(e);
        }
    }

    private EE_FEATURES[] doServerCall(String licenseKey)
            throws StorageQueryException, HttpResponseException, IOException, InvalidLicenseKeyException {
        try {
            Logging.debug(main, "Making API call to server with licenseKey: " + licenseKey);
            JsonObject json = new JsonObject();
            KeyValueInfo info = Telemetry.getTelemetryId(main);
            String telemetryId = info == null ? null : info.value;
            if (telemetryId != null) {
                // this can be null if we are using in mem db right now.
                json.addProperty("telemetryId", telemetryId);
            }
            json.addProperty("licenseKey", licenseKey);
            json.addProperty("superTokensVersion", Version.getVersion(main).getCoreVersion());
            json.add("paidFeatureUsageStats", this.getPaidFeatureStats());
            ProcessState.getInstance(main).addState(ProcessState.PROCESS_STATE.LICENSE_KEY_CHECK_NETWORK_CALL, null);
            JsonObject licenseCheckResponse = HttpRequest.sendJsonPOSTRequest(this.main, REQUEST_ID,
                    "https://api.supertokens.io/0/st/license/check",
                    json, 10000, 10000, 0);
            if (licenseCheckResponse.get("status").getAsString().equalsIgnoreCase("OK")) {
                Logging.debug(main, "API returned OK");
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
                Logging.debug(main, "Invalid license key: " + licenseKey);
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

    private void setEnabledEEFeaturesInDb(EE_FEATURES[] features) throws StorageQueryException {
        JsonArray json = new JsonArray();
        Arrays.stream(features).forEach(ee_features -> json.add(new JsonPrimitive(ee_features.toString())));
        Logging.debug(main, "Saving new feature flag in database: " + json);
        StorageLayer.getStorage(main).setKeyValue(FEATURE_FLAG_KEY_IN_DB, new KeyValueInfo(json.toString()));
        this.enabledFeaturesValueReadFromDbTime = System.currentTimeMillis();
        this.enabledFeaturesFromDb = features;
    }

    private EE_FEATURES[] getEnabledEEFeaturesFromDbOrCache()
            throws StorageQueryException {
        if (this.enabledFeaturesValueReadFromDbTime == -1
                || (System.currentTimeMillis()
                - this.enabledFeaturesValueReadFromDbTime > INTERVAL_BETWEEN_DB_READS)) {
            Logging.debug(main, "Reading feature flag from database");
            KeyValueInfo keyValueInfo = StorageLayer.getStorage(main).getKeyValue(FEATURE_FLAG_KEY_IN_DB);
            if (keyValueInfo == null) {
                Logging.debug(main, "No feature flag set in db");
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
            Logging.debug(main, "Returning feature flag from cache");
        }
        return this.enabledFeaturesFromDb;
    }

    private void setLicenseKeyInDb(String key) throws StorageQueryException {
        Logging.debug(main, "Setting license key in db: " + key);
        StorageLayer.getStorage(main).setKeyValue(LICENSE_KEY_IN_DB, new KeyValueInfo(key));
    }

    private void removeLicenseKeyFromDb() throws StorageQueryException {
        Logging.debug(main, "Removing license key from db");
        StorageLayer.getStorage(main)
                .setKeyValue(LICENSE_KEY_IN_DB, new KeyValueInfo(LICENSE_KEY_IN_DB_NOT_PRESENT_VALUE));
    }

    @Override
    public String getLicenseKeyFromDb() throws NoLicenseKeyFoundException, StorageQueryException {
        Logging.debug(main, "Attempting to fetch license key from db");
        KeyValueInfo info = StorageLayer.getStorage(main).getKeyValue(LICENSE_KEY_IN_DB);
        if (info == null || info.value.equals(LICENSE_KEY_IN_DB_NOT_PRESENT_VALUE)) {
            Logging.debug(main, "No license key found in db");
            throw new NoLicenseKeyFoundException();
        }
        Logging.debug(main, "Fetched license key from db: " + info.value);
        return info.value;
    }
}