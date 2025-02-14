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

package io.supertokens.test.webauthn;

import com.google.gson.JsonObject;
import io.supertokens.ProcessState;
import io.supertokens.emailpassword.EmailPassword;
import io.supertokens.featureflag.EE_FEATURES;
import io.supertokens.featureflag.FeatureFlagTestContent;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.pluginInterface.authRecipe.AuthRecipeUserInfo;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifier;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.test.TestingProcessManager;
import io.supertokens.test.Utils;
import io.supertokens.test.httpRequest.HttpRequestForTesting;
import io.supertokens.utils.SemVer;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.Assert.assertNotNull;

public class WebAuthNFlowTest {

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
    public void optionsRegisterAPITest() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args, false);
        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{
                        EE_FEATURES.ACCOUNT_LINKING, EE_FEATURES.MULTI_TENANCY});
        process.startProcess();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }
        TenantIdentifier tenantIdentifier = new TenantIdentifier(null, null, null);
        AuthRecipeUserInfo user = EmailPassword.signUp(process.getProcess(), "test@test.com", "password");
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("email","test@test.com");
        requestBody.addProperty("relyingPartyName","supertokens.com");
        requestBody.addProperty("relyingPartyId","supertokens.com");
        requestBody.addProperty("origin","supertokens.com");
        requestBody.addProperty("timeout",10000);

        System.out.println(requestBody);

        JsonObject response = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/webauthn/options/register",
                requestBody, 10000, 10000, null, SemVer.v5_2.get(), null);

        System.out.println(response.toString());

        String generatedOptionsId =  response.get("webauthnGeneratedOptionsId").getAsString();

        String credentialId = Base64.getUrlEncoder().encodeToString(io.supertokens.utils.Utils.getUUID().getBytes(
                StandardCharsets.UTF_8));
        System.out.println("CredentialId = " + credentialId);

        String clientDataJson = "{\"challenge\": \"" + response.get("challenge").getAsString() + "\""
                + ", \"crossorigin\": " + false
                + ", \"origin\": \"supertokens.com\""
                + ", \"type\": \"webauthn.create\""
                +"}";

        JsonObject credentialResponse = new JsonObject();
        credentialResponse.addProperty("type", "public-key");
        credentialResponse.addProperty("id", credentialId);
        credentialResponse.addProperty("rawId", credentialId);

        System.out.println("ATTESTATION:: ");
        System.out.println(Base64.getUrlDecoder().decode("o2NmbXRmcGFja2VkZ2F0dFN0bXSiY2FsZyZjc2lnWEgwRgIhAIxQByta1TIB0_gEG2k4ZUhZYWWXB7ItGz00sQsptELeAiEAwPobNP4IEXVTIdKps2OXznLR6W2-hSaBiJzDN_VkILdoYXV0aERhdGFYpKYbCoe63o889l_A5A0hNIQWx6EMYXbUdzEk7oCI-vkjRQAAAAAKsdMYwBjG-66Y9zSQW1qFACChmhVbEJe5T4JcIpEZueCTOcOwoHbzLb14LCuyWFtbsqUBAgMmIAEhWCCKY54Qll0PzBOgWIyt0Z6Q7A6Kir9JOrJXV6bY1D2KByJYIAi0OaNwchWW-qeBKdUvokiUkNn0-pWfe1v-cf7x5tvw"));

        JsonObject responseObj = new JsonObject();
        responseObj.addProperty("attestationObject", "o2NmbXRmcGFja2VkZ2F0dFN0bXSiY2FsZyZjc2lnWEgwRgIhAIxQByta1TIB0_gEG2k4ZUhZYWWXB7ItGz00sQsptELeAiEAwPobNP4IEXVTIdKps2OXznLR6W2-hSaBiJzDN_VkILdoYXV0aERhdGFYpKYbCoe63o889l_A5A0hNIQWx6EMYXbUdzEk7oCI-vkjRQAAAAAKsdMYwBjG-66Y9zSQW1qFACChmhVbEJe5T4JcIpEZueCTOcOwoHbzLb14LCuyWFtbsqUBAgMmIAEhWCCKY54Qll0PzBOgWIyt0Z6Q7A6Kir9JOrJXV6bY1D2KByJYIAi0OaNwchWW-qeBKdUvokiUkNn0-pWfe1v-cf7x5tvw");
        //responseObj.addProperty("clientDataJSON", "eyJ0eXBlIjoid2ViYXV0aG4uY3JlYXRlIiwiY2hhbGxlbmdlIjoicjVIWWN2WFZsdzMtY0xfd0dzXzNSZzlrM29FbmgwbEJkcW5Ob2NnXzVEayIsIm9yaWdpbiI6Imh0dHBzOi8vc3VwZXJ0b2tlbnMuaW8ifQ");
        responseObj.addProperty("clientDataJSON", Base64.getUrlEncoder().withoutPadding().encodeToString(clientDataJson.getBytes(StandardCharsets.UTF_8)));
        credentialResponse.add("response", responseObj);

        JsonObject signUpRequestBody = new JsonObject();
        signUpRequestBody.addProperty("webauthnGeneratedOptionsId", generatedOptionsId);
        signUpRequestBody.add("credential", credentialResponse);

        System.out.println();
        System.out.println(signUpRequestBody);

        //AuthRecipeUserInfo userInfo = WebAuthN.saveUser(StorageLayer.getStorage(process.getProcess()), tenantIdentifier, "testing@email.com", credentialId, "test.com");

        JsonObject signupResponse = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/webauthn/signup",
                signUpRequestBody, 10000, 1000, null, SemVer.v5_2.get(), null);


        System.out.println("========= SIGNUP RESPONSE =========");
        System.out.println(signupResponse.toString());
    }

    @Test
    public void signUpFlow() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args, false);
        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{
                        EE_FEATURES.ACCOUNT_LINKING, EE_FEATURES.MULTI_TENANCY});
        process.startProcess();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

    }
}
