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

public class EEFeatureFlag implements io.supertokens.featureflag.EEFeatureFlagInterface {
    public static final int INTERVAL_BETWEEN_SERVER_SYNC = 1000 * 3600 * 24; // 1 day.
    private static final long INTERVAL_BETWEEN_DB_READS = (long) 1000 * 3600 * 4; // 4 hour.
    private static final String REQUEST_ID = "licensecheck";

    private static final String FEATURE_FLAG_KEY_IN_DB = "FEATURE_FLAG";
    private static final String LICENSE_KEY_IN_DB = "LICENSE_KEY";

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
    public void constructor(Main main) throws StorageQueryException {
        this.main = main;
        Cronjobs.addCronjob(main, EELicenseCheck.getInstance(main));
        try {
            this.forceSyncFeatureFlagWithLicenseKey();
        } catch (HttpResponseException | IOException e) {
            Logging.error(main, "API Error during constructor sync", false, e);
            // server request failed. we ignore for now as later on it will sync up anyway.
        } catch (InvalidLicenseKeyException ignored) {
            // the license key that was in the db was invalid. If this error is thrown,
            // it means that the key was removed from the db anyway.. so we can just ignore
            // here.
        }
        // any other exception (like db related errors) will result in core not starting
    }

    @Override
    public EE_FEATURES[] getEnabledFeatures() throws StorageQueryException {
        if (!this.isLicenseKeyPresent) {
            return new EE_FEATURES[]{};
        }

        return this.getEnabledEEFeaturesFromDbOrCache();
    }

    @Override
    public void removeLicenseKeyAndSyncFeatures() throws HttpResponseException, IOException, StorageQueryException {
        this.removeLicenseKeyFromDb();
        try {
            this.forceSyncFeatureFlagWithLicenseKey();
        } catch (InvalidLicenseKeyException ignored) {
            // should never come here, because we are removing the license key.
        }
    }

    @Override
    public void setLicenseKeyAndSyncFeatures(String key)
            throws HttpResponseException, IOException, StorageQueryException, InvalidLicenseKeyException {
        this.setLicenseKeyInDb(key);
        this.forceSyncFeatureFlagWithLicenseKey();
    }

    @Override
    public void forceSyncFeatureFlagWithLicenseKey()
            throws HttpResponseException, IOException, StorageQueryException, InvalidLicenseKeyException {
        this.syncFeatureFlagWithLicenseKeyIfRequired();
    }

    @Override
    @TestOnly
    public Boolean getIsLicenseKeyPresent() {
        return isLicenseKeyPresent;
    }

    @Override
    public JsonObject getPaidFeatureStats() throws StorageQueryException {
        JsonObject result = new JsonObject();
        // TODO:
        return result;
    }

    private void syncFeatureFlagWithLicenseKeyIfRequired()
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
            // TODO: add test for making sure JWT validation is correct:
            // - should check for expiry in the claim is present in it
            // - if exp is not in the claim, then should assume infinite lifetime
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
            throw new InvalidLicenseKeyException();
        }
        throw new RuntimeException("Should never come here");
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
            EE_FEATURES[] features = new EE_FEATURES[featuresArrayJson.size()];
            for (int i = 0; i < featuresArrayJson.size(); i++) {
                features[i] = EE_FEATURES.valueOf(featuresArrayJson.get(i).getAsString());
            }
            this.enabledFeaturesFromDb = features;
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