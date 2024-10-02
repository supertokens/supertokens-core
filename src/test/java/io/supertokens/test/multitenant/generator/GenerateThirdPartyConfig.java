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

import io.supertokens.pluginInterface.multitenancy.ThirdPartyConfig;
import io.supertokens.test.multitenant.generator.utils.NullableStringGenerator;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Random;

public class GenerateThirdPartyConfig {
    private static class ProviderListGenerator {
        private static final String[] PROVIDERS = new String[]{
                "apple",
                "active-directory",
                "discord",
                "facebook",
                "google-workspaces",
                "google",
                "linkedin",
                "okta",
                "boxy-saml",
                "custom"};

        public static String[] generate() {
            Random r = new Random();
            if (r.nextDouble() < 0.1) return null;
            int numProviders = r.nextInt(11); // Generate upto 10 providers

            boolean includeDuplicates = new Random().nextDouble() < 0.1;
            String[] providers = new String[numProviders];
            for (int i = 0; i < numProviders; i++) {
                while (true) {
                    if (r.nextDouble() < 0.02) {
                        providers[i] = null;
                        break;
                    }

                    String thirdPartyId = PROVIDERS[r.nextInt(PROVIDERS.length)];
                    if (r.nextDouble() < 0.05) {
                        thirdPartyId += NullableStringGenerator.generate(0, 2, 5);
                    }
                    if (Arrays.asList(providers).contains(thirdPartyId) && !includeDuplicates) {
                        continue; // re-try
                    }
                    providers[i] = thirdPartyId;
                    break;
                }
            }

            return providers;
        }
    }

    public static ConfigGenerator.GeneratedValueAndExpectation generate_enabled() {
        boolean enabled = new Random().nextBoolean();
        return new ConfigGenerator.GeneratedValueAndExpectation(
                enabled,
                new ConfigGenerator.Expectation("ok", enabled));
    }

    public static ConfigGenerator.GeneratedValueAndExpectation generate_useThirdPartyProvidersFromStaticConfigIfEmpty() {
        boolean val = new Random().nextBoolean();
        return new ConfigGenerator.GeneratedValueAndExpectation(
                val,
                new ConfigGenerator.Expectation("ok", val));
    }

    public static ConfigGenerator.GeneratedValueAndExpectation generate_providers()
            throws ClassNotFoundException, InvocationTargetException, NoSuchMethodException, IllegalAccessException,
            InstantiationException {

        String[] thirdPartyIds = ProviderListGenerator.generate();

        if (thirdPartyIds == null) {
            return new ConfigGenerator.GeneratedValueAndExpectation(
                    null,
                    new ConfigGenerator.Expectation("ok", new ThirdPartyConfig.Provider[0])
            );
        }

        int numProviders = thirdPartyIds.length;
        ThirdPartyConfig.Provider[] providers = new ThirdPartyConfig.Provider[numProviders];
        ConfigGenerator.Expectation[] expectations = new ConfigGenerator.Expectation[numProviders];

        HashSet<String> thirdPartyIdSet = new HashSet<>();

        for (int i = 0; i < numProviders; i++) {
            ConfigGenerator.GeneratedValueAndExpectation generatedProvider = ConfigGenerator.generate(
                    ThirdPartyConfig.Provider.class, new Object[]{thirdPartyIds[i]});
            providers[i] = (ThirdPartyConfig.Provider) generatedProvider.value;
            expectations[i] = generatedProvider.expectation;
            if (thirdPartyIdSet.contains(providers[i].thirdPartyId)) {
                expectations = Arrays.copyOf(expectations, expectations.length + 1);
                expectations[expectations.length - 1] = new ConfigGenerator.Expectation("exception",
                        "Duplicate ThirdPartyId was specified in the providers list.");
            }
            thirdPartyIdSet.add(providers[i].thirdPartyId);
        }

        return new ConfigGenerator.GeneratedValueAndExpectation(
                providers,
                new ConfigGenerator.Expectation("array", expectations)
        );
    }
}
