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

public class EEFeatureFlag {
    private static EEFeatureFlag instance = null;
    private static final long INTERVAL_BETWEEN_SERVER_SYNC = (long) 1000 * 3600 * 24; // 1 day.
    private static final long INTERVAL_BETWEEN_DB_READS = (long) 1000 * 3600 * 4; // 4 hour.

    private long lastServerSyncTime = -1;

    private Boolean isLicenseKeyPresent = null;

    private long enabledFeaturesValueReadFromDbTime = -1;
    private EE_FEATURES[] enabledFeaturesFromDb = null;

    private EEFeatureFlag() {
        // TODO: fail start of core if db based error is thrown. If API error is thrown, ignore it.
        this.syncWithSuperTokensServerIfRequired(true);
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
            return new EE_FEATURES[]{};
        }
        try {
            this.syncWithSuperTokensServerIfRequired(false);
        } catch (Throwable ignored) {
            // we catch all errors so that this does not affect the functioning of the core.
        }
        if (this.lastServerSyncTime == -1) {
            // TODO: optimise the flow for this where if our servers are no more,
            //  then it tries and sync just once a day and not each time the syncWithSuperTokensServerIfRequired
            //  function is called


            // Never synced with SuperTokens for some reason.
            // We still let the user try all the features.
            // TODO: is this a good idea?
            return EE_FEATURES.values(); // returns all ENUM values.
        }
        return this.getEnabledEEFeaturesFromDbOrCache();
    }

    public void removeLicenseKeyAndSyncFeatures() {
        // TODO: expose this as an API
        this.removeLicenseKeyFromDb();
        this.forceSyncWithServer();
    }

    public void setLicenseKeyAndSyncFeatures(String key) {
        // TODO: expose this as an API
        this.setLicenseKeyInDb(key);
        this.forceSyncWithServer();
    }

    public void forceSyncWithServer() {
        // TODO: expose this as an API from the core as well.
        this.syncWithSuperTokensServerIfRequired(true);
    }

    private void syncWithSuperTokensServerIfRequired(boolean force) {
        if (!force && !this.isLicenseKeyPresent) {
            return;
        }
        if (force || this.lastServerSyncTime == -1 ||
                ((System.currentTimeMillis() - lastServerSyncTime) > INTERVAL_BETWEEN_SERVER_SYNC)) {
            String licenseKey = "";
            try {
                licenseKey = this.getLicenseKeyFromDb();
                this.isLicenseKeyPresent = true;
            } catch (NoLicenseKeyFoundException ex) {
                this.isLicenseKeyPresent = false;
                this.setEnabledEEFeaturesInDb(new EE_FEATURES[]{});
                return;
            }
            EE_FEATURES[] featuresEnabledFromServer = new EE_FEATURES[]{}; // TODO: read from API response
            this.setEnabledEEFeaturesInDb(featuresEnabledFromServer);
            this.lastServerSyncTime = System.currentTimeMillis();
        }
    }

    private void setEnabledEEFeaturesInDb(EE_FEATURES[] features) {
        // TODO: save in db
        this.enabledFeaturesValueReadFromDbTime = System.currentTimeMillis();
        this.enabledFeaturesFromDb = features;
    }

    private EE_FEATURES[] getEnabledEEFeaturesFromDbOrCache() {
        if (this.enabledFeaturesValueReadFromDbTime == -1 ||
                (System.currentTimeMillis() - this.enabledFeaturesValueReadFromDbTime > INTERVAL_BETWEEN_DB_READS)) {
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
}
