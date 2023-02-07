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

package io.supertokens.inmemorydb.config;

import io.supertokens.ResourceDistributor;
import io.supertokens.exceptions.TenantOrAppNotFoundException;
import io.supertokens.inmemorydb.Start;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifier;

public class Config extends ResourceDistributor.SingletonResource {

    private static final String RESOURCE_KEY = "io.supertokens.inmemorydb.config.Config";
    private final SQLiteConfig config;

    private Config() {
        this.config = new SQLiteConfig();
    }

    private static Config getInstance(Start start) {
        try {
            return (Config) start.getResourceDistributor()
                    .getResource(new TenantIdentifier(null, null, null), RESOURCE_KEY);
        } catch (TenantOrAppNotFoundException e) {
            throw new IllegalStateException("Should never come here");
        }
    }

    public static void loadConfig(Start start) {
        start.getResourceDistributor().setResource(new TenantIdentifier(null, null, null), RESOURCE_KEY, new Config());
    }

    public static SQLiteConfig getConfig(Start start) {
        if (getInstance(start) == null) {
            throw new IllegalStateException("Please call loadConfig() before calling getConfig()");
        }
        return getInstance(start).config;
    }

}
