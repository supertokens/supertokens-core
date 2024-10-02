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

package io.supertokens.test.multitenant.api;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import io.supertokens.Main;
import io.supertokens.pluginInterface.RECIPE_ID;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifier;
import io.supertokens.pluginInterface.multitenancy.ThirdPartyConfig;
import io.supertokens.test.httpRequest.HttpRequestForTesting;
import io.supertokens.test.httpRequest.HttpResponseException;
import io.supertokens.utils.SemVer;
import io.supertokens.webserver.WebserverAPI;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import static org.junit.Assert.assertEquals;

public class TestMultitenancyAPIHelper {
    public static JsonObject createConnectionUriDomain(Main main, TenantIdentifier sourceTenant,
                                                       String connectionUriDomain, Boolean emailPasswordEnabled,
                                                       Boolean thirdPartyEnabled, Boolean passwordlessEnabled,
                                                       JsonObject coreConfig)
            throws HttpResponseException, IOException {
        return createConnectionUriDomain(main, sourceTenant, connectionUriDomain, emailPasswordEnabled,
                thirdPartyEnabled,
                passwordlessEnabled, false, null, false, null, coreConfig, SemVer.v3_0);

    }

    public static JsonObject createConnectionUriDomain(Main main, TenantIdentifier sourceTenant,
                                                       String connectionUriDomain, Boolean emailPasswordEnabled,
                                                       Boolean thirdPartyEnabled, Boolean passwordlessEnabled,
                                                       boolean setFirstFactors, String[] firstFactors,
                                                       boolean setRequiredSecondaryFactors,
                                                       String[] requiredSecondaryFactors,
                                                       JsonObject coreConfig, SemVer version)
            throws HttpResponseException, IOException {
        JsonObject requestBody = new JsonObject();
        if (connectionUriDomain != null) {
            requestBody.addProperty("connectionUriDomain", connectionUriDomain);
        }
        if (emailPasswordEnabled != null) {
            requestBody.addProperty("emailPasswordEnabled", emailPasswordEnabled);
        }
        if (thirdPartyEnabled != null) {
            requestBody.addProperty("thirdPartyEnabled", thirdPartyEnabled);
        }
        if (passwordlessEnabled != null) {
            requestBody.addProperty("passwordlessEnabled", passwordlessEnabled);
        }
        if (setFirstFactors || firstFactors != null) {
            requestBody.add("firstFactors", new Gson().toJsonTree(firstFactors));
        }
        if (setRequiredSecondaryFactors || requiredSecondaryFactors != null) {
            requestBody.add("requiredSecondaryFactors", new Gson().toJsonTree(requiredSecondaryFactors));
        }

        requestBody.add("coreConfig", coreConfig);

        JsonObject response = HttpRequestForTesting.sendJsonPUTRequest(main, "",
                HttpRequestForTesting.getMultitenantUrl(sourceTenant, "/recipe/multitenancy/connectionuridomain"),
                requestBody, 1000, 2500, null,
                version.get(), "multitenancy");

        assertEquals("OK", response.getAsJsonPrimitive("status").getAsString());

        return response;
    }

    public static JsonObject listConnectionUriDomains(TenantIdentifier sourceTenant, Main main)
            throws HttpResponseException, IOException {
        JsonObject response = HttpRequestForTesting.sendGETRequest(main, "",
                HttpRequestForTesting.getMultitenantUrl(sourceTenant, "/recipe/multitenancy/connectionuridomain/list"),
                null, 1000, 1000, null,
                SemVer.v3_0.get(), "multitenancy");

        assertEquals("OK", response.getAsJsonPrimitive("status").getAsString());
        return response;
    }

    public static JsonObject deleteConnectionUriDomain(TenantIdentifier sourceTenant, String connectionUriDomain,
                                                       Main main)
            throws HttpResponseException, IOException {
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("connectionUriDomain", connectionUriDomain);

        JsonObject response = HttpRequestForTesting.sendJsonPOSTRequest(main, "",
                HttpRequestForTesting.getMultitenantUrl(sourceTenant,
                        "/recipe/multitenancy/connectionuridomain/remove"),
                requestBody, 1000, 2500, null,
                SemVer.v3_0.get(), "multitenancy");

        assertEquals("OK", response.getAsJsonPrimitive("status").getAsString());
        return response;
    }

