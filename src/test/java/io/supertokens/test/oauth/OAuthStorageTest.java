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

package io.supertokens.test.oauth;

import io.supertokens.ProcessState;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.pluginInterface.multitenancy.AppIdentifier;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.pluginInterface.oauth.OAuthClient;
import io.supertokens.pluginInterface.oauth.OAuthLogoutChallenge;
import io.supertokens.pluginInterface.oauth.OAuthStorage;
import io.supertokens.pluginInterface.oauth.exception.DuplicateOAuthLogoutChallengeException;
import io.supertokens.pluginInterface.oauth.exception.OAuthClientNotFoundException;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.test.TestingProcessManager;
import io.supertokens.test.Utils;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

public class OAuthStorageTest {
    @Rule
    public TestRule watchman = Utils.getOnFailure();

    @Rule
    public TestRule retryFlaky = Utils.retryFlakyTest();

    @AfterClass
    public static void afterTesting() {
        Utils.afterTesting();
    }

    @Before
    public void beforeEach() {
        Utils.reset();
    }

    @Test
    public void testClientCRUD() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        OAuthStorage storage = (OAuthStorage) StorageLayer.getStorage(process.getProcess());

        AppIdentifier appIdentifier = process.getAppForTesting().toAppIdentifier();
        assertEquals(0, storage.getOAuthClients(appIdentifier, new ArrayList<>()).size()); // TODO fix me

        storage.addOrUpdateOauthClient(appIdentifier, "clientid1", "secret123", false, false);
        storage.addOrUpdateOauthClient(appIdentifier, "clientid2", "secret123", true, false);

        OAuthClient client = storage.getOAuthClientById(appIdentifier, "clientid1");
        assertNotNull(client);
        assertEquals("secret123", client.clientSecret);
        assertFalse(client.isClientCredentialsOnly);
        assertFalse(client.enableRefreshTokenRotation);

        try {
            storage.getOAuthClientById(appIdentifier, "clientid3");
            fail();
        } catch (OAuthClientNotFoundException e) {
            // ignore
        }

        assertEquals(2, storage.countTotalNumberOfOAuthClients(appIdentifier));
        assertEquals(1, storage.countTotalNumberOfClientCredentialsOnlyOAuthClients(appIdentifier));

        List<OAuthClient> clients = storage.getOAuthClients(appIdentifier, List.of("clientid1", "clientid2"));
        assertEquals(2, clients.size());

        storage.deleteOAuthClient(appIdentifier, "clientid1");
        clients = storage.getOAuthClients(appIdentifier, List.of("clientid1", "clientid2"));
        assertEquals(1, clients.size());

        assertEquals(1, storage.countTotalNumberOfClientCredentialsOnlyOAuthClients(appIdentifier));
        storage.addOrUpdateOauthClient(appIdentifier, "clientid2", "secret123", false, false);
        assertEquals(0, storage.countTotalNumberOfClientCredentialsOnlyOAuthClients(appIdentifier));

        // Test all field updates
        storage.addOrUpdateOauthClient(appIdentifier, "clientid2", "newsecret", true, true);
        client = storage.getOAuthClientById(appIdentifier, "clientid2");
        assertEquals("newsecret", client.clientSecret);
        assertTrue(client.isClientCredentialsOnly);
        assertTrue(client.enableRefreshTokenRotation);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testLogoutChallenge() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        OAuthStorage storage = (OAuthStorage) StorageLayer.getStorage(process.getProcess());

        AppIdentifier appIdentifier = process.getAppForTesting().toAppIdentifier();

        storage.addOrUpdateOauthClient(appIdentifier, "clientid", "secret123", false, false);

        // Test nulls
        storage.addOAuthLogoutChallenge(appIdentifier, "challengeid", "clientid", null, null, null, System.currentTimeMillis());
        OAuthLogoutChallenge challenge = storage.getOAuthLogoutChallenge(
                appIdentifier, "challengeid");
        assertNull(challenge.postLogoutRedirectionUri);
        assertNull(challenge.sessionHandle);
        assertNull(challenge.state);

