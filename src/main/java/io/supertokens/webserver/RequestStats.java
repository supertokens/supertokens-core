/*
 *    Copyright (c) 2023, VRAI Labs and/or its affiliates. All rights reserved.
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

package io.supertokens.webserver;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import io.supertokens.Main;
import io.supertokens.ResourceDistributor;
import io.supertokens.multitenancy.Multitenancy;
import io.supertokens.pluginInterface.multitenancy.AppIdentifier;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;

public class RequestStats extends ResourceDistributor.SingletonResource {
    public static final String RESOURCE_KEY = "io.supertokens.webserver.RequestStats";

    private final int MAX_MINUTES = 24 * 60;

    private long currentMinute; // current minute since epoch
    private final int[] currentMinuteRequestCounts; // array of 60 items representing number of requests at each
    // second in the current minute

    // The 2 arrays below contains stats for a day for every minute
    // the array is stored in such a way that array[currentMinute % MAX_MINUTES] contains the stats for a day ago
    // until array[(currentMinute - 1) % MAX_MINUTES] which contains the stats for the last minute, circling around
    // from end of array to the beginning
    // for e.g. if currentMinute % MAX_MINUTES = 250,
    // then array[250] contains stats for now - 1440 minutes
    // array[251] contains stats for now - 1439 minutes
    // ...
    // array[1439] contains stats for now - 1191 minutes
    // array[0] contains stats for now - 1190 minutes
    // array[1] contains stats for now - 1189 minutes
    // ...
    // array[249] contains stats for now - 1 minute
    private final double[] averageRequestsPerSecond;
    private final int[] peakRequestsPerSecond;

    private RequestStats() {
        currentMinute = System.currentTimeMillis() / 60000;
        currentMinuteRequestCounts = new int[60];

        averageRequestsPerSecond = new double[MAX_MINUTES];
        peakRequestsPerSecond = new int[MAX_MINUTES];
        for (int i = 0; i < MAX_MINUTES; i++) {
            averageRequestsPerSecond[i] = -1;
            peakRequestsPerSecond[i] = -1;
        }
    }

    private void checkAndUpdateMinute(long currentSecond) {
        if (currentSecond / 60 == currentMinute) {
            return; // stats update not required
        }

        int sum = 0;
        int max = 0;
        for (int i = 0; i < 60; i++) {
            sum += currentMinuteRequestCounts[i];
            max = Math.max(max, currentMinuteRequestCounts[i]);
        }

        averageRequestsPerSecond[(int) (currentMinute % MAX_MINUTES)] = sum / 60.0;
        peakRequestsPerSecond[(int) (currentMinute % MAX_MINUTES)] = max;

        // fill zeros for passed minutes
        for (long i = currentMinute + 1; i < currentSecond / 60; i++) {
            averageRequestsPerSecond[(int) (i % MAX_MINUTES)] = 0;
            peakRequestsPerSecond[(int) (i % MAX_MINUTES)] = 0;
        }

        currentMinute = currentSecond / 60;
        for (int i = 0; i < 60; i++) {
            currentMinuteRequestCounts[i] = 0;
        }
    }

    private void updateCounts(long currentSecond) {
        currentMinuteRequestCounts[(int) (currentSecond % 60)]++;
    }

    public static RequestStats getInstance(Main main, AppIdentifier appIdentifier) throws TenantOrAppNotFoundException {
        try {
            return (RequestStats) main.getResourceDistributor()
                    .getResource(appIdentifier.getAsPublicTenantIdentifier(), RESOURCE_KEY);
        } catch (TenantOrAppNotFoundException e) {
            // appIdentifier parameter is coming from the API request and hence we need to check if the app exists
            // before creating a resource for it, otherwise someone could fill up memory by making requests for apps
            // that don't exist.
            // The other resources are created during init or while refreshing tenants from the db, so we don't need
            // this kind of pattern for those resources.
            if (Multitenancy.getTenantInfo(main, appIdentifier.getAsPublicTenantIdentifier()) == null) {
                throw e;
            }
            return (RequestStats) main.getResourceDistributor()
                    .setResource(appIdentifier.getAsPublicTenantIdentifier(), RESOURCE_KEY, new RequestStats());
        }
    }

    public void updateRequestStats() {
        this.updateRequestStats(true);
    }

    synchronized private void updateRequestStats(boolean updateCounts) {
        long now = System.currentTimeMillis() / 1000;
        this.checkAndUpdateMinute(now);
        if (updateCounts) {
            this.updateCounts(now);
        }
    }

    public JsonObject getStats() {
        this.updateRequestStats(false);

        JsonArray avgRps = new JsonArray();
        JsonArray peakRps = new JsonArray();

        long atMinute = System.currentTimeMillis() / 60000;

        int offset = (int) (atMinute % MAX_MINUTES);
        for (int i = 0; i < MAX_MINUTES; i++) {
            avgRps.add(new JsonPrimitive(this.averageRequestsPerSecond[(i + offset) % MAX_MINUTES]));
            peakRps.add(new JsonPrimitive(this.peakRequestsPerSecond[(i + offset) % MAX_MINUTES]));
        }

        JsonObject result = new JsonObject();
        result.addProperty("atMinute", atMinute);
        result.add("averageRequestsPerSecond", avgRps);
        result.add("peakRequestsPerSecond", peakRps);
        return result;
    }
}
