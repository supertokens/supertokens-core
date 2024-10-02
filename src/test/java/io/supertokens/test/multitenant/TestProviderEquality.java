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

package io.supertokens.test.multitenant;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import io.supertokens.pluginInterface.multitenancy.ThirdPartyConfig;
import org.junit.Test;

import javax.annotation.Nonnull;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Parameter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

public class TestProviderEquality {
    @Test
    public void testProviderEqualityChecksForAllFields() {
        Constructor<?> constructor = ThirdPartyConfig.Provider.class.getConstructors()[0];
        Parameter[] parameters = constructor.getParameters();
        Field[] fields = ThirdPartyConfig.Provider.class.getDeclaredFields();
        assert (parameters.length == fields.length);

        JsonObject baseObject = new JsonObject();

        for (int i = 0; i < parameters.length; i++) {
            Parameter parameter = parameters[i];
            Field field = fields[i];
            assert (parameter.getType() == field.getType());

            if (field.isAnnotationPresent(Nonnull.class)) {
                if (field.getType() == String.class) {
                    baseObject.addProperty(field.getName(), "test");
                } else if (field.getType() == Boolean.class) {
                    baseObject.addProperty(field.getName(), true);
                } else if (field.getType() == JsonObject.class) {
                    baseObject.add(field.getName(), new JsonObject());
                } else if (field.getType() == ThirdPartyConfig.ProviderClient[].class) {
                    baseObject.add(field.getName(), new JsonArray());
                } else if (field.getType() == ThirdPartyConfig.UserInfoMap.class) {
                    baseObject.add(field.getName(), new JsonObject());
                } else {
                    throw new RuntimeException("Unsupported type");
                }
            }
        }

        for (int i = 0; i < parameters.length; i++) {
            Parameter parameter = parameters[i];
            Field field = fields[i];
            assert (parameter.getType() == field.getType());

            testProviderField(field.getName(), field.getType(), baseObject);
        }
    }

    private void testProviderField(String name, Class<?> type, JsonObject baseObject) {
        JsonObject testObject1 = new Gson().fromJson(baseObject, JsonObject.class);

        if (type == String.class) {
            testObject1.addProperty(name, "test");
        } else if (type == Boolean.class) {
            testObject1.addProperty(name, true);
        } else if (type == JsonObject.class) {
            testObject1.add(name, new JsonObject());
        } else if (type == ThirdPartyConfig.ProviderClient[].class) {
            testObject1.add(name, new JsonArray());
        } else if (type == ThirdPartyConfig.UserInfoMap.class) {
            JsonObject userInfoMap = new JsonObject();
            JsonObject fromIdTokenPayload = new JsonObject();
            fromIdTokenPayload.addProperty("userId", "id");
            fromIdTokenPayload.addProperty("email", "email");
            fromIdTokenPayload.addProperty("emailVerified", "email_verified");
            userInfoMap.add("fromIdTokenPayload", fromIdTokenPayload);
            testObject1.add(name, userInfoMap);
        } else {
            throw new RuntimeException("Unsupported type");
        }

        ThirdPartyConfig.Provider provider1 = new Gson().fromJson(testObject1, ThirdPartyConfig.Provider.class);
        ThirdPartyConfig.Provider provider1copy = new Gson().fromJson(testObject1, ThirdPartyConfig.Provider.class);

        JsonObject testObject2 = new Gson().fromJson(baseObject, JsonObject.class);
        if (type == String.class) {
            testObject2.addProperty(name, "test2");
        } else if (type == Boolean.class) {
            testObject2.addProperty(name, false);
        } else if (type == JsonObject.class) {
            JsonObject obj = new JsonObject();
            obj.addProperty("hello", "world");
            testObject2.add(name, obj);
        } else if (type == ThirdPartyConfig.ProviderClient[].class) {
            JsonArray clients = new JsonArray();
            JsonObject client = new JsonObject();
            client.addProperty("clientId", "test");
            clients.add(client);
            testObject2.add(name, clients);
        } else if (type == ThirdPartyConfig.UserInfoMap.class) {
            JsonObject userInfoMap = new JsonObject();
            JsonObject fromIdTokenPayload = new JsonObject();
            fromIdTokenPayload.addProperty("userId", "id1");
            fromIdTokenPayload.addProperty("email", "email1");
            fromIdTokenPayload.addProperty("emailVerified", "email_verified1");
            userInfoMap.add("fromIdTokenPayload", fromIdTokenPayload);
            testObject2.add(name, userInfoMap);
        } else {
            throw new RuntimeException("Unsupported type");
        }

        ThirdPartyConfig.Provider provider2 = new Gson().fromJson(testObject2, ThirdPartyConfig.Provider.class);
        ThirdPartyConfig.Provider provider2copy = new Gson().fromJson(testObject2, ThirdPartyConfig.Provider.class);
        assertNotEquals(provider1, provider2);
        assertEquals(provider1, provider1copy);
        assertEquals(provider2, provider2copy);
    }

