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

import java.lang.reflect.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class ConfigGenerator {

    public static class Expectation {
        public final String type;
        public final Object value;

        Expectation(String type, Object value) {
            this.type = type;
            this.value = value;
        }
    }

    public static class GeneratedValueAndExpectation {
        public final Object value;

        public final Expectation expectation;

        public GeneratedValueAndExpectation(Object value, Expectation expectation) {
            this.value = value;
            this.expectation = expectation;
        }
    }

    public static boolean isOk(Expectation expectation) {
        switch (expectation.type) {
            case "ok":
                return true;
            case "array":
                for (Expectation exp : (Expectation[]) expectation.value) {
                    if (!isOk(exp)) return false;
                }
                return true;
            case "map":
                for (Expectation exp : ((HashMap<String, Expectation>) expectation.value).values()) {
                    if (!isOk(exp)) return false;
                }
                return true;
        }
        return false;
    }

    public static boolean matchExceptionInExpectation(String message, Expectation expectation) {
        switch (expectation.type) {
            case "ok":
                return false;
            case "exception":
                if (message.contains((String) expectation.value)) {
                    return true;
                }
                return false;
            case "array":
                for (Expectation exp : (Expectation[]) expectation.value) {
                    if (matchExceptionInExpectation(message, exp)) return true;
                }
                return false;
            case "map":
                for (Expectation exp : ((HashMap<String, Expectation>) expectation.value).values()) {
                    if (matchExceptionInExpectation(message, exp)) return true;
                }
                return false;
        }
        return false;
    }

    public static List<String> getExceptions(Expectation expectation) {
        return getExceptions(expectation, new ArrayList<>());
    }

    private static List<String> getExceptions(Expectation expectation, ArrayList<String> exceptions) {
        switch (expectation.type) {
            case "exception":
                exceptions.add((String) expectation.value);
                break;
            case "array":
                for (Expectation exp : (Expectation[]) expectation.value) {
                    getExceptions(exp, exceptions);
                }
                break;
            case "map":
                for (Expectation exp : ((HashMap<String, Expectation>) expectation.value).values()) {
                    getExceptions(exp, exceptions);
                }
                break;
        }
        return exceptions;
    }

    public static GeneratedValueAndExpectation generate(Class<?> type)
            throws ClassNotFoundException, InvocationTargetException, NoSuchMethodException, IllegalAccessException,
            InstantiationException {
        return generate(type, new Object[0]);
    }

    public static GeneratedValueAndExpectation generate(Class<?> type, Object[] generateParams)
            throws ClassNotFoundException, NoSuchMethodException, InvocationTargetException, IllegalAccessException,
            InstantiationException {

        // Take just the class name and load the generator class from `io.supertokens.test.multitenant.generator`
        String[] classNameParts = type.getName().split("\\.");
        Class<?> generatorClass = Class.forName("io.supertokens.test.multitenant.generator.Generate" +
                classNameParts[classNameParts.length - 1].replace('$', '_'));

        // Use the first constructor
        // Also expecting declared fields are the parameters to the constructor
        // This is because, constructor parameter names are unreadable from reflection
        Constructor<?> constructor = type.getConstructors()[0];
        Parameter[] parameters = constructor.getParameters();
        Field[] fields = type.getDeclaredFields();

        assert (parameters.length == fields.length); // Expecting same number of declared fields and constructor params
        Object[] constructorParamValues = new Object[parameters.length];
        HashMap<String, Expectation> expectations = new HashMap<>(); // Map for field to Expectation

        for (int i = 0; i < parameters.length; i++) {
            Parameter parameter = parameters[i];
            Field field = fields[i];
            assert (parameter.getType() == field.getType());

            Class<?>[] paramTypes = new Class[generateParams.length];
            for (int paramIdx = 0; paramIdx < generateParams.length; paramIdx++) {
                if (generateParams[paramIdx] == null) {
                    // FIXME: Right now passing this only for the GenerateThirdPartyConfig_Provider, have used string
                    // param for all the functions. Might be good idea to consider all functions with the name and
                    // then consider the function to invoke. The below hack just works for now.
                    paramTypes[paramIdx] = String.class; // TODO: any better ideas here?
                } else {
                    paramTypes[paramIdx] = generateParams[paramIdx].getClass();
                }
            }

            // generate_<fieldName> should be defined in the GenerateXYZ class, so that it returns
            // GeneratedValueAndExpectation as result.
            Method generatorMethod = generatorClass.getMethod("generate_" + field.getName(), paramTypes);
            GeneratedValueAndExpectation generated = (GeneratedValueAndExpectation) generatorMethod.invoke(null,
                    generateParams);
            constructorParamValues[i] = generated.value;
            expectations.put(field.getName(), generated.expectation);
        }

        Object value = constructor.newInstance(constructorParamValues);
        return new GeneratedValueAndExpectation(value, new Expectation("map", expectations));
    }
}