    public static JsonObject createApp(Main main, TenantIdentifier sourceTenant, String appId,
                                       Boolean emailPasswordEnabled,
                                       Boolean thirdPartyEnabled, Boolean passwordlessEnabled,
                                       JsonObject coreConfig) throws HttpResponseException, IOException {
        return createApp(main, sourceTenant, appId, emailPasswordEnabled, thirdPartyEnabled, passwordlessEnabled,
                false, null, false, null, coreConfig, SemVer.v3_0);
    }

    public static JsonObject createApp(Main main, TenantIdentifier sourceTenant, String appId,
                                       Boolean emailPasswordEnabled,
                                       Boolean thirdPartyEnabled, Boolean passwordlessEnabled,
                                       boolean setFirstFactors, String[] firstFactors,
                                       boolean setRequiredSecondaryFactors, String[] requiredSecondaryFactors,
                                       JsonObject coreConfig, SemVer version)
            throws HttpResponseException, IOException {
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("appId", appId);
        if (emailPasswordEnabled != null) {
            requestBody.addProperty("emailPasswordEnabled", emailPasswordEnabled);
        }
        if (thirdPartyEnabled != null) {
            requestBody.addProperty("thirdPartyEnabled", thirdPartyEnabled);
        }
        if (passwordlessEnabled != null) {
            requestBody.addProperty("passwordlessEnabled", passwordlessEnabled);
        }
        if (setFirstFactors || firstFactors != null) {
            requestBody.add("firstFactors", new Gson().toJsonTree(firstFactors));
        }
        if (setRequiredSecondaryFactors || requiredSecondaryFactors != null) {
            requestBody.add("requiredSecondaryFactors", new Gson().toJsonTree(requiredSecondaryFactors));
        }
        requestBody.add("coreConfig", coreConfig);

        JsonObject response = HttpRequestForTesting.sendJsonPUTRequest(main, "",
                HttpRequestForTesting.getMultitenantUrl(sourceTenant, "/recipe/multitenancy/app"),
                requestBody, 1000, 2500, null,
                version.get(), "multitenancy");

        assertEquals("OK", response.getAsJsonPrimitive("status").getAsString());
        return response;
    }

    public static JsonObject listApps(TenantIdentifier sourceTenant, Main main)
            throws HttpResponseException, IOException {
        JsonObject response = HttpRequestForTesting.sendGETRequest(main, "",
                HttpRequestForTesting.getMultitenantUrl(sourceTenant, "/recipe/multitenancy/app/list"),
                null, 1000, 1000, null,
                SemVer.v3_0.get(), "multitenancy");

        assertEquals("OK", response.getAsJsonPrimitive("status").getAsString());
        return response;
    }

    public static JsonObject deleteApp(TenantIdentifier sourceTenant, String appId, Main main)
            throws HttpResponseException, IOException {
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("appId", appId);

        JsonObject response = HttpRequestForTesting.sendJsonPOSTRequest(main, "",
                HttpRequestForTesting.getMultitenantUrl(sourceTenant, "/recipe/multitenancy/app/remove"),
                requestBody, 1000, 2500, null,
                SemVer.v3_0.get(), "multitenancy");

        assertEquals("OK", response.getAsJsonPrimitive("status").getAsString());
        return response;
    }

    public static JsonObject createTenant(Main main, TenantIdentifier sourceTenant, String tenantId,
                                          Boolean emailPasswordEnabled,
                                          Boolean thirdPartyEnabled, Boolean passwordlessEnabled,
                                          JsonObject coreConfig) throws HttpResponseException, IOException {
        return createTenant(main, sourceTenant, tenantId, emailPasswordEnabled, thirdPartyEnabled, passwordlessEnabled,
                false, null, false, null, coreConfig, SemVer.v3_0);
    }

