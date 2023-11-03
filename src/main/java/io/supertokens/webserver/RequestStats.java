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
import io.supertokens.pluginInterface.multitenancy.AppIdentifier;

public class RequestStats extends ResourceDistributor.SingletonResource {
    public static final String RESOURCE_KEY = "io.supertokens.webserver.RequestStats";

    private final int MAX_MINUTES = 24 * 60;

    private long currentMinute;
    private final int[] lastMinuteRps;

    private final double[] avgRps;
    private final int[] peakRps;

    private RequestStats() {
        currentMinute = System.currentTimeMillis() / 60000;
        lastMinuteRps = new int[60];

        avgRps = new double[MAX_MINUTES];
        peakRps = new int[MAX_MINUTES];
        for (int i = 0; i < MAX_MINUTES; i++) {
            avgRps[i] = -1;
            peakRps[i] = -1;
        }
    }

    private void checkAndUpdateMinute(long currentSecond) {
        if (currentSecond / 60 == currentMinute) {
            return; // no need to do anything
        }

        int sum = 0;
        int max = 0;
        for (int i = 0; i < 60; i++) {
            sum += lastMinuteRps[i];
            max = Math.max(max, lastMinuteRps[i]);
        }

        avgRps[(int) (currentMinute % MAX_MINUTES)] = sum / 60.0;
        peakRps[(int) (currentMinute % MAX_MINUTES)] = max;

        // Fill zeros for passed minutes
        for (long i = currentMinute + 1; i < currentSecond / 60; i++) {
            avgRps[(int) (i % MAX_MINUTES)] = 0;
            peakRps[(int) (i % MAX_MINUTES)] = 0;
        }

        currentMinute = currentSecond / 60;
        for (int i = 0; i < 60; i++) {
            lastMinuteRps[i] = 0;
        }
    }

    private void updateCounts(long currentSecond) {
        lastMinuteRps[(int) (currentSecond % 60)]++;
    }

    public static RequestStats getInstance(Main main, AppIdentifier appIdentifier) {
        return (RequestStats) main.getResourceDistributor()
                .setResource(appIdentifier.getAsPublicTenantIdentifier(), RESOURCE_KEY, new RequestStats());
    }

    synchronized public void updateRequestStats(boolean updateCounts) {
        long now = System.currentTimeMillis() / 1000;
        this.checkAndUpdateMinute(now);
        if (updateCounts) { this.updateCounts(now); }
    }

    public JsonObject getStats() {
        this.updateRequestStats(false);

        JsonArray avgRps = new JsonArray();
        JsonArray peakRps = new JsonArray();

        long atMinute = System.currentTimeMillis() / 60000;

        int offset = (int) (atMinute % MAX_MINUTES);
        for (int i = 0; i < MAX_MINUTES; i++) {
            avgRps.add(new JsonPrimitive(this.avgRps[(i + offset) % MAX_MINUTES]));
            peakRps.add(new JsonPrimitive(this.peakRps[(i + offset) % MAX_MINUTES]));
        }

        JsonObject result = new JsonObject();
        result.addProperty("atMinute", atMinute);
        result.add("averageRequestsPerSecond", avgRps);
        result.add("peakRequestsPerSecond", peakRps);
        return result;
    }
}
