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

package io.supertokens.test.session.api;

import com.google.gson.JsonObject;
import io.supertokens.ProcessState;
import io.supertokens.featureflag.EE_FEATURES;
import io.supertokens.featureflag.FeatureFlagTestContent;
import io.supertokens.featureflag.exceptions.FeatureNotEnabledException;
import io.supertokens.multitenancy.Multitenancy;
import io.supertokens.multitenancy.exception.BadPermissionException;
import io.supertokens.multitenancy.exception.CannotModifyBaseConfigException;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.pluginInterface.exceptions.InvalidConfigException;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.multitenancy.*;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.pluginInterface.multitenancy.TenantConfig;
import io.supertokens.session.jwt.JWT;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.test.TestingProcessManager;
import io.supertokens.test.Utils;
import io.supertokens.test.httpRequest.HttpRequestForTesting;
import io.supertokens.test.httpRequest.HttpResponseException;
import io.supertokens.thirdparty.InvalidProviderConfigException;
import io.supertokens.utils.SemVer;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

public class MultitenantAPITest {
    TestingProcessManager.TestingProcess process;
    TenantIdentifier t1, t2, t3;

    @AfterClass
    public static void afterTesting() {
        Utils.afterTesting();
    }

    @After
    public void afterEach() throws InterruptedException {
        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Before
    public void beforeEach() throws InterruptedException, InvalidProviderConfigException,
            StorageQueryException, FeatureNotEnabledException, TenantOrAppNotFoundException, IOException,
            InvalidConfigException, CannotModifyBaseConfigException, BadPermissionException {
        Utils.reset();

        String[] args = {"../"};

        this.process = TestingProcessManager.start(args);
        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{EE_FEATURES.MULTI_TENANCY});
        process.startProcess();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        createTenants();
    }


    private void createTenants()
            throws StorageQueryException, TenantOrAppNotFoundException, InvalidProviderConfigException,
            FeatureNotEnabledException, IOException, InvalidConfigException,
            CannotModifyBaseConfigException, BadPermissionException {
        // User pool 1 - (null, a1, null)
        // User pool 2 - (null, a1, t1), (null, a1, t2)

        { // tenant 1
            JsonObject config = new JsonObject();
            TenantIdentifier tenantIdentifier = new TenantIdentifier(null, "a1", null);

            StorageLayer.getStorage(new TenantIdentifier(null, null, null), process.getProcess())
                    .modifyConfigToAddANewUserPoolForTesting(config, 1);

            Multitenancy.addNewOrUpdateAppOrTenant(
                    process.getProcess(),
                    new TenantIdentifier(null, null, null),
                    new TenantConfig(
                            tenantIdentifier,
                            new EmailPasswordConfig(false),
                            new ThirdPartyConfig(false, null),
                            new PasswordlessConfig(true),
                            null, null,
                            config
                    )
            );
        }

        { // tenant 2
            JsonObject config = new JsonObject();
            TenantIdentifier tenantIdentifier = new TenantIdentifier(null, "a1", "t1");

            StorageLayer.getStorage(new TenantIdentifier(null, null, null), process.getProcess())
                    .modifyConfigToAddANewUserPoolForTesting(config, 2);

            Multitenancy.addNewOrUpdateAppOrTenant(
                    process.getProcess(),
                    new TenantIdentifier(null, "a1", null),
                    new TenantConfig(
                            tenantIdentifier,
                            new EmailPasswordConfig(false),
                            new ThirdPartyConfig(false, null),
                            new PasswordlessConfig(true),
                            null, null,
                            config
                    )
            );
        }

        { // tenant 3
            JsonObject config = new JsonObject();
            TenantIdentifier tenantIdentifier = new TenantIdentifier(null, "a1", "t2");

            StorageLayer.getStorage(new TenantIdentifier(null, null, null), process.getProcess())
                    .modifyConfigToAddANewUserPoolForTesting(config, 2);

            Multitenancy.addNewOrUpdateAppOrTenant(
                    process.getProcess(),
                    new TenantIdentifier(null, "a1", null),
                    new TenantConfig(
                            tenantIdentifier,
                            new EmailPasswordConfig(false),
                            new ThirdPartyConfig(false, null),
                            new PasswordlessConfig(true),
                            null, null,
                            config
                    )
            );
        }

        t1 = new TenantIdentifier(null, "a1", null);
        t2 = new TenantIdentifier(null, "a1", "t1");
        t3 = new TenantIdentifier(null, "a1", "t2");
    }

