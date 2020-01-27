/*
 *    Copyright (c) 2020, VRAI Labs and/or its affiliates. All rights reserved.
 *
 *    This program is licensed under the SuperTokens Community License (the
 *    "License") as published by VRAI Labs. You may not use this file except in
 *    compliance with the License. You are not permitted to transfer or
 *    redistribute this file without express written permission from VRAI Labs.
 *
 *    A copy of the License is available in the file titled
 *    "SuperTokensLicense.pdf" inside this repository or included with your copy of
 *    the software or its source code. If you have not received a copy of the
 *    License, please write to VRAI Labs at team@supertokens.io.
 *
 *    Please read the License carefully before accessing, downloading, copying,
 *    using, modifying, merging, transferring or sharing this software. By
 *    undertaking any of these activities, you indicate your agreement to the terms
 *    of the License.
 *
 *    This program is distributed with certain software that is licensed under
 *    separate terms, as designated in a particular file or component or in
 *    included license documentation. VRAI Labs hereby grants you an additional
 *    permission to link the program and your derivative works with the separately
 *    licensed software that they have included with this program, however if you
 *    modify this program, you shall be solely liable to ensure compliance of the
 *    modified program with the terms of licensing of the separately licensed
 *    software.
 *
 *    Unless required by applicable law or agreed to in writing, this program is
 *    distributed under the License on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 *    CONDITIONS OF ANY KIND, either express or implied. See the License for the
 *    specific language governing permissions and limitations under the License.
 *
 */

package io.supertokens.httpRequest;

import io.supertokens.Main;
import io.supertokens.ResourceDistributor;
import io.supertokens.ResourceDistributor.SingletonResource;

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
        SingletonResource instance = main.getResourceDistributor().getResource(RESOURCE_KEY);
        if (instance == null) {
            instance = main.getResourceDistributor().setResource(RESOURCE_KEY, new HttpRequestMocking());
        }
        return (HttpRequestMocking) instance;
    }

    public void setMockURL(String key, URLGetter urlGetter) {
        urlMap.put(key, urlGetter);
    }

    URL getMockURL(String key, String url) throws MalformedURLException {
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
