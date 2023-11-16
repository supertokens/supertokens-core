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

package io.supertokens.test.passwordless.api;

import com.google.gson.JsonObject;
import io.supertokens.ProcessState;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifier;
import io.supertokens.pluginInterface.passwordless.PasswordlessStorage;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.test.TestingProcessManager;
import io.supertokens.test.Utils;
import io.supertokens.test.httpRequest.HttpRequestForTesting;
import io.supertokens.webserver.WebserverAPI;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class PasswordlessUserPutAPITest {
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
    public void testIfPhoneNumberIsNormalisedInUpdate() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        String userId = "6347c997-4cc9-4f95-94c9-b96e2c65aefc";
        String phoneNumber = "+442071838750";
        String updatedPhoneNumber = "+44-207 183 8751";
        String normalisedUpdatedPhoneNumber = io.supertokens.utils.Utils.normalizeIfPhoneNumber(updatedPhoneNumber);

        PasswordlessStorage storage = (PasswordlessStorage) StorageLayer.getStorage(process.getProcess());
        storage.createUser(new TenantIdentifier(null, null, null),
                userId, null, phoneNumber, System.currentTimeMillis());

        JsonObject updateUserRequestBody = new JsonObject();
        updateUserRequestBody.addProperty("recipeUserId", userId);
        updateUserRequestBody.addProperty("phoneNumber", updatedPhoneNumber);

        JsonObject response = HttpRequestForTesting.sendJsonPUTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/user", updateUserRequestBody, 1000, 1000, null,
                WebserverAPI.getLatestCDIVersion().get(), "passwordless");

        assertEquals("OK", response.get("status").getAsString());

        assert (storage.listPrimaryUsersByPhoneNumber(new TenantIdentifier(null, null, null), phoneNumber).length == 0);
        assert (storage.listPrimaryUsersByPhoneNumber(new TenantIdentifier(null, null, null),
                updatedPhoneNumber).length == 0);
        assert (storage.listPrimaryUsersByPhoneNumber(new TenantIdentifier(null, null, null),
                normalisedUpdatedPhoneNumber).length == 1);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }
}
