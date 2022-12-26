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
import io.supertokens.ee.httpRequest.HttpRequest;
import io.supertokens.ee.httpRequest.HttpResponseException;
import io.supertokens.pluginInterface.KeyValueInfo;
import io.supertokens.pluginInterface.Storage;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;

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

/*
 * Different scenarios:
 *
 * 1) No license key added to db
 *  - good case:
 *      - on init -> sets empty array as features in db, isLicenseKeyPresent = false
 *      - in API -> returns empty array
 *      - in cronjob -> same as on init
 *      - remove license key called -> same as on init
 *      - on setting license key -> set license key in db, query API and set features in db -> go to (2) License key
 * in db
 *  - database not working case:
 *      - on init -> core stops.
 *      - in API -> return empty array
 *      - in cronjob -> error will be thrown in cronjob -> cronjob will ignore it
 *      - remove license key called -> error thrown in API
 *      - on setting license key -> error thrown in API
 *  - API not working case
 *      - on init -> Not applicable since no license key present
 *      - in API -> Not applicable since no license key present
 *      - in cronjob -> Not applicable since no license key present
 *      - remove license key called -> Not applicable since no license key present
 *      - on setting license key -> license key will be set in db, but enabled features will not be -> results in API
 *  error. -> calling getEnabledFeatures will lead to returning the older enabled features from the db, or if nothing
 *  existed, then no features.
 *
 * 2) License key in db
 *  - good case:
 *      - on init -> query API and set features in db
 *      - in API ->
 *          - if last synced attempt more than 24 hours ago
 *              - query API and set feature in db
 *              - return those features
 *          - if last sync within 24 hours
 *              - if features in db last read before 4 hours
 *                  - query db and return those features
 *              - else return feature list from memory
 *      - in cronjob -> same as init
 *      - remove license key called -> same as init
 *      - on setting license key -> same as init
 *  - database not working case
 *      - on init -> core not started
 *      - in API ->
 *          - no API call is made since license key fetching from db would fail
 *          - if last read time of features from db is more than 4 hours ago -> throw an error
 *          - else return enabled features from cache (memory)
 *      - in cronjob -> error will be thrown in cronjob -> cronjob will ignore it
 *      - remove license key called -> error thrown in API
 *      - on setting license key -> error thrown in API
 *  - API not working case
 *      - on init -> error ignored.
 *      - in API ->
 *          - if last synced attempt more than 24 hours ago
 *              - API call made -> fails -> read features enabled from db or cache
 *          - else read features enabled from db or cache
 *      - in cronjob -> call API -> fail -> cronjob ignores errors
 *      - remove license key called -> cleared from db, no API called, enabled features set to empty in db and in memory
 *      - on setting license key -> key set in db, API call failed resulting in API error -> calling
 * getEnabledFeatures will return the older enabled features from the db, or if nothing existed, then no features.
 * */

public class EEFeatureFlag {
    public static final int INTERVAL_BETWEEN_SERVER_SYNC = 1000 * 3600 * 24; // 1 day.
    private static final long INTERVAL_BETWEEN_DB_READS = (long) 1000 * 3600 * 4; // 4 hour.

    private static final String FEATURE_FLAG_KEY_IN_DB = "FEATURE_FLAG";
    private static final String LICENSE_KEY_IN_DB = "LICENSE_KEY";

    private static final String JWT_PUBLIC_KEY_N = "TODO";
    private static final String JWT_PUBLIC_KEY_E = "TODO";

    // the license key in the db will be set to this if we are explicitly removing
    // it because we do not have a remove key value function in the storage yet, and this
    // works well enough anyway.
    private static final String LICENSE_KEY_IN_DB_NOT_PRESENT_VALUE = "NOT_PRESENT";

    private Boolean isLicenseKeyPresent = null;

    private long enabledFeaturesValueReadFromDbTime = -1;
    private EE_FEATURES[] enabledFeaturesFromDb = null;

    private Storage storage;
    private String coreVersion;
    private Logging logger;
    private Telemetry getTelemetryId;
    private PaidFeatureStats paidFeatureStats;

    public interface Telemetry {
        String get() throws StorageQueryException;
    }

    public interface PaidFeatureStats {
        JsonObject get() throws StorageQueryException;
    }

    public void constructor(Storage storage, String coreVersion, Logging logger,
                            Telemetry getTelemetryId, PaidFeatureStats paidFeatureStats) throws StorageQueryException {
        this.coreVersion = coreVersion;
        this.storage = storage;
        this.logger = logger;
        this.getTelemetryId = getTelemetryId;
        this.paidFeatureStats = paidFeatureStats;
        try {
            this.forceSyncFeatureFlagWithLicenseKey();
        } catch (HttpResponseException | IOException e) {
            this.logger.error("API Error during constructor sync", false, e);
            // server request failed. we ignore for now as later on it will sync up anyway.
        } catch (InvalidLicenseKeyException ignored) {
            // the license key that was in the db was invalid. If this error is thrown,
            // it means that the key was removed from the db anyway.. so we can just ignore here.
        }
        // any other exception (like db related errors) will result in core not starting
    }

