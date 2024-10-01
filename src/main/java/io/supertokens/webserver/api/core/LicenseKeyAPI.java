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
import io.supertokens.featureflag.FeatureFlag;
import io.supertokens.featureflag.exceptions.InvalidLicenseKeyException;
import io.supertokens.featureflag.exceptions.NoLicenseKeyFoundException;
import io.supertokens.httpRequest.HttpResponseException;
import io.supertokens.multitenancy.exception.BadPermissionException;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.multitenancy.AppIdentifier;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
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
        // API is app specific and can be queried only from public tenant
        JsonObject input = InputParser.parseJsonObjectOrThrowError(req);
        String licenseKey = InputParser.parseStringOrThrowError(input, "licenseKey", true);
        try {
            AppIdentifier appIdentifier = this.getAppIdentifier(req);
            this.enforcePublicTenantAndGetPublicTenantStorage(req); // enforce public tenant
            boolean success = false;
            if (licenseKey != null) {
                success = FeatureFlag.getInstance(main, appIdentifier)
                        .setLicenseKeyAndSyncFeatures(licenseKey);
            } else {
                success = FeatureFlag.getInstance(main, appIdentifier)
                        .syncFeatureFlagWithLicenseKey();
            }
            JsonObject result = new JsonObject();
            result.addProperty("status", success ? "OK" : "MISSING_EE_FOLDER_ERROR");
            super.sendJsonResponse(200, result, resp);
        } catch (StorageQueryException | HttpResponseException | TenantOrAppNotFoundException |
                 BadPermissionException e) {
            throw new ServletException(e);
        } catch (InvalidLicenseKeyException e) {
            JsonObject result = new JsonObject();
            result.addProperty("status", "INVALID_LICENSE_KEY_ERROR");
            super.sendJsonResponse(200, result, resp);
        }
    }

    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {
        // API is app specific and can be queried only from public tenant
        try {
            this.enforcePublicTenantAndGetPublicTenantStorage(req); // enforce public tenant
            FeatureFlag.getInstance(main, getAppIdentifier(req))
                    .removeLicenseKeyAndSyncFeatures();
            JsonObject result = new JsonObject();
            result.addProperty("status", "OK");
            super.sendJsonResponse(200, result, resp);
        } catch (StorageQueryException | HttpResponseException | TenantOrAppNotFoundException |
                 BadPermissionException e) {
            throw new ServletException(e);
        }
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {
        // API is app specific and can be queried only from public tenant
        try {
            this.enforcePublicTenantAndGetPublicTenantStorage(req); // enforce public tenant
            String licenseKey = FeatureFlag.getInstance(main, getAppIdentifier(req))
                    .getLicenseKey();
            JsonObject result = new JsonObject();
            result.addProperty("licenseKey", licenseKey);
            result.addProperty("status", "OK");
            super.sendJsonResponse(200, result, resp);
        } catch (StorageQueryException | BadPermissionException | TenantOrAppNotFoundException e) {
            throw new ServletException(e);
        } catch (NoLicenseKeyFoundException e) {
            JsonObject result = new JsonObject();
            result.addProperty("status", "NO_LICENSE_KEY_FOUND_ERROR");
            super.sendJsonResponse(200, result, resp);
        }
    }
}