    private JsonObject createSession(TenantIdentifier tenantIdentifier, String userId, JsonObject userDataInJWT,
                                     JsonObject userDataInDatabase)
            throws HttpResponseException, IOException {
        JsonObject request = new JsonObject();
        request.addProperty("userId", userId);
        request.add("userDataInJWT", userDataInJWT);
        request.add("userDataInDatabase", userDataInDatabase);
        request.addProperty("enableAntiCsrf", false);

        JsonObject response = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                HttpRequestForTesting.getMultitenantUrl(tenantIdentifier, "/recipe/session"), request,
                1000, 1000, null, SemVer.v3_0.get(),
                "session");

        return response;
    }

    private JsonObject getSession(TenantIdentifier tenantIdentifier, String sessionHandle)
            throws HttpResponseException, IOException {
        HashMap<String, String> map = new HashMap<>();
        map.put("sessionHandle", sessionHandle);
        JsonObject sessionResponse = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                HttpRequestForTesting.getMultitenantUrl(tenantIdentifier, "/recipe/session"),
                map, 1000, 1000, null, SemVer.v3_0.get(),
                "session");

        assertEquals("OK", sessionResponse.getAsJsonPrimitive("status").getAsString());
        return sessionResponse;
    }

    private void getSessionUnauthorised(TenantIdentifier tenantIdentifier, String sessionHandle)
            throws HttpResponseException, IOException {
        HashMap<String, String> map = new HashMap<>();
        map.put("sessionHandle", sessionHandle);
        JsonObject sessionResponse = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                HttpRequestForTesting.getMultitenantUrl(tenantIdentifier, "/recipe/session"),
                map, 1000, 1000, null, SemVer.v3_0.get(),
                "session");

        assertEquals("UNAUTHORISED", sessionResponse.getAsJsonPrimitive("status").getAsString());
    }

    private void regenerateSession(TenantIdentifier tenantIdentifier, String accessToken, JsonObject newUserDataInJWT)
            throws HttpResponseException, IOException {
        JsonObject sessionRegenerateRequest = new JsonObject();
        sessionRegenerateRequest.addProperty("accessToken", accessToken);
        sessionRegenerateRequest.add("userDataInJWT", newUserDataInJWT);

        JsonObject sessionRegenerateResponse = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                HttpRequestForTesting.getMultitenantUrl(tenantIdentifier, "/recipe/session/regenerate"),
                sessionRegenerateRequest, 1000, 1000, null,
                SemVer.v3_0.get(), "session");

        assertEquals(sessionRegenerateResponse.get("status").getAsString(), "OK");
    }

    private JsonObject verifySession(TenantIdentifier tenantIdentifier, String accessToken)
            throws HttpResponseException, IOException {
        JsonObject request = new JsonObject();
        request.addProperty("accessToken", accessToken);
        request.addProperty("doAntiCsrfCheck", true);
        request.addProperty("enableAntiCsrf", false);
        request.addProperty("checkDatabase", false);
        JsonObject response = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                HttpRequestForTesting.getMultitenantUrl(tenantIdentifier, "/recipe/session/verify"), request,
                1000, 1000, null,
                SemVer.v3_0.get(), "session");
        return response;
    }

    private JsonObject refreshSession(TenantIdentifier tenantIdentifier, String refreshToken)
            throws HttpResponseException, IOException {
        JsonObject sessionRefreshBody = new JsonObject();

        sessionRefreshBody.addProperty("refreshToken", refreshToken);
        sessionRefreshBody.addProperty("enableAntiCsrf", false);

        JsonObject response = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                HttpRequestForTesting.getMultitenantUrl(tenantIdentifier, "/recipe/session/refresh"),
                sessionRefreshBody, 1000, 1000, null,
                SemVer.v3_0.get(), "session");
        return response;
    }

    @Test
    public void testSessionCreatedIsAccessableFromTheSameTenantOnly() throws Exception {
        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        JsonObject user1DataInJWT = new JsonObject();
        user1DataInJWT.addProperty("foo", "val1");
        JsonObject user1DataInDb = new JsonObject();
        user1DataInJWT.addProperty("bar", "val1");

        JsonObject user2DataInJWT = new JsonObject();
        user1DataInJWT.addProperty("foo", "val2");
        JsonObject user2DataInDb = new JsonObject();
        user1DataInJWT.addProperty("bar", "val2");

        JsonObject user3DataInJWT = new JsonObject();
        user1DataInJWT.addProperty("foo", "val3");
        JsonObject user3DataInDb = new JsonObject();
        user1DataInJWT.addProperty("bar", "val3");

        JsonObject session1 = createSession(t1, "userid", user1DataInJWT, user1DataInDb).get("session")
                .getAsJsonObject();
        JsonObject session2 = createSession(t2, "userid", user2DataInJWT, user2DataInDb).get("session")
                .getAsJsonObject();
        JsonObject session3 = createSession(t3, "userid", user3DataInJWT, user3DataInDb).get("session")
                .getAsJsonObject();

        {
            JsonObject getSession = getSession(t1, session1.get("handle").getAsString());
            assertEquals(session1.get("userId"), getSession.get("userId"));
            assertEquals(session1.get("handle"), getSession.get("sessionHandle"));
            assertEquals(user1DataInJWT, getSession.get("userDataInJWT"));
            assertEquals(user1DataInDb, getSession.get("userDataInDatabase"));
        }

        {
            JsonObject getSession = getSession(t2, session2.get("handle").getAsString());
            assertEquals(session2.get("userId"), getSession.get("userId"));
            assertEquals(session2.get("handle"), getSession.get("sessionHandle"));
            assertEquals(user2DataInJWT, getSession.get("userDataInJWT"));
            assertEquals(user2DataInDb, getSession.get("userDataInDatabase"));
        }

        {
            JsonObject getSession = getSession(t3, session3.get("handle").getAsString());
            assertEquals(session3.get("userId"), getSession.get("userId"));
            assertEquals(session3.get("handle"), getSession.get("sessionHandle"));
            assertEquals(user3DataInJWT, getSession.get("userDataInJWT"));
            assertEquals(user3DataInDb, getSession.get("userDataInDatabase"));
        }
    }

    @Test
    public void testSessionFromOneTenantCanBeFetchedFromAnother() throws Exception {
        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        TenantIdentifier[] tenants = new TenantIdentifier[]{t1, t2, t3};

        for (TenantIdentifier tenant1 : tenants) {
            for (TenantIdentifier tenant2 : tenants) {
                if (tenant1.equals(tenant2)) {
                    continue;
                }
                JsonObject userDataInJWT = new JsonObject();
                userDataInJWT.addProperty("foo", "val1");
                JsonObject userDataInDb = new JsonObject();
                userDataInJWT.addProperty("bar", "val1");

                JsonObject session = createSession(tenant1, "userid", userDataInJWT, userDataInDb).get("session")
                        .getAsJsonObject();
                getSession(tenant2, session.get("handle").getAsString());
            }
        }
    }

    @Test
    public void testRegenerateSessionWorksFromAnyTenantButUpdatesTheRightSession() throws Exception {
        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        TenantIdentifier[] tenants = new TenantIdentifier[]{t1, t2, t3};

        for (TenantIdentifier tenant1 : tenants) {
            for (TenantIdentifier tenant2 : tenants) {
                JsonObject userDataInJWT = new JsonObject();
                userDataInJWT.addProperty("foo", "val1");
                JsonObject userDataInDb = new JsonObject();
                userDataInJWT.addProperty("bar", "val1");

                JsonObject session = createSession(tenant1, "userid", userDataInJWT, userDataInDb);
                userDataInJWT.addProperty("hello", "world");

                regenerateSession(tenant2, session.get("accessToken").getAsJsonObject().get("token").getAsString(),
                        userDataInJWT);

                JsonObject getSession = getSession(tenant1,
                        session.get("session").getAsJsonObject().get("handle").getAsString());
                assertEquals(userDataInJWT, getSession.get("userDataInJWT"));
            }
        }
    }

    @Test
    public void testVerifySessionWorksFromAnyTenantInTheApp() throws Exception {
        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        TenantIdentifier[] tenants = new TenantIdentifier[]{t1, t2, t3};

        for (TenantIdentifier tenant1 : tenants) {
            for (TenantIdentifier tenant2 : tenants) {
                JsonObject userDataInJWT = new JsonObject();
                userDataInJWT.addProperty("foo", "val1");
                JsonObject userDataInDb = new JsonObject();
                userDataInJWT.addProperty("bar", "val1");

                JsonObject session = createSession(tenant1, "userid", userDataInJWT, userDataInDb);
                userDataInJWT.addProperty("hello", "world");

                JsonObject sessionResponse = verifySession(tenant2,
                        session.get("accessToken").getAsJsonObject().get("token").getAsString());
                assertEquals(session.get("session"), sessionResponse.get("session"));
            }
        }
    }

    @Test
    public void testVerifySessionDoesNotWorkFromDifferentApp() throws Exception {
        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        JsonObject userDataInJWT = new JsonObject();
        userDataInJWT.addProperty("foo", "val1");
        JsonObject userDataInDb = new JsonObject();
        userDataInJWT.addProperty("bar", "val1");

        JsonObject session = createSession(t1, "userid", userDataInJWT, userDataInDb);
        JsonObject sessionResponse = verifySession(new TenantIdentifier(null, null, null),
                session.get("accessToken").getAsJsonObject().get("token").getAsString());
        assertEquals("TRY_REFRESH_TOKEN", sessionResponse.get("status").getAsString());
    }

    @Test
    public void testRefreshSessionDoesNotWorkFromDifferentApp() throws Exception {
        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        JsonObject userDataInJWT = new JsonObject();
        userDataInJWT.addProperty("foo", "val1");
        JsonObject userDataInDb = new JsonObject();
        userDataInJWT.addProperty("bar", "val1");

        JsonObject session = createSession(t1, "userid", userDataInJWT, userDataInDb);
        JsonObject sessionResponse = refreshSession(new TenantIdentifier(null, null, null),
                session.get("refreshToken").getAsJsonObject().get("token").getAsString());
        assertEquals("UNAUTHORISED", sessionResponse.get("status").getAsString());
    }

    @Test
    public void testAccessTokensContainsTid() throws Exception {
        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        {
            JsonObject session = createSession(t1, "userid", new JsonObject(), new JsonObject());
            JWT.JWTInfo accessTokenInfo = JWT.getPayloadWithoutVerifying(
                    session.get("accessToken").getAsJsonObject().get("token").getAsString());
            assertEquals(t1.getTenantId(), accessTokenInfo.payload.get("tId").getAsString());
        }

        {
            JsonObject session = createSession(t2, "userid", new JsonObject(), new JsonObject());
            JWT.JWTInfo accessTokenInfo = JWT.getPayloadWithoutVerifying(
                    session.get("accessToken").getAsJsonObject().get("token").getAsString());
            assertEquals(t2.getTenantId(), accessTokenInfo.payload.get("tId").getAsString());
        }
    }

    @Test
    public void testFetchAndRevokeSessionForUserAcrossAllTenants() throws Exception {
        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        List<String> sessionHandles = new ArrayList<>();
        {
            JsonObject session = createSession(t1, "userid", new JsonObject(), new JsonObject());
            sessionHandles.add(session.get("session").getAsJsonObject().get("handle").getAsString());
        }
        {
            JsonObject session = createSession(t2, "userid", new JsonObject(), new JsonObject());
            sessionHandles.add(session.get("session").getAsJsonObject().get("handle").getAsString());
        }
        {
            JsonObject session = createSession(t3, "userid", new JsonObject(), new JsonObject());
            sessionHandles.add(session.get("session").getAsJsonObject().get("handle").getAsString());
        }


        String[] allSessionHandles = getAllUserSessionsAcrossAllTenants(t1, "userid", true);
        assertEquals(sessionHandles.size(), allSessionHandles.length);

        for (String sessionHandle : allSessionHandles) {
            assertTrue(sessionHandles.contains(sessionHandle));
        }

        String[] revokedSessionHandles = revokeAllUserSessionsAcrossAllTenants(t1, "userid", true);
        assertEquals(sessionHandles.size(), revokedSessionHandles.length);
        for (String sessionHandle : revokedSessionHandles) {
            assertTrue(sessionHandles.contains(sessionHandle));
        }

        allSessionHandles = getAllUserSessionsAcrossAllTenants(t1, "userid", true);
        assertEquals(0, allSessionHandles.length);
    }

    @Test
    public void testFetchAndRevokeSessionForUserAcrossAllTenantsByDefault() throws Exception {
        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        List<String> sessionHandles = new ArrayList<>();
        {
            JsonObject session = createSession(t1, "userid", new JsonObject(), new JsonObject());
            sessionHandles.add(session.get("session").getAsJsonObject().get("handle").getAsString());
        }
        {
            JsonObject session = createSession(t2, "userid", new JsonObject(), new JsonObject());
            sessionHandles.add(session.get("session").getAsJsonObject().get("handle").getAsString());
        }
        {
            JsonObject session = createSession(t3, "userid", new JsonObject(), new JsonObject());
            sessionHandles.add(session.get("session").getAsJsonObject().get("handle").getAsString());
        }


        String[] allSessionHandles = getAllUserSessionsAcrossAllTenants(t1, "userid", null);
        assertEquals(sessionHandles.size(), allSessionHandles.length);

        for (String sessionHandle : allSessionHandles) {
            assertTrue(sessionHandles.contains(sessionHandle));
        }

        String[] revokedSessionHandles = revokeAllUserSessionsAcrossAllTenants(t1, "userid", null);
        assertEquals(sessionHandles.size(), revokedSessionHandles.length);
        for (String sessionHandle : revokedSessionHandles) {
            assertTrue(sessionHandles.contains(sessionHandle));
        }

        allSessionHandles = getAllUserSessionsAcrossAllTenants(t1, "userid", null);
        assertEquals(0, allSessionHandles.length);
    }

    @Test
    public void testFetchAndRevokeSessionForUserOperatesPerTenantIfTheFlagIsSetToFalse() throws Exception {
        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        List<String> sessionHandles = new ArrayList<>();
        {
            JsonObject session = createSession(t1, "userid", new JsonObject(), new JsonObject());
            sessionHandles.add(session.get("session").getAsJsonObject().get("handle").getAsString());
        }
        {
            JsonObject session = createSession(t2, "userid", new JsonObject(), new JsonObject());
            sessionHandles.add(session.get("session").getAsJsonObject().get("handle").getAsString());
        }
        {
            JsonObject session = createSession(t3, "userid", new JsonObject(), new JsonObject());
            sessionHandles.add(session.get("session").getAsJsonObject().get("handle").getAsString());
        }

        // Get session handles
        {
            String[] allSessionHandles = getAllUserSessionsAcrossAllTenants(t1, "userid", false);
            assertEquals(1, allSessionHandles.length);
            assertEquals(sessionHandles.get(0), allSessionHandles[0]);
        }

        {
            String[] allSessionHandles = getAllUserSessionsAcrossAllTenants(t2, "userid", false);
            assertEquals(1, allSessionHandles.length);
            assertEquals(sessionHandles.get(1), allSessionHandles[0]);
        }

        {
            String[] allSessionHandles = getAllUserSessionsAcrossAllTenants(t3, "userid", false);
            assertEquals(1, allSessionHandles.length);
            assertEquals(sessionHandles.get(2), allSessionHandles[0]);
        }

        // Revoke sessions
        {
            String[] revokedSessionHandles = revokeAllUserSessionsAcrossAllTenants(t1, "userid", false);
            assertEquals(1, revokedSessionHandles.length);
            assertEquals(sessionHandles.get(0), revokedSessionHandles[0]);
        }

        {
            String[] revokedSessionHandles = revokeAllUserSessionsAcrossAllTenants(t2, "userid", false);
            assertEquals(1, revokedSessionHandles.length);
            assertEquals(sessionHandles.get(1), revokedSessionHandles[0]);
        }

        {
            String[] revokedSessionHandles = revokeAllUserSessionsAcrossAllTenants(t3, "userid", false);
            assertEquals(1, revokedSessionHandles.length);
            assertEquals(sessionHandles.get(2), revokedSessionHandles[0]);
        }

        String[] allSessionHandles = getAllUserSessionsAcrossAllTenants(t1, "userid", true);
        assertEquals(0, allSessionHandles.length);
    }

    private String[] getAllUserSessionsAcrossAllTenants(TenantIdentifier tenantIdentifier, String userid,
                                                        Boolean fetchAcrossAllTenants)
            throws HttpResponseException, IOException {
        Map<String, String> params = new HashMap<>();
        if (fetchAcrossAllTenants != null) {
            params.put("fetchAcrossAllTenants", fetchAcrossAllTenants.toString());
        }
        params.put("userId", userid);

        JsonObject sessionResponse = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                HttpRequestForTesting.getMultitenantUrl(tenantIdentifier, "/recipe/session/user"),
                params, 1000, 1000, null, SemVer.v3_0.get(),
                "session");

        assertEquals("OK", sessionResponse.getAsJsonPrimitive("status").getAsString());
        String[] sessionHandles = new String[sessionResponse.get("sessionHandles").getAsJsonArray().size()];
        for (int i = 0; i < sessionHandles.length; i++) {
            sessionHandles[i] = sessionResponse.get("sessionHandles").getAsJsonArray().get(i).getAsString();
        }
        return sessionHandles;
    }

    private String[] revokeAllUserSessionsAcrossAllTenants(TenantIdentifier tenantIdentifier, String userid,
                                                           Boolean revokeAcrossAllTenants)
            throws HttpResponseException, IOException {
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("userId", userid);
        if (revokeAcrossAllTenants != null) {
            requestBody.addProperty("revokeAcrossAllTenants", revokeAcrossAllTenants);
        }

        JsonObject sessionResponse = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                HttpRequestForTesting.getMultitenantUrl(tenantIdentifier, "/recipe/session/remove"), requestBody,
                1000, 1000, null, SemVer.v3_0.get(),
                "session");
        assertEquals("OK", sessionResponse.getAsJsonPrimitive("status").getAsString());
        String[] sessionHandles = new String[sessionResponse.get("sessionHandlesRevoked").getAsJsonArray().size()];
        for (int i = 0; i < sessionHandles.length; i++) {
            sessionHandles[i] = sessionResponse.get("sessionHandlesRevoked").getAsJsonArray().get(i).getAsString();
        }
        return sessionHandles;
    }
}
