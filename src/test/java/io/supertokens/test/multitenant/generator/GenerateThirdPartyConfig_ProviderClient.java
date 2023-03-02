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
import io.supertokens.test.multitenant.generator.utils.JsonObjectGenerator;
import io.supertokens.test.multitenant.generator.utils.NullableBoolGenerator;
import io.supertokens.test.multitenant.generator.utils.NullableStringGenerator;

import java.util.Arrays;
import java.util.Random;

public class GenerateThirdPartyConfig_ProviderClient {
    // TODO: generate valid and invalid configs based on thirdPartyId

    public static class ScopeGenerator {
        public static String[] generate() {
            Random random = new Random();
            // Randomly choose array length between 0 and 5
            int arrayLength = random.nextInt(6);
            String[] array = new String[arrayLength];
            for (int i = 0; i < arrayLength; i++) {
                // Randomly choose whether to add a null value
                if (random.nextDouble() < 0.2) {  // 20% chance of null
                    array[i] = null;
                } else {
                    // Randomly choose string length between 0 and 12
                    int stringLength = random.nextInt(13);
                    // Randomly generate string with uppercase letters and digits
                    StringBuilder sb = new StringBuilder();
                    for (int j = 0; j < stringLength; j++) {
                        char c = (char) (random.nextInt(36) + 65);
                        sb.append(c);
                    }
                    array[i] = sb.toString();
                }
            }
            return array;
        }
    }

    public static ConfigGenerator.GeneratedValueAndExpectation generate_clientType() {
        String clientType = NullableStringGenerator.generate();

        return new ConfigGenerator.GeneratedValueAndExpectation(
                clientType,
                new ConfigGenerator.Expectation("ok", clientType)
        );
    }

    public static ConfigGenerator.GeneratedValueAndExpectation generate_clientId() {
        String clientId = NullableStringGenerator.generate();
        if (clientId == null) {
            return new ConfigGenerator.GeneratedValueAndExpectation(
                    clientId,
                    new ConfigGenerator.Expectation("exception", "clientId cannot be null")
            );
        }
        return new ConfigGenerator.GeneratedValueAndExpectation(
                clientId,
                new ConfigGenerator.Expectation("ok", clientId)
        );
    }

    public static ConfigGenerator.GeneratedValueAndExpectation generate_clientSecret() {
        String clientSecret = NullableStringGenerator.generate();

        return new ConfigGenerator.GeneratedValueAndExpectation(
                clientSecret,
                new ConfigGenerator.Expectation("ok", clientSecret)
        );
    }

    public static ConfigGenerator.GeneratedValueAndExpectation generate_scope() {
        if (new Random().nextInt(10) == 0) {
            return new ConfigGenerator.GeneratedValueAndExpectation(
                    null,
                    new ConfigGenerator.Expectation("ok", null)
            );
        }

        String[] scope = ScopeGenerator.generate();
        if (Arrays.asList(scope).contains(null)) {
            return new ConfigGenerator.GeneratedValueAndExpectation(
                    scope,
                    new ConfigGenerator.Expectation("exception", "scope array cannot contain a null")
            );
        }
        return new ConfigGenerator.GeneratedValueAndExpectation(
                scope,
                new ConfigGenerator.Expectation("ok", scope)
        );
    }

    public static ConfigGenerator.GeneratedValueAndExpectation generate_forcePKCE() {
        Boolean forcePKCE = NullableBoolGenerator.generate();
        return new ConfigGenerator.GeneratedValueAndExpectation(
                forcePKCE,
                new ConfigGenerator.Expectation("ok", forcePKCE)
        );
    }

    public static ConfigGenerator.GeneratedValueAndExpectation generate_additionalConfig() {
        // TODO: provider specific generation and validation
        JsonObject config = JsonObjectGenerator.generate();

        return new ConfigGenerator.GeneratedValueAndExpectation(
                config,
                new ConfigGenerator.Expectation("ok", config)
        );
    }
}
