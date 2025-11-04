/*
 *    Copyright (c) 2020, VRAI Labs and/or its affiliates. All rights reserved.
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

package io.supertokens;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nonnull;

import org.jetbrains.annotations.TestOnly;

import io.supertokens.pluginInterface.multitenancy.AppIdentifier;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifier;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;

// the purpose of this class is to tie singleton classes to s specific main instance. So that
// when the main instance dies, those singleton classes die too.

public class ResourceDistributor {
    private final Map<KeyClass, SingletonResource> resources = new HashMap<>(1);
    private final Main main;

    private static TenantIdentifier appUsedForTesting = TenantIdentifier.BASE_TENANT;

    public ResourceDistributor(Main main) {
        this.main = main;
    }

    @TestOnly
    public static void setAppForTesting(TenantIdentifier app) {
        appUsedForTesting = app;
    }

    @TestOnly
    public static TenantIdentifier getAppForTesting() {
        return appUsedForTesting;
    }

    public SingletonResource getResource(AppIdentifier appIdentifier, @Nonnull String key)
            throws TenantOrAppNotFoundException {
        return getResource(appIdentifier.getAsPublicTenantIdentifier(), key);
    }

    public SingletonResource getResource(TenantIdentifier tenantIdentifier, @Nonnull String key)
            throws TenantOrAppNotFoundException {
        // first we do exact match
        SingletonResource resource = resources.get(new KeyClass(tenantIdentifier, key));
        if (resource != null) {
            return resource;
        }

        if (tenantIdentifier.equals(new TenantIdentifier(null, null, null))) {
            // this means we are looking at base tenant and it's not something that
            // refreshing tenants will help with (in fact it will cause an infinite loop)
            throw new TenantOrAppNotFoundException(tenantIdentifier);
        }

        throw new TenantOrAppNotFoundException(tenantIdentifier);
    }

    @TestOnly
    public SingletonResource getResource(@Nonnull String key) {
        return resources.get(new KeyClass(appUsedForTesting, key));
    }

    public SingletonResource setResource(TenantIdentifier tenantIdentifier,
                                                      @Nonnull String key,
                                                      SingletonResource resource) {
        SingletonResource alreadyExists = resources.get(new KeyClass(tenantIdentifier, key));
        if (alreadyExists != null) {
            return alreadyExists;
        }
        resources.put(new KeyClass(tenantIdentifier, key), resource);
        return resource;
    }

    public SingletonResource removeResource(TenantIdentifier tenantIdentifier,
                                                         @Nonnull String key) {
        SingletonResource singletonResource = resources.get(new KeyClass(tenantIdentifier, key));
        if (singletonResource == null) {
            return null;
        }
        resources.remove(new KeyClass(tenantIdentifier, key));
        return singletonResource;
    }

    public SingletonResource setResource(AppIdentifier appIdentifier,
                                                      @Nonnull String key,
                                                      SingletonResource resource) {
        return setResource(appIdentifier.getAsPublicTenantIdentifier(), key, resource);
    }

    public SingletonResource removeResource(AppIdentifier appIdentifier,
                                                         @Nonnull String key) {
        return removeResource(appIdentifier.getAsPublicTenantIdentifier(), key);
    }

    public void clearAllResourcesWithResourceKey(String inputKey) {
        List<KeyClass> toRemove = new ArrayList<>();
        resources.forEach((key, value) -> {
            if (key.key.equals(inputKey)) {
                toRemove.add(key);
            }
        });
        for (KeyClass keyClass : toRemove) {
            resources.remove(keyClass);
        }
    }

    public synchronized Map<KeyClass, SingletonResource> getAllResourcesWithResourceKey(String inputKey) {
        Map<KeyClass, SingletonResource> result = new HashMap<>();
        resources.forEach((key, value) -> {
            if (key.key.equals(inputKey)) {
                result.put(key, value);
            }
        });
        return result;
    }

    @TestOnly
    public SingletonResource setResource(@Nonnull String key,
                                                      SingletonResource resource) {
        return setResource(appUsedForTesting, key, resource);
    }

    public interface Func<T> {
        T performTask() throws FuncException;
    }

    public synchronized <T> T withResourceDistributorLock(Func<T> func) throws FuncException {
        return func.performTask();
    }

    public interface FuncWithReturn<T> {
        T performTask() throws FuncException;
    }

    public synchronized <T> T withResourceDistributorLockWithReturn(FuncWithReturn<T> func) throws FuncException {
        return func.performTask();
    }

    public static class FuncException extends Exception {
        public FuncException(Exception e) {
            super(e);
        }
    }

    public static class SingletonResource {

    }

    public static class KeyClass {
        @Nonnull
        private final String key;

        private final TenantIdentifier tenantIdentifier;

        public KeyClass(TenantIdentifier tenantIdentifier, @Nonnull String key) {
            this.key = key;
            this.tenantIdentifier = tenantIdentifier;
        }

        public KeyClass(AppIdentifier appIdentifier, @Nonnull String key) {
            this.key = key;
            this.tenantIdentifier = appIdentifier.getAsPublicTenantIdentifier();
        }

        public TenantIdentifier getTenantIdentifier() {
            return this.tenantIdentifier;
        }

        @Override
        public boolean equals(Object other) {
            if (other instanceof KeyClass) {
                KeyClass otherKeyClass = (KeyClass) other;
                return otherKeyClass.getTenantIdentifier().equals(this.getTenantIdentifier()) &&
                        otherKeyClass.key.equals(key);
            }
            return false;
        }

        @Override
        public int hashCode() {
            return (this.getTenantIdentifier().getTenantId() + "|" +
                    this.getTenantIdentifier().getConnectionUriDomain() + "|" +
                    this.getTenantIdentifier().getAppId() + "|" +
                    this.key).hashCode();
        }
    }

}
