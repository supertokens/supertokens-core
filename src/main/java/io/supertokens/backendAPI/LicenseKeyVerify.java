/*
 *    Copyright (c) 2020, VRAI Labs and/or its affiliates. All rights reserved.
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

package io.supertokens.backendAPI;

import com.google.gson.JsonObject;
import io.supertokens.Main;
import io.supertokens.httpRequest.HttpRequest;
import io.supertokens.httpRequest.HttpRequestBadResponseException;
import io.supertokens.httpRequest.HttpResponseException;
import io.supertokens.utils.Constants;

import java.io.IOException;

public class LicenseKeyVerify {

    public static final String REQUEST_ID = "io.supertokens.backendAPI.LinceseKeyVerify";

    // we accept these as params and not take them from LicenseKey class directly
    // because
    // this function will be called before LicenseKey class is ready.
    public static boolean verify(Main main, String licenseKeyId, String appId)
            throws IOException, HttpResponseException, HttpRequestBadResponseException {
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("licenseKeyId", licenseKeyId);
        JsonObject response = HttpRequest.sendJsonPOSTRequest(main, REQUEST_ID,
                Constants.SERVER_URL + "/app/" + appId + "/license-key/verify", requestBody, 2000, 10000, 0);
        if (!response.has("verified")) {
            throw new HttpRequestBadResponseException("'verified' key missing from response JSON");
        }
        return response.get("verified").getAsBoolean();
    }
}
