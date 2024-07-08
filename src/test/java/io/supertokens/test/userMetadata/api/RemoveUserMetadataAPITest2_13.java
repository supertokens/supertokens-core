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

public class RemoveUserMetadataAPITest2_13 {
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
    public void notExistingTest() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        String userId = "userId";

        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("userId", userId);
        JsonObject resp = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/user/metadata/remove", requestBody, 1000, 1000, null,
                SemVer.v2_13.get(), "usermetadata");

        // It should not throw
        assertEquals(1, resp.entrySet().size());
        assertEquals("OK", resp.get("status").getAsString());

        // And the metadata should be empty
        assertEquals(0, UserMetadata.getUserMetadata(process.getProcess(), userId).entrySet().size());

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void existingMetadataTest() throws Exception {
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

        UserMetadata.updateUserMetadata(process.getProcess(), userId, testMetadata);

        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("userId", userId);
        JsonObject resp = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/user/metadata/remove", requestBody, 1000, 1000, null,
                SemVer.v2_13.get(), "usermetadata");

        // It should not throw
        assertEquals(1, resp.entrySet().size());
        assertEquals("OK", resp.get("status").getAsString());

        // And the metadata should be empty
        assertEquals(0, UserMetadata.getUserMetadata(process.getProcess(), userId).entrySet().size());

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void deletedMetadataTest() throws Exception {
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

        UserMetadata.updateUserMetadata(process.getProcess(), userId, testMetadata);
        UserMetadata.deleteUserMetadata(process.getProcess(), userId);

        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("userId", userId);
        JsonObject resp = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/user/metadata/remove", requestBody, 1000, 1000, null,
                SemVer.v2_13.get(), "usermetadata");

        // It should not throw
        assertEquals(1, resp.entrySet().size());
        assertEquals("OK", resp.get("status").getAsString());

        // And the metadata should be empty
        assertEquals(0, UserMetadata.getUserMetadata(process.getProcess(), userId).entrySet().size());

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));

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

        JsonObject requestBody = new JsonObject();

        HttpResponseException error = null;
        try {
            HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/user/metadata/remove", requestBody, 1000, 1000, null,
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
}