    public static JsonObject createTenant(Main main, TenantIdentifier sourceTenant, String tenantId,
                                          Boolean emailPasswordEnabled,
                                          Boolean thirdPartyEnabled, Boolean passwordlessEnabled,
                                          boolean setFirstFactors, String[] firstFactors,
                                          boolean setRequiredSecondaryFactors, String[] requiredSecondaryFactors,
                                          JsonObject coreConfig, SemVer version)
            throws HttpResponseException, IOException {
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("tenantId", tenantId);
        if (emailPasswordEnabled != null) {
            requestBody.addProperty("emailPasswordEnabled", emailPasswordEnabled);
        }
        if (thirdPartyEnabled != null) {
            requestBody.addProperty("thirdPartyEnabled", thirdPartyEnabled);
        }
        if (passwordlessEnabled != null) {
            requestBody.addProperty("passwordlessEnabled", passwordlessEnabled);
        }
        if (setFirstFactors || firstFactors != null) {
            requestBody.add("firstFactors", new Gson().toJsonTree(firstFactors));
        }
        if (setRequiredSecondaryFactors || requiredSecondaryFactors != null) {
            requestBody.add("requiredSecondaryFactors", new Gson().toJsonTree(requiredSecondaryFactors));
        }

        requestBody.add("coreConfig", coreConfig);

        JsonObject response = HttpRequestForTesting.sendJsonPUTRequest(main, "",
                HttpRequestForTesting.getMultitenantUrl(sourceTenant, "/recipe/multitenancy/tenant"),
                requestBody, 1000, 2500, null,
                version.get(), "multitenancy");

        assertEquals("OK", response.getAsJsonPrimitive("status").getAsString());
        return response;
    }

    public static JsonObject listTenants(TenantIdentifier sourceTenant, Main main)
            throws HttpResponseException, IOException {
        JsonObject response = HttpRequestForTesting.sendGETRequest(main, "",
                HttpRequestForTesting.getMultitenantUrl(sourceTenant, "/recipe/multitenancy/tenant/list"),
                null, 1000, 1000, null,
                SemVer.v3_0.get(), "multitenancy");

        assertEquals("OK", response.getAsJsonPrimitive("status").getAsString());
        return response;
    }

    public static JsonObject deleteTenant(TenantIdentifier sourceTenant, String tenantId, Main main)
            throws HttpResponseException, IOException {
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("tenantId", tenantId);

        JsonObject response = HttpRequestForTesting.sendJsonPOSTRequest(main, "",
                HttpRequestForTesting.getMultitenantUrl(sourceTenant, "/recipe/multitenancy/tenant/remove"),
                requestBody, 1000, 2500, null,
                SemVer.v3_0.get(), "multitenancy");

        assertEquals("OK", response.getAsJsonPrimitive("status").getAsString());
        return response;
    }

    public static JsonObject getTenant(TenantIdentifier tenantIdentifier, Main main)
            throws HttpResponseException, IOException {
        return getTenant(tenantIdentifier, main, SemVer.v3_0);
    }

    public static JsonObject getTenant(TenantIdentifier tenantIdentifier, Main main, SemVer version)
            throws HttpResponseException, IOException {

        JsonObject response = HttpRequestForTesting.sendGETRequest(main, "",
                HttpRequestForTesting.getMultitenantUrl(tenantIdentifier, "/recipe/multitenancy/tenant"),
                null, 1000, 1000, null,
                version.get(), "multitenancy");

        assertEquals("OK", response.getAsJsonPrimitive("status").getAsString());
        return response;
    }

    public static JsonObject associateUserToTenant(TenantIdentifier tenantIdentifier, String userId, Main main)
            throws HttpResponseException, IOException {
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("recipeUserId", userId);

        JsonObject response = HttpRequestForTesting.sendJsonPOSTRequest(main, "",
                HttpRequestForTesting.getMultitenantUrl(tenantIdentifier, "/recipe/multitenancy/tenant/user"),
                requestBody, 1000, 1000, null,
                WebserverAPI.getLatestCDIVersion().get(), "multitenancy");

        return response;
    }

    public static JsonObject disassociateUserFromTenant(TenantIdentifier tenantIdentifier, String userId, Main main)
            throws HttpResponseException, IOException {
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("recipeUserId", userId);

        JsonObject response = HttpRequestForTesting.sendJsonPOSTRequest(main, "",
                HttpRequestForTesting.getMultitenantUrl(tenantIdentifier, "/recipe/multitenancy/tenant/user/remove"),
                requestBody, 1000, 1000, null,
                WebserverAPI.getLatestCDIVersion().get(), "multitenancy");

        assertEquals("OK", response.getAsJsonPrimitive("status").getAsString());
        return response;
    }

    public static JsonObject addOrUpdateThirdPartyProviderConfig(TenantIdentifier tenantIdentifier,
                                                                 ThirdPartyConfig.Provider provider, Main main)
            throws HttpResponseException, IOException {
        return addOrUpdateThirdPartyProviderConfig(tenantIdentifier, provider, false, main);
    }

