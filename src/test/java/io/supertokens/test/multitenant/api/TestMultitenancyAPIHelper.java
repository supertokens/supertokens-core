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
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import io.supertokens.Main;
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
    public static JsonObject createConnectionUriDomain(Main main, TenantIdentifier sourceTenant, String connectionUriDomain, Boolean emailPasswordEnabled,
                                                       Boolean thirdPartyEnabled, Boolean passwordlessEnabled,
                                                       JsonObject coreConfig) throws HttpResponseException, IOException {
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
        requestBody.add("coreConfig", coreConfig);

        JsonObject response = HttpRequestForTesting.sendJsonPUTRequest(main, "",
                HttpRequestForTesting.getMultitenantUrl(sourceTenant, "/recipe/multitenancy/connectionuridomain"),
                requestBody, 1000, 2500, null,
                SemVer.v3_0.get(), "multitenancy");

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
                HttpRequestForTesting.getMultitenantUrl(sourceTenant, "/recipe/multitenancy/connectionuridomain/remove"),
                requestBody, 1000, 2500, null,
                SemVer.v3_0.get(), "multitenancy");

        assertEquals("OK", response.getAsJsonPrimitive("status").getAsString());
        return response;
    }

    public static JsonObject createApp(Main main, TenantIdentifier sourceTenant, String appId, Boolean emailPasswordEnabled,
                                       Boolean thirdPartyEnabled, Boolean passwordlessEnabled,
                                       JsonObject coreConfig) throws HttpResponseException, IOException {
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
        requestBody.add("coreConfig", coreConfig);

        JsonObject response = HttpRequestForTesting.sendJsonPUTRequest(main, "",
                HttpRequestForTesting.getMultitenantUrl(sourceTenant, "/recipe/multitenancy/app"),
                requestBody, 1000, 2500, null,
                SemVer.v3_0.get(), "multitenancy");

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

    public static JsonObject createTenant(Main main, TenantIdentifier sourceTenant, String tenantId, Boolean emailPasswordEnabled,
                                          Boolean thirdPartyEnabled, Boolean passwordlessEnabled,
                                          JsonObject coreConfig) throws HttpResponseException, IOException {
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
        requestBody.add("coreConfig", coreConfig);

        JsonObject response = HttpRequestForTesting.sendJsonPUTRequest(main, "",
                HttpRequestForTesting.getMultitenantUrl(sourceTenant, "/recipe/multitenancy/tenant"),
                requestBody, 1000, 2500, null,
                SemVer.v3_0.get(), "multitenancy");

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

        JsonObject response = HttpRequestForTesting.sendGETRequest(main, "",
                HttpRequestForTesting.getMultitenantUrl(tenantIdentifier, "/recipe/multitenancy/tenant"),
                null, 1000, 1000, null,
                SemVer.v3_0.get(), "multitenancy");

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

    public static JsonObject addOrUpdateThirdPartyProviderConfig(TenantIdentifier tenantIdentifier, ThirdPartyConfig.Provider provider, Main main)
            throws HttpResponseException, IOException {
        return addOrUpdateThirdPartyProviderConfig(tenantIdentifier, provider, false, main);
    }

    public static JsonObject addOrUpdateThirdPartyProviderConfig(TenantIdentifier tenantIdentifier, ThirdPartyConfig.Provider provider, boolean skipValidation, Main main)
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
                HttpRequestForTesting.getMultitenantUrl(tenantIdentifier, "/recipe/multitenancy/config/thirdparty/remove"),
                requestBody, 1000, 2500, null,
                SemVer.v3_0.get(), "multitenancy");

        assertEquals("OK", response.getAsJsonPrimitive("status").getAsString());
        return response;
    }

    public static JsonObject listUsers(TenantIdentifier sourceTenant, String paginationToken, String limit, String includeRecipeIds, Main main)
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
        JsonObject signUpResponse = HttpRequestForTesting.sendJsonPOSTRequest(main, "",
                HttpRequestForTesting.getMultitenantUrl(tenantIdentifier, "/recipe/signup"),
                requestBody, 1000, 1000, null,
                SemVer.v3_0.get(), "emailpassword");
        assertEquals("OK", signUpResponse.getAsJsonPrimitive("status").getAsString());
        return signUpResponse.getAsJsonObject("user");
    }

    public static JsonObject tpSignInUp(TenantIdentifier tenantIdentifier, String thirdPartyId, String thirdPartyUserId, String email, Main main)
            throws HttpResponseException, IOException {
        JsonObject emailObject = new JsonObject();
        emailObject.addProperty("id", email);

        JsonObject signUpRequestBody = new JsonObject();
        signUpRequestBody.addProperty("thirdPartyId", thirdPartyId);
        signUpRequestBody.addProperty("thirdPartyUserId", thirdPartyUserId);
        signUpRequestBody.add("email", emailObject);

        JsonObject response = HttpRequestForTesting.sendJsonPOSTRequest(main, "",
                HttpRequestForTesting.getMultitenantUrl(tenantIdentifier, "/recipe/signinup"), signUpRequestBody,
                1000, 1000, null,
                SemVer.v3_0.get(), "thirdparty");
        assertEquals("OK", response.get("status").getAsString());
        assertEquals(3, response.entrySet().size());

        return response.get("user").getAsJsonObject();
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
        String exampleCode = generateRandomString(6);
        JsonObject createCodeRequestBody = new JsonObject();
        createCodeRequestBody.addProperty("email", email);
        createCodeRequestBody.addProperty("userInputCode", exampleCode);

        JsonObject response = HttpRequestForTesting.sendJsonPOSTRequest(main, "",
                HttpRequestForTesting.getMultitenantUrl(tenantIdentifier, "/recipe/signinup/code"),
                createCodeRequestBody, 1000, 1000, null,
                SemVer.v3_0.get(), "passwordless");

        assertEquals("OK", response.get("status").getAsString());
        assertEquals(8, response.entrySet().size());

        return response;
    }

    private static JsonObject consumeCode(TenantIdentifier tenantIdentifier, String deviceId, String preAuthSessionId,
                                          String userInputCode, Main main)
            throws HttpResponseException, IOException {
        JsonObject consumeCodeRequestBody = new JsonObject();
        consumeCodeRequestBody.addProperty("deviceId", deviceId);
        consumeCodeRequestBody.addProperty("preAuthSessionId", preAuthSessionId);
        consumeCodeRequestBody.addProperty("userInputCode", userInputCode);

        JsonObject response = HttpRequestForTesting.sendJsonPOSTRequest(main, "",
                HttpRequestForTesting.getMultitenantUrl(tenantIdentifier, "/recipe/signinup/code/consume"),
                consumeCodeRequestBody, 1000, 1000, null,
                SemVer.v3_0.get(), "passwordless");
        assertEquals("OK", response.get("status").getAsString());
        return response.get("user").getAsJsonObject();
    }

    public static JsonObject plSignInUpEmail(TenantIdentifier tenantIdentifier, String email, Main main)
            throws HttpResponseException, IOException {
        JsonObject code = createCodeWithEmail(tenantIdentifier, email, main);
        return consumeCode(tenantIdentifier, code.get("deviceId").getAsString(), code.get("preAuthSessionId").getAsString(), code.get("userInputCode").getAsString(), main);
    }

    private static JsonObject createCodeWithNumber(TenantIdentifier tenantIdentifier, String phoneNumber, Main main)
            throws HttpResponseException, IOException {
        JsonObject createCodeRequestBody = new JsonObject();
        createCodeRequestBody.addProperty("phoneNumber", phoneNumber);

        JsonObject response = HttpRequestForTesting.sendJsonPOSTRequest(main, "",
                HttpRequestForTesting.getMultitenantUrl(tenantIdentifier, "/recipe/signinup/code"),
                createCodeRequestBody, 1000, 1000, null,
                SemVer.v3_0.get(), "passwordless");

        assertEquals("OK", response.get("status").getAsString());
        assertEquals(8, response.entrySet().size());

        return response;
    }

    public static JsonObject plSignInUpNumber(TenantIdentifier tenantIdentifier, String phoneNumber, Main main)
            throws HttpResponseException, IOException {
        JsonObject code = createCodeWithNumber(tenantIdentifier, phoneNumber, main);
        return consumeCode(tenantIdentifier, code.get("deviceId").getAsString(), code.get("preAuthSessionId").getAsString(), code.get("userInputCode").getAsString(), main);
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

    public static void createUserIdMapping(TenantIdentifier tenantIdentifier, String supertokensUserId, String externalUserId, Main main)
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
}
