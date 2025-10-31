/*
 *    Copyright (c) 2025, VRAI Labs and/or its affiliates. All rights reserved.
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

package io.supertokens.saml;

import java.util.HashMap;
import java.util.Map;

import org.opensaml.core.config.InitializationException;
import org.opensaml.core.config.InitializationService;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;

public class SAMLBootstrap {
    private static volatile boolean initialized = false;

    private SAMLBootstrap() {}

    public static void initialize() {
        if (initialized) {
            return;
        }
        synchronized (SAMLBootstrap.class) {
            if (initialized) {
                return;
            }
            try {
                InitializationService.initialize();
                initialized = true;
            } catch (InitializationException e) {
                throw new RuntimeException("Failed to initialize OpenSAML", e);
            }
        }
    }
}
