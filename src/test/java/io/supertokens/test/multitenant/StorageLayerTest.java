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
import io.supertokens.featureflag.EE_FEATURES;
import io.supertokens.featureflag.FeatureFlagTestContent;
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

        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.LOADING_ALL_TENANT_STORAGE));
        MultitenancyStorage mtStorage = StorageLayer.getMultitenancyStorage(process.getProcess());

        TenantConfig[] tenantConfigs = mtStorage.getAllTenants();

        assertEquals(1, tenantConfigs.length);
        TenantConfig baseTenantConfig = tenantConfigs[0];

        assertEquals(new TenantIdentifier(null, null, null), baseTenantConfig.tenantIdentifier);
        assertTrue(baseTenantConfig.emailPasswordConfig.enabled);
        assertTrue(baseTenantConfig.passwordlessConfig.enabled);
        assertTrue(baseTenantConfig.thirdPartyConfig.enabled);
        assertNotNull(baseTenantConfig.thirdPartyConfig.providers);
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
            new JsonObject()
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
    public void testForNullsInUpdationOfDefaultTenant()
            throws InterruptedException, StorageQueryException, TenantOrAppNotFoundException,
            DuplicateThirdPartyIdException, DuplicateClientTypeException {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args, false);
        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{EE_FEATURES.MULTI_TENANCY});
        process.startProcess();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

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
                                                true,
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
                new JsonObject()
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
                new JsonObject()
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
        assertEquals(null, provider.clients);
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

        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.LOADING_ALL_TENANT_STORAGE));
        MultitenancyStorage mtStorage = StorageLayer.getMultitenancyStorage(process.getProcess());

        mtStorage.overwriteTenantConfig(new TenantConfig(
                new TenantIdentifier(null, null, null),
                new EmailPasswordConfig(true),
                new ThirdPartyConfig(true, null),
                new PasswordlessConfig(true),
                new JsonObject()
        ));

        TenantConfig[] tenantConfigs = mtStorage.getAllTenants();

        assertEquals(1, tenantConfigs.length);
        TenantConfig baseTenantConfig = tenantConfigs[0];

        assertEquals(new TenantIdentifier(null, null, null), baseTenantConfig.tenantIdentifier);
        assertTrue(baseTenantConfig.emailPasswordConfig.enabled);
        assertTrue(baseTenantConfig.passwordlessConfig.enabled);
        assertTrue(baseTenantConfig.thirdPartyConfig.enabled);
        assertNotNull(baseTenantConfig.thirdPartyConfig.providers);
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
                new JsonObject()
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
                new JsonObject()
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
                                            new ThirdPartyConfig.UserInfoMapKeyValue("id1", "email1", "email_verified1"),
                                            new ThirdPartyConfig.UserInfoMapKeyValue("id2", "email2", "email_verified2")
                                    )
                            )
                    }),
                    new PasswordlessConfig(true),
                    new JsonObject()
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
                                            new ThirdPartyConfig.UserInfoMapKeyValue("id1", "email1", "email_verified1"),
                                            new ThirdPartyConfig.UserInfoMapKeyValue("id2", "email2", "email_verified2")
                                    )
                            )
                    }),
                    new PasswordlessConfig(true),
                    new JsonObject()
            ));
            fail();
        } catch (TenantOrAppNotFoundException e) {
            // pass
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
                                            new ThirdPartyConfig.UserInfoMapKeyValue("id1", "email1", "email_verified1"),
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
                                            new ThirdPartyConfig.UserInfoMapKeyValue("id1", "email1", "email_verified1"),
                                            new ThirdPartyConfig.UserInfoMapKeyValue("id2", "email2", "email_verified2")
                                    )
                            )
                    }),
                    new PasswordlessConfig(true),
                    new JsonObject()
            ));
            fail();
        } catch (DuplicateThirdPartyIdException e) {
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
                                            new ThirdPartyConfig.UserInfoMapKeyValue("id1", "email1", "email_verified1"),
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
                                            new ThirdPartyConfig.UserInfoMapKeyValue("id1", "email1", "email_verified1"),
                                            new ThirdPartyConfig.UserInfoMapKeyValue("id2", "email2", "email_verified2")
                                    )
                            )
                    }),
                    new PasswordlessConfig(true),
                    new JsonObject()
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
                                            new ThirdPartyConfig.UserInfoMapKeyValue("id1", "email1", "email_verified1"),
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
                                            new ThirdPartyConfig.UserInfoMapKeyValue("id1", "email1", "email_verified1"),
                                            new ThirdPartyConfig.UserInfoMapKeyValue("id2", "email2", "email_verified2")
                                    )
                            )
                    }),
                    new PasswordlessConfig(true),
                    new JsonObject()
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
                                            new ThirdPartyConfig.UserInfoMapKeyValue("id1", "email1", "email_verified1"),
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
                                            new ThirdPartyConfig.UserInfoMapKeyValue("id1", "email1", "email_verified1"),
                                            new ThirdPartyConfig.UserInfoMapKeyValue("id2", "email2", "email_verified2")
                                    )
                            )
                    }),
                    new PasswordlessConfig(true),
                    new JsonObject()
            ));
            fail();
        } catch (DuplicateClientTypeException e) {
            // Pass
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }
}
