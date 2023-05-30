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

import com.google.gson.JsonObject;
import io.supertokens.ProcessState;
import io.supertokens.featureflag.EE_FEATURES;
import io.supertokens.featureflag.FeatureFlagTestContent;
import io.supertokens.multitenancy.Multitenancy;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.exceptions.StorageTransactionLogicException;
import io.supertokens.pluginInterface.multitenancy.*;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.pluginInterface.oauth2.OAuth2Client;
import io.supertokens.pluginInterface.oauth2.exception.*;
import io.supertokens.pluginInterface.oauth2.sqlStorage.OAuth2SQLStorage;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.test.TestingProcessManager;
import io.supertokens.test.Utils;
import org.junit.*;
import org.junit.rules.TestRule;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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

        OAuth2Client oAuth2Client = getOAuth2Client(null, null, null);
        createOAuth2Client_TransactionHelper(storage ,oAuth2Client);

        OAuth2Client oAuth2ClientFromGetQuery = storage.getOAuth2ClientById(new AppIdentifier(null, null),
                oAuth2Client.clientId );

        Assert.assertEquals(oAuth2Client, oAuth2ClientFromGetQuery);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testCreateOAuth2ClientTransactionExceptions() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        OAuth2SQLStorage storage = (OAuth2SQLStorage) StorageLayer.getStorage(process.getProcess());

        Exception error;
        // test - check createOauth2ClientTransaction for TenantOrAppNotFoundException
        {
            OAuth2Client oAuth2Client = getOAuth2Client(null, null, null);
            error = null;
            try {
                storage.startTransaction(con -> {
                    try {
                        storage.createOAuth2Client_Transaction(new AppIdentifier(null, "test-1"), con, oAuth2Client);
                    } catch (DuplicateOAuth2ClientSecretHash | DuplicateOAuth2ClientIdException | DuplicateOAuth2ClientNameException |
                             TenantOrAppNotFoundException e) {
                        throw new StorageTransactionLogicException(e);
                    }
                    return null;
                });
            } catch (StorageTransactionLogicException e) {
                error = e.actualException;
            }

            Assert.assertNotNull(error);
            assert (error instanceof TenantOrAppNotFoundException);
        }

        // test - check createOAuth2ClientTransaction for DuplicateOAuth2ClientIdException
        {
            OAuth2Client oAuth2Client = getOAuth2Client("test-1", "test-1", "test1-secret");
            createOAuth2Client_TransactionHelper(storage ,oAuth2Client);
            error = null;
            try {
                storage.startTransaction(con -> {
                    try {
                        storage.createOAuth2Client_Transaction(new AppIdentifier(null, null), con, oAuth2Client);
                    } catch (DuplicateOAuth2ClientSecretHash | DuplicateOAuth2ClientIdException | DuplicateOAuth2ClientNameException |
                             TenantOrAppNotFoundException e) {
                        throw new StorageTransactionLogicException(e);
                    }
                    return null;
                });
            } catch (StorageTransactionLogicException e) {
                error = e.actualException;
            }

            Assert.assertNotNull(error);
            assert (error instanceof DuplicateOAuth2ClientIdException);
        }

        // test - check createOauth2ClientTransaction for DuplicateClientSecretHashException
        {
            OAuth2Client oAuth2Client = getOAuth2Client(null, null, null);
            createOAuth2Client_TransactionHelper(storage ,oAuth2Client);
            error = null;
            try {

                storage.startTransaction(con -> {
                    try {
                        storage.createOAuth2Client_Transaction(new AppIdentifier(null, null), con,
                                getOAuth2Client("exp2", "random-name",oAuth2Client.clientSecretHash));
                    } catch (DuplicateOAuth2ClientSecretHash | DuplicateOAuth2ClientIdException | DuplicateOAuth2ClientNameException |
                             TenantOrAppNotFoundException e) {
                        throw new StorageTransactionLogicException(e);
                    }
                    return null;
                });
            } catch (StorageTransactionLogicException e) {
                error = e.actualException;
            }

            Assert.assertNotNull(error);
            assert (error instanceof DuplicateOAuth2ClientSecretHash);
        }
        // test - check createOauth2ClientTransaction for DuplicateOAuth2ClientNameException
        {
            error = null;
            try {
                OAuth2Client oAuth2Client = getOAuth2Client("test-name-ex", "test-name-ex", "test-name-ex");
                createOAuth2Client_TransactionHelper(storage ,oAuth2Client);
                storage.startTransaction(con -> {
                    try {
                        storage.createOAuth2Client_Transaction(new AppIdentifier(null, null), con,
                                getOAuth2Client("test-name-ex-2", "test-name-ex","test-name-ex-2"));
                    } catch (DuplicateOAuth2ClientSecretHash | DuplicateOAuth2ClientIdException | DuplicateOAuth2ClientNameException |
                             TenantOrAppNotFoundException e) {
                        throw new StorageTransactionLogicException(e);
                    }
                    return null;
                });
            } catch (StorageTransactionLogicException e) {
                error = e.actualException;
            }

            Assert.assertNotNull(error);
            assert (error instanceof DuplicateOAuth2ClientNameException);
        }
        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testGetOAuth2ClientByIdExceptions() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        OAuth2SQLStorage storage = (OAuth2SQLStorage) StorageLayer.getStorage(process.getProcess());

        Exception error = null;
        try {
            storage.getOAuth2ClientById(new AppIdentifier(null, null), "invalid-client-id" );
        } catch (Exception e) {
            error = e;
        }

        Assert.assertNotNull(error);
        assert (error instanceof UnknownOAuth2ClientIdException);

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

    @Test
    public void testCreateOauth2ScopeWithExceptions() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        OAuth2SQLStorage storage = (OAuth2SQLStorage) StorageLayer.getStorage(process.getProcess());

        // test : check  createOAuth2Scope for TenantOrAppNotFoundException on invalid app_id
        {
            String scope = "profile";
            Exception error;
            try {
                error = null;
                storage.createOAuth2Scope(new AppIdentifier(null, "main"), scope);
            } catch (Exception e) {
                error = e;
            }
            Assert.assertNotNull(error);

            assert (error instanceof TenantOrAppNotFoundException);
        }

        // test : check createOAuth2Scope for DuplicateOAuth2ScopeException on duplicate scope
        {
            String scope = "profile";
            Exception error;
            storage.createOAuth2Scope(new AppIdentifier(null, null), scope);
            try {
                error = null;
                storage.createOAuth2Scope(new AppIdentifier(null, null), scope);
            } catch (Exception e) {
                error = e;
            }

            Assert.assertNotNull(error);
            assert (error instanceof DuplicateOAuth2ScopeException);
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testGetOAuth2Scopes() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args, false);
        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{EE_FEATURES.MULTI_TENANCY});
        process.startProcess();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        Multitenancy.addNewOrUpdateAppOrTenant(
                process.getProcess(),
                new TenantIdentifier(null, null, null),
                new TenantConfig(
                        new TenantIdentifier(null, "test_app", null),
                        new EmailPasswordConfig(true),
                        new ThirdPartyConfig(true, null),
                        new PasswordlessConfig(true),
                        new JsonObject()
                )
        );

        OAuth2SQLStorage storage = (OAuth2SQLStorage) StorageLayer.getStorage(process.getProcess());
        // test - to check whether correct scopes are returned for an app while scopes for multiple apps are present
        {
            List<String> scopes = Stream.of("profile", "home", "special")
                    .collect(Collectors.toCollection(
                            ArrayList::new));
            scopes.forEach(
                    it -> {
                        try {
                            storage.createOAuth2Scope(new AppIdentifier(null,"test_app"),it);
                        } catch (StorageQueryException | DuplicateOAuth2ScopeException |TenantOrAppNotFoundException e) {
                            throw new RuntimeException(e);
                        }
                    }
            );

            List<String> getOauth2scopesFromAppId = storage.getOAuth2Scopes(new AppIdentifier(null, "test_app"));

            Assert.assertEquals(scopes,getOauth2scopesFromAppId);
        }

        // test - to check whether an empty scope list is returned for a non-existent app
        {
            List<String> scopes = storage.getOAuth2Scopes(new AppIdentifier(null, "test_app_non_existent"));

            Assert.assertTrue(scopes.isEmpty());
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    private static void createOAuth2Client_TransactionHelper(OAuth2SQLStorage storage, OAuth2Client oAuth2Client)
            throws StorageQueryException, StorageTransactionLogicException {

        storage.startTransaction(con -> {
            try {
                storage.createOAuth2Client_Transaction(new AppIdentifier(null, null), con, oAuth2Client);
            } catch (DuplicateOAuth2ClientSecretHash | DuplicateOAuth2ClientNameException | DuplicateOAuth2ClientIdException | TenantOrAppNotFoundException e) {
                throw new RuntimeException(e);
            }
            return null;
        });
    }

    private static OAuth2Client getOAuth2Client(String clientId, String name, String clientSecretHash){
        List<String> redirectUris = new ArrayList<>();
        redirectUris.add("randomURL1");
        redirectUris.add("randomURL2");

        clientId = clientId != null ? clientId : "random-client-id";
        name  = name != null ? name : "random-name";
        clientSecretHash = clientSecretHash != null ? clientSecretHash : "randomsecret";

        return new OAuth2Client(clientId,name,clientSecretHash, redirectUris,
                System.currentTimeMillis(), System.currentTimeMillis());
    }
}
