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

package io.supertokens.test.session;

import io.supertokens.ProcessState;
import io.supertokens.config.Config;
import io.supertokens.cronjobs.CronTaskTest;
import io.supertokens.cronjobs.deleteExpiredAccessTokenSigningKeys.DeleteExpiredAccessTokenSigningKeys;
import io.supertokens.pluginInterface.KeyValueInfo;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.pluginInterface.multitenancy.AppIdentifier;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.pluginInterface.session.SessionStorage;
import io.supertokens.pluginInterface.session.sqlStorage.SessionSQLStorage;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.test.TestingProcessManager;
import io.supertokens.test.Utils;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import static org.junit.Assert.*;

public class DeleteExpiredAccessTokenSigningKeysTest {
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
    public void intervalTimeSecondsCleanExpiredAccessTokenSigningKeysTest() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        assertEquals(DeleteExpiredAccessTokenSigningKeys.getInstance(process.getProcess()).getIntervalTimeSeconds(),
                86400);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void jobCleansOldKeysTest() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        CronTaskTest.getInstance(process.getProcess())
                .setIntervalInSeconds(DeleteExpiredAccessTokenSigningKeys.RESOURCE_KEY, 1);
        process.startProcess();

        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        SessionStorage sessionStorage = (SessionStorage) StorageLayer.getStorage(process.getProcess());

        if (sessionStorage.getType() != STORAGE_TYPE.SQL) {
            return;
        }
        long accessTokenValidity = Config.getConfig(process.getProcess()).getAccessTokenValidityInMillis();
        long signingKeyUpdateInterval = Config.getConfig(process.getProcess())
                .getAccessTokenDynamicSigningKeyUpdateIntervalInMillis();

        SessionSQLStorage sqlStorage = (SessionSQLStorage) sessionStorage;
        sqlStorage.startTransaction(con -> {
            try {
                sqlStorage.addAccessTokenSigningKey_Transaction(new AppIdentifier(null, null), con,
                        new KeyValueInfo("clean!", 100));
                sqlStorage.addAccessTokenSigningKey_Transaction(new AppIdentifier(null, null), con,
                        new KeyValueInfo("clean!",
                                System.currentTimeMillis() - signingKeyUpdateInterval - 3 * accessTokenValidity));
                sqlStorage.addAccessTokenSigningKey_Transaction(new AppIdentifier(null, null), con,
                        new KeyValueInfo("clean!",
                                System.currentTimeMillis() - signingKeyUpdateInterval - 2 * accessTokenValidity));
                sqlStorage.addAccessTokenSigningKey_Transaction(new AppIdentifier(null, null), con,
                        new KeyValueInfo("keep!",
                                System.currentTimeMillis() - signingKeyUpdateInterval - 1 * accessTokenValidity));
                sqlStorage.addAccessTokenSigningKey_Transaction(new AppIdentifier(null, null), con,
                        new KeyValueInfo("keep!", System.currentTimeMillis() - signingKeyUpdateInterval));
                sqlStorage.addAccessTokenSigningKey_Transaction(new AppIdentifier(null, null), con,
                        new KeyValueInfo("keep!", System.currentTimeMillis()));
            } catch (TenantOrAppNotFoundException e) {
                throw new IllegalStateException(e);
            }
            return true;
        });

        Thread.sleep(1500);

        sqlStorage.startTransaction(con -> {
            KeyValueInfo[] keys = sqlStorage.getAccessTokenSigningKeys_Transaction(new AppIdentifier(null, null), con);
            assertEquals(keys.length, 4);
            for (KeyValueInfo key : keys) {
                assertNotEquals("clean!", key.value);
            }
            return true;
        });

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }
}
