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

import java.util.HashMap;
import java.util.Map;

public class RateLimiter {
    private long lastRequestTime = 0;
    private final long timeInterval; // in milliseconds
    private long tokenBucketSize = 5;
    private long tokensAvailable = tokenBucketSize;

    private static final Map<Object, RateLimiter> rateLimiters = new HashMap<>();

    public static synchronized RateLimiter getInstance(Object key, long timeInterval) {
        if (!rateLimiters.containsKey(key)) {
            rateLimiters.put(key, new RateLimiter(timeInterval));
        }
        return rateLimiters.get(key);
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
