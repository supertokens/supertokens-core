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

package io.supertokens.test.oauth2;

import io.supertokens.ProcessState;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.multitenancy.AppIdentifier;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.pluginInterface.oauth2.OAuth2Client;
import io.supertokens.pluginInterface.oauth2.exception.DuplicateOAuth2ClientIdException;
import io.supertokens.pluginInterface.oauth2.exception.DuplicateOAuth2ClientSecretHash;
import io.supertokens.pluginInterface.oauth2.exception.DuplicateOAuth2ScopeException;
import io.supertokens.pluginInterface.oauth2.sqlStorage.OAuth2SQLStorage;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.test.TestingProcessManager;
import io.supertokens.test.Utils;
import org.junit.*;
import org.junit.rules.TestRule;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertNotNull;

public class StorageTests {

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
    public void testCreateOAuth2ClientTransaction() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        OAuth2SQLStorage storage = (OAuth2SQLStorage) StorageLayer.getStorage(process.getProcess());

        List<String> redirectUris = new ArrayList<>();
        redirectUris.add("r1");
        redirectUris.add("r2");

        OAuth2Client oAuth2Client = new OAuth2Client("test-c", "name", "secret", redirectUris,
                System.currentTimeMillis(), System.currentTimeMillis());
        storage.startTransaction(con -> {
            try {
                storage.createOAuth2Client_Transaction(new AppIdentifier(null, null), con, oAuth2Client);
            } catch (DuplicateOAuth2ClientSecretHash | DuplicateOAuth2ClientIdException | TenantOrAppNotFoundException e) {
                throw new RuntimeException(e);
            }
            return null;
        });

        OAuth2Client oAuth2ClientFromGetQuery = storage.getOAuth2ClientById(new AppIdentifier(null, null),
                oAuth2Client.clientId);

        Assert.assertEquals(oAuth2Client, oAuth2ClientFromGetQuery);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testCreateOAuth2Scope() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        OAuth2SQLStorage storage = (OAuth2SQLStorage) StorageLayer.getStorage(process.getProcess());
        List<String> scopes = new ArrayList<>();
        scopes.add("profile");
        scopes.add("home_page");

        scopes.forEach(
                it -> {
                    try {
                        storage.createOAuth2Scope(new AppIdentifier(null, null), it);
                    } catch (DuplicateOAuth2ScopeException | TenantOrAppNotFoundException | StorageQueryException e) {
                        throw new RuntimeException(e);
                    }
                }
        );

        List<String> getOAuth2ScopesFromAppId = storage.getOAuth2Scopes(new AppIdentifier(null, null));
        Assert.assertEquals(scopes, getOAuth2ScopesFromAppId);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }
}