    @Test
    public void testProviderClientEqualityChecksForAllFields() {
        Constructor<?> constructor = ThirdPartyConfig.ProviderClient.class.getConstructors()[0];
        Parameter[] parameters = constructor.getParameters();
        Field[] fields = ThirdPartyConfig.ProviderClient.class.getDeclaredFields();
        assert (parameters.length == fields.length);

        JsonObject baseObject = new JsonObject();

        for (int i = 0; i < parameters.length; i++) {
            Parameter parameter = parameters[i];
            Field field = fields[i];
            assert (parameter.getType() == field.getType());

            if (field.isAnnotationPresent(Nonnull.class)) {
                if (field.getType() == String.class) {
                    baseObject.addProperty(field.getName(), "test");
                } else if (field.getType() == Boolean.class) {
                    baseObject.addProperty(field.getName(), true);
                } else if (field.getType() == JsonObject.class) {
                    baseObject.add(field.getName(), new JsonObject());
                } else {
                    throw new RuntimeException("Unsupported type");
                }
            }
        }

        for (int i = 0; i < parameters.length; i++) {
            Parameter parameter = parameters[i];
            Field field = fields[i];
            assert (parameter.getType() == field.getType());

            testProviderClientField(field.getName(), field.getType(), baseObject);
        }
    }

    private void testProviderClientField(String name, Class<?> type, JsonObject baseObject) {
        JsonObject testObject1 = new Gson().fromJson(baseObject, JsonObject.class);

        if (type == String.class) {
            testObject1.addProperty(name, "test");
        } else if (type == Boolean.class) {
            testObject1.addProperty(name, true);
        } else if (type == String[].class) {
            JsonArray array = new JsonArray();
            array.add(new JsonPrimitive("val1"));
            testObject1.add(name, array);
        } else if (type == JsonObject.class) {
            testObject1.add(name, new JsonObject());
        } else if (type == ThirdPartyConfig.ProviderClient[].class) {
            testObject1.add(name, new JsonArray());
        } else if (type == ThirdPartyConfig.UserInfoMap.class) {
            JsonObject userInfoMap = new JsonObject();
            JsonObject fromIdTokenPayload = new JsonObject();
            fromIdTokenPayload.addProperty("userId", "id");
            userInfoMap.add("fromIdTokenPayload", fromIdTokenPayload);
            testObject1.add(name, userInfoMap);
        } else {
            throw new RuntimeException("Unsupported type");
        }

        ThirdPartyConfig.ProviderClient providerClient1 = new Gson().fromJson(testObject1,
                ThirdPartyConfig.ProviderClient.class);
        ThirdPartyConfig.ProviderClient providerClient1copy = new Gson().fromJson(testObject1,
                ThirdPartyConfig.ProviderClient.class);

        JsonObject testObject2 = new Gson().fromJson(baseObject, JsonObject.class);
        if (type == String.class) {
            testObject2.addProperty(name, "test2");
        } else if (type == Boolean.class) {
            testObject2.addProperty(name, false);
        } else if (type == String[].class) {
            JsonArray array = new JsonArray();
            array.add(new JsonPrimitive("val2"));
            testObject2.add(name, array);
        } else if (type == JsonObject.class) {
            JsonObject obj = new JsonObject();
            obj.addProperty("hello", "world");
            testObject2.add(name, obj);
        } else {
            throw new RuntimeException("Unsupported type");
        }

        ThirdPartyConfig.ProviderClient providerClient2 = new Gson().fromJson(testObject2,
                ThirdPartyConfig.ProviderClient.class);
        ThirdPartyConfig.ProviderClient providerClient2copy = new Gson().fromJson(testObject2,
                ThirdPartyConfig.ProviderClient.class);
        assertNotEquals(providerClient1, providerClient2);
        assertEquals(providerClient1, providerClient1copy);
        assertEquals(providerClient2, providerClient2copy);
    }

    @Test
    public void testUserInfoMapEqualityChecksForAllFields() {
        Constructor<?> constructor = ThirdPartyConfig.UserInfoMap.class.getConstructors()[0];
        Parameter[] parameters = constructor.getParameters();
        Field[] fields = ThirdPartyConfig.UserInfoMap.class.getDeclaredFields();
        assert (parameters.length == fields.length);

        JsonObject baseObject = new JsonObject();

        for (int i = 0; i < parameters.length; i++) {
            Parameter parameter = parameters[i];
            Field field = fields[i];
            assert (parameter.getType() == field.getType());

            if (field.isAnnotationPresent(Nonnull.class)) {
                if (field.getType() == ThirdPartyConfig.UserInfoMapKeyValue.class) {
                    JsonObject userInfoMapKeyValue = new JsonObject();
                    userInfoMapKeyValue.addProperty("userId", "id");
                    userInfoMapKeyValue.addProperty("email", "email");
                    userInfoMapKeyValue.addProperty("emailVerified", "email_verified");
                    baseObject.add(field.getName(), userInfoMapKeyValue);
                } else {
                    throw new RuntimeException("Unsupported type");
                }
            }
        }

        for (int i = 0; i < parameters.length; i++) {
            Parameter parameter = parameters[i];
            Field field = fields[i];
            assert (parameter.getType() == field.getType());

            testUserInfoMapField(field.getName(), field.getType(), baseObject);
        }
    }

