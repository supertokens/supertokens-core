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
import io.supertokens.pluginInterface.utils.ConfigMapper;
import org.junit.Assert;
import org.junit.Test;

public class ConfigMapperTest {

    public static class DummyConfig {
        @JsonProperty
        int int_property;

        @JsonProperty
        long long_property;

        @JsonProperty
        String string_property;

        @JsonProperty
        boolean bool_property;
    }

    @Test
    public void testAllValidConversions() throws Exception {
        // valid for int
        {
            JsonObject config = new JsonObject();
            config.addProperty("int_property", "100");
            Assert.assertEquals(100, ConfigMapper.mapConfig(config, DummyConfig.class).int_property);
        }
        {
            JsonObject config = new JsonObject();
            config.addProperty("int_property", 100);
            Assert.assertEquals(100, ConfigMapper.mapConfig(config, DummyConfig.class).int_property);
        }

        // valid for long
        {
            JsonObject config = new JsonObject();
            config.addProperty("long_property", "100");
            Assert.assertEquals(100, ConfigMapper.mapConfig(config, DummyConfig.class).long_property);
        }
        {
            JsonObject config = new JsonObject();
            config.addProperty("long_property", 100);
            Assert.assertEquals(100, ConfigMapper.mapConfig(config, DummyConfig.class).long_property);
        }

        // valid for bool
        {
            JsonObject config = new JsonObject();
            config.addProperty("bool_property", "true");
            Assert.assertEquals(true, ConfigMapper.mapConfig(config, DummyConfig.class).bool_property);
        }
        {
            JsonObject config = new JsonObject();
            config.addProperty("bool_property", "TRUE");
            Assert.assertEquals(true, ConfigMapper.mapConfig(config, DummyConfig.class).bool_property);
        }
        {
            JsonObject config = new JsonObject();
            config.addProperty("bool_property", "false");
            Assert.assertEquals(false, ConfigMapper.mapConfig(config, DummyConfig.class).bool_property);
        }
        {
            JsonObject config = new JsonObject();
            config.addProperty("bool_property", true);
            Assert.assertEquals(true, ConfigMapper.mapConfig(config, DummyConfig.class).bool_property);
        }
        {
            JsonObject config = new JsonObject();
            config.addProperty("bool_property", false);
            Assert.assertEquals(false, ConfigMapper.mapConfig(config, DummyConfig.class).bool_property);
        }

        // valid for string
        {
            JsonObject config = new JsonObject();
            config.addProperty("string_property", "true");
            Assert.assertEquals("true", ConfigMapper.mapConfig(config, DummyConfig.class).string_property);
        }
        {
            JsonObject config = new JsonObject();
            config.addProperty("string_property", true);
            Assert.assertEquals("true", ConfigMapper.mapConfig(config, DummyConfig.class).string_property);
        }
        {
            JsonObject config = new JsonObject();
            config.addProperty("string_property", 100);
            Assert.assertEquals("100", ConfigMapper.mapConfig(config, DummyConfig.class).string_property);
        }
        {
            JsonObject config = new JsonObject();
            config.addProperty("string_property", 3.14);
            Assert.assertEquals("3.14", ConfigMapper.mapConfig(config, DummyConfig.class).string_property);
        }
        {
            JsonObject config = new JsonObject();
            config.addProperty("string_property", "hello");
            Assert.assertEquals("hello", ConfigMapper.mapConfig(config, DummyConfig.class).string_property);
        }
    }

    @Test
    public void testInvalidConversions() throws Exception {

    }
}
