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
import io.supertokens.pluginInterface.oauth.OAuthLogoutChallenge;
import io.supertokens.pluginInterface.oauth.OAuthRevokeTargetType;
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

        AppIdentifier appIdentifier = new AppIdentifier(null, null);
        assertEquals(0, storage.getOAuthClients(appIdentifier, new ArrayList<>()).size()); // TODO fix me

        storage.addOrUpdateOauthClient(appIdentifier, "clientid1", "secret123", false, false);
        storage.addOrUpdateOauthClient(appIdentifier, "clientid2", "secret123", true, false);

        assertNotNull(storage.getOAuthClientById(appIdentifier, "clientid1"));
        try {
            storage.getOAuthClientById(appIdentifier, "clientid3");
            fail();
        } catch (OAuthClientNotFoundException e) {
            // ignore
        }

        assertEquals(2, storage.countTotalNumberOfOAuthClients(appIdentifier));
        assertEquals(1, storage.countTotalNumberOfClientCredentialsOnlyOAuthClients(appIdentifier));

        assertEquals(List.of("clientid1", "clientid2"), storage.getOAuthClients(appIdentifier, new ArrayList<>())); // TODO FIX ME

        storage.deleteOAuthClient(appIdentifier, "clientid1");
        assertEquals(List.of("clientid2"), storage.getOAuthClients(appIdentifier, new ArrayList<>())); // TODO FIX ME

        assertEquals(1, storage.countTotalNumberOfClientCredentialsOnlyOAuthClients(appIdentifier));
        storage.addOrUpdateOauthClient(appIdentifier, "clientid2", "secret123", false, false);
        assertEquals(0, storage.countTotalNumberOfClientCredentialsOnlyOAuthClients(appIdentifier));

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

        AppIdentifier appIdentifier = new AppIdentifier(null, null);

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

        AppIdentifier appIdentifier = new AppIdentifier(null, null);

        storage.revokeOAuthTokensBasedOnTargetFields(appIdentifier, OAuthRevokeTargetType.GID, "abcd", System.currentTimeMillis()/1000 + 2 - 3600 * 24 * 31);
        storage.revokeOAuthTokensBasedOnTargetFields(appIdentifier, OAuthRevokeTargetType.SESSION_HANDLE, "efgh", System.currentTimeMillis()/1000 + 2 - 3600 * 24 * 31);
        storage.revokeOAuthTokensBasedOnTargetFields(appIdentifier, OAuthRevokeTargetType.JTI, "ijkl", System.currentTimeMillis()/1000 + 2 - 3600 * 24 * 31);

        assertTrue(storage.isOAuthTokenRevokedBasedOnTargetFields(
                appIdentifier,
                new OAuthRevokeTargetType[]{OAuthRevokeTargetType.GID},
                new String[]{"abcd"},
                System.currentTimeMillis()/1000 - 2
        ));
        assertFalse(storage.isOAuthTokenRevokedBasedOnTargetFields(
                appIdentifier,
                new OAuthRevokeTargetType[]{OAuthRevokeTargetType.GID},
                new String[]{"efgh"},
                System.currentTimeMillis()/1000 - 2
        ));
        assertTrue(storage.isOAuthTokenRevokedBasedOnTargetFields(
                appIdentifier,
                new OAuthRevokeTargetType[]{OAuthRevokeTargetType.GID, OAuthRevokeTargetType.SESSION_HANDLE},
                new String[]{"efgh", "efgh"},
                System.currentTimeMillis()/1000 - 2
        ));

        // test cleanup
        Thread.sleep(3000);
        storage.cleanUpExpiredAndRevokedOAuthTokensList();

        assertFalse(storage.isOAuthTokenRevokedBasedOnTargetFields(
                appIdentifier,
                new OAuthRevokeTargetType[]{OAuthRevokeTargetType.GID},
                new String[]{"abcd"},
                System.currentTimeMillis()/1000 - 5
        ));
        assertFalse(storage.isOAuthTokenRevokedBasedOnTargetFields(
                appIdentifier,
                new OAuthRevokeTargetType[]{OAuthRevokeTargetType.GID, OAuthRevokeTargetType.SESSION_HANDLE},
                new String[]{"efgh", "efgh"},
                System.currentTimeMillis()/1000 - 5
        ));

        // newly issued should be allowed
        storage.revokeOAuthTokensBasedOnTargetFields(appIdentifier, OAuthRevokeTargetType.GID, "abcd", System.currentTimeMillis()/1000 + 2 - 3600 * 24 * 31);
        storage.revokeOAuthTokensBasedOnTargetFields(appIdentifier, OAuthRevokeTargetType.SESSION_HANDLE, "efgh", System.currentTimeMillis()/1000 + 2 - 3600 * 24 * 31);
        storage.revokeOAuthTokensBasedOnTargetFields(appIdentifier, OAuthRevokeTargetType.JTI, "ijkl", System.currentTimeMillis()/1000 + 2 - 3600 * 24 * 31);

        Thread.sleep(2000);

        assertFalse(storage.isOAuthTokenRevokedBasedOnTargetFields(
                appIdentifier,
                new OAuthRevokeTargetType[]{OAuthRevokeTargetType.GID},
                new String[]{"abcd"},
                System.currentTimeMillis()/1000
        ));
        assertFalse(storage.isOAuthTokenRevokedBasedOnTargetFields(
                appIdentifier,
                new OAuthRevokeTargetType[]{OAuthRevokeTargetType.GID, OAuthRevokeTargetType.SESSION_HANDLE},
                new String[]{"efgh", "efgh"},
                System.currentTimeMillis()/1000
        ));

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
        AppIdentifier appIdentifier = new AppIdentifier(null, null);

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
        AppIdentifier appIdentifier = new AppIdentifier(null, null);

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
            storage.revokeOAuthTokensBasedOnTargetFields(appIdentifier, OAuthRevokeTargetType.GID, "abcd", 0);
            storage.revokeOAuthTokensBasedOnTargetFields(appIdentifier, OAuthRevokeTargetType.GID, "abcd", 0); // should update
        }

        // App id FK
        AppIdentifier appIdentifier2 = new AppIdentifier(null,"a1");
        try {
            storage.addOrUpdateOauthClient(appIdentifier2, "clientid", "secret123", false, false);
            fail();
        } catch (TenantOrAppNotFoundException e) {
            // expected
        }
        try {
            storage.revokeOAuthTokensBasedOnTargetFields(appIdentifier2, OAuthRevokeTargetType.GID, "abcd", 0);
            fail();
        } catch (TenantOrAppNotFoundException e) {
            // expected
        }

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

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }
}
