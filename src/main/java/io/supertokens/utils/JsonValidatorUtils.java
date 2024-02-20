/*
 *    Copyright (c) 2024, VRAI Labs and/or its affiliates. All rights reserved.
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

 import java.util.ArrayList;
 import java.util.List;
 
 import com.google.gson.JsonArray;
 import com.google.gson.JsonElement;
 import com.google.gson.JsonObject;
 
 public class JsonValidatorUtils {
     @SuppressWarnings("unchecked")
     public static <T> T parseAndValidateField(JsonObject jsonObject, String key, ValueType expectedType,
             boolean isRequired, Class<T> targetType, List<String> errors, String errorSuffix) {
         if (jsonObject.has(key)) {
             if (validateJsonFieldType(jsonObject, key, expectedType)) {
                 T value;
                 switch (expectedType) {
                     case STRING:
                         value = (T) jsonObject.get(key).getAsString();
                         break;
                     case NUMBER:
                         value = (T) jsonObject.get(key).getAsNumber();
                         break;
                     case BOOLEAN:
                         Boolean boolValue = jsonObject.get(key).getAsBoolean();
                         value = (T) boolValue;
                         break;
                     case OBJECT:
                         value = (T) jsonObject.get(key).getAsJsonObject();
                         break;
                     case ARRAY_OF_OBJECT, ARRAY_OF_STRING:
                         value = (T) jsonObject.get(key).getAsJsonArray();
                         break;
                     default:
                         value = null;
                         break;
                 }
                 if (value != null) {
                     return targetType.cast(value);
                 } else {
                     errors.add(key + " should be of type " + getTypeForErrorMessage(expectedType) + errorSuffix);
                 }
             } else {
                 errors.add(key + " should be of type " + getTypeForErrorMessage(expectedType) + errorSuffix);
             }
         } else if (isRequired) {
             errors.add(key + " is required" + errorSuffix);
         }
         return null;
     }
 
     public enum ValueType {
         STRING,
         NUMBER,
         BOOLEAN,
         OBJECT,
         ARRAY_OF_STRING,
         ARRAY_OF_OBJECT
     }
 
     private static String getTypeForErrorMessage(ValueType type) {
         return switch (type) {
             case STRING -> "string";
             case NUMBER -> "number";
             case BOOLEAN -> "boolean";
             case OBJECT -> "object";
             case ARRAY_OF_STRING -> "array of string";
             case ARRAY_OF_OBJECT -> "array of object";
         };
     }
 
     public static boolean validateJsonFieldType(JsonObject jsonObject, String key, ValueType expectedType) {
         if (jsonObject.has(key)) {
             return switch (expectedType) {
                 case STRING -> jsonObject.get(key).isJsonPrimitive() && jsonObject.getAsJsonPrimitive(key).isString()
                         && !jsonObject.get(key).getAsString().isEmpty();
                 case NUMBER -> jsonObject.get(key).isJsonPrimitive() && jsonObject.getAsJsonPrimitive(key).isNumber();
                 case BOOLEAN -> jsonObject.get(key).isJsonPrimitive() && jsonObject.getAsJsonPrimitive(key).isBoolean();
                 case OBJECT -> jsonObject.get(key).isJsonObject();
                 case ARRAY_OF_OBJECT, ARRAY_OF_STRING -> jsonObject.get(key).isJsonArray()
                         && validateArrayElements(jsonObject.getAsJsonArray(key), expectedType);
                 default -> false;
             };
         }
         return false;
     }
 
     public static boolean validateArrayElements(JsonArray array, ValueType expectedType) {
         List<JsonElement> elements = new ArrayList<>();
         array.forEach(elements::add);
 
         return switch (expectedType) {
             case ARRAY_OF_OBJECT -> elements.stream().allMatch(JsonElement::isJsonObject);
             case ARRAY_OF_STRING ->
                 elements.stream().allMatch(el -> el.isJsonPrimitive() && el.getAsJsonPrimitive().isString()
                         && !el.getAsString().isEmpty());
             default -> false;
         };
     }
 }
 