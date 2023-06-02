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

package io.supertokens.test.passwordless;

import io.supertokens.ProcessState;
import io.supertokens.config.Config;
import io.supertokens.cronjobs.CronTaskTest;
import io.supertokens.cronjobs.deleteExpiredPasswordlessDevices.DeleteExpiredPasswordlessDevices;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifier;
import io.supertokens.pluginInterface.passwordless.PasswordlessCode;
import io.supertokens.pluginInterface.passwordless.PasswordlessStorage;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.test.TestingProcessManager;
import io.supertokens.test.Utils;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public class DeleteExpiredPasswordlessDevicesTest {
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
    public void jobDeletesDevicesWithOnlyExpiredCodesTest() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        CronTaskTest.getInstance(process.getProcess())
                .setIntervalInSeconds(DeleteExpiredPasswordlessDevices.RESOURCE_KEY, 1);
        process.startProcess();

        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        PasswordlessStorage passwordlessStorage = (PasswordlessStorage) StorageLayer.getStorage(process.getProcess());

        long codeLifetime = Config.getConfig(process.getProcess()).getPasswordlessCodeLifetime();

        String codeId = "deletedCode";
        String deviceIdHash = "deviceIdHash";
        passwordlessStorage.createDeviceWithCode(new TenantIdentifier(null, null, null), "test@example.com", null,
                "linkCodeSalt",
                new PasswordlessCode(codeId, deviceIdHash, "linkCodeHash", System.currentTimeMillis() - codeLifetime));

        Thread.sleep(1500);

        assertNull(passwordlessStorage.getDevice(new TenantIdentifier(null, null, null), deviceIdHash));

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void jobKeepsDevicesWithActiveCodesTest() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        CronTaskTest.getInstance(process.getProcess())
                .setIntervalInSeconds(DeleteExpiredPasswordlessDevices.RESOURCE_KEY, 1);
        process.startProcess();

        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        PasswordlessStorage passwordlessStorage = (PasswordlessStorage) StorageLayer.getStorage(process.getProcess());

        long codeLifetime = Config.getConfig(process.getProcess()).getPasswordlessCodeLifetime();

        String codeId = "expiredCode";
        String deviceIdHash = "deviceIdHash";
        passwordlessStorage.createDeviceWithCode(new TenantIdentifier(null, null, null), "test@example.com", null,
                "linkCodeSalt",
                new PasswordlessCode(codeId, deviceIdHash, "linkCodeHash", System.currentTimeMillis() - codeLifetime));
        passwordlessStorage
                .createCode(new TenantIdentifier(null, null, null),
                        new PasswordlessCode("id", deviceIdHash, "linkCodeHash2", System.currentTimeMillis()));

        Thread.sleep(1500);

        assertNotNull(passwordlessStorage.getDevice(new TenantIdentifier(null, null, null), deviceIdHash));

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }
}
