/*
 *    Copyright (c) 2025, VRAI Labs and/or its affiliates. All rights reserved.
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

package io.supertokens.webserver.api.oauth;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

public final class NamedLockManager {

    // Prevent instantiation of the utility class
    private NamedLockManager() {}

    /**
     * Private static inner class to hold the Lock and its reference counter.
     */
    private static class NamedLockEntry {
        final ReentrantLock lock = new ReentrantLock();
        final AtomicInteger refCount = new AtomicInteger(0);
    }

    // The central repository for all named lock entries.
    private static final ConcurrentHashMap<String, NamedLockEntry> lockMap = new ConcurrentHashMap<>();

    // --- Static Public Methods ---

    /**
     * Acquires the lock associated with the given name.
     * * This method is a combination of:
     * 1. Getting or creating the lock entry.
     * 2. Incrementing the lock's reference count.
     * 3. Calling lock() on the underlying ReentrantLock.
     * * @param name The unique name of the lock to acquire.
     */
    public static void lock(String name) {
        if (name == null) {
            throw new IllegalArgumentException("Lock name cannot be null.");
        }

        // 1. Get or create the lock entry atomically
        NamedLockEntry entry = lockMap.computeIfAbsent(name, k -> new NamedLockEntry());

        // 2. Atomically increment the usage count.
        entry.refCount.incrementAndGet();

        // 3. Acquire the actual lock
        entry.lock.lock();

        // IMPORTANT: The unlock call must be wrapped in a try-finally block by the caller.
        // We cannot enforce it here because the try-finally structure is required
        // around the critical section, which is executed by the caller.
    }

    /**
     * Releases the lock associated with the given name and attempts safe removal
     * from the map if its reference count drops to zero.
     * * This method is a combination of:
     * 1. Calling unlock() on the underlying ReentrantLock.
     * 2. Decrementing the lock's reference count.
     * 3. Safely removing the lock entry if the count is zero.
     * * @param name The unique name of the lock to release.
     */
    public static void unlock(String name) {
        if (name == null) {
            throw new IllegalArgumentException("Lock name cannot be null.");
        }

        NamedLockEntry entry = lockMap.get(name);
        if (entry == null) {
            System.err.println("Warning: Attempted to unlock an unmanaged or already released lock: " + name);
            return;
        }

        // 1. Release the actual lock
        // This must be done BEFORE decrementing the count to avoid race conditions
        // where a subsequent lock() call could see a ref count of 0 but the lock is still held.
        try {
            entry.lock.unlock();
        } catch (IllegalMonitorStateException e) {
            // This happens if a thread tries to unlock a lock it doesn't own.
            // Re-throw or handle as necessary for your application's concurrency model.
            throw new IllegalMonitorStateException("Thread does not own the lock for: " + name);
        }

        // 2. Decrement the reference count.
        int newCount = entry.refCount.decrementAndGet();

        // 3. Safely attempt removal if the count is zero.
        if (newCount == 0) {
            // Only remove if the count is zero, using the (key, value) overload for safety.
            // This ensures we only remove *this* specific entry, preventing a race
            // where a new thread might have acquired a lock with the same name.
            lockMap.remove(name, entry);
        } else if (newCount < 0) {
            // Reset the count to 0 and throw an error for mismatched calls.
            entry.refCount.set(0);
            throw new IllegalStateException("Mismatched lock acquire/release calls for lock: " + name);
        }
    }

}