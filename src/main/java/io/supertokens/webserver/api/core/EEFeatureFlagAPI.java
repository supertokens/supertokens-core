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

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import io.supertokens.Main;
import io.supertokens.featureflag.EE_FEATURES;
import io.supertokens.featureflag.FeatureFlag;
import io.supertokens.multitenancy.exception.BadPermissionException;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifier;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.webserver.WebserverAPI;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.Arrays;

public class EEFeatureFlagAPI extends WebserverAPI {

    public EEFeatureFlagAPI(Main main) {
        super(main, "");
    }

    @Override
    public String getPath() {
        return "/ee/featureflag";
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {
        // API is app specific and can be queried only from public tenant
        try {
            EE_FEATURES[] features = FeatureFlag.getInstance(main, this.getAppIdentifierWithStorageFromRequestAndEnforcePublicTenant(req))
                    .getEnabledFeatures();
            JsonObject stats = FeatureFlag.getInstance(main, this.getAppIdentifierWithStorageFromRequestAndEnforcePublicTenant(req))
                    .getPaidFeatureStats();
            JsonObject result = new JsonObject();
            JsonArray featuresJson = new JsonArray();
            Arrays.stream(features).forEach(ee_features -> {
                featuresJson.add(new JsonPrimitive(ee_features.toString()));
            });
            result.add("features", featuresJson);
            result.add("usageStats", stats);
            result.addProperty("status", "OK");
            super.sendJsonResponse(200, result, resp);
        } catch (StorageQueryException | BadPermissionException | TenantOrAppNotFoundException e) {
            throw new ServletException(e);
        }
    }
}
