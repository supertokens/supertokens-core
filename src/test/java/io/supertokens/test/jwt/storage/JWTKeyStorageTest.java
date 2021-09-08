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

package io.supertokens.test.jwt.storage;

import io.supertokens.ProcessState;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.pluginInterface.jwt.JWTRecipeStorage;
import io.supertokens.pluginInterface.jwt.JWTSigningKeyInfo;
import io.supertokens.pluginInterface.jwt.JWTSymmetricSigningKeyInfo;
import io.supertokens.pluginInterface.jwt.exceptions.DuplicateKeyIdException;
import io.supertokens.pluginInterface.jwt.sqlstorage.JWTRecipeSQLStorage;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.test.TestingProcessManager;
import io.supertokens.test.Utils;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import static org.junit.Assert.assertNotNull;

public class JWTKeyStorageTest {
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
     * Test that if a keyId is set when it already exists in storage, a DuplicateKeyIdException should be thrown
     */
    @Test
    public void testThatWhenSettingKeysWithSameIdDuplicateExceptionIsThrown() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        JWTRecipeStorage storage = StorageLayer.getJWTRecipeStorage(process.getProcess());

        if (storage.getType() != STORAGE_TYPE.SQL) {
            return;
        }

        JWTRecipeSQLStorage sqlStorage = (JWTRecipeSQLStorage) storage;
        JWTSigningKeyInfo keyToSet = new JWTSymmetricSigningKeyInfo("keyId-1234", 1000, "RSA", "somekeystring");

        boolean success = sqlStorage.startTransaction(con -> {
            try {
                sqlStorage.setJWTSigningKey_Transaction(con, keyToSet);
            } catch (DuplicateKeyIdException e) {
                return false;
            }

            try {
                sqlStorage.setJWTSigningKey_Transaction(con, keyToSet);
                return false;
            } catch (DuplicateKeyIdException e) {
                return true;
            }
        });

        assert success;
    }
}
