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

package io.supertokens.test.multitenant.generator.utils;

import com.google.gson.JsonObject;

import java.util.Random;

public class JsonObjectGenerator {
    private static final String ALPHABET = "abcdefghijklmnopqrstuvwxyz0123456789";

    public static JsonObject generate() {
        return generate(0.25);
    }

    public static JsonObject generate(double nullProbability) {
        if (new Random().nextDouble() < nullProbability) {
            return null; // Return null 10% of the times
        }

        Random random = new Random();
        int numKeys = random.nextInt(6); // generate a random number of keys between 0 and 5
        JsonObject jsonObject = new JsonObject();

        for (int i = 0; i < numKeys; i++) {
            // generate a random key
            int keyLength = random.nextInt(11) + 5; // generate a key length between 5 and 15
            StringBuilder keyBuilder = new StringBuilder();
            for (int j = 0; j < keyLength; j++) {
                int randomIndex = random.nextInt(ALPHABET.length());
                char randomChar = ALPHABET.charAt(randomIndex);
                keyBuilder.append(randomChar);
            }
            String key = keyBuilder.toString();

            // generate a random value or null with a 20% chance of being null
            String value = null;
            if (random.nextInt(5) != 0) { // 20% chance of generating a null value
                int valueLength = random.nextInt(21) + 10; // generate a value length between 10 and 30
                StringBuilder valueBuilder = new StringBuilder();
                for (int j = 0; j < valueLength; j++) {
                    int randomIndex = random.nextInt(ALPHABET.length());
                    char randomChar = ALPHABET.charAt(randomIndex);
                    valueBuilder.append(randomChar);
                }
                value = valueBuilder.toString();
            }

            // add the key-value pair to the JSON object
            jsonObject.addProperty(key, value);
        }

        return jsonObject;
    }
}
