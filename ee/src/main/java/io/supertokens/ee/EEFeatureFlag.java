/*
 *    Copyright (c) 2022, VRAI Labs and/or its affiliates. All rights reserved.
 *
 *    This software is licensed under the Apache License, Version 2.0 (the
 *    "License") as published by the Apache Software Foundation.
 *
 *    You may not use this file except in compliance with the License. You may
 *    obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 *    WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 *    License for the specific language governing permissions and limitations
 *    under the License.
 */

package io.supertokens.ee;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.supertokens.ee.httpRequest.HttpRequest;
import io.supertokens.ee.httpRequest.HttpResponseException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EEFeatureFlag {
    private static EEFeatureFlag instance = null;
    private static final long INTERVAL_BETWEEN_SERVER_SYNC = (long) 1000 * 3600 * 24; // 1 day.
    private static final long INTERVAL_BETWEEN_DB_READS = (long) 1000 * 3600 * 4; // 4 hour.

    private long lastServerSyncAttemptTime = -1;

    private Boolean isLicenseKeyPresent = null;

    private long enabledFeaturesValueReadFromDbTime = -1;
    private EE_FEATURES[] enabledFeaturesFromDb = null;

    private EEFeatureFlag() {
        // TODO: fail start of core if db based error is thrown. If API error is thrown, ignore it.
        try {
            this.syncWithSuperTokensServerIfRequired(true);
        } catch (HttpResponseException | IOException ignored) {
            // server request failed. we ignore for now as later on it will sync up anyway.
        }
    }

    public static EEFeatureFlag getInstanceOrThrow() {
        if (EEFeatureFlag.instance == null) {
            throw new IllegalStateException("EEFeatureFlag not initialised");
        }
        return instance;
    }

    public static void init() {
        EEFeatureFlag.instance = new EEFeatureFlag();
    }

    public EE_FEATURES[] getEnabledFeatures() {
        if (!this.isLicenseKeyPresent) {
            return new EE_FEATURES[] {};
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
            // TODO: is this a good idea?
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
                this.setEnabledEEFeaturesInDb(new EE_FEATURES[] {});
                return;
            }
            this.lastServerSyncAttemptTime = System.currentTimeMillis();

            Map<String, String> queryParams = new HashMap<>();
            queryParams.put("licenseKey", licenseKey);
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
            this.enabledFeaturesFromDb = new EE_FEATURES[] {}; // TODO: read from db
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
}