    public static JsonObject addOrUpdateThirdPartyProviderConfig(TenantIdentifier tenantIdentifier,
                                                                 ThirdPartyConfig.Provider provider,
                                                                 boolean skipValidation, Main main)
            throws HttpResponseException, IOException {
        JsonObject requestBody = new JsonObject();
        JsonObject tpConfig = provider.toJson();
        requestBody.add("config", tpConfig);
        requestBody.addProperty("skipValidation", skipValidation);

        JsonObject response = HttpRequestForTesting.sendJsonPUTRequest(main, "",
                HttpRequestForTesting.getMultitenantUrl(tenantIdentifier, "/recipe/multitenancy/config/thirdparty"),
                requestBody, 1000, 2500, null,
                SemVer.v3_0.get(), "multitenancy");

        assertEquals("OK", response.getAsJsonPrimitive("status").getAsString());
        return response;
    }

    public static JsonObject deleteThirdPartyProvider(TenantIdentifier tenantIdentifier, String thirdPartyId, Main main)
            throws HttpResponseException, IOException {
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("thirdPartyId", thirdPartyId);

        JsonObject response = HttpRequestForTesting.sendJsonPOSTRequest(main, "",
                HttpRequestForTesting.getMultitenantUrl(tenantIdentifier,
                        "/recipe/multitenancy/config/thirdparty/remove"),
                requestBody, 1000, 2500, null,
                SemVer.v3_0.get(), "multitenancy");

        assertEquals("OK", response.getAsJsonPrimitive("status").getAsString());
        return response;
    }

    public static JsonObject listUsers(TenantIdentifier sourceTenant, String paginationToken, String limit,
                                       String includeRecipeIds, Main main)
            throws HttpResponseException, IOException {
        Map<String, String> params = new HashMap<>();
        if (paginationToken != null) {
            params.put("paginationToken", paginationToken);
        }
        if (limit != null) {
            params.put("limit", limit);
        }
        if (includeRecipeIds != null) {
            params.put("includeRecipeIds", includeRecipeIds);
        }
        JsonObject response = HttpRequestForTesting.sendGETRequest(main, "",
                HttpRequestForTesting.getMultitenantUrl(sourceTenant, "/users"),
                params, 1000, 1000, null,
                SemVer.v3_0.get(), null);

        assertEquals("OK", response.getAsJsonPrimitive("status").getAsString());
        return response;
    }

    public static JsonObject epSignUp(TenantIdentifier tenantIdentifier, String email, String password, Main main)
            throws HttpResponseException, IOException {
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("email", email);
        requestBody.addProperty("password", password);
        JsonObject signUpResponse = epSignUpAndGetResponse(tenantIdentifier, email, password, main, SemVer.v3_0);
        assertEquals("OK", signUpResponse.getAsJsonPrimitive("status").getAsString());
        return signUpResponse.getAsJsonObject("user");
    }

    public static JsonObject epSignUpAndGetResponse(TenantIdentifier tenantIdentifier, String email, String password,
                                                    Main main, SemVer version)
            throws HttpResponseException, IOException {
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("email", email);
        requestBody.addProperty("password", password);
        JsonObject signUpResponse = HttpRequestForTesting.sendJsonPOSTRequest(main, "",
                HttpRequestForTesting.getMultitenantUrl(tenantIdentifier, "/recipe/signup"),
                requestBody, 1000, 1000, null,
                version.get(), "emailpassword");
        return signUpResponse;
    }

    public static JsonObject epSignInAndGetResponse(TenantIdentifier tenantIdentifier, String email, String password,
                                                    Main main, SemVer version)
            throws HttpResponseException, IOException {
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("email", email);
        requestBody.addProperty("password", password);
        JsonObject signUpResponse = HttpRequestForTesting.sendJsonPOSTRequest(main, "",
                HttpRequestForTesting.getMultitenantUrl(tenantIdentifier, "/recipe/signin"),
                requestBody, 1000, 1000, null,
                version.get(), "emailpassword");
        return signUpResponse;
    }