        // Test values
        storage.addOAuthLogoutChallenge(appIdentifier, "challengeid1", "clientid", "a", "b", "c", System.currentTimeMillis());
        challenge = storage.getOAuthLogoutChallenge(
                appIdentifier, "challengeid1");
        assertEquals("a", challenge.postLogoutRedirectionUri);
        assertEquals("b", challenge.sessionHandle);
        assertEquals("c", challenge.state);

        Thread.sleep(100);

        // Test revoke
        storage.deleteOAuthLogoutChallengesBefore(System.currentTimeMillis());

        assertNull(storage.getOAuthLogoutChallenge(appIdentifier, "challengeid"));
        assertNull(storage.getOAuthLogoutChallenge(appIdentifier, "challengeid1"));

        // Test delete
        storage.addOAuthLogoutChallenge(appIdentifier, "challengeid", "clientid", null, null, null, 0);
        assertNotNull(storage.getOAuthLogoutChallenge(appIdentifier, "challengeid"));
        storage.deleteOAuthLogoutChallenge(appIdentifier, "challengeid");
        assertNull(storage.getOAuthLogoutChallenge(appIdentifier, "challengeid"));

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testRevoke() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        OAuthStorage storage = (OAuthStorage) StorageLayer.getStorage(process.getProcess());

        AppIdentifier appIdentifier = process.getAppForTesting().toAppIdentifier();

        storage.addOrUpdateOauthClient(appIdentifier, "clientid", "clientSecret", false, true);
        storage.createOrUpdateOAuthSession(appIdentifier, "abcd", "clientid", "externalRefreshToken",
                "internalRefreshToken", "efgh", "ijkl", System.currentTimeMillis() + 1000 * 60 * 60 * 24);
        storage.createOrUpdateOAuthSession(appIdentifier, "abcd", "clientid", "externalRefreshToken",
                "internalRefreshToken", "efgh", "mnop", System.currentTimeMillis() + 1000 * 60 * 60 * 24);

        assertFalse(storage.isOAuthTokenRevokedByJTI(appIdentifier, "abcd", "ijkl"));
        assertFalse(storage.isOAuthTokenRevokedByJTI(appIdentifier, "abcd", "mnop"));

        storage.revokeOAuthTokenByJTI(appIdentifier, "abcd","ijkl");
        assertTrue(storage.isOAuthTokenRevokedByJTI(appIdentifier, "abcd", "ijkl"));
        assertFalse(storage.isOAuthTokenRevokedByJTI(appIdentifier, "abcd", "mnop"));

        storage.revokeOAuthTokenByJTI(appIdentifier, "abcd","mnop");
        assertTrue(storage.isOAuthTokenRevokedByJTI(appIdentifier, "abcd", "ijkl"));
        assertTrue(storage.isOAuthTokenRevokedByJTI(appIdentifier, "abcd", "mnop"));


        storage.revokeOAuthTokenByGID(appIdentifier, "abcd");
        assertTrue(storage.isOAuthTokenRevokedByJTI(appIdentifier, "abcd", "mnop"));

        storage.createOrUpdateOAuthSession(appIdentifier, "abcd", "clientid", "externalRefreshToken",
                "internalRefreshToken", "efgh", "ijkl", System.currentTimeMillis() + 1000 * 60 * 60 * 24);
        storage.createOrUpdateOAuthSession(appIdentifier, "abcd", "clientid", "externalRefreshToken",
                "internalRefreshToken", "efgh", "mnop", System.currentTimeMillis() + 1000 * 60 * 60 * 24);

        storage.revokeOAuthTokenBySessionHandle(appIdentifier, "efgh");
        assertTrue(storage.isOAuthTokenRevokedByJTI(appIdentifier, "abcd", "mnop"));

