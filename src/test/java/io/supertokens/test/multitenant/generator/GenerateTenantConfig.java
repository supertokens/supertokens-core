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

public class GenerateTenantConfig {
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

    public static ConfigGenerator.GeneratedValueAndExpectation generate_coreConfig() {
        // TODO:
        return new ConfigGenerator.GeneratedValueAndExpectation(new JsonObject(),  new ConfigGenerator.Expectation("ok", new JsonObject()));
    }
}