    private void testUserInfoMapField(String name, Class<?> type, JsonObject baseObject) {
        JsonObject testObject1 = new Gson().fromJson(baseObject, JsonObject.class);

        if (type == ThirdPartyConfig.UserInfoMapKeyValue.class) {
            JsonObject userInfoMapKeyValue = new JsonObject();
            userInfoMapKeyValue.addProperty("userId", "id1");
            userInfoMapKeyValue.addProperty("email", "email1");
            userInfoMapKeyValue.addProperty("emailVerified", "email_verified1");
            testObject1.add(name, userInfoMapKeyValue);
        } else {
            throw new RuntimeException("Unsupported type");
        }

        ThirdPartyConfig.UserInfoMap userInfoMap1 = new Gson().fromJson(testObject1,
                ThirdPartyConfig.UserInfoMap.class);
        ThirdPartyConfig.UserInfoMap userInfoMap1copy = new Gson().fromJson(testObject1,
                ThirdPartyConfig.UserInfoMap.class);

        JsonObject testObject2 = new Gson().fromJson(baseObject, JsonObject.class);
        if (type == ThirdPartyConfig.UserInfoMapKeyValue.class) {
            JsonObject userInfoMapKeyValue = new JsonObject();
            userInfoMapKeyValue.addProperty("userId", "id2");
            userInfoMapKeyValue.addProperty("email", "email2");
            userInfoMapKeyValue.addProperty("emailVerified", "email_verified2");
            testObject2.add(name, userInfoMapKeyValue);
        } else {
            throw new RuntimeException("Unsupported type");
        }

        ThirdPartyConfig.UserInfoMap userInfoMap2 = new Gson().fromJson(testObject2,
                ThirdPartyConfig.UserInfoMap.class);
        ThirdPartyConfig.UserInfoMap userInfoMap2copy = new Gson().fromJson(testObject2,
                ThirdPartyConfig.UserInfoMap.class);
        assertNotEquals(userInfoMap1, userInfoMap2);
        assertEquals(userInfoMap1, userInfoMap1copy);
        assertEquals(userInfoMap2, userInfoMap2copy);
    }

    @Test
    public void testUserInfoMapKeyValueEqualityChecksForAllFields() {
        Constructor<?> constructor = ThirdPartyConfig.UserInfoMapKeyValue.class.getConstructors()[0];
        Parameter[] parameters = constructor.getParameters();
        Field[] fields = ThirdPartyConfig.UserInfoMapKeyValue.class.getDeclaredFields();
        assert (parameters.length == fields.length);

        JsonObject baseObject = new JsonObject();

        for (int i = 0; i < parameters.length; i++) {
            Parameter parameter = parameters[i];
            Field field = fields[i];
            assert (parameter.getType() == field.getType());

            if (field.isAnnotationPresent(Nonnull.class)) {
                if (field.getType() == String.class) {
                    baseObject.addProperty(field.getName(), "val");
                } else {
                    throw new RuntimeException("Unsupported type");
                }
            }
        }

        for (int i = 0; i < parameters.length; i++) {
            Parameter parameter = parameters[i];
            Field field = fields[i];
            assert (parameter.getType() == field.getType());

            testUserInfoMapKeyValueField(field.getName(), field.getType(), baseObject);
        }
    }

    private void testUserInfoMapKeyValueField(String name, Class<?> type, JsonObject baseObject) {
        JsonObject testObject1 = new Gson().fromJson(baseObject, JsonObject.class);

        if (type == String.class) {
            testObject1.addProperty(name, "val1");
        } else {
            throw new RuntimeException("Unsupported type");
        }

        ThirdPartyConfig.UserInfoMapKeyValue userInfoMap1 = new Gson().fromJson(testObject1,
                ThirdPartyConfig.UserInfoMapKeyValue.class);
        ThirdPartyConfig.UserInfoMapKeyValue userInfoMap1copy = new Gson().fromJson(testObject1,
                ThirdPartyConfig.UserInfoMapKeyValue.class);

        JsonObject testObject2 = new Gson().fromJson(baseObject, JsonObject.class);
        if (type == String.class) {
            JsonObject userInfoMapKeyValue = new JsonObject();
            testObject2.addProperty(name, "val2");
        } else {
            throw new RuntimeException("Unsupported type");
        }

        ThirdPartyConfig.UserInfoMapKeyValue userInfoMap2 = new Gson().fromJson(testObject2,
                ThirdPartyConfig.UserInfoMapKeyValue.class);
        ThirdPartyConfig.UserInfoMapKeyValue userInfoMap2copy = new Gson().fromJson(testObject2,
                ThirdPartyConfig.UserInfoMapKeyValue.class);
        assertNotEquals(userInfoMap1, userInfoMap2);
        assertEquals(userInfoMap1, userInfoMap1copy);
        assertEquals(userInfoMap2, userInfoMap2copy);
    }
}