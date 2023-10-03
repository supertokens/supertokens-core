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

import com.google.gson.JsonObject;
import io.supertokens.ProcessState;
import io.supertokens.featureflag.EE_FEATURES;
import io.supertokens.featureflag.FeatureFlagTestContent;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.pluginInterface.mfa.sqlStorage.MfaSQLStorage;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.test.TestingProcessManager;
import io.supertokens.test.Utils;
import io.supertokens.test.httpRequest.HttpRequestForTesting;
import io.supertokens.test.httpRequest.HttpResponseException;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.TestRule;

import java.io.IOException;
import java.util.HashMap;

import static org.junit.Assert.*;

public class MfaTestBase {
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


    public class TestSetupResult {
        public MfaSQLStorage storage;
        public TestingProcessManager.TestingProcess process;

        public TestSetupResult(MfaSQLStorage storage, TestingProcessManager.TestingProcess process) {
            this.storage = storage;
            this.process = process;
        }
    }

    public TestSetupResult initSteps(boolean enableMfaFeature)
            throws InterruptedException {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return null;
        }

        MfaSQLStorage storage = (MfaSQLStorage) StorageLayer.getStorage(process.getProcess());

        if (enableMfaFeature) {
            FeatureFlagTestContent.getInstance(process.main)
                    .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{EE_FEATURES.MFA});
        }

        return new TestSetupResult(storage, process);
    }

    public TestSetupResult initSteps()
            throws InterruptedException {
        return initSteps(true);
    }

    public void checkFieldMissingErrorResponse(Exception ex, String fieldName) {
        assert ex instanceof HttpResponseException;
        HttpResponseException e = (HttpResponseException) ex;
        assert e.statusCode == 400;
        assertTrue(e.getMessage().contains(
                "Http error. Status Code: 400. Message: Field name '" + fieldName + "' is invalid in JSON input"));
    }

    public void checkResponseErrorContains(Exception ex, String msg) {
        assert ex instanceof HttpResponseException;
        HttpResponseException e = (HttpResponseException) ex;
        assert e.statusCode == 400;
        assertTrue(e.getMessage().contains(msg));
    }


    public JsonObject enableFactorRequest(TestingProcessManager.TestingProcess process, JsonObject body)
            throws HttpResponseException, IOException {
        return HttpRequestForTesting.sendJsonPOSTRequest(
                process.getProcess(),
                "",
                "http://localhost:3567/recipe/mfa/factors/enable",
                body,
                1000,
                1000,
                null,
                Utils.getCdiVersionStringLatestForTests(),
                "mfa");
    }

    public JsonObject listFactorsRequest(TestingProcessManager.TestingProcess process, HashMap<String, String> params)
            throws HttpResponseException, IOException {
        return HttpRequestForTesting.sendGETRequest(
                process.getProcess(),
                "",
                "http://localhost:3567/recipe/mfa/factors/list",
                params,
                1000,
                1000,
                null,
                Utils.getCdiVersionStringLatestForTests(),
                "mfa");
    }

    public JsonObject disableFactorRequest(TestingProcessManager.TestingProcess process, JsonObject body)
            throws HttpResponseException, IOException {
        return HttpRequestForTesting.sendJsonPOSTRequest(
                process.getProcess(),
                "",
                "http://localhost:3567/recipe/mfa/factors/disable",
                body,
                1000,
                1000,
                null,
                Utils.getCdiVersionStringLatestForTests(),
                "mfa");
    }
}
