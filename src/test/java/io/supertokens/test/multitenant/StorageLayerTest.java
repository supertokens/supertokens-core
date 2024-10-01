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

package io.supertokens.test.multitenant;

import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import io.supertokens.ProcessState;
import io.supertokens.ResourceDistributor;
import io.supertokens.featureflag.EE_FEATURES;
import io.supertokens.featureflag.FeatureFlagTestContent;
import io.supertokens.multitenancy.Multitenancy;
import io.supertokens.multitenancy.MultitenancyHelper;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.pluginInterface.Storage;
import io.supertokens.pluginInterface.exceptions.InvalidConfigException;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.multitenancy.*;
import io.supertokens.pluginInterface.multitenancy.exceptions.DuplicateClientTypeException;
import io.supertokens.pluginInterface.multitenancy.exceptions.DuplicateTenantException;
import io.supertokens.pluginInterface.multitenancy.exceptions.DuplicateThirdPartyIdException;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.test.TestingProcessManager;
import io.supertokens.test.Utils;
import org.junit.*;
import org.junit.rules.TestRule;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.*;

public class StorageLayerTest {
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
    public void normalConfigContinuesToWork() throws InterruptedException, IOException {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args, false);
        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{EE_FEATURES.MULTI_TENANCY});
        process.startProcess();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.LOADING_ALL_TENANT_STORAGE));

        Assert.assertEquals(
                process.getProcess().getResourceDistributor().getAllResourcesWithResourceKey(StorageLayer.RESOURCE_KEY)
                        .size(), 1);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testDefaultTenant() throws InterruptedException, StorageQueryException {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args, false);
        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{EE_FEATURES.MULTI_TENANCY});
        process.startProcess();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.LOADING_ALL_TENANT_STORAGE));
        MultitenancyStorage mtStorage = StorageLayer.getMultitenancyStorage(process.getProcess());

        TenantConfig[] tenantConfigs = mtStorage.getAllTenants();

        assertEquals(1, tenantConfigs.length);
        TenantConfig baseTenantConfig = tenantConfigs[0];

        assertEquals(new TenantIdentifier(null, null, null), baseTenantConfig.tenantIdentifier);
        assertTrue(baseTenantConfig.emailPasswordConfig.enabled);
        assertTrue(baseTenantConfig.passwordlessConfig.enabled);
        assertTrue(baseTenantConfig.thirdPartyConfig.enabled);
        assertEquals(0, baseTenantConfig.thirdPartyConfig.providers.length);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testUpdationOfDefaultTenant()
            throws InterruptedException, DuplicateTenantException, StorageQueryException, TenantOrAppNotFoundException,
            DuplicateThirdPartyIdException, DuplicateClientTypeException {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args, false);
        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{EE_FEATURES.MULTI_TENANCY});
        process.startProcess();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.LOADING_ALL_TENANT_STORAGE));
        MultitenancyStorage mtStorage = StorageLayer.getMultitenancyStorage(process.getProcess());

        JsonObject authParams = new JsonObject();
        authParams.add("auth-param", new JsonPrimitive("auth-val"));

        JsonObject tokenParams = new JsonObject();
        tokenParams.add("token-param", new JsonPrimitive("token-val"));

        JsonObject userInfoParams = new JsonObject();
        userInfoParams.add("user-param", new JsonPrimitive("user-val"));

        JsonObject userInfoHeaders = new JsonObject();
        userInfoHeaders.add("user-header", new JsonPrimitive("user-header-val"));

        mtStorage.overwriteTenantConfig(new TenantConfig(
                new TenantIdentifier(null, null, null),
                new EmailPasswordConfig(true),
                new ThirdPartyConfig(true, new ThirdPartyConfig.Provider[]{
                        new ThirdPartyConfig.Provider(
                                "google",
                                "Google",
                                new ThirdPartyConfig.ProviderClient[]{
                                        new ThirdPartyConfig.ProviderClient(
                                                "web",
                                                "client-id",
                                                "client-secret",
                                                new String[]{"scope-1", "scope-2"},
                                                true,
                                                new JsonObject()
                                        )
                                },
                                "https://auth.example.com/auth",
                                authParams,
                                "https://auth.example.com/token",
                                tokenParams,
                                "https://auth.example.com/user",
                                userInfoParams,
                                userInfoHeaders,
                                "https://auth.example.com/jwks",
                                "https://auth.example.com",
                                true,
                                new ThirdPartyConfig.UserInfoMap(
                                        new ThirdPartyConfig.UserInfoMapKeyValue("id1", "email1", "email_verified1"),
                                        new ThirdPartyConfig.UserInfoMapKeyValue("id2", "email2", "email_verified2")
                                )
                        )
                }),
                new PasswordlessConfig(true),
                null, null, new JsonObject()
        ));

        TenantConfig[] tenantConfigs = mtStorage.getAllTenants();

        assertEquals(1, tenantConfigs.length);
        TenantConfig baseTenantConfig = tenantConfigs[0];

        assertEquals(new TenantIdentifier(null, null, null), baseTenantConfig.tenantIdentifier);
        assertTrue(baseTenantConfig.emailPasswordConfig.enabled);
        assertTrue(baseTenantConfig.passwordlessConfig.enabled);
        assertTrue(baseTenantConfig.thirdPartyConfig.enabled);
        assertNotNull(baseTenantConfig.thirdPartyConfig.providers);
        assertEquals(1, baseTenantConfig.thirdPartyConfig.providers.length);

        ThirdPartyConfig.Provider provider = baseTenantConfig.thirdPartyConfig.providers[0];

        assertEquals("google", provider.thirdPartyId);
        assertEquals("Google", provider.name);
        assertEquals("https://auth.example.com/auth", provider.authorizationEndpoint);
        assertEquals(authParams, provider.authorizationEndpointQueryParams);
        assertEquals("https://auth.example.com/token", provider.tokenEndpoint);
        assertEquals(tokenParams, provider.tokenEndpointBodyParams);
        assertEquals("https://auth.example.com/user", provider.userInfoEndpoint);
        assertEquals(userInfoParams, provider.userInfoEndpointQueryParams);
        assertEquals(userInfoHeaders, provider.userInfoEndpointHeaders);
        assertEquals("https://auth.example.com/jwks", provider.jwksURI);
        assertEquals("https://auth.example.com", provider.oidcDiscoveryEndpoint);
        assertTrue(provider.requireEmail);
        assertEquals("id1", provider.userInfoMap.fromIdTokenPayload.userId);
        assertEquals("email1", provider.userInfoMap.fromIdTokenPayload.email);
        assertEquals("email_verified1", provider.userInfoMap.fromIdTokenPayload.emailVerified);
        assertEquals("id2", provider.userInfoMap.fromUserInfoAPI.userId);
        assertEquals("email2", provider.userInfoMap.fromUserInfoAPI.email);
        assertEquals("email_verified2", provider.userInfoMap.fromUserInfoAPI.emailVerified);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testUpdationOfDefaultTenantWithNullClientType()
            throws InterruptedException, DuplicateTenantException, StorageQueryException, TenantOrAppNotFoundException,
            DuplicateThirdPartyIdException, DuplicateClientTypeException {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args, false);
        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{EE_FEATURES.MULTI_TENANCY});
        process.startProcess();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.LOADING_ALL_TENANT_STORAGE));
        MultitenancyStorage mtStorage = StorageLayer.getMultitenancyStorage(process.getProcess());

        JsonObject authParams = new JsonObject();
        authParams.add("auth-param", new JsonPrimitive("auth-val"));

        JsonObject tokenParams = new JsonObject();
        tokenParams.add("token-param", new JsonPrimitive("token-val"));

        JsonObject userInfoParams = new JsonObject();
        userInfoParams.add("user-param", new JsonPrimitive("user-val"));

        JsonObject userInfoHeaders = new JsonObject();
        userInfoHeaders.add("user-header", new JsonPrimitive("user-header-val"));

        mtStorage.overwriteTenantConfig(new TenantConfig(
                new TenantIdentifier(null, null, null),
                new EmailPasswordConfig(true),
                new ThirdPartyConfig(true, new ThirdPartyConfig.Provider[]{
                        new ThirdPartyConfig.Provider(
                                "google",
                                "Google",
                                new ThirdPartyConfig.ProviderClient[]{
                                        new ThirdPartyConfig.ProviderClient(
                                                null,
                                                "client-id",
                                                "client-secret",
                                                new String[]{"scope-1", "scope-2"},
                                                true,
                                                new JsonObject()
                                        )
                                },
                                "https://auth.example.com/auth",
                                authParams,
                                "https://auth.example.com/token",
                                tokenParams,
                                "https://auth.example.com/user",
                                userInfoParams,
                                userInfoHeaders,
                                "https://auth.example.com/jwks",
                                "https://auth.example.com",
                                true,
                                new ThirdPartyConfig.UserInfoMap(
                                        new ThirdPartyConfig.UserInfoMapKeyValue("id1", "email1", "email_verified1"),
                                        new ThirdPartyConfig.UserInfoMapKeyValue("id2", "email2", "email_verified2")
                                )
                        )
                }),
                new PasswordlessConfig(true),
                null, null, new JsonObject()
        ));

        TenantConfig[] tenantConfigs = mtStorage.getAllTenants();

        assertEquals(1, tenantConfigs.length);
        TenantConfig baseTenantConfig = tenantConfigs[0];

        assertEquals(new TenantIdentifier(null, null, null), baseTenantConfig.tenantIdentifier);
        assertTrue(baseTenantConfig.emailPasswordConfig.enabled);
        assertTrue(baseTenantConfig.passwordlessConfig.enabled);
        assertTrue(baseTenantConfig.thirdPartyConfig.enabled);
        assertNotNull(baseTenantConfig.thirdPartyConfig.providers);
        assertEquals(1, baseTenantConfig.thirdPartyConfig.providers.length);

        ThirdPartyConfig.Provider provider = baseTenantConfig.thirdPartyConfig.providers[0];

        assertEquals("google", provider.thirdPartyId);
        assertEquals("Google", provider.name);
        assertEquals("https://auth.example.com/auth", provider.authorizationEndpoint);
        assertEquals(authParams, provider.authorizationEndpointQueryParams);
        assertEquals("https://auth.example.com/token", provider.tokenEndpoint);
        assertEquals(tokenParams, provider.tokenEndpointBodyParams);
        assertEquals("https://auth.example.com/user", provider.userInfoEndpoint);
        assertEquals(userInfoParams, provider.userInfoEndpointQueryParams);
        assertEquals(userInfoHeaders, provider.userInfoEndpointHeaders);
        assertEquals("https://auth.example.com/jwks", provider.jwksURI);
        assertEquals("https://auth.example.com", provider.oidcDiscoveryEndpoint);
        assertTrue(provider.requireEmail);
        assertEquals("id1", provider.userInfoMap.fromIdTokenPayload.userId);
        assertEquals("email1", provider.userInfoMap.fromIdTokenPayload.email);
        assertEquals("email_verified1", provider.userInfoMap.fromIdTokenPayload.emailVerified);
        assertEquals("id2", provider.userInfoMap.fromUserInfoAPI.userId);
        assertEquals("email2", provider.userInfoMap.fromUserInfoAPI.email);
        assertEquals("email_verified2", provider.userInfoMap.fromUserInfoAPI.emailVerified);

        assertEquals(1, provider.clients.length);
        ThirdPartyConfig.ProviderClient client = provider.clients[0];

        assertEquals(null, client.clientType);
        assertEquals("client-id", client.clientId);
        assertEquals("client-secret", client.clientSecret);
        assertEquals(new String[]{"scope-1", "scope-2"}, client.scope);
        assertTrue(client.forcePKCE);
        assertEquals(new JsonObject(), client.additionalConfig);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testForNullsInUpdationOfDefaultTenant()
            throws InterruptedException, StorageQueryException, TenantOrAppNotFoundException,
            DuplicateThirdPartyIdException, DuplicateClientTypeException {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args, false);
        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{EE_FEATURES.MULTI_TENANCY});
        process.startProcess();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.LOADING_ALL_TENANT_STORAGE));
        MultitenancyStorage mtStorage = StorageLayer.getMultitenancyStorage(process.getProcess());

        mtStorage.overwriteTenantConfig(new TenantConfig(
                new TenantIdentifier(null, null, null),
                new EmailPasswordConfig(true),
                new ThirdPartyConfig(true, new ThirdPartyConfig.Provider[]{
                        new ThirdPartyConfig.Provider(
                                "google",
                                "Google",
                                new ThirdPartyConfig.ProviderClient[]{
                                        new ThirdPartyConfig.ProviderClient(
                                                null,
                                                "client-id",
                                                null,
                                                null,
                                                false,
                                                null
                                        )
                                },
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                true,
                                new ThirdPartyConfig.UserInfoMap(
                                        new ThirdPartyConfig.UserInfoMapKeyValue(null, null, null),
                                        new ThirdPartyConfig.UserInfoMapKeyValue(null, null, null)
                                )
                        )
                }),
                new PasswordlessConfig(true),
                null, null, new JsonObject()
        ));

        TenantConfig[] tenantConfigs = mtStorage.getAllTenants();

        assertEquals(1, tenantConfigs.length);
        TenantConfig baseTenantConfig = tenantConfigs[0];

        assertEquals(new TenantIdentifier(null, null, null), baseTenantConfig.tenantIdentifier);
        assertTrue(baseTenantConfig.emailPasswordConfig.enabled);
        assertTrue(baseTenantConfig.passwordlessConfig.enabled);
        assertTrue(baseTenantConfig.thirdPartyConfig.enabled);
        assertNotNull(baseTenantConfig.thirdPartyConfig.providers);
        assertEquals(1, baseTenantConfig.thirdPartyConfig.providers.length);

        ThirdPartyConfig.Provider provider = baseTenantConfig.thirdPartyConfig.providers[0];

        assertEquals("google", provider.thirdPartyId);
        assertEquals("Google", provider.name);
        assertEquals(null, provider.authorizationEndpoint);
        assertEquals(null, provider.authorizationEndpointQueryParams);
        assertEquals(null, provider.tokenEndpoint);
        assertEquals(null, provider.tokenEndpointBodyParams);
        assertEquals(null, provider.userInfoEndpoint);
        assertEquals(null, provider.userInfoEndpointQueryParams);
        assertEquals(null, provider.userInfoEndpointHeaders);
        assertEquals(null, provider.jwksURI);
        assertEquals(null, provider.oidcDiscoveryEndpoint);
        assertTrue(provider.requireEmail);
        assertEquals(null, provider.userInfoMap.fromIdTokenPayload.userId);
        assertEquals(null, provider.userInfoMap.fromIdTokenPayload.email);
        assertEquals(null, provider.userInfoMap.fromIdTokenPayload.emailVerified);
        assertEquals(null, provider.userInfoMap.fromUserInfoAPI.userId);
        assertEquals(null, provider.userInfoMap.fromUserInfoAPI.email);
        assertEquals(null, provider.userInfoMap.fromUserInfoAPI.emailVerified);

        assertEquals(1, provider.clients.length);
        ThirdPartyConfig.ProviderClient client = provider.clients[0];

        assertEquals(null, client.clientType);
        assertEquals("client-id", client.clientId);
        assertEquals(null, client.clientSecret);
        assertEquals(null, client.scope);
        assertFalse(client.forcePKCE);
        assertEquals(null, client.additionalConfig);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testForNullClientsListInUpdationOfDefaultTenant()
            throws InterruptedException, StorageQueryException, TenantOrAppNotFoundException,
            DuplicateThirdPartyIdException, DuplicateClientTypeException {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args, false);
        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{EE_FEATURES.MULTI_TENANCY});
        process.startProcess();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.LOADING_ALL_TENANT_STORAGE));
        MultitenancyStorage mtStorage = StorageLayer.getMultitenancyStorage(process.getProcess());

        mtStorage.overwriteTenantConfig(new TenantConfig(
                new TenantIdentifier(null, null, null),
                new EmailPasswordConfig(true),
                new ThirdPartyConfig(true, new ThirdPartyConfig.Provider[]{
                        new ThirdPartyConfig.Provider(
                                "google",
                                "Google",
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                true,
                                new ThirdPartyConfig.UserInfoMap(
                                        new ThirdPartyConfig.UserInfoMapKeyValue(null, null, null),
                                        new ThirdPartyConfig.UserInfoMapKeyValue(null, null, null)
                                )
                        )
                }),
                new PasswordlessConfig(true),
                null, null, new JsonObject()
        ));

        TenantConfig[] tenantConfigs = mtStorage.getAllTenants();

        assertEquals(1, tenantConfigs.length);
        TenantConfig baseTenantConfig = tenantConfigs[0];

        assertEquals(new TenantIdentifier(null, null, null), baseTenantConfig.tenantIdentifier);
        assertTrue(baseTenantConfig.emailPasswordConfig.enabled);
        assertTrue(baseTenantConfig.passwordlessConfig.enabled);
        assertTrue(baseTenantConfig.thirdPartyConfig.enabled);
        assertNotNull(baseTenantConfig.thirdPartyConfig.providers);
        assertEquals(1, baseTenantConfig.thirdPartyConfig.providers.length);

        ThirdPartyConfig.Provider provider = baseTenantConfig.thirdPartyConfig.providers[0];

        assertEquals("google", provider.thirdPartyId);
        assertEquals("Google", provider.name);
        assertEquals(0, provider.clients.length);
        assertEquals(null, provider.authorizationEndpoint);
        assertEquals(null, provider.authorizationEndpointQueryParams);
        assertEquals(null, provider.tokenEndpoint);
        assertEquals(null, provider.tokenEndpointBodyParams);
        assertEquals(null, provider.userInfoEndpoint);
        assertEquals(null, provider.userInfoEndpointQueryParams);
        assertEquals(null, provider.userInfoEndpointHeaders);
        assertEquals(null, provider.jwksURI);
        assertEquals(null, provider.oidcDiscoveryEndpoint);
        assertTrue(provider.requireEmail);
        assertEquals(null, provider.userInfoMap.fromIdTokenPayload.userId);
        assertEquals(null, provider.userInfoMap.fromIdTokenPayload.email);
        assertEquals(null, provider.userInfoMap.fromIdTokenPayload.emailVerified);
        assertEquals(null, provider.userInfoMap.fromUserInfoAPI.userId);
        assertEquals(null, provider.userInfoMap.fromUserInfoAPI.email);
        assertEquals(null, provider.userInfoMap.fromUserInfoAPI.emailVerified);

        assertEquals(0, provider.clients.length);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testForNullProvidersListInUpdationOfDefaultTenant()
            throws InterruptedException, StorageQueryException, TenantOrAppNotFoundException,
            DuplicateThirdPartyIdException, DuplicateClientTypeException {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args, false);
        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{EE_FEATURES.MULTI_TENANCY});
        process.startProcess();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.LOADING_ALL_TENANT_STORAGE));
        MultitenancyStorage mtStorage = StorageLayer.getMultitenancyStorage(process.getProcess());

        mtStorage.overwriteTenantConfig(new TenantConfig(
                new TenantIdentifier(null, null, null),
                new EmailPasswordConfig(true),
                new ThirdPartyConfig(true, null),
                new PasswordlessConfig(true),
                null, null, new JsonObject()
        ));

        TenantConfig[] tenantConfigs = mtStorage.getAllTenants();

        assertEquals(1, tenantConfigs.length);
        TenantConfig baseTenantConfig = tenantConfigs[0];

        assertEquals(new TenantIdentifier(null, null, null), baseTenantConfig.tenantIdentifier);
        assertTrue(baseTenantConfig.emailPasswordConfig.enabled);
        assertTrue(baseTenantConfig.passwordlessConfig.enabled);
        assertTrue(baseTenantConfig.thirdPartyConfig.enabled);
        assertEquals(0, baseTenantConfig.thirdPartyConfig.providers.length);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testCreateTenantPersistsDataCorrectly()
            throws DuplicateTenantException, StorageQueryException, InterruptedException, InvalidConfigException,
            DuplicateThirdPartyIdException, DuplicateClientTypeException {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args, false);
        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{EE_FEATURES.MULTI_TENANCY});
        process.startProcess();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.LOADING_ALL_TENANT_STORAGE));
        MultitenancyStorage mtStorage = StorageLayer.getMultitenancyStorage(process.getProcess());

        JsonObject authParams = new JsonObject();
        authParams.add("auth-param", new JsonPrimitive("auth-val"));

        JsonObject tokenParams = new JsonObject();
        tokenParams.add("token-param", new JsonPrimitive("token-val"));

        JsonObject userInfoParams = new JsonObject();
        userInfoParams.add("user-param", new JsonPrimitive("user-val"));

        JsonObject userInfoHeaders = new JsonObject();
        userInfoHeaders.add("user-header", new JsonPrimitive("user-header-val"));

        mtStorage.createTenant(new TenantConfig(
                new TenantIdentifier(null, null, "t1"),
                new EmailPasswordConfig(true),
                new ThirdPartyConfig(true, new ThirdPartyConfig.Provider[]{
                        new ThirdPartyConfig.Provider(
                                "google",
                                "Google",
                                new ThirdPartyConfig.ProviderClient[]{
                                        new ThirdPartyConfig.ProviderClient(
                                                "web",
                                                "client-id",
                                                "client-secret",
                                                new String[]{"scope-1", "scope-2"},
                                                true,
                                                new JsonObject()
                                        )
                                },
                                "https://auth.example.com/auth",
                                authParams,
                                "https://auth.example.com/token",
                                tokenParams,
                                "https://auth.example.com/user",
                                userInfoParams,
                                userInfoHeaders,
                                "https://auth.example.com/jwks",
                                "https://auth.example.com",
                                true,
                                new ThirdPartyConfig.UserInfoMap(
                                        new ThirdPartyConfig.UserInfoMapKeyValue("id1", "email1", "email_verified1"),
                                        new ThirdPartyConfig.UserInfoMapKeyValue("id2", "email2", "email_verified2")
                                )
                        )
                }),
                new PasswordlessConfig(true),
                null, null, new JsonObject()
        ));

        TenantConfig[] tenantConfigs = mtStorage.getAllTenants();

        assertEquals(2, tenantConfigs.length);
        TenantConfig newTenantConfig = tenantConfigs[1];

        assertEquals(new TenantIdentifier(null, null, "t1"), newTenantConfig.tenantIdentifier);
        assertTrue(newTenantConfig.emailPasswordConfig.enabled);
        assertTrue(newTenantConfig.passwordlessConfig.enabled);
        assertTrue(newTenantConfig.thirdPartyConfig.enabled);
        assertNotNull(newTenantConfig.thirdPartyConfig.providers);
        assertEquals(1, newTenantConfig.thirdPartyConfig.providers.length);

        ThirdPartyConfig.Provider provider = newTenantConfig.thirdPartyConfig.providers[0];

        assertEquals("google", provider.thirdPartyId);
        assertEquals("Google", provider.name);
        assertEquals("https://auth.example.com/auth", provider.authorizationEndpoint);
        assertEquals(authParams, provider.authorizationEndpointQueryParams);
        assertEquals("https://auth.example.com/token", provider.tokenEndpoint);
        assertEquals(tokenParams, provider.tokenEndpointBodyParams);
        assertEquals("https://auth.example.com/user", provider.userInfoEndpoint);
        assertEquals(userInfoParams, provider.userInfoEndpointQueryParams);
        assertEquals(userInfoHeaders, provider.userInfoEndpointHeaders);
        assertEquals("https://auth.example.com/jwks", provider.jwksURI);
        assertEquals("https://auth.example.com", provider.oidcDiscoveryEndpoint);
        assertTrue(provider.requireEmail);
        assertEquals("id1", provider.userInfoMap.fromIdTokenPayload.userId);
        assertEquals("email1", provider.userInfoMap.fromIdTokenPayload.email);
        assertEquals("email_verified1", provider.userInfoMap.fromIdTokenPayload.emailVerified);
        assertEquals("id2", provider.userInfoMap.fromUserInfoAPI.userId);
        assertEquals("email2", provider.userInfoMap.fromUserInfoAPI.email);
        assertEquals("email_verified2", provider.userInfoMap.fromUserInfoAPI.emailVerified);

        assertEquals(1, provider.clients.length);
        ThirdPartyConfig.ProviderClient client = provider.clients[0];

        assertEquals("web", client.clientType);
        assertEquals("client-id", client.clientId);
        assertEquals("client-secret", client.clientSecret);
        assertEquals(new String[]{"scope-1", "scope-2"}, client.scope);
        assertTrue(client.forcePKCE);
        assertEquals(new JsonObject(), client.additionalConfig);


        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testCreationOfDuplicationTenantThrowsDuplicateTenantException()
            throws DuplicateTenantException, StorageQueryException, InterruptedException, InvalidConfigException,
            DuplicateThirdPartyIdException, DuplicateClientTypeException {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args, false);
        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{EE_FEATURES.MULTI_TENANCY});
        process.startProcess();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.LOADING_ALL_TENANT_STORAGE));
        MultitenancyStorage mtStorage = StorageLayer.getMultitenancyStorage(process.getProcess());

        JsonObject authParams = new JsonObject();
        authParams.add("auth-param", new JsonPrimitive("auth-val"));

        JsonObject tokenParams = new JsonObject();
        tokenParams.add("token-param", new JsonPrimitive("token-val"));

        JsonObject userInfoParams = new JsonObject();
        userInfoParams.add("user-param", new JsonPrimitive("user-val"));

        JsonObject userInfoHeaders = new JsonObject();
        userInfoHeaders.add("user-header", new JsonPrimitive("user-header-val"));

        mtStorage.createTenant(new TenantConfig(
                new TenantIdentifier(null, null, "t1"),
                new EmailPasswordConfig(true),
                new ThirdPartyConfig(true, new ThirdPartyConfig.Provider[]{
                        new ThirdPartyConfig.Provider(
                                "google",
                                "Google",
                                new ThirdPartyConfig.ProviderClient[]{
                                        new ThirdPartyConfig.ProviderClient(
                                                "web",
                                                "client-id",
                                                "client-secret",
                                                new String[]{"scope-1", "scope-2"},
                                                true,
                                                new JsonObject()
                                        )
                                },
                                "https://auth.example.com/auth",
                                authParams,
                                "https://auth.example.com/token",
                                tokenParams,
                                "https://auth.example.com/user",
                                userInfoParams,
                                userInfoHeaders,
                                "https://auth.example.com/jwks",
                                "https://auth.example.com",
                                true,
                                new ThirdPartyConfig.UserInfoMap(
                                        new ThirdPartyConfig.UserInfoMapKeyValue("id1", "email1", "email_verified1"),
                                        new ThirdPartyConfig.UserInfoMapKeyValue("id2", "email2", "email_verified2")
                                )
                        )
                }),
                new PasswordlessConfig(true),
                null, null, new JsonObject()
        ));

        try {
            mtStorage.createTenant(new TenantConfig(
                    new TenantIdentifier(null, null, "t1"),
                    new EmailPasswordConfig(true),
                    new ThirdPartyConfig(true, new ThirdPartyConfig.Provider[]{
                            new ThirdPartyConfig.Provider(
                                    "google",
                                    "Google",
                                    new ThirdPartyConfig.ProviderClient[]{
                                            new ThirdPartyConfig.ProviderClient(
                                                    "web",
                                                    "client-id",
                                                    "client-secret",
                                                    new String[]{"scope-1", "scope-2"},
                                                    true,
                                                    new JsonObject()
                                            )
                                    },
                                    "https://auth.example.com/auth",
                                    authParams,
                                    "https://auth.example.com/token",
                                    tokenParams,
                                    "https://auth.example.com/user",
                                    userInfoParams,
                                    userInfoHeaders,
                                    "https://auth.example.com/jwks",
                                    "https://auth.example.com",
                                    true,
                                    new ThirdPartyConfig.UserInfoMap(
                                            new ThirdPartyConfig.UserInfoMapKeyValue("id1", "email1",
                                                    "email_verified1"),
                                            new ThirdPartyConfig.UserInfoMapKeyValue("id2", "email2", "email_verified2")
                                    )
                            )
                    }),
                    new PasswordlessConfig(true),
                    null, null, new JsonObject()
            ));
            fail();
        } catch (DuplicateTenantException e) {
            // pass
        }

        TenantConfig[] tenantConfigs = mtStorage.getAllTenants();

        assertEquals(2, tenantConfigs.length);
        TenantConfig newTenantConfig = tenantConfigs[1];

        assertEquals(new TenantIdentifier(null, null, "t1"), newTenantConfig.tenantIdentifier);
        assertTrue(newTenantConfig.emailPasswordConfig.enabled);
        assertTrue(newTenantConfig.passwordlessConfig.enabled);
        assertTrue(newTenantConfig.thirdPartyConfig.enabled);
        assertNotNull(newTenantConfig.thirdPartyConfig.providers);
        assertEquals(1, newTenantConfig.thirdPartyConfig.providers.length);

        ThirdPartyConfig.Provider provider = newTenantConfig.thirdPartyConfig.providers[0];

        assertEquals("google", provider.thirdPartyId);
        assertEquals("Google", provider.name);
        assertEquals("https://auth.example.com/auth", provider.authorizationEndpoint);
        assertEquals(authParams, provider.authorizationEndpointQueryParams);
        assertEquals("https://auth.example.com/token", provider.tokenEndpoint);
        assertEquals(tokenParams, provider.tokenEndpointBodyParams);
        assertEquals("https://auth.example.com/user", provider.userInfoEndpoint);
        assertEquals(userInfoParams, provider.userInfoEndpointQueryParams);
        assertEquals(userInfoHeaders, provider.userInfoEndpointHeaders);
        assertEquals("https://auth.example.com/jwks", provider.jwksURI);
        assertEquals("https://auth.example.com", provider.oidcDiscoveryEndpoint);
        assertTrue(provider.requireEmail);
        assertEquals("id1", provider.userInfoMap.fromIdTokenPayload.userId);
        assertEquals("email1", provider.userInfoMap.fromIdTokenPayload.email);
        assertEquals("email_verified1", provider.userInfoMap.fromIdTokenPayload.emailVerified);
        assertEquals("id2", provider.userInfoMap.fromUserInfoAPI.userId);
        assertEquals("email2", provider.userInfoMap.fromUserInfoAPI.email);
        assertEquals("email_verified2", provider.userInfoMap.fromUserInfoAPI.emailVerified);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testOverwriteTenantOfNonExistantTenantThrowsTenantOrAppNotFoundException()
            throws StorageQueryException, InterruptedException, DuplicateThirdPartyIdException,
            DuplicateClientTypeException {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args, false);
        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{EE_FEATURES.MULTI_TENANCY});
        process.startProcess();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.LOADING_ALL_TENANT_STORAGE));
        MultitenancyStorage mtStorage = StorageLayer.getMultitenancyStorage(process.getProcess());

        JsonObject authParams = new JsonObject();
        authParams.add("auth-param", new JsonPrimitive("auth-val"));

        JsonObject tokenParams = new JsonObject();
        tokenParams.add("token-param", new JsonPrimitive("token-val"));

        JsonObject userInfoParams = new JsonObject();
        userInfoParams.add("user-param", new JsonPrimitive("user-val"));

        JsonObject userInfoHeaders = new JsonObject();
        userInfoHeaders.add("user-header", new JsonPrimitive("user-header-val"));

        try {
            mtStorage.overwriteTenantConfig(new TenantConfig(
                    new TenantIdentifier(null, null, "t1"),
                    new EmailPasswordConfig(true),
                    new ThirdPartyConfig(true, new ThirdPartyConfig.Provider[]{
                            new ThirdPartyConfig.Provider(
                                    "google",
                                    "Google",
                                    new ThirdPartyConfig.ProviderClient[]{
                                            new ThirdPartyConfig.ProviderClient(
                                                    "web",
                                                    "client-id",
                                                    "client-secret",
                                                    new String[]{"scope-1", "scope-2"},
                                                    true,
                                                    new JsonObject()
                                            )
                                    },
                                    "https://auth.example.com/auth",
                                    authParams,
                                    "https://auth.example.com/token",
                                    tokenParams,
                                    "https://auth.example.com/user",
                                    userInfoParams,
                                    userInfoHeaders,
                                    "https://auth.example.com/jwks",
                                    "https://auth.example.com",
                                    true,
                                    new ThirdPartyConfig.UserInfoMap(
                                            new ThirdPartyConfig.UserInfoMapKeyValue("id1", "email1",
                                                    "email_verified1"),
                                            new ThirdPartyConfig.UserInfoMapKeyValue("id2", "email2", "email_verified2")
                                    )
                            )
                    }),
                    new PasswordlessConfig(true),
                    null, null, new JsonObject()
            ));
            fail();
        } catch (TenantOrAppNotFoundException e) {
            // pass0-89uuuuuui8j=
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testCreateTenantWithDuplicateProviderIdThrowsException()
            throws DuplicateTenantException, InterruptedException, StorageQueryException, DuplicateClientTypeException {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args, false);
        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{EE_FEATURES.MULTI_TENANCY});
        process.startProcess();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.LOADING_ALL_TENANT_STORAGE));
        MultitenancyStorage mtStorage = StorageLayer.getMultitenancyStorage(process.getProcess());

        JsonObject authParams = new JsonObject();
        authParams.add("auth-param", new JsonPrimitive("auth-val"));

        JsonObject tokenParams = new JsonObject();
        tokenParams.add("token-param", new JsonPrimitive("token-val"));

        JsonObject userInfoParams = new JsonObject();
        userInfoParams.add("user-param", new JsonPrimitive("user-val"));

        JsonObject userInfoHeaders = new JsonObject();
        userInfoHeaders.add("user-header", new JsonPrimitive("user-header-val"));
        try {
            mtStorage.createTenant(new TenantConfig(
                    new TenantIdentifier(null, null, "t1"),
                    new EmailPasswordConfig(true),
                    new ThirdPartyConfig(true, new ThirdPartyConfig.Provider[]{
                            new ThirdPartyConfig.Provider(
                                    "google",
                                    "Google",
                                    new ThirdPartyConfig.ProviderClient[]{
                                            new ThirdPartyConfig.ProviderClient(
                                                    "web",
                                                    "client-id",
                                                    "client-secret",
                                                    new String[]{"scope-1", "scope-2"},
                                                    true,
                                                    new JsonObject()
                                            )
                                    },
                                    "https://auth.example.com/auth",
                                    authParams,
                                    "https://auth.example.com/token",
                                    tokenParams,
                                    "https://auth.example.com/user",
                                    userInfoParams,
                                    userInfoHeaders,
                                    "https://auth.example.com/jwks",
                                    "https://auth.example.com",
                                    true,
                                    new ThirdPartyConfig.UserInfoMap(
                                            new ThirdPartyConfig.UserInfoMapKeyValue("id1", "email1",
                                                    "email_verified1"),
                                            new ThirdPartyConfig.UserInfoMapKeyValue("id2", "email2", "email_verified2")
                                    )
                            ),
                            new ThirdPartyConfig.Provider(
                                    "google",
                                    "Google",
                                    new ThirdPartyConfig.ProviderClient[]{
                                            new ThirdPartyConfig.ProviderClient(
                                                    "web",
                                                    "client-id",
                                                    "client-secret",
                                                    new String[]{"scope-1", "scope-2"},
                                                    true,
                                                    new JsonObject()
                                            )
                                    },
                                    "https://auth.example.com/auth",
                                    authParams,
                                    "https://auth.example.com/token",
                                    tokenParams,
                                    "https://auth.example.com/user",
                                    userInfoParams,
                                    userInfoHeaders,
                                    "https://auth.example.com/jwks",
                                    "https://auth.example.com",
                                    true,
                                    new ThirdPartyConfig.UserInfoMap(
                                            new ThirdPartyConfig.UserInfoMapKeyValue("id1", "email1",
                                                    "email_verified1"),
                                            new ThirdPartyConfig.UserInfoMapKeyValue("id2", "email2", "email_verified2")
                                    )
                            )
                    }),
                    new PasswordlessConfig(true),
                    null, null, new JsonObject()
            ));
            fail();
        } catch (DuplicateThirdPartyIdException e) {
            // pass
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testCreateDuplicateTenantWithDuplicateProviderIdThrowsDuplicateTenantException()
            throws InterruptedException, StorageQueryException, DuplicateClientTypeException,
            DuplicateThirdPartyIdException {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args, false);
        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{EE_FEATURES.MULTI_TENANCY});
        process.startProcess();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.LOADING_ALL_TENANT_STORAGE));
        MultitenancyStorage mtStorage = StorageLayer.getMultitenancyStorage(process.getProcess());

        JsonObject authParams = new JsonObject();
        authParams.add("auth-param", new JsonPrimitive("auth-val"));

        JsonObject tokenParams = new JsonObject();
        tokenParams.add("token-param", new JsonPrimitive("token-val"));

        JsonObject userInfoParams = new JsonObject();
        userInfoParams.add("user-param", new JsonPrimitive("user-val"));

        JsonObject userInfoHeaders = new JsonObject();
        userInfoHeaders.add("user-header", new JsonPrimitive("user-header-val"));
        try {
            mtStorage.createTenant(new TenantConfig(
                    new TenantIdentifier(null, null, "t1"),
                    new EmailPasswordConfig(true),
                    new ThirdPartyConfig(true, new ThirdPartyConfig.Provider[]{
                            new ThirdPartyConfig.Provider(
                                    "google",
                                    "Google",
                                    new ThirdPartyConfig.ProviderClient[]{
                                            new ThirdPartyConfig.ProviderClient(
                                                    "web",
                                                    "client-id",
                                                    "client-secret",
                                                    new String[]{"scope-1", "scope-2"},
                                                    true,
                                                    new JsonObject()
                                            )
                                    },
                                    "https://auth.example.com/auth",
                                    authParams,
                                    "https://auth.example.com/token",
                                    tokenParams,
                                    "https://auth.example.com/user",
                                    userInfoParams,
                                    userInfoHeaders,
                                    "https://auth.example.com/jwks",
                                    "https://auth.example.com",
                                    true,
                                    new ThirdPartyConfig.UserInfoMap(
                                            new ThirdPartyConfig.UserInfoMapKeyValue("id1", "email1",
                                                    "email_verified1"),
                                            new ThirdPartyConfig.UserInfoMapKeyValue("id2", "email2", "email_verified2")
                                    )
                            )
                    }),
                    new PasswordlessConfig(true),
                    null, null, new JsonObject()
            ));
        } catch (DuplicateTenantException e) {
            fail();
        }

        try {
            mtStorage.createTenant(new TenantConfig(
                    new TenantIdentifier(null, null, "t1"),
                    new EmailPasswordConfig(true),
                    new ThirdPartyConfig(true, new ThirdPartyConfig.Provider[]{
                            new ThirdPartyConfig.Provider(
                                    "google",
                                    "Google",
                                    new ThirdPartyConfig.ProviderClient[]{
                                            new ThirdPartyConfig.ProviderClient(
                                                    "web",
                                                    "client-id",
                                                    "client-secret",
                                                    new String[]{"scope-1", "scope-2"},
                                                    true,
                                                    new JsonObject()
                                            )
                                    },
                                    "https://auth.example.com/auth",
                                    authParams,
                                    "https://auth.example.com/token",
                                    tokenParams,
                                    "https://auth.example.com/user",
                                    userInfoParams,
                                    userInfoHeaders,
                                    "https://auth.example.com/jwks",
                                    "https://auth.example.com",
                                    true,
                                    new ThirdPartyConfig.UserInfoMap(
                                            new ThirdPartyConfig.UserInfoMapKeyValue("id1", "email1",
                                                    "email_verified1"),
                                            new ThirdPartyConfig.UserInfoMapKeyValue("id2", "email2", "email_verified2")
                                    )
                            ),
                            new ThirdPartyConfig.Provider(
                                    "google",
                                    "Google",
                                    new ThirdPartyConfig.ProviderClient[]{
                                            new ThirdPartyConfig.ProviderClient(
                                                    "web",
                                                    "client-id",
                                                    "client-secret",
                                                    new String[]{"scope-1", "scope-2"},
                                                    true,
                                                    new JsonObject()
                                            )
                                    },
                                    "https://auth.example.com/auth",
                                    authParams,
                                    "https://auth.example.com/token",
                                    tokenParams,
                                    "https://auth.example.com/user",
                                    userInfoParams,
                                    userInfoHeaders,
                                    "https://auth.example.com/jwks",
                                    "https://auth.example.com",
                                    true,
                                    new ThirdPartyConfig.UserInfoMap(
                                            new ThirdPartyConfig.UserInfoMapKeyValue("id1", "email1",
                                                    "email_verified1"),
                                            new ThirdPartyConfig.UserInfoMapKeyValue("id2", "email2", "email_verified2")
                                    )
                            )
                    }),
                    new PasswordlessConfig(true),
                    null, null, new JsonObject()
            ));
            fail();
        } catch (DuplicateTenantException e) {
            // pass
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testCreateDuplicateTenantWithDuplicateProviderClientTypeThrowsDuplicateTenantException()
            throws InterruptedException, StorageQueryException, DuplicateClientTypeException,
            DuplicateThirdPartyIdException {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args, false);
        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{EE_FEATURES.MULTI_TENANCY});
        process.startProcess();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.LOADING_ALL_TENANT_STORAGE));
        MultitenancyStorage mtStorage = StorageLayer.getMultitenancyStorage(process.getProcess());

        JsonObject authParams = new JsonObject();
        authParams.add("auth-param", new JsonPrimitive("auth-val"));

        JsonObject tokenParams = new JsonObject();
        tokenParams.add("token-param", new JsonPrimitive("token-val"));

        JsonObject userInfoParams = new JsonObject();
        userInfoParams.add("user-param", new JsonPrimitive("user-val"));

        JsonObject userInfoHeaders = new JsonObject();
        userInfoHeaders.add("user-header", new JsonPrimitive("user-header-val"));
        try {
            mtStorage.createTenant(new TenantConfig(
                    new TenantIdentifier(null, null, "t1"),
                    new EmailPasswordConfig(true),
                    new ThirdPartyConfig(true, new ThirdPartyConfig.Provider[]{
                            new ThirdPartyConfig.Provider(
                                    "google",
                                    "Google",
                                    new ThirdPartyConfig.ProviderClient[]{
                                            new ThirdPartyConfig.ProviderClient(
                                                    "web",
                                                    "client-id",
                                                    "client-secret",
                                                    new String[]{"scope-1", "scope-2"},
                                                    true,
                                                    new JsonObject()
                                            )
                                    },
                                    "https://auth.example.com/auth",
                                    authParams,
                                    "https://auth.example.com/token",
                                    tokenParams,
                                    "https://auth.example.com/user",
                                    userInfoParams,
                                    userInfoHeaders,
                                    "https://auth.example.com/jwks",
                                    "https://auth.example.com",
                                    true,
                                    new ThirdPartyConfig.UserInfoMap(
                                            new ThirdPartyConfig.UserInfoMapKeyValue("id1", "email1",
                                                    "email_verified1"),
                                            new ThirdPartyConfig.UserInfoMapKeyValue("id2", "email2", "email_verified2")
                                    )
                            )
                    }),
                    new PasswordlessConfig(true),
                    null, null, new JsonObject()
            ));
        } catch (DuplicateTenantException e) {
            fail();
        }

        try {
            mtStorage.createTenant(new TenantConfig(
                    new TenantIdentifier(null, null, "t1"),
                    new EmailPasswordConfig(true),
                    new ThirdPartyConfig(true, new ThirdPartyConfig.Provider[]{
                            new ThirdPartyConfig.Provider(
                                    "google",
                                    "Google",
                                    new ThirdPartyConfig.ProviderClient[]{
                                            new ThirdPartyConfig.ProviderClient(
                                                    "web",
                                                    "client-id",
                                                    "client-secret",
                                                    new String[]{"scope-1", "scope-2"},
                                                    true,
                                                    new JsonObject()
                                            ),
                                            new ThirdPartyConfig.ProviderClient(
                                                    "web",
                                                    "client-id",
                                                    "client-secret",
                                                    new String[]{"scope-1", "scope-2"},
                                                    true,
                                                    new JsonObject()
                                            )
                                    },
                                    "https://auth.example.com/auth",
                                    authParams,
                                    "https://auth.example.com/token",
                                    tokenParams,
                                    "https://auth.example.com/user",
                                    userInfoParams,
                                    userInfoHeaders,
                                    "https://auth.example.com/jwks",
                                    "https://auth.example.com",
                                    true,
                                    new ThirdPartyConfig.UserInfoMap(
                                            new ThirdPartyConfig.UserInfoMapKeyValue("id1", "email1",
                                                    "email_verified1"),
                                            new ThirdPartyConfig.UserInfoMapKeyValue("id2", "email2", "email_verified2")
                                    )
                            )
                    }),
                    new PasswordlessConfig(true),
                    null, null, new JsonObject()
            ));
            fail();
        } catch (DuplicateTenantException e) {
            // pass
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testCreateTenantWithDuplicateClientTypeThrowsException()
            throws DuplicateTenantException, InterruptedException, StorageQueryException,
            DuplicateThirdPartyIdException {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args, false);
        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{EE_FEATURES.MULTI_TENANCY});
        process.startProcess();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.LOADING_ALL_TENANT_STORAGE));
        MultitenancyStorage mtStorage = StorageLayer.getMultitenancyStorage(process.getProcess());

        JsonObject authParams = new JsonObject();
        authParams.add("auth-param", new JsonPrimitive("auth-val"));

        JsonObject tokenParams = new JsonObject();
        tokenParams.add("token-param", new JsonPrimitive("token-val"));

        JsonObject userInfoParams = new JsonObject();
        userInfoParams.add("user-param", new JsonPrimitive("user-val"));

        JsonObject userInfoHeaders = new JsonObject();
        userInfoHeaders.add("user-header", new JsonPrimitive("user-header-val"));
        try {
            mtStorage.createTenant(new TenantConfig(
                    new TenantIdentifier(null, null, "t1"),
                    new EmailPasswordConfig(true),
                    new ThirdPartyConfig(true, new ThirdPartyConfig.Provider[]{
                            new ThirdPartyConfig.Provider(
                                    "google",
                                    "Google",
                                    new ThirdPartyConfig.ProviderClient[]{
                                            new ThirdPartyConfig.ProviderClient(
                                                    "web",
                                                    "client-id",
                                                    "client-secret",
                                                    new String[]{"scope-1", "scope-2"},
                                                    true,
                                                    new JsonObject()
                                            ),
                                            new ThirdPartyConfig.ProviderClient(
                                                    "web",
                                                    "client-id",
                                                    "client-secret",
                                                    new String[]{"scope-1", "scope-2"},
                                                    true,
                                                    new JsonObject()
                                            ),
                                    },
                                    "https://auth.example.com/auth",
                                    authParams,
                                    "https://auth.example.com/token",
                                    tokenParams,
                                    "https://auth.example.com/user",
                                    userInfoParams,
                                    userInfoHeaders,
                                    "https://auth.example.com/jwks",
                                    "https://auth.example.com",
                                    true,
                                    new ThirdPartyConfig.UserInfoMap(
                                            new ThirdPartyConfig.UserInfoMapKeyValue("id1", "email1",
                                                    "email_verified1"),
                                            new ThirdPartyConfig.UserInfoMapKeyValue("id2", "email2", "email_verified2")
                                    )
                            ),
                            new ThirdPartyConfig.Provider(
                                    "google",
                                    "Google",
                                    new ThirdPartyConfig.ProviderClient[]{
                                            new ThirdPartyConfig.ProviderClient(
                                                    "web",
                                                    "client-id",
                                                    "client-secret",
                                                    new String[]{"scope-1", "scope-2"},
                                                    true,
                                                    new JsonObject()
                                            )
                                    },
                                    "https://auth.example.com/auth",
                                    authParams,
                                    "https://auth.example.com/token",
                                    tokenParams,
                                    "https://auth.example.com/user",
                                    userInfoParams,
                                    userInfoHeaders,
                                    "https://auth.example.com/jwks",
                                    "https://auth.example.com",
                                    true,
                                    new ThirdPartyConfig.UserInfoMap(
                                            new ThirdPartyConfig.UserInfoMapKeyValue("id1", "email1",
                                                    "email_verified1"),
                                            new ThirdPartyConfig.UserInfoMapKeyValue("id2", "email2", "email_verified2")
                                    )
                            )
                    }),
                    new PasswordlessConfig(true),
                    null, null, new JsonObject()
            ));
            fail();
        } catch (DuplicateClientTypeException e) {
            // Pass
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testOverwriteTenantWithDuplicateProviderIdThrowsException()
            throws InterruptedException, StorageQueryException, DuplicateClientTypeException,
            TenantOrAppNotFoundException {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args, false);
        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{EE_FEATURES.MULTI_TENANCY});
        process.startProcess();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.LOADING_ALL_TENANT_STORAGE));
        MultitenancyStorage mtStorage = StorageLayer.getMultitenancyStorage(process.getProcess());

        JsonObject authParams = new JsonObject();
        authParams.add("auth-param", new JsonPrimitive("auth-val"));

        JsonObject tokenParams = new JsonObject();
        tokenParams.add("token-param", new JsonPrimitive("token-val"));

        JsonObject userInfoParams = new JsonObject();
        userInfoParams.add("user-param", new JsonPrimitive("user-val"));

        JsonObject userInfoHeaders = new JsonObject();
        userInfoHeaders.add("user-header", new JsonPrimitive("user-header-val"));
        try {
            mtStorage.overwriteTenantConfig(new TenantConfig(
                    new TenantIdentifier(null, null, null),
                    new EmailPasswordConfig(true),
                    new ThirdPartyConfig(true, new ThirdPartyConfig.Provider[]{
                            new ThirdPartyConfig.Provider(
                                    "google",
                                    "Google",
                                    new ThirdPartyConfig.ProviderClient[]{
                                            new ThirdPartyConfig.ProviderClient(
                                                    "web",
                                                    "client-id",
                                                    "client-secret",
                                                    new String[]{"scope-1", "scope-2"},
                                                    true,
                                                    new JsonObject()
                                            )
                                    },
                                    "https://auth.example.com/auth",
                                    authParams,
                                    "https://auth.example.com/token",
                                    tokenParams,
                                    "https://auth.example.com/user",
                                    userInfoParams,
                                    userInfoHeaders,
                                    "https://auth.example.com/jwks",
                                    "https://auth.example.com",
                                    true,
                                    new ThirdPartyConfig.UserInfoMap(
                                            new ThirdPartyConfig.UserInfoMapKeyValue("id1", "email1",
                                                    "email_verified1"),
                                            new ThirdPartyConfig.UserInfoMapKeyValue("id2", "email2", "email_verified2")
                                    )
                            ),
                            new ThirdPartyConfig.Provider(
                                    "google",
                                    "Google",
                                    new ThirdPartyConfig.ProviderClient[]{
                                            new ThirdPartyConfig.ProviderClient(
                                                    "web",
                                                    "client-id",
                                                    "client-secret",
                                                    new String[]{"scope-1", "scope-2"},
                                                    true,
                                                    new JsonObject()
                                            )
                                    },
                                    "https://auth.example.com/auth",
                                    authParams,
                                    "https://auth.example.com/token",
                                    tokenParams,
                                    "https://auth.example.com/user",
                                    userInfoParams,
                                    userInfoHeaders,
                                    "https://auth.example.com/jwks",
                                    "https://auth.example.com",
                                    true,
                                    new ThirdPartyConfig.UserInfoMap(
                                            new ThirdPartyConfig.UserInfoMapKeyValue("id1", "email1",
                                                    "email_verified1"),
                                            new ThirdPartyConfig.UserInfoMapKeyValue("id2", "email2", "email_verified2")
                                    )
                            )
                    }),
                    new PasswordlessConfig(true),
                    null, null, new JsonObject()
            ));
            fail();
        } catch (DuplicateThirdPartyIdException e) {
            // pass
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testOverwriteTenantWithDuplicateClientTypeThrowsException()
            throws InterruptedException, StorageQueryException,
            DuplicateThirdPartyIdException, TenantOrAppNotFoundException {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args, false);
        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{EE_FEATURES.MULTI_TENANCY});
        process.startProcess();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.LOADING_ALL_TENANT_STORAGE));
        MultitenancyStorage mtStorage = StorageLayer.getMultitenancyStorage(process.getProcess());

        JsonObject authParams = new JsonObject();
        authParams.add("auth-param", new JsonPrimitive("auth-val"));

        JsonObject tokenParams = new JsonObject();
        tokenParams.add("token-param", new JsonPrimitive("token-val"));

        JsonObject userInfoParams = new JsonObject();
        userInfoParams.add("user-param", new JsonPrimitive("user-val"));

        JsonObject userInfoHeaders = new JsonObject();
        userInfoHeaders.add("user-header", new JsonPrimitive("user-header-val"));
        try {
            mtStorage.overwriteTenantConfig(new TenantConfig(
                    new TenantIdentifier(null, null, null),
                    new EmailPasswordConfig(true),
                    new ThirdPartyConfig(true, new ThirdPartyConfig.Provider[]{
                            new ThirdPartyConfig.Provider(
                                    "google",
                                    "Google",
                                    new ThirdPartyConfig.ProviderClient[]{
                                            new ThirdPartyConfig.ProviderClient(
                                                    "web",
                                                    "client-id",
                                                    "client-secret",
                                                    new String[]{"scope-1", "scope-2"},
                                                    true,
                                                    new JsonObject()
                                            ),
                                            new ThirdPartyConfig.ProviderClient(
                                                    "web",
                                                    "client-id",
                                                    "client-secret",
                                                    new String[]{"scope-1", "scope-2"},
                                                    true,
                                                    new JsonObject()
                                            ),
                                    },
                                    "https://auth.example.com/auth",
                                    authParams,
                                    "https://auth.example.com/token",
                                    tokenParams,
                                    "https://auth.example.com/user",
                                    userInfoParams,
                                    userInfoHeaders,
                                    "https://auth.example.com/jwks",
                                    "https://auth.example.com",
                                    true,
                                    new ThirdPartyConfig.UserInfoMap(
                                            new ThirdPartyConfig.UserInfoMapKeyValue("id1", "email1",
                                                    "email_verified1"),
                                            new ThirdPartyConfig.UserInfoMapKeyValue("id2", "email2", "email_verified2")
                                    )
                            ),
                            new ThirdPartyConfig.Provider(
                                    "google",
                                    "Google",
                                    new ThirdPartyConfig.ProviderClient[]{
                                            new ThirdPartyConfig.ProviderClient(
                                                    "web",
                                                    "client-id",
                                                    "client-secret",
                                                    new String[]{"scope-1", "scope-2"},
                                                    true,
                                                    new JsonObject()
                                            )
                                    },
                                    "https://auth.example.com/auth",
                                    authParams,
                                    "https://auth.example.com/token",
                                    tokenParams,
                                    "https://auth.example.com/user",
                                    userInfoParams,
                                    userInfoHeaders,
                                    "https://auth.example.com/jwks",
                                    "https://auth.example.com",
                                    true,
                                    new ThirdPartyConfig.UserInfoMap(
                                            new ThirdPartyConfig.UserInfoMapKeyValue("id1", "email1",
                                                    "email_verified1"),
                                            new ThirdPartyConfig.UserInfoMapKeyValue("id2", "email2", "email_verified2")
                                    )
                            )
                    }),
                    new PasswordlessConfig(true),
                    null, null, new JsonObject()
            ));
            fail();
        } catch (DuplicateClientTypeException e) {
            // Pass
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testOverwriteTenantForRaceConditions()
            throws StorageQueryException, InterruptedException {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args, false);
        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{EE_FEATURES.MULTI_TENANCY});
        process.startProcess();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.LOADING_ALL_TENANT_STORAGE));
        MultitenancyStorage mtStorage = StorageLayer.getMultitenancyStorage(process.getProcess());

        if (StorageLayer.isInMemDb(process.getProcess())) {
            return;
        }

        JsonObject authParams = new JsonObject();
        authParams.add("auth-param", new JsonPrimitive("auth-val"));

        JsonObject tokenParams = new JsonObject();
        tokenParams.add("token-param", new JsonPrimitive("token-val"));

        JsonObject userInfoParams = new JsonObject();
        userInfoParams.add("user-param", new JsonPrimitive("user-val"));

        JsonObject userInfoHeaders = new JsonObject();
        userInfoHeaders.add("user-header", new JsonPrimitive("user-header-val"));

        ExecutorService es = Executors.newFixedThreadPool(1000);

        AtomicBoolean pass = new AtomicBoolean(true);

        for (int i = 0; i < 3000; i++) {
            es.execute(() -> {
                while (true) {
                    try {
                        mtStorage.overwriteTenantConfig(new TenantConfig(
                                new TenantIdentifier(null, null, null),
                                new EmailPasswordConfig(true),
                                new ThirdPartyConfig(true, new ThirdPartyConfig.Provider[]{
                                        new ThirdPartyConfig.Provider(
                                                "google",
                                                "Google",
                                                new ThirdPartyConfig.ProviderClient[]{
                                                        new ThirdPartyConfig.ProviderClient(
                                                                "web",
                                                                "client-id",
                                                                "client-secret",
                                                                new String[]{"scope-1", "scope-2"},
                                                                true,
                                                                new JsonObject()
                                                        )
                                                },
                                                "https://auth.example.com/auth",
                                                authParams,
                                                "https://auth.example.com/token",
                                                tokenParams,
                                                "https://auth.example.com/user",
                                                userInfoParams,
                                                userInfoHeaders,
                                                "https://auth.example.com/jwks",
                                                "https://auth.example.com",
                                                true,
                                                new ThirdPartyConfig.UserInfoMap(
                                                        new ThirdPartyConfig.UserInfoMapKeyValue("id1", "email1",
                                                                "email_verified1"),
                                                        new ThirdPartyConfig.UserInfoMapKeyValue("id2", "email2",
                                                                "email_verified2")
                                                )
                                        )
                                }),
                                new PasswordlessConfig(true),
                                null, null, new JsonObject()
                        ));
                        break;
                    } catch (Exception e) {
                        if (e.getMessage().toLowerCase().contains("request timed out") ||
                                e.getMessage().contains("concurrent delete") ||
                                e.getMessage().contains("concurrent update")) {
                            // retry, because connection was timed out, or
                            // in case of postgres, number of retries may not be enough, we retry here anyway
                            continue;
                        }
                        System.out.println(e.getMessage());
                        pass.set(false);
                        break;
                    }
                }
            });
        }

        es.shutdown();
        es.awaitTermination(2, TimeUnit.MINUTES);

        assert (pass.get());

        TenantConfig[] tenantConfigs = mtStorage.getAllTenants();

        assertEquals(1, tenantConfigs.length);
        TenantConfig baseTenantConfig = tenantConfigs[0];

        assertEquals(new TenantIdentifier(null, null, null), baseTenantConfig.tenantIdentifier);
        assertTrue(baseTenantConfig.emailPasswordConfig.enabled);
        assertTrue(baseTenantConfig.passwordlessConfig.enabled);
        assertTrue(baseTenantConfig.thirdPartyConfig.enabled);
        assertNotNull(baseTenantConfig.thirdPartyConfig.providers);
        assertEquals(1, baseTenantConfig.thirdPartyConfig.providers.length);

        ThirdPartyConfig.Provider provider = baseTenantConfig.thirdPartyConfig.providers[0];

        assertEquals("google", provider.thirdPartyId);
        assertEquals("Google", provider.name);
        assertEquals("https://auth.example.com/auth", provider.authorizationEndpoint);
        assertEquals(authParams, provider.authorizationEndpointQueryParams);
        assertEquals("https://auth.example.com/token", provider.tokenEndpoint);
        assertEquals(tokenParams, provider.tokenEndpointBodyParams);
        assertEquals("https://auth.example.com/user", provider.userInfoEndpoint);
        assertEquals(userInfoParams, provider.userInfoEndpointQueryParams);
        assertEquals(userInfoHeaders, provider.userInfoEndpointHeaders);
        assertEquals("https://auth.example.com/jwks", provider.jwksURI);
        assertEquals("https://auth.example.com", provider.oidcDiscoveryEndpoint);
        assertTrue(provider.requireEmail);
        assertEquals("id1", provider.userInfoMap.fromIdTokenPayload.userId);
        assertEquals("email1", provider.userInfoMap.fromIdTokenPayload.email);
        assertEquals("email_verified1", provider.userInfoMap.fromIdTokenPayload.emailVerified);
        assertEquals("id2", provider.userInfoMap.fromUserInfoAPI.userId);
        assertEquals("email2", provider.userInfoMap.fromUserInfoAPI.email);
        assertEquals("email_verified2", provider.userInfoMap.fromUserInfoAPI.emailVerified);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testThatStoragePointingToSameDbSharesThInstance() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args, false);
        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{EE_FEATURES.MULTI_TENANCY});
        process.startProcess();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        JsonObject config1 = new JsonObject();
        JsonObject config2 = new JsonObject();
        StorageLayer.getStorage(new TenantIdentifier(null, null, null), process.getProcess())
                .modifyConfigToAddANewUserPoolForTesting(config2, 1);

        StorageLayer.loadAllTenantStorage(process.getProcess(), new TenantConfig[]{
                new TenantConfig(
                        new TenantIdentifier(null, null, "t1"),
                        new EmailPasswordConfig(true),
                        new ThirdPartyConfig(true, null),
                        new PasswordlessConfig(true),
                        null, null, config1
                ),
                new TenantConfig(
                        new TenantIdentifier(null, null, "t2"),
                        new EmailPasswordConfig(true),
                        new ThirdPartyConfig(true, null),
                        new PasswordlessConfig(true),
                        null, null, config1
                ),
                new TenantConfig(
                        new TenantIdentifier(null, "a1", null),
                        new EmailPasswordConfig(true),
                        new ThirdPartyConfig(true, null),
                        new PasswordlessConfig(true),
                        null, null, config2
                ),
                new TenantConfig(
                        new TenantIdentifier(null, "a1", "t1"),
                        new EmailPasswordConfig(true),
                        new ThirdPartyConfig(true, null),
                        new PasswordlessConfig(true),
                        null, null, config2
                )
        });

        TenantIdentifier[] tenants = new TenantIdentifier[]{
                TenantIdentifier.BASE_TENANT,
                new TenantIdentifier(null, null, "t1"),
                new TenantIdentifier(null, null, "t2"),
                new TenantIdentifier(null, "a1", null),
                new TenantIdentifier(null, "a1", "t1")
        };
        Map<String, Storage> storageMap = new HashMap<>();

        for (TenantIdentifier tenant : tenants) {
            Storage storage = StorageLayer.getStorage(tenant, process.getProcess());
            String userPoolId = storage.getUserPoolId();
            String connectionPoolId = storage.getConnectionPoolId();
            String uniqueId = userPoolId + "~" + connectionPoolId;

            if (storageMap.containsKey(uniqueId)) {
                assertEquals(storageMap.get(uniqueId), storage);
            } else {
                storageMap.put(uniqueId, storage);
            }
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testThatStorageIsClosedAfterTenantDeletion() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args, false);
        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{EE_FEATURES.MULTI_TENANCY});
        process.startProcess();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        if (StorageLayer.isInMemDb(process.getProcess())) {
            return;
        }

        JsonObject config = new JsonObject();
        StorageLayer.getStorage(new TenantIdentifier(null, null, null), process.getProcess())
                .modifyConfigToAddANewUserPoolForTesting(config, 1);

        Multitenancy.addNewOrUpdateAppOrTenant(process.getProcess(), new TenantConfig(
                new TenantIdentifier(null, null, "t1"),
                new EmailPasswordConfig(true),
                new ThirdPartyConfig(true, null),
                new PasswordlessConfig(true),
                null, null, config
        ), false);

        Storage storage = StorageLayer.getStorage(new TenantIdentifier(null, null, "t1"), process.getProcess());

        Multitenancy.deleteTenant(new TenantIdentifier(null, null, "t1"), process.getProcess());

        // Should not be able to query from the storage
        try {
            storage.getKeyValue(new TenantIdentifier(null, null, "t1"), "somekey");
            fail();
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("call initPool before getConnection"));
        }
        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testThatStorageIsClosedOnlyWhenNoMoreTenantsArePointingToIt() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args, false);
        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{EE_FEATURES.MULTI_TENANCY});
        process.startProcess();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        if (StorageLayer.isInMemDb(process.getProcess())) {
            return;
        }

        JsonObject config = new JsonObject();
        StorageLayer.getStorage(new TenantIdentifier(null, null, null), process.getProcess())
                .modifyConfigToAddANewUserPoolForTesting(config, 1);

        // 2 tenants using the same storage
        Multitenancy.addNewOrUpdateAppOrTenant(process.getProcess(), new TenantConfig(
                new TenantIdentifier(null, null, "t1"),
                new EmailPasswordConfig(true),
                new ThirdPartyConfig(true, null),
                new PasswordlessConfig(true),
                null, null, config
        ), false);
        Multitenancy.addNewOrUpdateAppOrTenant(process.getProcess(), new TenantConfig(
                new TenantIdentifier(null, null, "t2"),
                new EmailPasswordConfig(true),
                new ThirdPartyConfig(true, null),
                new PasswordlessConfig(true),
                null, null, config
        ), false);

        Storage storage = StorageLayer.getStorage(new TenantIdentifier(null, null, "t1"), process.getProcess());

        Multitenancy.deleteTenant(new TenantIdentifier(null, null, "t1"), process.getProcess());

        // Storage should still be active
        storage.getKeyValue(new TenantIdentifier(null, null, "t1"), "somekey");

        Multitenancy.deleteTenant(new TenantIdentifier(null, null, "t2"), process.getProcess());

        // Storage should be closed now
        try {
            storage.getKeyValue(new TenantIdentifier(null, null, "t1"), "somekey");
            fail();
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("call initPool before getConnection"));
        }
        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testStorageDoesNotLoadAgainAfterTenantDeletionWhenRefreshedFromDb() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args, false);
        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{EE_FEATURES.MULTI_TENANCY});
        process.startProcess();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        if (StorageLayer.isInMemDb(process.getProcess())) {
            return;
        }

        JsonObject config = new JsonObject();
        StorageLayer.getStorage(new TenantIdentifier(null, null, null), process.getProcess())
                .modifyConfigToAddANewUserPoolForTesting(config, 1);

        // 2 tenants using the same storage
        Multitenancy.addNewOrUpdateAppOrTenant(process.getProcess(), new TenantConfig(
                new TenantIdentifier(null, null, "t1"),
                new EmailPasswordConfig(true),
                new ThirdPartyConfig(true, null),
                new PasswordlessConfig(true),
                null, null, config
        ), false);
        Multitenancy.addNewOrUpdateAppOrTenant(process.getProcess(), new TenantConfig(
                new TenantIdentifier(null, null, "t2"),
                new EmailPasswordConfig(true),
                new ThirdPartyConfig(true, null),
                new PasswordlessConfig(true),
                null, null, config
        ), false);


        String userPoolId = StorageLayer.getStorage(new TenantIdentifier(null, null, "t1"), process.getProcess())
                .getUserPoolId();

        {
            Set<String> userPoolIds = new HashSet<>();
            Map<ResourceDistributor.KeyClass, ResourceDistributor.SingletonResource> existingStorages =
                    process.getProcess()
                            .getResourceDistributor().getAllResourcesWithResourceKey(StorageLayer.RESOURCE_KEY);

            for (ResourceDistributor.SingletonResource sl : existingStorages.values()) {
                userPoolIds.add(((StorageLayer) sl).getUnderlyingStorage().getUserPoolId());
            }
            assertTrue(userPoolIds.contains(userPoolId));
        }

        Multitenancy.deleteTenant(new TenantIdentifier(null, null, "t1"), process.getProcess());
        MultitenancyHelper.getInstance(process.getProcess())
                .refreshTenantsInCoreBasedOnChangesInCoreConfigOrIfTenantListChanged(true);

        {
            Set<String> userPoolIds = new HashSet<>();
            Map<ResourceDistributor.KeyClass, ResourceDistributor.SingletonResource> existingStorages =
                    process.getProcess()
                            .getResourceDistributor().getAllResourcesWithResourceKey(StorageLayer.RESOURCE_KEY);

            for (ResourceDistributor.SingletonResource sl : existingStorages.values()) {
                userPoolIds.add(((StorageLayer) sl).getUnderlyingStorage().getUserPoolId());
            }
            assertTrue(userPoolIds.contains(userPoolId));
        }

        Multitenancy.deleteTenant(new TenantIdentifier(null, null, "t2"), process.getProcess());
        MultitenancyHelper.getInstance(process.getProcess())
                .refreshTenantsInCoreBasedOnChangesInCoreConfigOrIfTenantListChanged(true);

        {
            Set<String> userPoolIds = new HashSet<>();
            Map<ResourceDistributor.KeyClass, ResourceDistributor.SingletonResource> existingStorages =
                    process.getProcess()
                            .getResourceDistributor().getAllResourcesWithResourceKey(StorageLayer.RESOURCE_KEY);

            for (ResourceDistributor.SingletonResource sl : existingStorages.values()) {
                userPoolIds.add(((StorageLayer) sl).getUnderlyingStorage().getUserPoolId());
            }
            assertFalse(userPoolIds.contains(userPoolId));
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testThatOriginalStorageIsNotClosedIfTheStorageForATenantChangesAndTheOriginalStorageIsUsedByAnotherTenant()
            throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args, false);
        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{EE_FEATURES.MULTI_TENANCY});
        process.startProcess();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        if (StorageLayer.isInMemDb(process.getProcess())) {
            return;
        }

        // The tenant shares storage with base
        Multitenancy.addNewOrUpdateAppOrTenant(process.getProcess(), new TenantConfig(
                new TenantIdentifier(null, null, "t1"),
                new EmailPasswordConfig(true),
                new ThirdPartyConfig(true, null),
                new PasswordlessConfig(true),
                null, null, new JsonObject()
        ), false);

        Storage storage = StorageLayer.getBaseStorage(process.getProcess());
        storage.getKeyValue(TenantIdentifier.BASE_TENANT, "somekey"); // Should pass

        // Change the storage
        JsonObject config = new JsonObject();
        StorageLayer.getStorage(new TenantIdentifier(null, null, null), process.getProcess())
                .modifyConfigToAddANewUserPoolForTesting(config, 1);
        Multitenancy.addNewOrUpdateAppOrTenant(process.getProcess(), new TenantConfig(
                new TenantIdentifier(null, null, "t1"),
                new EmailPasswordConfig(true),
                new ThirdPartyConfig(true, null),
                new PasswordlessConfig(true),
                null, null, config
        ), false);

        storage = StorageLayer.getBaseStorage(process.getProcess());
        storage.getKeyValue(TenantIdentifier.BASE_TENANT, "somekey"); // Should continue to pass

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }
}
