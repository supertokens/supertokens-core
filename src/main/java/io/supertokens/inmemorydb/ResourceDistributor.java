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
 *
 */

package io.supertokens.inmemorydb;

import java.util.HashMap;
import java.util.Map;

// the purpose of this class is to tie singleton classes to s specific main instance. So that 
// when the main instance dies, those singleton classes die too.

public class ResourceDistributor {

    private final Object lock = new Object();
    private Map<String, SingletonResource> resources = new HashMap<String, SingletonResource>();

    public SingletonResource getResource(String key) {
        return resources.get(key);
    }

    public SingletonResource setResource(String key, SingletonResource resource) {
        synchronized (lock) {
            resources.putIfAbsent(key, resource);
            return resource;
        }
    }

    public static class SingletonResource {

    }

}