        // test cleanup
        Thread.sleep(3000);
        storage.deleteExpiredOAuthSessions(System.currentTimeMillis() / 1000 - 3);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testM2MTokenAndStats() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        OAuthStorage storage = (OAuthStorage) StorageLayer.getStorage(process.getProcess());
        AppIdentifier appIdentifier = process.getAppForTesting().toAppIdentifier();

        long now = System.currentTimeMillis() / 1000;

        storage.addOrUpdateOauthClient(appIdentifier, "clientid", "secret123", true, false);

        storage.addOAuthM2MTokenForStats(appIdentifier, "clientid", now - 3600 - 2, now + 2);
        storage.addOAuthM2MTokenForStats(appIdentifier, "clientid", now - 3600 * 24 - 2, now + 2);
        storage.addOAuthM2MTokenForStats(appIdentifier, "clientid", now - 3600 * 48 - 2, now + 2);

        assertEquals(3, storage.countTotalNumberOfOAuthM2MTokensAlive(appIdentifier));
        assertEquals(2, storage.countTotalNumberOfOAuthM2MTokensCreatedSince(appIdentifier, 1000 * (now - 3600 * 24 - 3)));

        Thread.sleep(3000);
        assertEquals(0, storage.countTotalNumberOfOAuthM2MTokensAlive(appIdentifier));

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testConstraints() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        OAuthStorage storage = (OAuthStorage) StorageLayer.getStorage(process.getProcess());
        AppIdentifier appIdentifier = process.getAppForTesting().toAppIdentifier();

        storage.addOrUpdateOauthClient(appIdentifier, "clientid", "secret123", false, false);

        // PK
        {
            long now = System.currentTimeMillis() / 1000;
            storage.addOAuthM2MTokenForStats(appIdentifier, "clientid", now, now + 100);
            storage.addOAuthM2MTokenForStats(appIdentifier, "clientid", now, now + 100); // should not throw
        }

        try {
            storage.addOAuthLogoutChallenge(appIdentifier, "challengeid", "clientid", null, null, null, 0);
            storage.addOAuthLogoutChallenge(appIdentifier, "challengeid", "clientid", null, null, null, 0);
            fail();
        } catch (DuplicateOAuthLogoutChallengeException e) {
            // this is what we expect
        }
        {
            assertFalse(storage.revokeOAuthTokenByGID(appIdentifier, "abcd"));
        }

        // App id FK
        AppIdentifier appIdentifier2 = new AppIdentifier(null,"a1");
        try {
            storage.addOrUpdateOauthClient(appIdentifier2, "clientid", "secret123", false, false);
            fail();
        } catch (TenantOrAppNotFoundException e) {
            // expected
        }

        assertFalse(storage.revokeOAuthTokenByGID(appIdentifier2, "abcd"));

        // Client FK
        try {
            storage.addOAuthLogoutChallenge(appIdentifier2, "challenge1", "clientid", null, null, null, 0);
            fail();
        } catch (OAuthClientNotFoundException e) {
            // ignore
        }
        try {
            storage.addOAuthLogoutChallenge(appIdentifier, "challenge1", "clientidx", null, null, null, 0);
            fail();
        } catch (OAuthClientNotFoundException e) {
            // ignore
        }
        try {
            storage.addOAuthM2MTokenForStats(appIdentifier2, "clientid", 0, 0);
            fail();
        } catch (OAuthClientNotFoundException e) {
            // expected
        }
        try {
            storage.addOAuthM2MTokenForStats(appIdentifier, "clientidx", 0, 0);
            fail();
        } catch (OAuthClientNotFoundException e) {
            // expected
        }

        try {
            storage.createOrUpdateOAuthSession(appIdentifier2, "abcd", "clientid", null, null, null, "asdasd",
                    System.currentTimeMillis() + 10000);
            fail();
        } catch (OAuthClientNotFoundException e) {
            //expected
        }

        try {
            storage.createOrUpdateOAuthSession(appIdentifier2, "abcd", "clientid-not-existing", null, null, null, "asdasd",
                    System.currentTimeMillis() + 10000);
            fail();
        } catch (OAuthClientNotFoundException e) {
            //expected
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }
}
