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
import io.supertokens.pluginInterface.multitenancy.*;

import java.lang.reflect.InvocationTargetException;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

public class GenerateTenantConfig {
    private static final String[] FACTORS = new String[]{
            "emailpassword1",
            "thirdparty1",
            "otp-email1",
            "otp-phone1",
            "link-email1",
            "link-phone1",
            "totp",
            "biometric",
            "custom"
    };

    private static String[] selectRandomElements(String[] inputArray) {
        Random random = new Random();
        int numElementsToSelect = random.nextInt(4);  // Randomly select 0 to 3 elements

        // Ensure numElementsToSelect is within the bounds of the array
        numElementsToSelect = Math.min(numElementsToSelect, inputArray.length);

        // Create a set to store unique indices
        Set<Integer> selectedIndices = new HashSet<>();

        // Generate random indices and select the corresponding elements
        while (selectedIndices.size() < numElementsToSelect) {
            int randomIndex = random.nextInt(inputArray.length);
            selectedIndices.add(randomIndex);
        }

        // Create an array to hold the randomly selected elements
        String[] selectedElements = new String[numElementsToSelect];

        // Fill the array with the selected elements
        int i = 0;
        for (int index : selectedIndices) {
            selectedElements[i++] = inputArray[index];
        }

        return selectedElements;
    }

    public static ConfigGenerator.GeneratedValueAndExpectation generate_tenantIdentifier() {
        // TODO: generate different appid and tenantid
        return new ConfigGenerator.GeneratedValueAndExpectation(
                new TenantIdentifier(null, null, null),
                new ConfigGenerator.Expectation("ok", new TenantIdentifier(null, null, null))
        );
    }

    public static ConfigGenerator.GeneratedValueAndExpectation generate_emailPasswordConfig()
            throws ClassNotFoundException, InvocationTargetException, NoSuchMethodException, IllegalAccessException,
            InstantiationException {
        return ConfigGenerator.generate(EmailPasswordConfig.class);
    }

    public static ConfigGenerator.GeneratedValueAndExpectation generate_passwordlessConfig()
            throws ClassNotFoundException, InvocationTargetException, NoSuchMethodException, IllegalAccessException,
            InstantiationException {
        return ConfigGenerator.generate(PasswordlessConfig.class);
    }

    public static ConfigGenerator.GeneratedValueAndExpectation generate_thirdPartyConfig()
            throws ClassNotFoundException, InvocationTargetException, NoSuchMethodException, IllegalAccessException,
            InstantiationException {
        return ConfigGenerator.generate(ThirdPartyConfig.class);
    }

    public static ConfigGenerator.GeneratedValueAndExpectation generate_firstFactors() {
        if (new Random().nextFloat() < 0.15) {
            return new ConfigGenerator.GeneratedValueAndExpectation(
                    null,
                    new ConfigGenerator.Expectation("ok", null));
        }

        String[] factors = selectRandomElements(FACTORS);
        return new ConfigGenerator.GeneratedValueAndExpectation(
                factors,
                new ConfigGenerator.Expectation("ok", factors));
    }

    public static ConfigGenerator.GeneratedValueAndExpectation generate_requiredSecondaryFactors() {
        if (new Random().nextFloat() < 0.15) {
            return new ConfigGenerator.GeneratedValueAndExpectation(
                    null,
                    new ConfigGenerator.Expectation("ok", null));
        }

        String[] factors = selectRandomElements(FACTORS);
        return new ConfigGenerator.GeneratedValueAndExpectation(
                factors,
                new ConfigGenerator.Expectation("ok", factors));
    }

    public static ConfigGenerator.GeneratedValueAndExpectation generate_coreConfig() {
        // TODO:
        return new ConfigGenerator.GeneratedValueAndExpectation(new JsonObject(),
                new ConfigGenerator.Expectation("ok", new JsonObject()));
    }
}
