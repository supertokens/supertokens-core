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

    private long enabledFeaturesValueReadFromDbTime = -1;
    private ENABLED_FEATURES[] enabledFeaturesFromDb = null;

    private EEFeatureFlag() {
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

    public ENABLED_FEATURES[] getEnabledFeatures() {
        this.syncWithSuperTokensServerIfRequired(false);
        if (this.lastServerSyncTime == -1) {
            // Never synced with SuperTokens for some reason.
            // We still let the user try all the features.
            // TODO: is this a good idea?
            return ENABLED_FEATURES.values(); // returns all ENUM values.
        }
        return this.getFeatureFlagSavedInDb();
    }

    /*
     * params:
     *   - onInit: This is true when this function is called from the constructor. We use
     *              this boolean to set larger timeout values when querying SuperTokens.
     * */
    private void syncWithSuperTokensServerIfRequired(boolean onInit) {
        try {
            if (this.lastServerSyncTime == -1 ||
                    ((System.currentTimeMillis() - lastServerSyncTime) > INTERVAL_BETWEEN_SERVER_SYNC)) {
                // TODO: set request's connect and read timeout to be 20 seconds if onInit is true
                ENABLED_FEATURES[] featuresEnabledFromServer = new ENABLED_FEATURES[]{}; // TODO: read from API response
                this.setFeatureFlagInDb(featuresEnabledFromServer);
                this.lastServerSyncTime = System.currentTimeMillis();
            }
        } catch (Throwable ignored) {
            // we catch all errors so that this does not affect the functioning of the core.
            try {
                // this is done cause the syncing with server failed for some reason,
                // and if this is happening on core init, then at least
                // we should read the list of features enabled in the db before moving on.
                this.getFeatureFlagSavedInDb();
            } catch (Throwable ignored2) {
            }
        }
    }

    private void setFeatureFlagInDb(ENABLED_FEATURES[] features) {
        // TODO: save in db
        this.enabledFeaturesValueReadFromDbTime = System.currentTimeMillis();
        this.enabledFeaturesFromDb = features;
    }

    private ENABLED_FEATURES[] getFeatureFlagSavedInDb() {
        if (this.enabledFeaturesValueReadFromDbTime == -1 ||
                (System.currentTimeMillis() - this.enabledFeaturesValueReadFromDbTime > INTERVAL_BETWEEN_DB_READS)) {
            this.enabledFeaturesFromDb = new ENABLED_FEATURES[]{}; // TODO: read from db
            this.enabledFeaturesValueReadFromDbTime = System.currentTimeMillis();
        }
        return this.enabledFeaturesFromDb;
    }
}
