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

package io.supertokens.cronjobs.rollupUserLastActive;

import io.supertokens.Main;
import io.supertokens.ResourceDistributor;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifier;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A per-storage, in-memory "there is something to fold" flag for the {@link RollupUserLastActive} cron.
 *
 * <p>The rollup-relevant activity-log emit paths (the {@code user_last_active} append, and — once wired —
 * the account linking/unlinking events) call {@link #markDirty} after writing a row. The cron consumes the
 * flag ({@link #consumeDirty}, a consume-on-read compare-and-set) at the top of each pass so it can skip the
 * pass — and avoid borrowing a connection from a possibly-idle pool — when nothing has changed.
 *
 * <p>The flag is purely an optimization: it decides <em>whether</em> to run, never <em>what window</em> to
 * fold, so losing a signal (e.g. the only dirty instance dies, or a storage is recreated on config reload)
 * is corrected by the cron's periodic unconditional backstop fold and by its first-tick existence check. It
 * is intentionally per-{@link Main} (a {@link ResourceDistributor} singleton) rather than process-global so
 * that isolated test processes do not share dirty state.
 */
public class RollupDirtySignal extends ResourceDistributor.SingletonResource {

    public static final String RESOURCE_KEY = "io.supertokens.cronjobs.rollupUserLastActive.RollupDirtySignal";

    // Keyed by storage user pool id — the same granularity the cron iterates over (one pass per unique
    // user pool). Entries are created on first use and never removed; the number of distinct storages is
    // small and bounded, so this does not grow without bound.
    private final ConcurrentHashMap<String, AtomicBoolean> dirtyByUserPoolId = new ConcurrentHashMap<>();

    private RollupDirtySignal() {
    }

    public static RollupDirtySignal getInstance(Main main) {
        try {
            return (RollupDirtySignal) main.getResourceDistributor()
                    .getResource(new TenantIdentifier(null, null, null), RESOURCE_KEY);
        } catch (TenantOrAppNotFoundException e) {
            return (RollupDirtySignal) main.getResourceDistributor()
                    .setResource(new TenantIdentifier(null, null, null), RESOURCE_KEY, new RollupDirtySignal());
        }
    }

    private AtomicBoolean flagFor(String userPoolId) {
        return dirtyByUserPoolId.computeIfAbsent(userPoolId, k -> new AtomicBoolean(false));
    }

    /**
     * Marks the given storage as having rollup-relevant activity that has not yet been folded.
     */
    public void markDirty(String userPoolId) {
        flagFor(userPoolId).set(true);
    }

    /**
     * Atomically reads and clears the dirty flag for the given storage. Returns {@code true} if the flag was
     * set (there is work to fold), {@code false} otherwise. Two consumers race safely: exactly one sees
     * {@code true}.
     */
    public boolean consumeDirty(String userPoolId) {
        return flagFor(userPoolId).compareAndSet(true, false);
    }
}
