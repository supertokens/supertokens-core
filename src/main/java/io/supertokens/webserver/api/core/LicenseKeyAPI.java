/*
 *    Copyright (c) 2022, VRAI Labs and/or its affiliates. All rights reserved.
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

package io.supertokens.webserver.api.core;

import com.google.gson.JsonObject;
import io.supertokens.Main;
import io.supertokens.ee.httpRequest.HttpResponseException;
import io.supertokens.featureflag.FeatureFlag;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.webserver.InputParser;
import io.supertokens.webserver.WebserverAPI;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

public class LicenseKeyAPI extends WebserverAPI {

    public LicenseKeyAPI(Main main) {
        super(main, "");
    }

    @Override
    public String getPath() {
        return "/ee/license";
    }

    @Override
    protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {
        JsonObject input = InputParser.parseJsonObjectOrThrowError(req);
        String licenseKey = InputParser.parseStringOrThrowError(input, "licenseKey", false);
        try {
            boolean success = FeatureFlag.getInstance(main).setLicenseKeyAndSyncFeatures(licenseKey);
            JsonObject result = new JsonObject();
            result.addProperty("success", success);
            super.sendJsonResponse(200, result, resp);
        } catch (StorageQueryException | HttpResponseException e) {
            throw new ServletException(e);
        }
    }
}
