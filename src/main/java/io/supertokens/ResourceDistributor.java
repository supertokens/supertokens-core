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

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// the purpose of this class is to tie singleton classes to s specific main instance. So that
// when the main instance dies, those singleton classes die too.

public class ResourceDistributor {

    private final Object lock = new Object();
    private Map<KeyClass, SingletonResource> resources = new HashMap<>();

    public SingletonResource getResource(@Nullable String connectionUriDomain, @Nullable String tenantId,
                                         @Nonnull String key) {
        synchronized (lock) {
            // first we do exact match
            SingletonResource resource = resources.get(new KeyClass(connectionUriDomain, tenantId, key));
            if (resource != null) {
                return resource;
            }

            // then we prioritise based on connectionUriDomain match
            resource = resources.get(new KeyClass(connectionUriDomain, null, key));
            if (resource != null) {
                return resource;
            }

            // then we prioritise based on tenantId match
            resource = resources.get(new KeyClass(null, tenantId, key));
            if (resource != null) {
                return resource;
            }

            // then we return the base case
            resource = resources.get(new KeyClass(null, null, key));
            return resource;
        }
    }

    @Deprecated
    public SingletonResource getResource(@Nonnull String key) {
        synchronized (lock) {
            return resources.get(new KeyClass(null, null, key));
        }
    }

    public SingletonResource setResource(@Nullable String connectionUriDomain, @Nullable String tenantId,
                                         @Nonnull String key,
                                         SingletonResource resource) {
        synchronized (lock) {
            SingletonResource alreadyExists = resources.get(new KeyClass(connectionUriDomain, tenantId, key));
            if (alreadyExists != null) {
                return alreadyExists;
            }
            resources.put(new KeyClass(connectionUriDomain, tenantId, key), resource);
            return resource;
        }
    }

    public void clearAllResourcesWithResourceKey(String inputKey) {
        List<KeyClass> toRemove = new ArrayList<>();
        synchronized (lock) {
            resources.forEach((key, value) -> {
                if (key.key.equals(inputKey)) {
                    toRemove.add(key);
                }
            });
            for (KeyClass keyClass : toRemove) {
                resources.remove(keyClass);
            }
        }
    }

    public Map<KeyClass, SingletonResource> getAllResourcesWithResourceKey(String inputKey) {
        Map<KeyClass, SingletonResource> result = new HashMap<>();
        synchronized (lock) {
            resources.forEach((key, value) -> {
                if (key.key.equals(inputKey)) {
                    result.put(key, value);
                }
            });
        }
        return result;
    }


    public void clearAllResourcesForTenantWithExactMatch(String connectionUriDomain, String tenantId) {
        List<KeyClass> toRemove = new ArrayList<>();
        synchronized (lock) {
            resources.forEach((key, value) -> {
                if (key.tenantId.equals(tenantId) && key.connectionUriDomain.equals(connectionUriDomain)) {
                    toRemove.add(key);
                }
            });
            for (KeyClass keyClass : toRemove) {
                resources.remove(keyClass);
            }
        }
    }

    @Deprecated
    public SingletonResource setResource(@Nonnull String key,
                                         SingletonResource resource) {
        return setResource(null, null, key, resource);
    }

    public static class SingletonResource {

    }

    public static class KeyClass {
        @Nonnull
        private String key;

        @Nonnull
        private final String connectionUriDomain;

        @Nonnull
        private final String tenantId;

        public KeyClass(@Nullable String connectionUriDomain, @Nullable String tenantId, @Nonnull String key) {
            this.key = key;
            this.connectionUriDomain = connectionUriDomain == null ? "" : connectionUriDomain;
            this.tenantId = tenantId == null ? "" : tenantId;
        }

        public String getConnectionUriDomain() {
            return connectionUriDomain.equals("") ? null : connectionUriDomain;
        }

        public String getTenantId() {
            return tenantId.equals("") ? null : tenantId;
        }

        @Override
        public boolean equals(Object other) {
            if (other instanceof KeyClass) {
                KeyClass otherKeyClass = (KeyClass) other;
                return otherKeyClass.tenantId.equals(this.tenantId) &&
                        otherKeyClass.connectionUriDomain.equals(connectionUriDomain) &&
                        otherKeyClass.key.equals(key);
            }
            return false;
        }

        @Override
        public int hashCode() {
            return (this.tenantId + "|" + this.connectionUriDomain + "|" + this.key).hashCode();
        }
    }

}
