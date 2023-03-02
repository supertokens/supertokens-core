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

public class URLGenerator {
    private static final String[] PROTOCOLS = {"http", "https"};
    private static final String ALPHABET = "abcdefghijklmnopqrstuvwxyz0123456789";
    private static final int DOMAIN_LENGTH = 10; // the length of the domain you want to generate
    private static final int SUBDOMAIN_LENGTH = 5; // the length of the subdomain you want to generate
    private static final int PATH_LENGTH = 10; // the length of the path you want to generate

    public static String generate() {
        return generate(0.1);
    }

    public static String generate(double nullProbability) {
        if (new Random().nextDouble() < nullProbability) {
            return null; // Return null 10% of the times
        }

        Random random = new Random();
        StringBuilder sb = new StringBuilder();

        // randomize protocol
        int randomProtocolIndex = random.nextInt(PROTOCOLS.length);
        String randomProtocol = PROTOCOLS[randomProtocolIndex];
        sb.append(randomProtocol).append("://");

        // randomize subdomain
        for (int i = 0; i < SUBDOMAIN_LENGTH; i++) {
            int randomIndex = random.nextInt(ALPHABET.length());
            char randomChar = ALPHABET.charAt(randomIndex);
            sb.append(randomChar);
        }
        sb.append(".");

        // randomize domain
        for (int i = 0; i < DOMAIN_LENGTH; i++) {
            int randomIndex = random.nextInt(ALPHABET.length());
            char randomChar = ALPHABET.charAt(randomIndex);
            sb.append(randomChar);
        }

        // randomize path
        sb.append("/");
        for (int i = 0; i < PATH_LENGTH; i++) {
            int randomIndex = random.nextInt(ALPHABET.length());
            char randomChar = ALPHABET.charAt(randomIndex);
            sb.append(randomChar);
        }

        return sb.toString();
    }
}