    public static JsonObject tpSignInUp(TenantIdentifier tenantIdentifier, String thirdPartyId, String thirdPartyUserId,
                                        String email, Main main)
            throws HttpResponseException, IOException {
        JsonObject emailObject = new JsonObject();
        emailObject.addProperty("id", email);

        JsonObject signUpRequestBody = new JsonObject();
        signUpRequestBody.addProperty("thirdPartyId", thirdPartyId);
        signUpRequestBody.addProperty("thirdPartyUserId", thirdPartyUserId);
        signUpRequestBody.add("email", emailObject);

        JsonObject response = tpSignInUpAndGetResponse(tenantIdentifier, thirdPartyId, thirdPartyUserId, email, main,
                SemVer.v3_0);
        assertEquals("OK", response.get("status").getAsString());
        assertEquals(3, response.entrySet().size());

        return response.get("user").getAsJsonObject();
    }

    public static JsonObject tpSignInUpAndGetResponse(TenantIdentifier tenantIdentifier, String thirdPartyId,
                                                      String thirdPartyUserId, String email, Main main, SemVer version)
            throws HttpResponseException, IOException {
        JsonObject emailObject = new JsonObject();
        emailObject.addProperty("id", email);
        emailObject.addProperty("isVerified", false);

        JsonObject signUpRequestBody = new JsonObject();
        signUpRequestBody.addProperty("thirdPartyId", thirdPartyId);
        signUpRequestBody.addProperty("thirdPartyUserId", thirdPartyUserId);
        signUpRequestBody.add("email", emailObject);

        JsonObject response = HttpRequestForTesting.sendJsonPOSTRequest(main, "",
                HttpRequestForTesting.getMultitenantUrl(tenantIdentifier, "/recipe/signinup"), signUpRequestBody,
                1000, 1000, null,
                version.get(), "thirdparty");
        return response;
    }

    private static String generateRandomString(int length) {
        StringBuilder sb = new StringBuilder(length);
        final String ALPHABET = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
        final Random RANDOM = new Random();
        for (int i = 0; i < length; i++) {
            int randomIndex = RANDOM.nextInt(ALPHABET.length());
            char randomChar = ALPHABET.charAt(randomIndex);
            sb.append(randomChar);
        }
        return sb.toString();
    }

    private static JsonObject createCodeWithEmail(TenantIdentifier tenantIdentifier, String email, Main main)
            throws HttpResponseException, IOException {
        return createCodeWithEmail(tenantIdentifier, email, main, SemVer.v3_0);
    }

    private static JsonObject createCodeWithEmail(TenantIdentifier tenantIdentifier, String email, Main main,
                                                  SemVer version)
            throws HttpResponseException, IOException {
        String exampleCode = generateRandomString(6);
        JsonObject createCodeRequestBody = new JsonObject();
        createCodeRequestBody.addProperty("email", email);
        createCodeRequestBody.addProperty("userInputCode", exampleCode);

        JsonObject response = HttpRequestForTesting.sendJsonPOSTRequest(main, "",
                HttpRequestForTesting.getMultitenantUrl(tenantIdentifier, "/recipe/signinup/code"),
                createCodeRequestBody, 1000, 1000, null,
                version.get(), "passwordless");

        assertEquals("OK", response.get("status").getAsString());
        assertEquals(8, response.entrySet().size());

        return response;
    }

    private static JsonObject consumeCode(TenantIdentifier tenantIdentifier, String deviceId, String preAuthSessionId,
                                          String userInputCode, Main main) throws HttpResponseException, IOException {
        return consumeCode(tenantIdentifier, deviceId, preAuthSessionId, userInputCode, main, SemVer.v3_0);
    }

    private static JsonObject consumeCode(TenantIdentifier tenantIdentifier, String deviceId, String preAuthSessionId,
                                          String userInputCode, Main main, SemVer version)
            throws HttpResponseException, IOException {
        JsonObject consumeCodeRequestBody = new JsonObject();
        consumeCodeRequestBody.addProperty("deviceId", deviceId);
        consumeCodeRequestBody.addProperty("preAuthSessionId", preAuthSessionId);
        consumeCodeRequestBody.addProperty("userInputCode", userInputCode);

        JsonObject response = consumeCodeAndGetResponse(tenantIdentifier, deviceId, preAuthSessionId, userInputCode,
                main, version);
        assertEquals("OK", response.get("status").getAsString());
        return response.get("user").getAsJsonObject();
    }

