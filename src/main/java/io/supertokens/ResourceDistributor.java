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

import io.supertokens.multitenancy.MultitenancyHelper;
import io.supertokens.pluginInterface.multitenancy.AppIdentifier;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifier;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import org.jetbrains.annotations.TestOnly;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// the purpose of this class is to tie singleton classes to s specific main instance. So that
// when the main instance dies, those singleton classes die too.

public class ResourceDistributor {
    private final Map<KeyClass, SingletonResource> resources = new HashMap<>(1);
    private final Main main;

    public ResourceDistributor(Main main) {
        this.main = main;
    }

    public synchronized SingletonResource getResource(AppIdentifier appIdentifier, @Nonnull String key)
            throws TenantOrAppNotFoundException {
        return getResource(appIdentifier.getAsPublicTenantIdentifier(), key);
    }

    public synchronized SingletonResource getResource(TenantIdentifier tenantIdentifier, @Nonnull String key)
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

        MultitenancyHelper.getInstance(main).refreshTenantsInCoreBasedOnChangesInCoreConfigOrIfTenantListChanged(true);

        // we try again..
        resource = resources.get(new KeyClass(tenantIdentifier, key));
        if (resource != null) {
            return resource;
        }

        // then we see if the user has configured anything to do with connectionUriDomain, and if they have,
        // then we must return null cause the user has not specifically added tenantId to it
        for (KeyClass currKey : resources.keySet()) {
            if (currKey.getTenantIdentifier().getConnectionUriDomain()
                    .equals(tenantIdentifier.getConnectionUriDomain())) {
                throw new TenantOrAppNotFoundException(tenantIdentifier);
            }
        }

        // if it comes here, it means that the user has not configured anything to do with
        // connectionUriDomain, and therefore we fallback on the case where connectionUriDomain is the base one.
        // This is useful when the base connectionuri can be localhost or 127.0.0.1 or anything else that's
        // not specifically configured by the dev.
        resource = resources.get(new KeyClass(
                new TenantIdentifier(null, tenantIdentifier.getAppId(), tenantIdentifier.getTenantId()), key));
        if (resource != null) {
            return resource;
        }

        throw new TenantOrAppNotFoundException(tenantIdentifier);
    }

    @TestOnly
    public synchronized SingletonResource getResource(@Nonnull String key) {
        return resources.get(new KeyClass(new TenantIdentifier(null, null, null), key));
    }

    public synchronized SingletonResource setResource(TenantIdentifier tenantIdentifier,
                                                      @Nonnull String key,
                                                      SingletonResource resource) {
        SingletonResource alreadyExists = resources.get(new KeyClass(tenantIdentifier, key));
        if (alreadyExists != null) {
            return alreadyExists;
        }
        resources.put(new KeyClass(tenantIdentifier, key), resource);
        return resource;
    }

    public synchronized SingletonResource removeResource(TenantIdentifier tenantIdentifier,
                                                         @Nonnull String key) {
        SingletonResource singletonResource = resources.get(new KeyClass(tenantIdentifier, key));
        if (singletonResource == null) {
            return null;
        }
        resources.remove(new KeyClass(tenantIdentifier, key));
        return singletonResource;
    }

    public synchronized SingletonResource setResource(AppIdentifier appIdentifier,
                                                      @Nonnull String key,
                                                      SingletonResource resource) {
        return setResource(appIdentifier.getAsPublicTenantIdentifier(), key, resource);
    }

    public synchronized SingletonResource removeResource(AppIdentifier appIdentifier,
                                                         @Nonnull String key) {
        return removeResource(appIdentifier.getAsPublicTenantIdentifier(), key);
    }

    public synchronized void clearAllResourcesWithResourceKey(String inputKey) {
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
    public synchronized SingletonResource setResource(@Nonnull String key,
                                                      SingletonResource resource) {
        return setResource(new TenantIdentifier(null, null, null), key, resource);
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
