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

package io.supertokens.utils;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonAlias;
import io.supertokens.pluginInterface.exceptions.InvalidConfigException;

import java.lang.reflect.Field;
import java.util.Map;

public class ConfigMapper {

    public static <T> T mapConfig(JsonObject config, Class<T> clazz) throws InvalidConfigException {
        try {
            T result = clazz.newInstance();
            for (Map.Entry<String, JsonElement> entry : config.entrySet()) {
                Field field = findField(clazz, entry.getKey());
                if (field != null) {
                    setValue(result, field, entry.getValue());
                }
            }
            return result;
        } catch (InstantiationException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    private static <T> Field findField(Class<T> clazz, String key) {
        Field[] fields = clazz.getDeclaredFields();

        for (Field field : fields) {
            if (field.getName().equals(key)) {
                return field;
            }

            // Check for JsonProperty annotation
            JsonProperty jsonProperty = field.getAnnotation(JsonProperty.class);
            if (jsonProperty != null && jsonProperty.value().equals(key)) {
                return field;
            }

            // Check for JsonAlias annotation
            JsonAlias jsonAlias = field.getAnnotation(JsonAlias.class);
            if (jsonAlias != null) {
                for (String alias : jsonAlias.value()) {
                    if (alias.equals(key)) {
                        return field;
                    }
                }
            }
        }

        return null; // Field not found
    }

    private static <T> void setValue(T object, Field field, JsonElement value) throws InvalidConfigException {
        field.setAccessible(true);
        Object convertedValue = convertJsonElementToTargetType(value, field.getType(), field.getName());
        if (convertedValue != null || isNullable(field.getType())) {
            try {
                field.set(object, convertedValue);
            } catch (IllegalAccessException e) {
                throw new IllegalStateException("should never happen");
            }
        }
    }

    private static boolean isNullable(Class<?> type) {
        return !type.isPrimitive();
    }

    private static Object convertJsonElementToTargetType(JsonElement value, Class<?> targetType, String fieldName)
            throws InvalidConfigException {
        // If the value is JsonNull, return null for any type
        if (value instanceof JsonNull || value == null) {
            return null;
        }

        try {
            if (targetType == String.class) {
                return value.getAsString();
            } else if (targetType == Integer.class || targetType == int.class) {
                if (value.getAsDouble() == (double) value.getAsInt()) {
                    return value.getAsInt();
                }
            } else if (targetType == Long.class || targetType == long.class) {
                if (value.getAsDouble() == (double) value.getAsLong()) {
                    return value.getAsLong();
                }
            } else if (targetType == Double.class || targetType == double.class) {
                return value.getAsDouble();
            } else if (targetType == Float.class || targetType == float.class) {
                return value.getAsFloat();
            } else if (targetType == Boolean.class || targetType == boolean.class) {
                // Handle boolean conversion from strings like "true", "false"
                return handleBooleanConversion(value, fieldName);
            }
        } catch (NumberFormatException e) {
            // do nothing, will fall into InvalidConfigException
        }

        // Throw an exception for unsupported conversions
        throw new InvalidConfigException("'" + fieldName + "' must be of type " + targetType.getSimpleName());
    }

    private static Object handleBooleanConversion(JsonElement value, String fieldName) throws InvalidConfigException {
        // Handle boolean conversion from strings like "true", "false"
        if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
            String stringValue = value.getAsString().toLowerCase();
            if (stringValue.equals("true")) {
                return true;
            } else if (stringValue.equals("false")) {
                return false;
            }
        } else if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isBoolean()) {
            return value.getAsBoolean();
        }

        // Throw an exception for unsupported conversions
        throw new InvalidConfigException("'" + fieldName + "' must be of type boolean");
    }
}