    private static JsonObject consumeCodeAndGetResponse(TenantIdentifier tenantIdentifier, String deviceId,
                                                        String preAuthSessionId,
                                                        String userInputCode, Main main, SemVer version)
            throws HttpResponseException, IOException {
        JsonObject consumeCodeRequestBody = new JsonObject();
        consumeCodeRequestBody.addProperty("deviceId", deviceId);
        consumeCodeRequestBody.addProperty("preAuthSessionId", preAuthSessionId);
        consumeCodeRequestBody.addProperty("userInputCode", userInputCode);

        return HttpRequestForTesting.sendJsonPOSTRequest(main, "",
                HttpRequestForTesting.getMultitenantUrl(tenantIdentifier, "/recipe/signinup/code/consume"),
                consumeCodeRequestBody, 1000, 1000, null,
                version.get(), "passwordless");
    }

    private static JsonObject consumeCodeAndGetResponse(TenantIdentifier tenantIdentifier, String preAuthSessionId,
                                                        String linkCode, Main main, SemVer version)
            throws HttpResponseException, IOException {
        JsonObject consumeCodeRequestBody = new JsonObject();
        consumeCodeRequestBody.addProperty("preAuthSessionId", preAuthSessionId);
        consumeCodeRequestBody.addProperty("linkCode", linkCode);

        return HttpRequestForTesting.sendJsonPOSTRequest(main, "",
                HttpRequestForTesting.getMultitenantUrl(tenantIdentifier, "/recipe/signinup/code/consume"),
                consumeCodeRequestBody, 1000, 1000, null,
                version.get(), "passwordless");
    }

    public static JsonObject plSignInUpEmail(TenantIdentifier tenantIdentifier, String email, Main main)
            throws HttpResponseException, IOException {
        JsonObject code = createCodeWithEmail(tenantIdentifier, email, main);
        return consumeCode(tenantIdentifier, code.get("deviceId").getAsString(),
                code.get("preAuthSessionId").getAsString(), code.get("userInputCode").getAsString(), main);
    }

    public static JsonObject plSignInUpWithEmailOTP(TenantIdentifier tenantIdentifier, String email, Main main,
                                                    SemVer version)
            throws HttpResponseException, IOException {
        JsonObject code = createCodeWithEmail(tenantIdentifier, email, main, version);
        return consumeCodeAndGetResponse(tenantIdentifier, code.get("deviceId").getAsString(),
                code.get("preAuthSessionId").getAsString(), code.get("userInputCode").getAsString(), main, version);
    }

    public static JsonObject plSignInUpWithEmailLink(TenantIdentifier tenantIdentifier, String email, Main main,
                                                     SemVer version)
            throws HttpResponseException, IOException {
        JsonObject code = createCodeWithEmail(tenantIdentifier, email, main, version);
        return consumeCodeAndGetResponse(tenantIdentifier, code.get("preAuthSessionId").getAsString(),
                code.get("linkCode").getAsString(), main, version);
    }

    private static JsonObject createCodeWithNumber(TenantIdentifier tenantIdentifier, String phoneNumber, Main main)
            throws HttpResponseException, IOException {
        return createCodeWithNumber(tenantIdentifier, phoneNumber, main, SemVer.v3_0);
    }

    private static JsonObject createCodeWithNumber(TenantIdentifier tenantIdentifier, String phoneNumber, Main main,
                                                   SemVer version)
            throws HttpResponseException, IOException {
        JsonObject createCodeRequestBody = new JsonObject();
        createCodeRequestBody.addProperty("phoneNumber", phoneNumber);

        JsonObject response = HttpRequestForTesting.sendJsonPOSTRequest(main, "",
                HttpRequestForTesting.getMultitenantUrl(tenantIdentifier, "/recipe/signinup/code"),
                createCodeRequestBody, 1000, 1000, null,
                version.get(), "passwordless");

        assertEquals("OK", response.get("status").getAsString());
        assertEquals(8, response.entrySet().size());

        return response;
    }

    public static JsonObject plSignInUpNumber(TenantIdentifier tenantIdentifier, String phoneNumber, Main main)
            throws HttpResponseException, IOException {
        JsonObject code = createCodeWithNumber(tenantIdentifier, phoneNumber, main);
        return consumeCode(tenantIdentifier, code.get("deviceId").getAsString(),
                code.get("preAuthSessionId").getAsString(), code.get("userInputCode").getAsString(), main);
    }

    public static JsonObject plSignInUpWithPhoneOTP(TenantIdentifier tenantIdentifier, String phoneNumber, Main main,
                                                    SemVer version)
            throws HttpResponseException, IOException {
        JsonObject code = createCodeWithNumber(tenantIdentifier, phoneNumber, main, version);
        return consumeCodeAndGetResponse(tenantIdentifier, code.get("deviceId").getAsString(),
                code.get("preAuthSessionId").getAsString(), code.get("userInputCode").getAsString(), main, version);
    }

