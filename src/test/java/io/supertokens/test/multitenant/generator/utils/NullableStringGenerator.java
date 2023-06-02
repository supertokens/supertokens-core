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

import java.util.Random;

public class NullableStringGenerator {

    public static String generate() {
        return generate(0.25);
    }

    public static String generate(double nullProbability) {
        return generate(nullProbability, 5, 15);
    }

    public static String generate(double nullProbability, int minLength, int maxLength) {
        Random random = new Random();

        if (random.nextDouble() < nullProbability) return null;

        // Randomly choose string length between min and max
        int stringLength = minLength + random.nextInt(maxLength - minLength + 1);
        // Randomly generate string with uppercase letters and digits
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < stringLength; i++) {
            char c = (char) (random.nextInt(36) + 65);
            sb.append(c);
        }
        return sb.toString();
    }
}
