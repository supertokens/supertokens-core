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

 package io.supertokens.test;

 import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
 import io.supertokens.ProcessState;
 import io.supertokens.httpRequest.HttpRequest;
 import org.junit.AfterClass;
 import org.junit.Before;
 import org.junit.Rule;
 import org.junit.Test;
 import org.junit.rules.TestRule;

 
 import static org.junit.Assert.*;
 
 public class CoreConfigListAPITest {
     @Rule
     public TestRule watchman = Utils.getOnFailure();
 
     @AfterClass
     public static void afterTesting() {
         Utils.afterTesting();
     }
 
     @Before
     public void beforeEach() {
         Utils.reset();
     }
 
     @Test
     public void testRetreivingConfigProperties() throws Exception {
        String[] args = { "../" };

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        JsonObject response = HttpRequest.sendGETRequest(process.getProcess(), "", "http://localhost:3567/core-config/list", null,
        1000, 1000, null);

        assertEquals(response.get("status").getAsString(), "OK");
        JsonArray result = response.get("config").getAsJsonArray();

        for (int i = 0; i < result.size(); i++) {
            JsonObject config = result.get(i).getAsJsonObject();
            assertTrue(config.get("name").getAsJsonPrimitive().isString());
            assertTrue(config.get("description").getAsJsonPrimitive().isString());
            assertTrue(config.get("isDifferentAcrossTenants").getAsJsonPrimitive().isBoolean());
            assertTrue(config.get("type").getAsJsonPrimitive().isString());
            String type = config.get("type").getAsString();
            assertTrue(type.equals("number") || type.equals("boolean") || type.equals("string") || type.equals("enum"));
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
     }
 
 }
 