    public EE_FEATURES[] getEnabledFeatures() throws StorageQueryException {
        if (!this.isLicenseKeyPresent) {
            return new EE_FEATURES[]{};
        }

        try {
            return this.getEnabledEEFeaturesFromDbOrCache();
        } catch (EnabledFeaturesNotSetInDbException e) {
            // Never synced with SuperTokens for some reason.
            return new EE_FEATURES[]{};
        }
    }

    public void removeLicenseKeyAndSyncFeatures() throws HttpResponseException, IOException, StorageQueryException {
        this.removeLicenseKeyFromDb();
        try {
            this.forceSyncFeatureFlagWithLicenseKey();
        } catch (InvalidLicenseKeyException ignored) {
            // should never come here, because we are removing the license key.
        }
    }

    public void setLicenseKeyAndSyncFeatures(String key)
            throws HttpResponseException, IOException, StorageQueryException, InvalidLicenseKeyException {
        this.setLicenseKeyInDb(key);
        this.forceSyncFeatureFlagWithLicenseKey();
    }

    public void forceSyncFeatureFlagWithLicenseKey()
            throws HttpResponseException, IOException, StorageQueryException, InvalidLicenseKeyException {
        this.syncFeatureFlagWithLicenseKeyIfRequired();
    }

    private void syncFeatureFlagWithLicenseKeyIfRequired()
            throws HttpResponseException, IOException, StorageQueryException, InvalidLicenseKeyException {
        this.logger.debug("Syncing feature flag with license key");
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
            if (doesLicenseKeyRequireServerQuery(licenseKey)) {
                this.setEnabledEEFeaturesInDb(doServerCall(licenseKey));
            } else {
                this.setEnabledEEFeaturesInDb(decodeLicenseKeyToGetFeatures(licenseKey));
            }
        } catch (InvalidLicenseKeyException e) {
            this.removeLicenseKeyAndSyncFeatures();
            throw e;
        }
    }

    private boolean doesLicenseKeyRequireServerQuery(String licenseKey) {
        return licenseKey.split("\\.").length != 3;
    }

    private EE_FEATURES[] decodeLicenseKeyToGetFeatures(String licenseKey) throws InvalidLicenseKeyException {
        this.logger.debug("Decoding JWT license key");
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
            // TODO: add test for making sure JWT validation is correct:
            //  - should check for expiry in the claim is present in it
            //  - if exp is not in the claim, then should assume infinite lifetime
            DecodedJWT decoded = verifier.verify(licenseKey);
            String enabled_features = decoded.getClaim("enabled_features").asString();
            JsonArray enabledFeaturesJSON = new JsonParser().parse(enabled_features).getAsJsonArray();
            List<EE_FEATURES> enabledFeatures = new ArrayList<>();
            enabledFeaturesJSON.forEach(jsonElement -> {
                EE_FEATURES feature = EE_FEATURES.getEnumFromString(jsonElement.toString());
                if (feature != null) { // this check cause maybe the core is of an older version
                    enabledFeatures.add(feature);
                }
            });
            return enabledFeatures.toArray(EE_FEATURES[]::new);
        } catch (JWTVerificationException e) {
            this.logger.debug("Invalid license key: " + licenseKey);
            this.logger.error("Invalid license key", false, e);
            throw new InvalidLicenseKeyException(e);
        } catch (InvalidKeySpecException | NoSuchAlgorithmException e) {
            // should never come here...
            throw new RuntimeException(e);
        }
    }

    private EE_FEATURES[] doServerCall(String licenseKey)
            throws StorageQueryException, HttpResponseException, IOException, InvalidLicenseKeyException {
        this.logger.debug("Making API call to server with licenseKey: " + licenseKey);
        JsonObject json = new JsonObject();
        String telemetryId = this.getTelemetryId.get();
        if (telemetryId != null) {
            // this can be null if we are using in mem db right now.
            json.addProperty("telemetryId", telemetryId);
        }
        json.addProperty("licenseKey", licenseKey);
        json.addProperty("superTokensVersion", this.coreVersion);
        json.add("paidFeatureUsageStats", this.paidFeatureStats.get());
        JsonObject licenseCheckResponse = HttpRequest.sendJsonPOSTRequest(
                "https://api.supertokens.io/0/st/license/check",
                json, 10000, 10000, 0);
        if (licenseCheckResponse.get("status").getAsString().equalsIgnoreCase("OK")) {
            this.logger.debug("API returned OK");
            JsonArray enabledFeaturesJSON = licenseCheckResponse.getAsJsonArray("enabled_features");
            List<EE_FEATURES> enabledFeatures = new ArrayList<>();
            enabledFeaturesJSON.forEach(jsonElement -> {
                // TODO: think about changes in features over versions of the core and APIs
                EE_FEATURES feature = EE_FEATURES.getEnumFromString(jsonElement.toString());
                if (feature != null) { // this check cause maybe the core is of an older version
                    enabledFeatures.add(feature);
                }
            });
            return enabledFeatures.toArray(EE_FEATURES[]::new);
        } else if (licenseCheckResponse.get("status").getAsString().equalsIgnoreCase("INVALID_LICENSE_KEY")) {
            this.logger.debug("Invalid license key: " + licenseKey);
            throw new InvalidLicenseKeyException();
        }
        throw new RuntimeException("Should never come here");
    }

    private void setEnabledEEFeaturesInDb(EE_FEATURES[] features) throws StorageQueryException {
        JsonArray json = new JsonArray();
        Arrays.stream(features).forEach(ee_features -> json.add(new JsonPrimitive(ee_features.toString())));
        this.logger.debug("Saving new feature flag in database: " + json);
        storage.setKeyValue(FEATURE_FLAG_KEY_IN_DB, new KeyValueInfo(json.toString()));
        this.enabledFeaturesValueReadFromDbTime = System.currentTimeMillis();
        this.enabledFeaturesFromDb = features;
    }

    private EE_FEATURES[] getEnabledEEFeaturesFromDbOrCache()
            throws EnabledFeaturesNotSetInDbException, StorageQueryException {
        try {
            if (this.enabledFeaturesValueReadFromDbTime == -1
                    || (System.currentTimeMillis() - this.enabledFeaturesValueReadFromDbTime >
                    INTERVAL_BETWEEN_DB_READS)) {
                this.logger.debug("Reading feature flag from database");
                KeyValueInfo keyValueInfo = storage.getKeyValue(FEATURE_FLAG_KEY_IN_DB);
                if (keyValueInfo == null) {
                    throw new EnabledFeaturesNotSetInDbException();
                }
                JsonArray featuresArrayJson = new JsonParser().parse(keyValueInfo.value).getAsJsonArray();
                EE_FEATURES[] features = new EE_FEATURES[featuresArrayJson.size()];
                for (int i = 0; i < featuresArrayJson.size(); i++) {
                    features[i] = EE_FEATURES.valueOf(featuresArrayJson.get(i).getAsString());
                }
                this.enabledFeaturesFromDb = features;
                this.enabledFeaturesValueReadFromDbTime = System.currentTimeMillis();
            } else {
                this.logger.debug("Returning feature flag from cache");
            }
            return this.enabledFeaturesFromDb;
        } catch (EnabledFeaturesNotSetInDbException e) {
            this.logger.debug("No feature flag set in db");
            throw e;
        }
    }

    private void setLicenseKeyInDb(String key) throws StorageQueryException {
        this.logger.debug("Setting license key in db: " + key);
        storage.setKeyValue(LICENSE_KEY_IN_DB, new KeyValueInfo(key));
    }

    private void removeLicenseKeyFromDb() throws StorageQueryException {
        this.logger.debug("Removing license key from db");
        storage.setKeyValue(LICENSE_KEY_IN_DB, new KeyValueInfo(LICENSE_KEY_IN_DB_NOT_PRESENT_VALUE));
    }

    public String getLicenseKeyFromDb() throws NoLicenseKeyFoundException, StorageQueryException {
        this.logger.debug("Attempting to fetch license key from db");
        try {
            KeyValueInfo info = storage.getKeyValue(LICENSE_KEY_IN_DB);
            if (info == null || info.value.equals(LICENSE_KEY_IN_DB_NOT_PRESENT_VALUE)) {
                throw new NoLicenseKeyFoundException();
            }
            this.logger.debug("Fetched license key from db: " + info.value);
            return info.value;
        } catch (NoLicenseKeyFoundException e) {
            this.logger.debug("No license key found in db");
            throw e;
        }
    }

    public static class NoLicenseKeyFoundException extends Exception {

        public NoLicenseKeyFoundException() {
        }
    }

    private static class EnabledFeaturesNotSetInDbException extends Exception {

        public EnabledFeaturesNotSetInDbException() {
        }
    }

    public static class InvalidLicenseKeyException extends Exception {
        public InvalidLicenseKeyException() {

        }

        public InvalidLicenseKeyException(Exception e) {
            super(e);
        }
    }
}