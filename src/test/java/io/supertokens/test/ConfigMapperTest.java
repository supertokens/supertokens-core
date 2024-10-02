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

package io.supertokens.test;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.gson.JsonObject;
import io.supertokens.pluginInterface.exceptions.InvalidConfigException;
import io.supertokens.utils.ConfigMapper;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class ConfigMapperTest {

    public static class DummyConfig {
        @JsonProperty
        int int_property = -1;

        @JsonProperty
        long long_property = -1;

        @JsonProperty
        float float_property = -1;

        @JsonProperty
        double double_property = -1;

        @JsonProperty
        String string_property = "default_string";

        @JsonProperty
        boolean bool_property;

        @JsonProperty
        Long nullable_long_property = new Long(-1);
    }

    @Test
    public void testAllValidConversions() throws Exception {
        // Test defaults
        {
            JsonObject config = new JsonObject();
            assertEquals(-1, ConfigMapper.mapConfig(config, DummyConfig.class).int_property);
            assertEquals(-1, ConfigMapper.mapConfig(config, DummyConfig.class).long_property);
            assertEquals(-1, ConfigMapper.mapConfig(config, DummyConfig.class).float_property, 0.0001);
            assertEquals(-1, ConfigMapper.mapConfig(config, DummyConfig.class).double_property, 0.0001);
            assertEquals("default_string", ConfigMapper.mapConfig(config, DummyConfig.class).string_property);
            assertEquals(new Long(-1), ConfigMapper.mapConfig(config, DummyConfig.class).nullable_long_property);
        }

        // valid for int
        {
            JsonObject config = new JsonObject();
            config.addProperty("int_property", "100");
            assertEquals(100, ConfigMapper.mapConfig(config, DummyConfig.class).int_property);
        }
        {
            JsonObject config = new JsonObject();
            config.addProperty("int_property", 100);
            assertEquals(100, ConfigMapper.mapConfig(config, DummyConfig.class).int_property);
        }

        // valid for long
        {
            JsonObject config = new JsonObject();
            config.addProperty("long_property", "100");
            assertEquals(100, ConfigMapper.mapConfig(config, DummyConfig.class).long_property);
        }
        {
            JsonObject config = new JsonObject();
            config.addProperty("long_property", 100);
            assertEquals(100, ConfigMapper.mapConfig(config, DummyConfig.class).long_property);
        }

        // valid for float
        {
            JsonObject config = new JsonObject();
            config.addProperty("float_property", 100);
            System.out.println(ConfigMapper.mapConfig(config, DummyConfig.class).float_property);
            assertEquals((float) 100, ConfigMapper.mapConfig(config, DummyConfig.class).float_property, 0.001);
        }
        {
            JsonObject config = new JsonObject();
            config.addProperty("float_property", 3.14);
            assertEquals((float) 3.14, ConfigMapper.mapConfig(config, DummyConfig.class).float_property, 0.001);
        }
        {
            JsonObject config = new JsonObject();
            config.addProperty("float_property", "100");
            assertEquals((float) 100, ConfigMapper.mapConfig(config, DummyConfig.class).float_property, 0.001);
        }
        {
            JsonObject config = new JsonObject();
            config.addProperty("float_property", "3.14");
            assertEquals((float) 3.14, ConfigMapper.mapConfig(config, DummyConfig.class).float_property, 0.001);
        }

        // valid double
        {
            JsonObject config = new JsonObject();
            config.addProperty("double_property", 100);
            assertEquals((double) 100, ConfigMapper.mapConfig(config, DummyConfig.class).double_property, 0.001);
        }
        {
            JsonObject config = new JsonObject();
            config.addProperty("double_property", 3.14);
            assertEquals((double) 3.14, ConfigMapper.mapConfig(config, DummyConfig.class).double_property, 0.001);
        }
        {
            JsonObject config = new JsonObject();
            config.addProperty("double_property", "100");
            assertEquals((double) 100, ConfigMapper.mapConfig(config, DummyConfig.class).double_property, 0.001);
        }
        {
            JsonObject config = new JsonObject();
            config.addProperty("double_property", "3.14");
            assertEquals((double) 3.14, ConfigMapper.mapConfig(config, DummyConfig.class).double_property, 0.001);
        }

        // valid for bool
        {
            JsonObject config = new JsonObject();
            config.addProperty("bool_property", "true");
            assertEquals(true, ConfigMapper.mapConfig(config, DummyConfig.class).bool_property);
        }
        {
            JsonObject config = new JsonObject();
            config.addProperty("bool_property", "TRUE");
            assertEquals(true, ConfigMapper.mapConfig(config, DummyConfig.class).bool_property);
        }
        {
            JsonObject config = new JsonObject();
            config.addProperty("bool_property", "false");
            assertEquals(false, ConfigMapper.mapConfig(config, DummyConfig.class).bool_property);
        }
        {
            JsonObject config = new JsonObject();
            config.addProperty("bool_property", true);
            assertEquals(true, ConfigMapper.mapConfig(config, DummyConfig.class).bool_property);
        }
        {
            JsonObject config = new JsonObject();
            config.addProperty("bool_property", false);
            assertEquals(false, ConfigMapper.mapConfig(config, DummyConfig.class).bool_property);
        }

        // valid for string
        {
            JsonObject config = new JsonObject();
            config.addProperty("string_property", "true");
            assertEquals("true", ConfigMapper.mapConfig(config, DummyConfig.class).string_property);
        }
        {
            JsonObject config = new JsonObject();
            config.addProperty("string_property", true);
            assertEquals("true", ConfigMapper.mapConfig(config, DummyConfig.class).string_property);
        }
        {
            JsonObject config = new JsonObject();
            config.addProperty("string_property", 100);
            assertEquals("100", ConfigMapper.mapConfig(config, DummyConfig.class).string_property);
        }
        {
            JsonObject config = new JsonObject();
            config.addProperty("string_property", 3.14);
            assertEquals("3.14", ConfigMapper.mapConfig(config, DummyConfig.class).string_property);
        }
        {
            JsonObject config = new JsonObject();
            config.addProperty("string_property", "hello");
            assertEquals("hello", ConfigMapper.mapConfig(config, DummyConfig.class).string_property);
        }

        {
            JsonObject config = new JsonObject();
            config.add("string_property", null);
            assertEquals(null, ConfigMapper.mapConfig(config, DummyConfig.class).string_property);
        }

        // valid for nullable long
        {
            JsonObject config = new JsonObject();
            config.add("nullable_long_property", null);
            assertEquals(null, ConfigMapper.mapConfig(config, DummyConfig.class).nullable_long_property);
        }

        {
            JsonObject config = new JsonObject();
            config.addProperty("nullable_long_property", 100);
            assertEquals(new Long(100), ConfigMapper.mapConfig(config, DummyConfig.class).nullable_long_property);
        }
    }

    @Test
    public void testInvalidConversions() throws Exception {
        String[] properties = new String[]{
                "int_property",
                "int_property",
                "int_property",
                "int_property",
                "int_property",

                "long_property",
                "long_property",
                "long_property",
                "long_property",

                "float_property",
                "float_property",
                "float_property",

                "double_property",
                "double_property",
                "double_property",
        };
        Object[] values = new Object[]{
                "abcd", // int
                "", // int
                true, // int
                new Double(4.5), // int
                new Long(1234567892342l), // int

                "abcd", // long
                "", // long
                true, // long
                new Double(4.5), // long

                "abcd", // float
                "", // float
                true, // float

                "abcd", // double
                "", // double
                true, // double
        };

        String[] expectedErrorMessages = new String[]{
                "'int_property' must be of type int", // int
                "'int_property' must be of type int", // int
                "'int_property' must be of type int", // int
                "'int_property' must be of type int", // int
                "'int_property' must be of type int", // int

                "'long_property' must be of type long", // long
                "'long_property' must be of type long", // long
                "'long_property' must be of type long", // long
                "'long_property' must be of type long", // long

                "'float_property' must be of type float", // float
                "'float_property' must be of type float", // float
                "'float_property' must be of type float", // float

                "'double_property' must be of type double", // double
                "'double_property' must be of type double", // double
                "'double_property' must be of type double", // double
        };

        for (int i = 0; i < properties.length; i++) {
            try {
                System.out.println("Test case " + i);
                JsonObject config = new JsonObject();
                if (values[i] == null) {
                    config.add(properties[i], null);
                } else if (values[i] instanceof String) {
                    config.addProperty(properties[i], (String) values[i]);
                } else if (values[i] instanceof Boolean) {
                    config.addProperty(properties[i], (Boolean) values[i]);
                } else if (values[i] instanceof Number) {
                    config.addProperty(properties[i], (Number) values[i]);
                } else {
                    throw new RuntimeException("Invalid type");
                }
                DummyConfig dc = ConfigMapper.mapConfig(config, DummyConfig.class);
                fail();
            } catch (InvalidConfigException e) {
                assertEquals(expectedErrorMessages[i], e.getMessage());
            }
        }
    }
}
