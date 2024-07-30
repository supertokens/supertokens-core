/*
 *    Copyright (c) 2024, VRAI Labs and/or its affiliates. All rights reserved.
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

package io.supertokens.test.oauth.api;

import io.supertokens.ProcessState;
import io.supertokens.featureflag.EE_FEATURES;
import io.supertokens.featureflag.FeatureFlagTestContent;
import io.supertokens.httpRequest.HttpResponseException;
import io.supertokens.oauth.OAuth;
import io.supertokens.oauth.exceptions.OAuthAuthException;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.pluginInterface.exceptions.InvalidConfigException;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.multitenancy.AppIdentifier;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.pluginInterface.oauth.OAuthAuthResponse;
import io.supertokens.pluginInterface.oauth.OAuthStorage;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.test.TestingProcessManager;
import io.supertokens.test.Utils;
import org.junit.*;
import org.junit.rules.TestRule;

import java.io.IOException;

import static org.junit.Assert.assertNotNull;

public class OAuthAPITest {
    TestingProcessManager.TestingProcess process;

    @Rule
    public TestRule watchman = Utils.getOnFailure();

    @AfterClass
    public static void afterTesting() {
        Utils.afterTesting();
    }

    @Before
    public void beforeEach() throws InterruptedException {
        Utils.reset();
        String[] args = {"../"};

        this.process = TestingProcessManager.start(args);
        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{EE_FEATURES.OAUTH});
        process.startProcess();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) { //TODO check if this is true here also
            return;
        }
    }

    @After
    public void afterEach() throws InterruptedException {
        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    // TODO rename this!
    @Test
    public void testHappyPath()
            throws StorageQueryException, OAuthAuthException, HttpResponseException, TenantOrAppNotFoundException,
            InvalidConfigException, IOException {

        String clientId = "a685663d-1b5d-4a70-b7f7-025ff2e2d7a4";
        String redirectUri = "http://localhost.com:3031/auth/callback/ory";
        String responseType = "code";
        String scope = "profile";
        String state = "%EF%BF%BD%EF%BF%BD%EF%BF%BD%EF%BF%BD%EF%BF%BD%EF%BF%BDv%EF%BF%BD%EF%BF%BD%EF%BF%BD%EF%BF%BD%EF%BF%BD";

        OAuthStorage oAuthStorage = (OAuthStorage) StorageLayer.getStorage(process.getProcess());

        OAuthAuthResponse response = OAuth.getAuthorizationUrl(process.getProcess(), new AppIdentifier(null, null), oAuthStorage,  clientId, redirectUri, responseType, scope, state);

        System.out.println(response);
        System.out.println(response.redirectTo);

        assertNotNull(response);
        assertNotNull(response.redirectTo);
        assertNotNull(response.cookies);
    }

}
