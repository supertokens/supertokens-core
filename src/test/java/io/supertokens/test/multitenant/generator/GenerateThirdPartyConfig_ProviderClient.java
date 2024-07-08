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
import com.google.gson.JsonPrimitive;
import io.supertokens.test.multitenant.generator.utils.JsonObjectGenerator;
import io.supertokens.test.multitenant.generator.utils.NullableBoolGenerator;
import io.supertokens.test.multitenant.generator.utils.NullableStringGenerator;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Random;

public class GenerateThirdPartyConfig_ProviderClient {
    // TODO: generate valid and invalid configs based on thirdPartyId

    private static class ScopeGenerator {
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

    private static class AdditionalConfigGenerator {
        public static ConfigGenerator.GeneratedValueAndExpectation generate(String thirdPartyId) {
            boolean valid = new Random().nextDouble() > 0.05;

            if (thirdPartyId != null) {
                if (thirdPartyId.startsWith("apple")) {
                    return generateForApple(valid);
                } else if (thirdPartyId.startsWith("google-workspaces")) {
                    return generateForGoogleWorkspaces(valid);
                } else if (thirdPartyId.startsWith("boxy-saml")) {
                    return generateForBoxy(valid);
                }
            }

            JsonObject jsonObject = JsonObjectGenerator.generate();
            return new ConfigGenerator.GeneratedValueAndExpectation(
                    jsonObject,
                    new ConfigGenerator.Expectation("ok", jsonObject)
            );
        }

        private static ConfigGenerator.GeneratedValueAndExpectation generateForApple(boolean valid) {
            if (valid) {
                JsonObject result = new JsonObject();
                result.add("keyId", new JsonPrimitive(NullableStringGenerator.generate(0)));
                result.add("teamId", new JsonPrimitive(NullableStringGenerator.generate(0)));
                result.add("privateKey", new JsonPrimitive(NullableStringGenerator.generate(0)));
                return new ConfigGenerator.GeneratedValueAndExpectation(
                        result,
                        new ConfigGenerator.Expectation("ok", result)
                );
            } else {
                String EXPECTED_ERROR = "a non empty string value must be specified for keyId, teamId and privateKey " +
                        "in the additionalConfig for Apple provider";
                int option = new Random().nextInt(3);
                String[] PROPERTIES = new String[]{"keyId", "teamId", "privateKey"};
                Random rand = new Random();

                switch (option) {
                    case 0:
                        return new ConfigGenerator.GeneratedValueAndExpectation(
                                null,
                                new ConfigGenerator.Expectation("exception", EXPECTED_ERROR)
                        );
                    case 1:
                        int numProps = rand.nextInt(3); // select 0 to 2 properties
                        HashSet<String> selectedProp = new HashSet<String>();
                        while (selectedProp.size() < numProps) {
                            int index = rand.nextInt(PROPERTIES.length);
                            selectedProp.add(PROPERTIES[index]);
                        }
                        JsonObject result = new JsonObject();
                        for (String item : selectedProp) {
                            result.add(item, new JsonPrimitive(NullableStringGenerator.generate(0)));
                        }
                        return new ConfigGenerator.GeneratedValueAndExpectation(
                                result,
                                new ConfigGenerator.Expectation("exception", EXPECTED_ERROR)
                        );
                    default:
                        result = new JsonObject();
                        String invalidProperty = PROPERTIES[rand.nextInt(PROPERTIES.length)];
                        for (String prop : PROPERTIES) {
                            if (prop.equals(invalidProperty)) {
                                result.add(prop, new JsonPrimitive(100));
                            } else {
                                result.add(prop, new JsonPrimitive(NullableStringGenerator.generate(0)));
                            }
                        }
                        result.add("directoryId", new JsonPrimitive(100));
                        return new ConfigGenerator.GeneratedValueAndExpectation(
                                result,
                                new ConfigGenerator.Expectation("exception", EXPECTED_ERROR)
                        );
                }
            }
        }

