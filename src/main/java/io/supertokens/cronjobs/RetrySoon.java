/*
 *    Copyright (c) 2026, VRAI Labs and/or its affiliates. All rights reserved.
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

package io.supertokens.cronjobs;

import io.supertokens.Main;
import io.supertokens.output.Logging;
import io.supertokens.pluginInterface.multitenancy.AppIdentifier;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Re-runs a daily per-app job (the telemetry ping, the license sync) a few minutes after it failed, instead of
 * waiting for the next daily run: a day's report is otherwise lost to a transient failure such as a user count that
 * could not be taken. At most {@link #MAX_ATTEMPTS} retries in a row per job and app; a success resets the count.
 */
public final class RetrySoon {

    public static final int MAX_ATTEMPTS = 3;
    private static final long DEFAULT_DELAY_MS = 5 * 60 * 1000L;

    private static volatile long delayMs = DEFAULT_DELAY_MS;

    // tests opt in (a retry scheduled by one test would otherwise fire into a later one)
    private static volatile boolean enabledInTests = false;

    private static final ScheduledExecutorService EXECUTOR = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "retry-soon");
        t.setDaemon(true);
        return t;
    });

    private static final ConcurrentHashMap<String, Integer> ATTEMPTS = new ConcurrentHashMap<>();

    public interface Job {
        void run(AppIdentifier app) throws Exception;
    }

    private RetrySoon() {
    }

    /**
     * Runs {@code job} for {@code app}; if it throws, schedules a retry (up to {@link #MAX_ATTEMPTS} in a row) and
     * rethrows.
     */
    public static void run(Main main, String jobName, AppIdentifier app, Job job) throws Exception {
        String key = System.identityHashCode(main) + "~" + jobName + "~" + app.getConnectionUriDomain() + "~"
                + app.getAppId();
        try {
            job.run(app);
            ATTEMPTS.remove(key);
        } catch (Exception e) {
            int attempt = ATTEMPTS.merge(key, 1, Integer::sum);
            if (attempt <= MAX_ATTEMPTS && (!Main.isTesting || enabledInTests)) {
                Logging.warn(main, app.getAsPublicTenantIdentifier(), jobName + " failed for app " + app.getAppId()
                        + "; retrying in " + (delayMs / 1000) + "s (attempt " + attempt + " of " + MAX_ATTEMPTS
                        + "): " + e.getMessage());
                EXECUTOR.schedule(() -> {
                    try {
                        run(main, jobName, app, job);
                    } catch (Exception ignored) {
                        // logged, and rescheduled if attempts are left
                    }
                }, delayMs, TimeUnit.MILLISECONDS);
            } else {
                ATTEMPTS.remove(key); // the next daily run starts a fresh series
            }
            throw e;
        }
    }

    // Test-only seam: enables retries in tests, with the given delay; 0 disables them again.
    public static void enableForTesting(long delay) {
        enabledInTests = delay > 0;
        delayMs = delay > 0 ? delay : DEFAULT_DELAY_MS;
    }
}
