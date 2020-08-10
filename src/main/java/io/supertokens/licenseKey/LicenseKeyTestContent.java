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

package io.supertokens.licenseKey;

import io.supertokens.Main;
import io.supertokens.ResourceDistributor;
import io.supertokens.ResourceDistributor.SingletonResource;

import java.util.HashMap;
import java.util.Map;

public class LicenseKeyTestContent extends ResourceDistributor.SingletonResource {

    public static final String APP_ID_KEY = "appId";
    public static final String TIME_CREATED_KEY = "timeCreated";
    public static final String IS_INTERNET_AVAILABLE = "isInternetAvailable";
    private static final String RESOURCE_ID = "io.supertokens.licenseKey.LicenseKeyTestContent";
    private Map<String, Object> keyValue = new HashMap<String, Object>();

    private LicenseKeyTestContent() {

    }

    public static LicenseKeyTestContent getInstance(Main main) {
        SingletonResource resource = main.getResourceDistributor().getResource(RESOURCE_ID);
        if (resource == null) {
            resource = main.getResourceDistributor().setResource(RESOURCE_ID, new LicenseKeyTestContent());
        }
        return (LicenseKeyTestContent) resource;
    }

    public void setKeyValue(String key, Object value) {
        this.keyValue.put(key, value);
    }

    @SuppressWarnings("unchecked")
    <T> T getValue(String key) {
        return (T) this.keyValue.get(key);
    }

}
