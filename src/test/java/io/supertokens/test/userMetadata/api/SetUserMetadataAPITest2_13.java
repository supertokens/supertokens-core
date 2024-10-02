/*
 *    Copyright (c) 2021, VRAI Labs and/or its affiliates. All rights reserved.
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

package io.supertokens.test.userMetadata.api;

import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import io.supertokens.ProcessState;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.test.TestingProcessManager;
import io.supertokens.test.Utils;
import io.supertokens.test.httpRequest.HttpRequestForTesting;
import io.supertokens.test.httpRequest.HttpResponseException;
import io.supertokens.usermetadata.UserMetadata;

import io.supertokens.utils.SemVer;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import static org.junit.Assert.*;

import java.util.HashMap;

public class SetUserMetadataAPITest2_13 {
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
    public void createMetadataTest() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        String userId = "userId";

        JsonObject testMetadata = new JsonObject();
        testMetadata.addProperty("number", 123);
        testMetadata.addProperty("string", "string!");

        JsonObject subObject = new JsonObject();
        subObject.add("nullSubProp", JsonNull.INSTANCE);
        subObject.addProperty("subProp", "test");
        testMetadata.add("subobject", subObject);

        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("userId", userId);
        requestBody.add("metadataUpdate", testMetadata);
        JsonObject resp = HttpRequestForTesting.sendJsonPUTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/user/metadata", requestBody, 1000, 1000, null,
                SemVer.v2_13.get(), "usermetadata");

        assertEquals(2, resp.entrySet().size());
        assertEquals("OK", resp.get("status").getAsString());
        assert (resp.has("metadata"));
        JsonObject respMetadata = resp.getAsJsonObject("metadata");
        assertEquals(testMetadata, respMetadata);

        JsonObject storedMetadata = UserMetadata.getUserMetadata(process.getProcess(), userId);
        assertEquals(testMetadata, storedMetadata);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void updateMetadataTest() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        String userId = "userId";

        JsonObject originalMetadata = new JsonObject();
        JsonObject subObject = new JsonObject();
        subObject.addProperty("subsub", "123");
        originalMetadata.add("testUpdate", subObject);
        originalMetadata.addProperty("unmodified", "123");
        originalMetadata.addProperty("cleared", 123);

        // First we create the original
        UserMetadata.updateUserMetadata(process.getProcess(), userId, originalMetadata);

        JsonObject update = new JsonObject();
        JsonObject updateSubObject = new JsonObject();
        updateSubObject.addProperty("subsubupdate", "subnew!");
        update.add("testUpdate", updateSubObject);
        update.addProperty("testNew", "new!");
        update.add("cleared", JsonNull.INSTANCE);

        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("userId", userId);
        requestBody.add("metadataUpdate", update);
        JsonObject resp = HttpRequestForTesting.sendJsonPUTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/user/metadata", requestBody, 1000, 1000, null,
                SemVer.v2_13.get(), "usermetadata");

        assertEquals(2, resp.entrySet().size());
        assertEquals("OK", resp.get("status").getAsString());
        assert (resp.has("metadata"));
        JsonObject updateResult = resp.getAsJsonObject("metadata");

        JsonObject newMetadata = UserMetadata.getUserMetadata(process.getProcess(), userId);
        assertEquals(updateResult, newMetadata);

        // We removed what we set to null
        assert (!newMetadata.has("cleared"));

        // The old metadata is left intact
        assertEquals("123", newMetadata.get("unmodified").getAsString());

        JsonObject newSubObj = newMetadata.getAsJsonObject("testUpdate");
        // The up
        assertEquals(1, newSubObj.entrySet().size());
        assertEquals("subnew!", newSubObj.get("subsubupdate").getAsString());

        assert (newMetadata.has("testNew"));
        assertEquals("new!", newMetadata.get("testNew").getAsString());

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void emptyRequestTest() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        JsonObject requestBody = new JsonObject();

        HttpResponseException error = null;
        try {
            HttpRequestForTesting.sendJsonPUTRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/user/metadata", requestBody, 1000, 1000, null,
                    SemVer.v2_13.get(), "usermetadata");
        } catch (HttpResponseException ex) {
            error = ex;
        }

        assertNotNull(error);
        assertEquals(400, error.statusCode);
        assertEquals("Http error. Status Code: 400. Message: Field name 'userId' is invalid in JSON input",
                error.getMessage());

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void noIdTest() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        JsonObject testMetadata = new JsonObject();
        testMetadata.addProperty("number", 123);
        testMetadata.addProperty("string", "string!");

        JsonObject requestBody = new JsonObject();
        requestBody.add("metadataUpdate", testMetadata);

        HttpResponseException error = null;
        try {
            HttpRequestForTesting.sendJsonPUTRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/user/metadata", requestBody, 1000, 1000, null,
                    SemVer.v2_13.get(), "usermetadata");
        } catch (HttpResponseException ex) {
            error = ex;
        }

        assertNotNull(error);
        assertEquals(400, error.statusCode);
        assertEquals("Http error. Status Code: 400. Message: Field name 'userId' is invalid in JSON input",
                error.getMessage());

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void noMetadataTest() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        String userId = "userId";

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("userId", userId);

        HttpResponseException error = null;
        try {
            HttpRequestForTesting.sendJsonPUTRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/user/metadata", requestBody, 1000, 1000, null,
                    SemVer.v2_13.get(), "usermetadata");
        } catch (HttpResponseException ex) {
            error = ex;
        }

        assertNotNull(error);
        assertEquals(400, error.statusCode);
        assertEquals("Http error. Status Code: 400. Message: Field name 'metadataUpdate' is invalid in JSON input",
                error.getMessage());

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }
}
