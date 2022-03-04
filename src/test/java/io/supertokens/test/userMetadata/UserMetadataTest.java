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

package io.supertokens.test.userMetadata;

import io.supertokens.ProcessState;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.pluginInterface.usermetadata.sqlStorage.UserMetadataSQLStorage;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.test.TestingProcessManager;
import io.supertokens.test.Utils;
import io.supertokens.usermetadata.UserMetadata;

import org.junit.*;
import org.junit.rules.TestRule;

import static io.supertokens.test.passwordless.PasswordlessUtility.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import com.google.gson.JsonNull;
import com.google.gson.JsonObject;

/**
 * This UT encompasses tests related to update user
 */
public class UserMetadataTest {

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

    /**
     * gets the metadata for a user who doesn't have one
     *
     * @throws Exception
     */
    @Test
    public void getEmptyMetadata() throws Exception {
        String[] args = { "../" };

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        String userId = "userId";
        JsonObject metadata = UserMetadata.getUserMetadata(process.getProcess(), userId);

        assertNotNull(metadata);
        assertEquals(metadata.entrySet().size(), 0);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    /**
     * set the metadata for a user who doesn't have one
     *
     * @throws Exception
     */
    @Test
    public void createMetadata() throws Exception {
        String[] args = { "../" };

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        String userId = "userId";
        JsonObject update = new JsonObject();
        update.addProperty("test", "123");
        update.add("testNull", JsonNull.INSTANCE);
        JsonObject updateResult = UserMetadata.updateUserMetadata(process.getProcess(), userId, update);

        JsonObject newMetadata = UserMetadata.getUserMetadata(process.getProcess(), userId);

        assertEquals(updateResult, newMetadata);
        assertEquals(1, updateResult.entrySet().size());
        assertEquals("123", updateResult.get("test").getAsString());
        assert (!updateResult.has("testNull"));

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    /**
     * updates the metadata of a user and merges the result
     *
     * @throws Exception
     */
    @Test
    public void updateMetadata() throws Exception {
        String[] args = { "../" };

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

        JsonObject updateResult = UserMetadata.updateUserMetadata(process.getProcess(), userId, update);

        JsonObject newMetadata = UserMetadata.getUserMetadata(process.getProcess(), userId);

        assertEquals(updateResult, newMetadata);

        // We removed what we set to null
        assert (!newMetadata.has("toClear"));

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

}
