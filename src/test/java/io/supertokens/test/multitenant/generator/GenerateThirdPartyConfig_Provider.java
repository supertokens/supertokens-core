/*
 *    Copyright (c) 2023, VRAI Labs and/or its affiliates. All rights reserved.
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

package io.supertokens.test.multitenant.generator;

import com.google.gson.JsonObject;
import io.supertokens.pluginInterface.multitenancy.ThirdPartyConfig;
import io.supertokens.test.multitenant.generator.utils.JsonObjectGenerator;
import io.supertokens.test.multitenant.generator.utils.NullableBoolGenerator;
import io.supertokens.test.multitenant.generator.utils.NullableStringGenerator;
import io.supertokens.test.multitenant.generator.utils.URLGenerator;

import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Random;

public class GenerateThirdPartyConfig_Provider {

    public static ConfigGenerator.GeneratedValueAndExpectation generate_thirdPartyId(String thirdPartyId) {
        if (thirdPartyId == null || thirdPartyId.equals("")) {
            return new ConfigGenerator.GeneratedValueAndExpectation(
                    thirdPartyId,
                    new ConfigGenerator.Expectation(
                            "exception", "thirdPartyId cannot be null or empty"
                    )
            );
        }
        return new ConfigGenerator.GeneratedValueAndExpectation(
                thirdPartyId,
                new ConfigGenerator.Expectation("ok", thirdPartyId)
        );
    }

    public static ConfigGenerator.GeneratedValueAndExpectation generate_name(String thirdPartyId) {
        String name = NullableStringGenerator.generate();
        return new ConfigGenerator.GeneratedValueAndExpectation(
                name,
                new ConfigGenerator.Expectation("ok", name)
        );
    }

    public static ConfigGenerator.GeneratedValueAndExpectation generate_clients(String thirdPartyId)
            throws ClassNotFoundException, InvocationTargetException, NoSuchMethodException, IllegalAccessException,
            InstantiationException {
        int numClients = new Random().nextInt(5); // Generate upto 4 clients
        ThirdPartyConfig.ProviderClient[] clients = new ThirdPartyConfig.ProviderClient[numClients];
        ConfigGenerator.Expectation[] expectations = new ConfigGenerator.Expectation[numClients];

        HashSet<String> clientTypeSet = new HashSet<>();
        for (int i = 0; i < numClients; i++) {
            ConfigGenerator.GeneratedValueAndExpectation generated = ConfigGenerator.generate(
                    ThirdPartyConfig.ProviderClient.class, new Object[]{thirdPartyId});
            clients[i] = (ThirdPartyConfig.ProviderClient) generated.value;
            expectations[i] = generated.expectation;
            if (clientTypeSet.contains(clients[i].clientType)) {
                expectations = Arrays.copyOf(expectations, expectations.length + 1);
                expectations[expectations.length - 1] = new ConfigGenerator.Expectation("exception",
                        "Duplicate clientType was specified in the clients list.");
            }
            clientTypeSet.add(clients[i].clientType);
        }

        return new ConfigGenerator.GeneratedValueAndExpectation(
                clients,
                new ConfigGenerator.Expectation("array", expectations)
        );
    }

    public static ConfigGenerator.GeneratedValueAndExpectation generate_authorizationEndpoint(String thirdPartyId) {
        String url = URLGenerator.generate();
        return new ConfigGenerator.GeneratedValueAndExpectation(
                url,
                new ConfigGenerator.Expectation("ok", url)
        );
    }

    public static ConfigGenerator.GeneratedValueAndExpectation generate_authorizationEndpointQueryParams(
            String thirdPartyId) {
        JsonObject params = JsonObjectGenerator.generate();
        return new ConfigGenerator.GeneratedValueAndExpectation(
                params,
                new ConfigGenerator.Expectation("ok", params)
        );
    }

    public static ConfigGenerator.GeneratedValueAndExpectation generate_tokenEndpoint(String thirdPartyId) {
        String url = URLGenerator.generate();
        return new ConfigGenerator.GeneratedValueAndExpectation(
                url,
                new ConfigGenerator.Expectation("ok", url)
        );
    }

    public static ConfigGenerator.GeneratedValueAndExpectation generate_tokenEndpointBodyParams(String thirdPartyId) {
        JsonObject params = JsonObjectGenerator.generate();
        return new ConfigGenerator.GeneratedValueAndExpectation(
                params,
                new ConfigGenerator.Expectation("ok", params)
        );
    }

    public static ConfigGenerator.GeneratedValueAndExpectation generate_userInfoEndpoint(String thirdPartyId) {
        String url = URLGenerator.generate();
        return new ConfigGenerator.GeneratedValueAndExpectation(
                url,
                new ConfigGenerator.Expectation("ok", url)
        );
    }

    public static ConfigGenerator.GeneratedValueAndExpectation generate_userInfoEndpointQueryParams(
            String thirdPartyId) {
        JsonObject params = JsonObjectGenerator.generate();
        return new ConfigGenerator.GeneratedValueAndExpectation(
                params,
                new ConfigGenerator.Expectation("ok", params)
        );
    }

    public static ConfigGenerator.GeneratedValueAndExpectation generate_userInfoEndpointHeaders(String thirdPartyId) {
        JsonObject params = JsonObjectGenerator.generate();
        return new ConfigGenerator.GeneratedValueAndExpectation(
                params,
                new ConfigGenerator.Expectation("ok", params)
        );
    }

    public static ConfigGenerator.GeneratedValueAndExpectation generate_jwksURI(String thirdPartyId) {
        String url = URLGenerator.generate(0.5);
        return new ConfigGenerator.GeneratedValueAndExpectation(
                url,
                new ConfigGenerator.Expectation("ok", url)
        );
    }

    public static ConfigGenerator.GeneratedValueAndExpectation generate_oidcDiscoveryEndpoint(String thirdPartyId) {
        String url = URLGenerator.generate(0.5);
        return new ConfigGenerator.GeneratedValueAndExpectation(
                url,
                new ConfigGenerator.Expectation("ok", url)
        );
    }

    public static ConfigGenerator.GeneratedValueAndExpectation generate_requireEmail(String thirdPartyId) {
        Boolean requireEmail = NullableBoolGenerator.generate();
        return new ConfigGenerator.GeneratedValueAndExpectation(
                requireEmail,
                new ConfigGenerator.Expectation("ok", requireEmail)
        );
    }

    public static ConfigGenerator.GeneratedValueAndExpectation generate_userInfoMap(String thirdPartyId) {
        return new ConfigGenerator.GeneratedValueAndExpectation(
                new ThirdPartyConfig.UserInfoMap(null, null),
                new ConfigGenerator.Expectation(
                        "ok", new ThirdPartyConfig.UserInfoMap(null, null))
        );
    }
}
