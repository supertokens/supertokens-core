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

import com.google.gson.JsonObject;
import io.supertokens.Main;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifier;
import io.supertokens.test.Utils;
import io.supertokens.test.httpRequest.HttpRequestForTesting;
import io.supertokens.test.httpRequest.HttpResponseException;

import java.io.IOException;

import static org.junit.Assert.assertEquals;

public class TestMultitenancyAPIHelper {
    protected void createConnectionUriDomain(Main main, TenantIdentifier sourceTenant, String connectionUriDomain, boolean emailPasswordEnabled,
                                             boolean thirdPartyEnabled, boolean passwordlessEnabled,
                                             JsonObject coreConfig) throws HttpResponseException, IOException {
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("connectionUriDomain", connectionUriDomain);
        requestBody.addProperty("emailPasswordEnabled", emailPasswordEnabled);
        requestBody.addProperty("thirdPartyEnabled", thirdPartyEnabled);
        requestBody.addProperty("passwordlessEnabled", passwordlessEnabled);
        requestBody.add("coreConfig", coreConfig);

        JsonObject response = HttpRequestForTesting.sendJsonPUTRequest(main, "",
                HttpRequestForTesting.getMultitenantUrl(sourceTenant, "/recipe/multitenancy/connectionuridomain"),
                requestBody, 1000, 1000, null,
                Utils.getCdiVersionStringLatestForTests(), "multitenancy");

        assertEquals("OK", response.getAsJsonPrimitive("status").getAsString());
    }

    protected JsonObject listConnectionUriDomains(TenantIdentifier sourceTenant, Main main)
            throws HttpResponseException, IOException {
        JsonObject response = HttpRequestForTesting.sendGETRequest(main, "",
                HttpRequestForTesting.getMultitenantUrl(sourceTenant, "/recipe/multitenancy/connectionuridomain/list"),
                null, 1000, 1000, null,
                Utils.getCdiVersionStringLatestForTests(), "multitenancy");

        assertEquals("OK", response.getAsJsonPrimitive("status").getAsString());
        return response;
    }

    protected JsonObject deleteConnectionUriDomain(TenantIdentifier sourceTenant, Main main, String connectionUriDomain)
            throws HttpResponseException, IOException {
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("connectionUriDomain", connectionUriDomain);

        JsonObject response = HttpRequestForTesting.sendJsonPOSTRequest(main, "",
                HttpRequestForTesting.getMultitenantUrl(sourceTenant, "/recipe/multitenancy/connectionuridomain/remove"),
                requestBody, 1000, 1000, null,
                Utils.getCdiVersionStringLatestForTests(), "multitenancy");

        assertEquals("OK", response.getAsJsonPrimitive("status").getAsString());
        return response;
    }
}
