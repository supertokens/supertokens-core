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

import io.supertokens.pluginInterface.mfa.MfaFirstFactors;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

public class GenerateMfaConfig {
    private static final String[] FACTORS = new String[]{
            "emailpassword",
            "thirdparty",
            "otp-email",
            "otp-phone",
            "link-email",
            "link-phone"
    };

    private static final String[] OTHER_FACTORS = new String[]{
            "totp", "biometric", "custom"
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

    public static ConfigGenerator.GeneratedValueAndExpectation generate_firstFactors() {
        String[] factors = selectRandomElements(FACTORS);
        String[] customFactors = selectRandomElements(OTHER_FACTORS);

        return new ConfigGenerator.GeneratedValueAndExpectation(
                new MfaFirstFactors(factors, customFactors),
                new ConfigGenerator.Expectation("ok", factors));
    }

    public static ConfigGenerator.GeneratedValueAndExpectation generate_defaultRequiredFactors() {
        String[] factors = selectRandomElements(FACTORS);

        return new ConfigGenerator.GeneratedValueAndExpectation(
                factors,
                new ConfigGenerator.Expectation("ok", factors));
    }
}
