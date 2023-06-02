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

package io.supertokens.featureflag;

import io.supertokens.Main;
import io.supertokens.ResourceDistributor;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifier;
import org.jetbrains.annotations.TestOnly;

import java.util.HashMap;
import java.util.Map;

import org.jetbrains.annotations.TestOnly;

public class FeatureFlagTestContent extends ResourceDistributor.SingletonResource {

    public static final String EE_FOLDER_LOCATION = "validityTesting";
    public static final String ENABLED_FEATURES = "enabledFeatures";
    private static final String RESOURCE_ID = "io.supertokens.featureflag.FeatureFlagTestContent";
    private Map<String, Object> keyValue = new HashMap<String, Object>();

    private FeatureFlagTestContent() {

    }

    public static FeatureFlagTestContent getInstance(Main main) {
        try {
            return (FeatureFlagTestContent) main.getResourceDistributor()
                    .getResource(new TenantIdentifier(null, null, null), RESOURCE_ID);
        } catch (TenantOrAppNotFoundException ignored) {
            return (FeatureFlagTestContent) main.getResourceDistributor()
                    .setResource(new TenantIdentifier(null, null, null), RESOURCE_ID, new FeatureFlagTestContent());
        }
    }

    @TestOnly
    public void setKeyValue(String key, Object value) {
        if (Main.isTesting) {
            this.keyValue.put(key, value);
        }
    }

    @SuppressWarnings("unchecked")
    <T> T getValue(String key) {
        return (T) this.keyValue.get(key);
    }
}