    public static JsonObject plSignInUpWithPhoneLink(TenantIdentifier tenantIdentifier, String phoneNumber, Main main,
                                                     SemVer version)
            throws HttpResponseException, IOException {
        JsonObject code = createCodeWithNumber(tenantIdentifier, phoneNumber, main, version);
        return consumeCodeAndGetResponse(tenantIdentifier, code.get("preAuthSessionId").getAsString(),
                code.get("linkCode").getAsString(), main, version);
    }

    public static void addLicense(String licenseKey, Main main) throws HttpResponseException, IOException {
        JsonObject licenseKeyRequest = new JsonObject();
        licenseKeyRequest.addProperty("licenseKey", licenseKey);

        JsonObject response = HttpRequestForTesting.sendJsonPUTRequest(main, "",
                "http://localhost:3567/ee/license", licenseKeyRequest,
                2000, 5000, null,
                SemVer.v3_0.get(), null);
        assertEquals("OK", response.get("status").getAsString());
    }

    public static void removeLicense(Main main) throws HttpResponseException, IOException {
        JsonObject response = HttpRequestForTesting.sendJsonDELETERequest(main, "",
                "http://localhost:3567/ee/license", null,
                1000, 1000, null,
                SemVer.v3_0.get(), null);
        assertEquals("OK", response.get("status").getAsString());
    }

    public static JsonObject getEpUserById(TenantIdentifier tenantIdentifier, String userId, Main main)
            throws HttpResponseException, IOException {
        HashMap<String, String> map = new HashMap<>();
        map.put("userId", userId);
        JsonObject userResponse = HttpRequestForTesting.sendGETRequest(main, "",
                HttpRequestForTesting.getMultitenantUrl(tenantIdentifier, "/recipe/user"),
                map, 1000, 1000, null, SemVer.v3_0.get(),
                "emailpassword");
        assertEquals("OK", userResponse.getAsJsonPrimitive("status").getAsString());
        return userResponse.getAsJsonObject("user");
    }

    public static void createUserIdMapping(TenantIdentifier tenantIdentifier, String supertokensUserId,
                                           String externalUserId, Main main)
            throws HttpResponseException, IOException {
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("superTokensUserId", supertokensUserId);
        requestBody.addProperty("externalUserId", externalUserId);

        JsonObject response = HttpRequestForTesting.sendJsonPOSTRequest(main, "",
                HttpRequestForTesting.getMultitenantUrl(tenantIdentifier, "/recipe/userid/map"), requestBody,
                1000, 1000, null,
                SemVer.v3_0.get(), "useridmapping");
        assertEquals("OK", response.get("status").getAsString());
    }

    public static JsonObject getUserById(TenantIdentifier tenantIdentifier, String userId, Main main)
            throws HttpResponseException, IOException {
        Map<String, String> params = new HashMap<>();
        params.put("userId", userId);
        JsonObject response = HttpRequestForTesting.sendGETRequest(main, "",
                HttpRequestForTesting.getMultitenantUrl(tenantIdentifier, "/user/id"),
                params, 1000, 1000, null,
                WebserverAPI.getLatestCDIVersion().get(), "");
        return response;
    }

    public static JsonObject updateUserMetadata(TenantIdentifier tenantIdentifier, String userId, JsonObject metadata,
                                                Main main)
            throws HttpResponseException, IOException {
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("userId", userId);
        requestBody.add("metadataUpdate", metadata);
        JsonObject resp = HttpRequestForTesting.sendJsonPUTRequest(main, "",
                HttpRequestForTesting.getMultitenantUrl(tenantIdentifier, "/recipe/user/metadata"),
                requestBody, 1000, 1000, null,
                WebserverAPI.getLatestCDIVersion().get(), "usermetadata");
        return resp;
    }

    public static JsonObject removeMetadata(TenantIdentifier tenantIdentifier, String userId, Main main)
            throws HttpResponseException, IOException {
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("userId", userId);
        JsonObject resp = HttpRequestForTesting.sendJsonPOSTRequest(main, "",
                HttpRequestForTesting.getMultitenantUrl(tenantIdentifier, "/recipe/user/metadata/remove"),
                requestBody, 1000, 1000, null,
                WebserverAPI.getLatestCDIVersion().get(), "usermetadata");

        return resp;
    }

