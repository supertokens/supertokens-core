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
import io.supertokens.pluginInterface.exceptions.InvalidConfigException;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.multitenancy.AppIdentifier;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.oauth.OAuthAuthResponse;
import io.supertokens.pluginInterface.oauth.exceptions.ClientAlreadyExistsForAppException;
import io.supertokens.pluginInterface.oauth.sqlStorage.OAuthSQLStorage;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.test.TestingProcessManager;
import io.supertokens.test.Utils;
import org.junit.*;
import org.junit.rules.TestRule;

import java.io.IOException;

import static org.junit.Assert.*;

public class OAuthAuthAPITest {
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
    }

    @After
    public void afterEach() throws InterruptedException {
        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testLocalhostChangedToApiDomain()
            throws StorageQueryException, OAuthAuthException, HttpResponseException, TenantOrAppNotFoundException,
            InvalidConfigException, IOException, ClientAlreadyExistsForAppException {

        String clientId = "6030f07e-c8ef-4289-80c9-c18e0bf4f679";
        String redirectUri = "http://localhost.com:3031/auth/callback/ory";
        String responseType = "code";
        String scope = "profile";
        String state = "%EF%BF%BD%EF%BF%BD%EF%BF%BD%EF%BF%BD%EF%BF%BD%EF%BF%BDv%EF%BF%BD%EF%BF%BD%EF%BF%BD%EF%BF%BD%EF%BF%BD";

        OAuthSQLStorage oAuthStorage = (OAuthSQLStorage) StorageLayer.getStorage(process.getProcess());

        AppIdentifier testApp = new AppIdentifier("", "");
        oAuthStorage.addClientForApp(testApp, clientId);

        OAuthAuthResponse response = OAuth.getAuthorizationUrl(process.getProcess(), new AppIdentifier("", ""), oAuthStorage,  clientId, redirectUri, responseType, scope, state);

        assertNotNull(response);
        assertNotNull(response.redirectTo);
        assertNotNull(response.cookies);

        assertTrue(response.redirectTo.startsWith("http://{apiDomain}/login?login_challenge="));
        assertTrue(response.cookies.get(0).startsWith("ory_hydra_login_csrf_dev_134972871="));
    }

    @Test
    public void testCalledWithWrongClientIdNotInST_exceptionThrown()
            throws StorageQueryException, ClientAlreadyExistsForAppException {

        String clientId = "Not-Existing-In-Client-App-Table";
        String redirectUri = "http://localhost.com:3031/auth/callback/ory";
        String responseType = "code";
        String scope = "profile";
        String state = "%EF%BF%BD%EF%BF%BD%EF%BF%BD%EF%BF%BD%EF%BF%BD%EF%BF%BDv%EF%BF%BD%EF%BF%BD%EF%BF%BD%EF%BF%BD%EF%BF%BD";

        OAuthSQLStorage oAuthStorage = (OAuthSQLStorage) StorageLayer.getStorage(process.getProcess());

        AppIdentifier testApp = new AppIdentifier("", "");
        oAuthStorage.addClientForApp(testApp, clientId);

        OAuthAuthException thrown = assertThrows(OAuthAuthException.class, () -> {

            OAuthAuthResponse response = OAuth.getAuthorizationUrl(process.getProcess(), new AppIdentifier("", ""), oAuthStorage,  clientId, redirectUri, responseType, scope, state);
        });

        String expectedError = "invalid_client";
        String expectedDescription = "Client authentication failed (e.g., unknown client, no client authentication included, or unsupported authentication method). The requested OAuth 2.0 Client does not exist.";

        assertEquals(expectedError, thrown.error);
        assertEquals(expectedDescription, thrown.errorDescription);
    }

    @Test
    public void testCalledWithWrongClientIdNotInHydraButInST_exceptionThrown()
            throws StorageQueryException, ClientAlreadyExistsForAppException {

        String clientId = "6030f07e-c8ef-4289-80c9-c18e0bf4f679NotInHydra";
        String redirectUri = "http://localhost.com:3031/auth/callback/ory";
        String responseType = "code";
        String scope = "profile";
        String state = "%EF%BF%BD%EF%BF%BD%EF%BF%BD%EF%BF%BD%EF%BF%BD%EF%BF%BDv%EF%BF%BD%EF%BF%BD%EF%BF%BD%EF%BF%BD%EF%BF%BD";

        OAuthSQLStorage oAuthStorage = (OAuthSQLStorage) StorageLayer.getStorage(process.getProcess());

        AppIdentifier testApp = new AppIdentifier("", "");
        oAuthStorage.addClientForApp(testApp, clientId);

        OAuthAuthException thrown = assertThrows(OAuthAuthException.class, () -> {

            OAuthAuthResponse response = OAuth.getAuthorizationUrl(process.getProcess(), new AppIdentifier("", ""), oAuthStorage,  clientId, redirectUri, responseType, scope, state);
        });

        String expectedError = "invalid_client";
        String expectedDescription = "Client authentication failed (e.g., unknown client, no client authentication included, or unsupported authentication method). The requested OAuth 2.0 Client does not exist.";

        assertEquals(expectedError, thrown.error);
        assertEquals(expectedDescription, thrown.errorDescription);
    }

}
