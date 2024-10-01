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

package io.supertokens.utils;

import io.supertokens.Main;
import io.supertokens.ResourceDistributor;
import io.supertokens.pluginInterface.multitenancy.AppIdentifier;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;

import java.util.HashMap;
import java.util.Map;

public class RateLimiter extends ResourceDistributor.SingletonResource {

    private static final String RESOURCE_KEY = "io.supertokens.utils.RateLimiter";

    private long lastRequestTime = 0;
    private final long timeInterval; // in milliseconds
    private long tokenBucketSize = 5;
    private long tokensAvailable = tokenBucketSize;

    public static RateLimiter getInstance(AppIdentifier appIdentifier, Main main,
                                          long timeIntervalIfCreatingNewRateLimiter) {
        try {
            return (RateLimiter) main.getResourceDistributor().getResource(appIdentifier, RESOURCE_KEY);
        } catch (TenantOrAppNotFoundException e) {
            // Create a new rate limiter
            RateLimiter rateLimiter = new RateLimiter(timeIntervalIfCreatingNewRateLimiter);
            main.getResourceDistributor().setResource(appIdentifier, RESOURCE_KEY, rateLimiter);
            return rateLimiter;
        }
    }

    public RateLimiter(long timeInterval) {
        this.timeInterval = timeInterval;
    }

    public synchronized boolean checkRequest() {
        long currentTime = System.currentTimeMillis();
        long timeSinceLastRequest = currentTime - lastRequestTime;

        // Add tokens to the bucket based on elapsed time since the last request
        long newTokens = (timeSinceLastRequest / timeInterval);
        tokensAvailable = Math.min(tokensAvailable + newTokens, tokenBucketSize);

        // If there are no tokens available, return false
        if (tokensAvailable == 0) {
            return false;
        }

        // Take a token from the bucket and make the request
        tokensAvailable--;
        lastRequestTime = currentTime;
        return true;
    }
}
