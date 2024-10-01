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

import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import io.supertokens.ProcessState;
import io.supertokens.inmemorydb.Start;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.pluginInterface.exceptions.StorageTransactionLogicException;
import io.supertokens.pluginInterface.multitenancy.AppIdentifier;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.pluginInterface.usermetadata.sqlStorage.UserMetadataSQLStorage;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.test.TestingProcessManager;
import io.supertokens.test.Utils;
import io.supertokens.usermetadata.UserMetadata;
import io.supertokens.utils.MetadataUtils;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;

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
        String[] args = {"../"};

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
        String[] args = {"../"};

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

        JsonObject updateResult = UserMetadata.updateUserMetadata(process.getProcess(), userId, update);

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
    public void testUserMetadataEmptyRowLocking() throws Exception {

        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL
                || StorageLayer.getStorage(process.getProcess()) instanceof Start) {
            return;
        }

        String userId = "userId";

        JsonObject expected = new JsonObject();
        JsonObject update1 = new JsonObject();
        update1.addProperty("a", 1);
        expected.addProperty("a", 1);

        JsonObject update2 = new JsonObject();
        update2.addProperty("b", 2);
        expected.addProperty("b", 2);

        UserMetadataSQLStorage sqlStorage = (UserMetadataSQLStorage) StorageLayer.getStorage(process.getProcess());

        AtomicReference<String> t1State = new AtomicReference<>("init");
        AtomicReference<String> t2State = new AtomicReference<>("init");
        final Object syncObject = new Object();

        AtomicInteger tryCount1 = new AtomicInteger(0);
        AtomicInteger tryCount2 = new AtomicInteger(0);
        AtomicBoolean success1 = new AtomicBoolean(false);
        AtomicBoolean success2 = new AtomicBoolean(false);

        Runnable r1 = () -> {
            try {
                sqlStorage.startTransaction(con -> {
                    tryCount1.incrementAndGet();
                    JsonObject originalMetadata = sqlStorage.getUserMetadata_Transaction(new AppIdentifier(null, null),
                            con, userId);

                    synchronized (syncObject) {
                        t1State.set("read");
                        syncObject.notifyAll();
                    }

                    synchronized (syncObject) {
                        while (!t2State.get().equals("read")) {
                            try {
                                syncObject.wait();
                            } catch (InterruptedException e) {
                            }
                        }
                    }

                    JsonObject updatedMetadata = originalMetadata == null ? new JsonObject() : originalMetadata;
                    MetadataUtils.shallowMergeMetadataUpdate(updatedMetadata, update1);

                    try {
                        sqlStorage.setUserMetadata_Transaction(new AppIdentifier(null, null), con, userId,
                                updatedMetadata);
                    } catch (TenantOrAppNotFoundException e) {
                        throw new StorageTransactionLogicException(e);
                    }
                    sqlStorage.commitTransaction(con);
                    success1.set(true); // it should come here because we will try three times.
                    return null;
                });
            } catch (StorageTransactionLogicException e) {
                if (e.actualException instanceof TenantOrAppNotFoundException) {
                    throw new IllegalStateException(e.actualException);
                }
            } catch (Exception ignored) {
            }
        };

        Runnable r2 = () -> {
            try {
                sqlStorage.startTransaction(con -> {
                    tryCount2.incrementAndGet();

                    JsonObject originalMetadata = sqlStorage.getUserMetadata_Transaction(new AppIdentifier(null, null),
                            con, userId);

                    synchronized (syncObject) {
                        t2State.set("read");
                        syncObject.notifyAll();
                    }

                    synchronized (syncObject) {
                        while (!t1State.get().equals("read")) {
                            try {
                                syncObject.wait();
                            } catch (InterruptedException e) {
                            }
                        }
                    }

                    JsonObject updatedMetadata = originalMetadata == null ? new JsonObject() : originalMetadata;
                    MetadataUtils.shallowMergeMetadataUpdate(updatedMetadata, update2);

                    try {
                        sqlStorage.setUserMetadata_Transaction(new AppIdentifier(null, null), con, userId,
                                updatedMetadata);
                    } catch (TenantOrAppNotFoundException e) {
                        throw new StorageTransactionLogicException(e);
                    }

                    sqlStorage.commitTransaction(con);
                    success2.set(true); // it should come here because we will try three times.
                    return null;
                });
            } catch (StorageTransactionLogicException e) {
                if (e.actualException instanceof TenantOrAppNotFoundException) {
                    throw new IllegalStateException(e.actualException);
                }
            } catch (Exception ignored) {
            }
        };
        Thread t1 = new Thread(r1);
        Thread t2 = new Thread(r2);

        t1.start();
        t2.start();

        t1.join(5000);
        t2.join(5000);

        // The empty row did not lock, so we check if the system found a deadlock and that we could resolve it.

        // Both succeeds in the end
        assertTrue(success1.get());
        assertTrue(success2.get());

        // One of them had to be retried (not deterministic which)
        assertEquals(3, tryCount1.get() + tryCount2.get());
        // assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.DEADLOCK_FOUND));

        // The end result is as expected
        assertEquals(expected, sqlStorage.getUserMetadata(new AppIdentifier(null, null), userId));

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }
}