        private static ConfigGenerator.GeneratedValueAndExpectation generateForGoogleWorkspaces(boolean valid) {
            if (valid) {
                int option = new Random().nextInt(3);
                switch (option) {
                    case 0:
                        return new ConfigGenerator.GeneratedValueAndExpectation(
                                null,
                                new ConfigGenerator.Expectation("ok", null)
                        );
                    case 1:
                        return new ConfigGenerator.GeneratedValueAndExpectation(
                                new JsonObject(),
                                new ConfigGenerator.Expectation("ok", new JsonObject())
                        );
                    default:
                        JsonObject result = new JsonObject();
                        result.add("hd", new JsonPrimitive(NullableStringGenerator.generate(0)));

                        return new ConfigGenerator.GeneratedValueAndExpectation(
                                result,
                                new ConfigGenerator.Expectation("ok", result)
                        );
                }
            } else {
                String EXPECTED_ERROR = "hd in additionalConfig must be a non empty string value";
                JsonObject result = new JsonObject();
                int option = new Random().nextInt(3);
                switch (option) {
                    case 0:
                        result.add("hd", new JsonPrimitive(100));
                        return new ConfigGenerator.GeneratedValueAndExpectation(
                                result,
                                new ConfigGenerator.Expectation("exception", EXPECTED_ERROR)
                        );
                    case 1:
                        result.add("hd", null);
                        return new ConfigGenerator.GeneratedValueAndExpectation(
                                result,
                                new ConfigGenerator.Expectation("exception", EXPECTED_ERROR)
                        );
                    default:
                        result.add("hd", new JsonPrimitive(""));
                        return new ConfigGenerator.GeneratedValueAndExpectation(
                                result,
                                new ConfigGenerator.Expectation("exception", EXPECTED_ERROR)
                        );
                }
            }
        }

        private static ConfigGenerator.GeneratedValueAndExpectation generateForBoxy(boolean valid) {
            if (valid) {
                JsonObject result = new JsonObject();
                result.add("boxyURL", new JsonPrimitive(NullableStringGenerator.generate(0)));
                return new ConfigGenerator.GeneratedValueAndExpectation(
                        result,
                        new ConfigGenerator.Expectation("ok", result)
                );
            } else {
                String EXPECTED_ERROR = "a non empty string value must be specified for boxyURL in the " +
                        "additionalConfig for Boxy SAML provider";
                int option = new Random().nextInt(3);
                switch (option) {
                    case 0:
                        JsonObject result = new JsonObject();
                        result.add("boxyURL", null);
                        return new ConfigGenerator.GeneratedValueAndExpectation(
                                result,
                                new ConfigGenerator.Expectation("exception", EXPECTED_ERROR)
                        );
                    case 1:
                        result = new JsonObject();
                        result.add("boxyURL", new JsonPrimitive(""));
                        return new ConfigGenerator.GeneratedValueAndExpectation(
                                result,
                                new ConfigGenerator.Expectation("exception", EXPECTED_ERROR)
                        );
                    default:
                        result = new JsonObject();
                        result.add("boxyURL", new JsonPrimitive(100));
                        return new ConfigGenerator.GeneratedValueAndExpectation(
                                result,
                                new ConfigGenerator.Expectation("exception", EXPECTED_ERROR)
                        );
                }
            }
        }
    }

    public static ConfigGenerator.GeneratedValueAndExpectation generate_clientType(String thirdPartyId) {
        String clientType = NullableStringGenerator.generate();

        return new ConfigGenerator.GeneratedValueAndExpectation(
                clientType,
                new ConfigGenerator.Expectation("ok", clientType)
        );
    }

    public static ConfigGenerator.GeneratedValueAndExpectation generate_clientId(String thirdPartyId) {
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

    public static ConfigGenerator.GeneratedValueAndExpectation generate_clientSecret(String thirdPartyId) {
        String clientSecret = NullableStringGenerator.generate();

        return new ConfigGenerator.GeneratedValueAndExpectation(
                clientSecret,
                new ConfigGenerator.Expectation("ok", clientSecret)
        );
    }

    public static ConfigGenerator.GeneratedValueAndExpectation generate_scope(String thirdPartyId) {
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

    public static ConfigGenerator.GeneratedValueAndExpectation generate_forcePKCE(String thirdPartyId) {
        Boolean forcePKCE = NullableBoolGenerator.generate();
        return new ConfigGenerator.GeneratedValueAndExpectation(
                forcePKCE,
                new ConfigGenerator.Expectation("ok", forcePKCE)
        );
    }

    public static ConfigGenerator.GeneratedValueAndExpectation generate_additionalConfig(String thirdPartyId) {
        return AdditionalConfigGenerator.generate(thirdPartyId);
    }
}
