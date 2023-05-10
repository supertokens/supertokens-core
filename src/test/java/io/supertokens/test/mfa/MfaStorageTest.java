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

package io.supertokens.test.mfa;

import io.supertokens.ProcessState;
import io.supertokens.featureflag.EE_FEATURES;
import io.supertokens.featureflag.FeatureFlagTestContent;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.pluginInterface.mfa.MfaStorage;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifier;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.test.TestingProcessManager;
import io.supertokens.test.Utils;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import static org.junit.Assert.assertNotNull;

public class MfaStorageTest {
    public class TestSetupResult {
        public MfaStorage storage;
        public TestingProcessManager.TestingProcess process;

        public TestSetupResult(MfaStorage storage, TestingProcessManager.TestingProcess process) {
            this.storage = storage;
            this.process = process;
        }
    }

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

    public TestSetupResult initSteps()
            throws InterruptedException {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return null;
        }
        MfaStorage storage = (MfaStorage) StorageLayer.getStorage(process.getProcess());

        FeatureFlagTestContent.getInstance(process.main)
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{EE_FEATURES.MFA});

        return new TestSetupResult(storage, process);
    }

    @Test
    public void enableFactorTests() throws Exception {
        TestSetupResult result = initSteps();
        if (result == null) {
            return;
        }
        MfaStorage storage = result.storage;
        TenantIdentifier tid = new TenantIdentifier(null, null, null);

        boolean insertedF1 = storage.enableFactor(tid, "userId", "f1");
        assert insertedF1;

        String[] factors = storage.listFactors(tid, "userId");

        assert factors != null;
        assert factors.length == 1;
        assert factors[0].equals("f1");

        boolean insertedF1Again = storage.enableFactor(tid, "userId", "f1");
        boolean insertedF2 = storage.enableFactor(tid, "userId", "f2");

        assert !insertedF1Again;
        assert insertedF2;
    }

    @Test
    public void listFactorsTest() throws Exception {
        TestSetupResult result = initSteps();
        if (result == null) {
            return;
        }
        MfaStorage storage = result.storage;
        TenantIdentifier tid = new TenantIdentifier(null, null, null);

        assert storage.enableFactor(tid, "userId", "f1") == true;
        assert storage.enableFactor(tid, "userId", "f2") == true;
        assert storage.enableFactor(tid, "userId", "f3") == true;

        assert storage.disableFactor(tid, "userId", "f2") == true;

        String[] factors = storage.listFactors(tid, "userId");

        assert factors != null;
        assert factors.length == 2;
        assert factors[0].equals("f1");
        assert factors[1].equals("f3");
    }

    @Test
    public void disableFactorsTest() throws Exception {
        TestSetupResult result = initSteps();
        if (result == null) {
            return;
        }
        MfaStorage storage = result.storage;
        TenantIdentifier tid = new TenantIdentifier(null, null, null);

        assert storage.enableFactor(tid, "userId", "f1") == true;
        assert storage.enableFactor(tid, "userId", "f2") == true;

        assert storage.disableFactor(tid, "non-existent-userId", "f1") == false; // userId does not exist
        assert storage.disableFactor(tid, "userId", "f3") == true; // f3 was enabled
        assert storage.disableFactor(tid, "userId", "f3") == false; // f2 was never enabled

        String[] factors = storage.listFactors(tid, "userId");

        assert factors != null;
        assert factors.length == 1;
        assert factors[0].equals("f1");

        factors = storage.listFactors(tid, "non-existent-user");
        assert factors == null;
    }


    @Test
    public void deleteUserTest() throws Exception {
        TestSetupResult result = initSteps();
        if (result == null) {
            return;
        }
        MfaStorage storage = result.storage;
        TenantIdentifier tid = new TenantIdentifier(null, null, null);

        assert storage.enableFactor(tid, "user1", "f1") == true;
        assert storage.enableFactor(tid, "user1", "f2") == true;

        assert storage.enableFactor(tid, "user2", "f1") == true;
        assert storage.enableFactor(tid, "user2", "f3") == true;

        assert storage.deleteUser(tid.toAppIdentifier(), "non-existent-user") == false;
        assert storage.deleteUser(tid.toAppIdentifier(), "user2") == true;

        String[] factors = storage.listFactors(tid, "user2");
        assert factors == null;

        factors = storage.listFactors(tid, "user1");

        assert factors != null;
        assert factors.length == 2;
        assert factors[0].equals("f1");
        assert factors[1].equals("f2");
    }

}
