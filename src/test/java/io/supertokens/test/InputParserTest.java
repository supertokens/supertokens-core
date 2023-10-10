/*
 *    Copyright (c) 2022, VRAI Labs and/or its affiliates. All rights reserved.
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

import com.google.gson.JsonObject;
import io.supertokens.ProcessState;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.webserver.InputParser;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import jakarta.servlet.ServletException;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class InputParserTest {
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
    public void testParseStringOrJSONNullOrThrowError() throws Exception {
        String[] args = { "../" };

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        {
            // parse JsonObject with a String input
            String fieldName = "inputField";
            String value = "testVal";

            JsonObject request = new JsonObject();
            request.addProperty(fieldName, value);

            String response = InputParser.parseStringOrJSONNullOrThrowError(request, fieldName, false);
            assertEquals(value, response);
        }
        {
            // parse a JsonObject when field doesnt exist
            String fieldName = "field";

            String responseMessage = null;
            try {
                InputParser.parseStringOrJSONNullOrThrowError(new JsonObject(), fieldName, false);
            } catch (ServletException e) {
                responseMessage = e.getRootCause().getMessage();
            }

            assertNotNull(responseMessage);
            assertEquals("Field name '" + fieldName + "' is invalid in JSON input", responseMessage);
        }

        {
            // parse a JsonObject when field doesnt exist and nullable set to true
            assertNull(InputParser.parseStringOrJSONNullOrThrowError(new JsonObject(), "field", true));
        }

        {
            // parse a JsonObject when field contains JSON Null
            String fieldName = "field";
            JsonObject request = new JsonObject();
            request.add(fieldName, null);

            // with nullable set to false
            assertNull(InputParser.parseStringOrJSONNullOrThrowError(request, fieldName, false));

            // with nullable set to true
            assertNull(InputParser.parseStringOrJSONNullOrThrowError(request, fieldName, true));

        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testParseStringOrThrowError() throws Exception {
        String[] args = { "../" };

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        JsonObject json = new JsonObject();
        json.addProperty("untrimed mixedcase email", "userName@DoMaIn.com       ");
        json.addProperty("email", "username@domain.com");
        json.addProperty("untrimed mixedcase text", "   TexT    ");
        json.addProperty("mixedcase text", "TeXt");

        assertEquals(InputParser.parseStringOrThrowError(json, "untrimed mixedcase email", false), "username@domain.com");
        assertEquals(InputParser.parseStringOrThrowError(json, "email", false), "username@domain.com");
        assertEquals(InputParser.parseStringOrThrowError(json, "untrimed mixedcase text", false), "TexT");
        assertEquals(InputParser.parseStringOrThrowError(json, "mixedcase text", false), "TeXt");
        assertNull(InputParser.parseStringOrThrowError(json, "undefined", true));

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testGetQueryParamOrThrowError() throws Exception {
        String[] args = { "../" };

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        HttpServletRequest request = mock(HttpServletRequest.class);

        when(request.getParameter("untrimed mixedcase email")).thenReturn("userName@DoMaIn.com       ");
        when(request.getParameter("email")).thenReturn("username@domain.com");
        when(request.getParameter("untrimed mixedcase text")).thenReturn("   TexT    ");
        when(request.getParameter("mixedcase text")).thenReturn("TeXt");

        assertEquals(InputParser.getQueryParamOrThrowError(request, "untrimed mixedcase email", false), "username@domain.com");
        assertEquals(InputParser.getQueryParamOrThrowError(request, "email", false), "username@domain.com");
        assertEquals(InputParser.getQueryParamOrThrowError(request, "untrimed mixedcase text", false), "TexT");
        assertEquals(InputParser.getQueryParamOrThrowError(request, "mixedcase text", false), "TeXt");
        assertThrows(ServletException.class, () -> InputParser.getQueryParamOrThrowError(request, "undefined", true));

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }
}