    public static void createRole(TenantIdentifier tenantIdentifier, String role, Main main)
            throws HttpResponseException, IOException {
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("role", role);
        JsonObject response = HttpRequestForTesting.sendJsonPUTRequest(main, "",
                HttpRequestForTesting.getMultitenantUrl(tenantIdentifier, "/recipe/role"),
                requestBody, 1000, 1000, null, WebserverAPI.getLatestCDIVersion().get(),
                "userroles");
        assertEquals("OK", response.get("status").getAsString());
    }

    public static void addRoleToUser(TenantIdentifier tenantIdentifier, String userId, String role, Main main)
            throws HttpResponseException, IOException {
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("role", role);
        requestBody.addProperty("userId", userId);

        JsonObject response = HttpRequestForTesting.sendJsonPUTRequest(main, "",
                HttpRequestForTesting.getMultitenantUrl(tenantIdentifier, "/recipe/user/role"), requestBody, 1000, 1000,
                null,
                WebserverAPI.getLatestCDIVersion().get(), "userroles");

        assertEquals(2, response.entrySet().size());
        assertEquals("OK", response.get("status").getAsString());
    }

    public static JsonObject getUserRoles(TenantIdentifier tenantIdentifier, String userId, Main main)
            throws HttpResponseException, IOException {
        HashMap<String, String> QUERY_PARAMS = new HashMap<>();
        QUERY_PARAMS.put("userId", userId);
        JsonObject response = HttpRequestForTesting.sendGETRequest(main, "",
                HttpRequestForTesting.getMultitenantUrl(tenantIdentifier, "/recipe/user/roles"), QUERY_PARAMS, 1000,
                1000, null,
                WebserverAPI.getLatestCDIVersion().get(), "userroles");
        return response;
    }

    public static void deleteRole(TenantIdentifier tenantIdentifier, String role, Main main)
            throws HttpResponseException, IOException {
        JsonObject request = new JsonObject();
        request.addProperty("role", role);

        JsonObject response = HttpRequestForTesting.sendJsonPOSTRequest(main, "",
                HttpRequestForTesting.getMultitenantUrl(tenantIdentifier, "/recipe/role/remove"), request, 1000, 1000,
                null,
                WebserverAPI.getLatestCDIVersion().get(), "userroles");
        assertEquals(2, response.entrySet().size());
        assertEquals("OK", response.get("status").getAsString());
    }

    public static void verifyEmail(TenantIdentifier tenantIdentifier, String userId, String email, Main main)
            throws HttpResponseException, IOException {
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("userId", userId);
        requestBody.addProperty("email", email);

        JsonObject response = HttpRequestForTesting.sendJsonPOSTRequest(main, "",
                HttpRequestForTesting.getMultitenantUrl(tenantIdentifier, "/recipe/user/email/verify/token"),
                requestBody, 1000, 1000, null,
                WebserverAPI.getLatestCDIVersion().get(), "emailverification");

        assertEquals(response.entrySet().size(), 2);
        assertEquals(response.get("status").getAsString(), "OK");

        JsonObject verifyResponseBody = new JsonObject();
        verifyResponseBody.addProperty("method", "token");
        verifyResponseBody.addProperty("token", response.get("token").getAsString());

        JsonObject response2 = HttpRequestForTesting.sendJsonPOSTRequest(main, "",
                HttpRequestForTesting.getMultitenantUrl(tenantIdentifier, "/recipe/user/email/verify"),
                verifyResponseBody, 1000, 1000, null,
                WebserverAPI.getLatestCDIVersion().get(), "emailverification");

        assertEquals(response2.entrySet().size(), 3);
        assertEquals(response2.get("status").getAsString(), "OK");
    }

    public static void unverifyEmail(TenantIdentifier tenantIdentifier, String userId, String email, Main main)
            throws HttpResponseException, IOException {
        JsonObject body = new JsonObject();
        body.addProperty("userId", userId);
        body.addProperty("email", email);

        HttpRequestForTesting.sendJsonPOSTRequest(main, "",
                HttpRequestForTesting.getMultitenantUrl(tenantIdentifier, "/recipe/user/email/verify/remove"), body,
                1000, 1000, null,
                WebserverAPI.getLatestCDIVersion().get(), RECIPE_ID.EMAIL_VERIFICATION.toString());
    }
}
