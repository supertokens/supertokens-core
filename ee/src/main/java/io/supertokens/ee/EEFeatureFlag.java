package io.supertokens.ee;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.supertokens.ee.httpRequest.HttpRequest;
import io.supertokens.ee.httpRequest.HttpResponseException;
import io.supertokens.pluginInterface.Storage;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
 *  existed, then all features.
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
 * getEnabledFeatures will return the older enabled features from the db, or if nothing existed, then all features.
 * */

public class EEFeatureFlag {
    private static final long INTERVAL_BETWEEN_SERVER_SYNC = (long) 1000 * 3600 * 24; // 1 day.
    private static final long INTERVAL_BETWEEN_DB_READS = (long) 1000 * 3600 * 4; // 4 hour.

    private long lastServerSyncAttemptTime = -1;

    private Boolean isLicenseKeyPresent = null;

    private long enabledFeaturesValueReadFromDbTime = -1;
    private EE_FEATURES[] enabledFeaturesFromDb = null;

    private Storage storage;

    public void constructor(Storage storage) {
        this.storage = storage;
        // TODO: fail start of core if db based error is thrown. If API error is thrown, ignore it.
        try {
            this.forceSyncWithServer();
        } catch (HttpResponseException | IOException ignored) {
            // server request failed. we ignore for now as later on it will sync up anyway.
        }
    }

    public EE_FEATURES[] getEnabledFeatures() {
        // TODO: expose this in API as well
        if (!this.isLicenseKeyPresent) {
            return new EE_FEATURES[]{};
        }
        try {
            this.syncWithSuperTokensServerIfRequired(false);
        } catch (Throwable ignored) {
            // we catch all errors so that this does not affect the functioning of the core.
        }

        try {
            return this.getEnabledEEFeaturesFromDbOrCache();
        } catch (EnabledFeaturesNotSetInDbException e) {
            // Never synced with SuperTokens for some reason.
            // We still let the user try all the features.
            return EE_FEATURES.values(); // returns all ENUM values.
        }
    }

    public void removeLicenseKeyAndSyncFeatures() throws HttpResponseException, IOException {
        // TODO: expose this as an API
        this.removeLicenseKeyFromDb();
        this.forceSyncWithServer();
    }

    public void setLicenseKeyAndSyncFeatures(String key) throws HttpResponseException, IOException {
        // TODO: expose this as an API
        this.setLicenseKeyInDb(key);
        this.forceSyncWithServer();
    }

    public void forceSyncWithServer() throws HttpResponseException, IOException {
        // TODO: call this in a cronjob once a day.
        // TODO: expose this as an API from the core as well.
        this.syncWithSuperTokensServerIfRequired(true);
    }

    private void syncWithSuperTokensServerIfRequired(boolean force) throws HttpResponseException, IOException {
        if (!force && !this.isLicenseKeyPresent) {
            return;
        }
        if (force || this.lastServerSyncAttemptTime == -1
                || ((System.currentTimeMillis() - lastServerSyncAttemptTime) > INTERVAL_BETWEEN_SERVER_SYNC)) {
            String licenseKey = "";
            try {
                licenseKey = this.getLicenseKeyFromDb();
                this.isLicenseKeyPresent = true;
            } catch (NoLicenseKeyFoundException ex) {
                this.isLicenseKeyPresent = false;
                this.setEnabledEEFeaturesInDb(new EE_FEATURES[]{});
                return;
            }
            this.lastServerSyncAttemptTime = System.currentTimeMillis();

            Map<String, String> queryParams = new HashMap<>();
            queryParams.put("licenseKey", licenseKey);
            // TODO: add telemetry ID (which is optional in case of in mem db) + details of paid features usage stats
            //  to calculate accurate billing + core version.
            // TODO: add currently enabled features from db.
            JsonObject licenseCheckResponse = HttpRequest.sendGETRequest("https://api.supertokens.io/0/st/license",
                    queryParams, 10000, 10000, 0);
            if (licenseCheckResponse.get("status").getAsString().equalsIgnoreCase("OK")) {
                JsonArray enabledFeaturesJSON = licenseCheckResponse.getAsJsonArray("enabled_features");
                List<EE_FEATURES> enabledFeatures = new ArrayList<>();
                enabledFeaturesJSON.forEach(jsonElement -> {
                    // TODO: think about changes in features over versions of the core and APIs
                    EE_FEATURES feature = EE_FEATURES.getEnumFromString(jsonElement.toString());
                    if (feature != null) { // this check cause maybe the core is of an older version
                        enabledFeatures.add(feature);
                    }
                });
                this.setEnabledEEFeaturesInDb(enabledFeatures.toArray(EE_FEATURES[]::new));
            } else if (licenseCheckResponse.get("status").getAsString().equalsIgnoreCase("INVALID_LICENSE_KEY")) {
                // TODO: logging here and in other places.
                this.removeLicenseKeyAndSyncFeatures();
            }
        }
    }

    private void setEnabledEEFeaturesInDb(EE_FEATURES[] features) {
        // TODO: save in db
        this.enabledFeaturesValueReadFromDbTime = System.currentTimeMillis();
        this.enabledFeaturesFromDb = features;
    }

    private EE_FEATURES[] getEnabledEEFeaturesFromDbOrCache() throws EnabledFeaturesNotSetInDbException {
        if (this.enabledFeaturesValueReadFromDbTime == -1
                || (System.currentTimeMillis() - this.enabledFeaturesValueReadFromDbTime > INTERVAL_BETWEEN_DB_READS)) {
            this.enabledFeaturesFromDb = new EE_FEATURES[]{}; // TODO: read from db
            this.enabledFeaturesValueReadFromDbTime = System.currentTimeMillis();
        }
        return this.enabledFeaturesFromDb;
    }

    private void setLicenseKeyInDb(String key) {
        // TODO: save in db
    }

    private void removeLicenseKeyFromDb() {
        // TODO: save in db
    }

    private String getLicenseKeyFromDb() throws NoLicenseKeyFoundException {
        return ""; // TODO: query from db
    }

    private static class NoLicenseKeyFoundException extends Exception {

        public NoLicenseKeyFoundException() {
        }
    }

    private static class EnabledFeaturesNotSetInDbException extends Exception {

        public EnabledFeaturesNotSetInDbException() {
        }
    }

    // TODO: Add a way to know which features are enabled to the backend SDK.
}

/*
 * TODO: Long term changes:
 *      - For people who want an air gaped system, we can issue license keys which are JWTs and the public
 *          key is stored in the core. The JWT payload contains the list of enabled features.
 *      - We want to log the license key that's being used so that if there is license key sharing,
 *          then we can see the logs and know. Users can't easily spoof this license key in logs cause
 *          we are the only ones who can generate the licese keys in the first place.
 *              - EDIT: this is no longer required cause the key will be in the db anyway.
 * */