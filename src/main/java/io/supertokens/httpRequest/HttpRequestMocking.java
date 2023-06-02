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

package io.supertokens.httpRequest;

import io.supertokens.Main;
import io.supertokens.ResourceDistributor;
import io.supertokens.ResourceDistributor.SingletonResource;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifier;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

public class HttpRequestMocking extends ResourceDistributor.SingletonResource {

    private static final String RESOURCE_KEY = "io.supertokens.httpRequest.HttpRequestMocking";

    private Map<String, URLGetter> urlMap = new HashMap<>();

    private HttpRequestMocking() {
    }

    public static HttpRequestMocking getInstance(Main main) {
        SingletonResource instance = null;
        try {
            instance = main.getResourceDistributor()
                    .getResource(new TenantIdentifier(null, null, null), RESOURCE_KEY);
        } catch (TenantOrAppNotFoundException e) {
            instance = main.getResourceDistributor()
                    .setResource(new TenantIdentifier(null, null, null), RESOURCE_KEY, new HttpRequestMocking());
        }
        if (instance == null) {
            instance = main.getResourceDistributor()
                    .setResource(new TenantIdentifier(null, null, null), RESOURCE_KEY, new HttpRequestMocking());
        }
        return (HttpRequestMocking) instance;
    }

    public void setMockURL(String key, URLGetter urlGetter) {
        urlMap.put(key, urlGetter);
    }

    public URL getMockURL(String key, String url) throws MalformedURLException {
        URLGetter urlGetter = urlMap.get(key);
        if (urlGetter != null) {
            return urlGetter.getUrl(url);
        }
        return null;
    }

    public abstract static class URLGetter {
        public abstract URL getUrl(String url) throws MalformedURLException;
    }
}
