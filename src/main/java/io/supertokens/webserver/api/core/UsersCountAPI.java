/*
 *    Copyright (c) 2021, VRAI Labs and/or its affiliates. All rights reserved.
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
import io.supertokens.authRecipe.ApproximateUserCount;
import io.supertokens.authRecipe.AuthRecipe;
import io.supertokens.multitenancy.exception.BadPermissionException;
import io.supertokens.pluginInterface.RECIPE_ID;
import io.supertokens.pluginInterface.Storage;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.multitenancy.AppIdentifier;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifier;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.utils.SemVer;
import io.supertokens.webserver.InputParser;
import io.supertokens.webserver.WebserverAPI;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.stream.Stream;

public class UsersCountAPI extends WebserverAPI {

    private static final long serialVersionUID = -2225750492558064634L;

    public UsersCountAPI(Main main) {
        super(main, "");
    }

    @Override
    public String getPath() {
        return "/users/count";
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {
        // API is tenant specific is includeAllTenants is false
        // API is app specific if includeAllTenants is true
        String[] recipeIds = InputParser.getCommaSeparatedStringArrayQueryParamOrThrowError(req, "includeRecipeIds",
                true);

        String includeAllTenantsStr = InputParser.getQueryParamOrThrowError(req, "includeAllTenants", true);

        Stream.Builder<RECIPE_ID> recipeIdsEnumBuilder = Stream.<RECIPE_ID>builder();

        if (recipeIds != null) {
            for (String recipeId : recipeIds) {
                RECIPE_ID recipeID = RECIPE_ID.getEnumFromString(recipeId);
                if (recipeID == null) {
                    throw new ServletException(new BadRequestException("Unknown recipe ID: " + recipeId));
                }
                recipeIdsEnumBuilder.add(recipeID);
            }
        }

        boolean includeAllTenants = true;
        if (includeAllTenantsStr == null || !includeAllTenantsStr.equalsIgnoreCase("true")) {
            includeAllTenants = false;
        }

        // From the CDI version that serves counts from the lifecycle-event ledger, the default (param-less)
        // path returns the fast anchor+fold value and carries the approximate/asOf metadata (PLAN-010). The
        // historical `allowApproximate` opt-in is now a no-op - accepted (unknown query params are ignored) so
        // clients still sending it keep working, but it changes nothing. Older CDI versions see byte-for-byte
        // unchanged behaviour: an exact recompute and never the new fields.
        boolean ledgerServed = getVersionFromRequest(req).greaterThanOrEqualTo(SemVer.v5_6);

        RECIPE_ID[] recipeIdsEnum = recipeIdsEnumBuilder.build().toArray(RECIPE_ID[]::new);

        try {
            long count;
            // Whether the served value came from a cached anchor snapshot. Stays false when we compute exact -
            // which the ledger-served path only ever falls back to for the shapes it does not cover
            // (all-tenants, or a recipe-filtered count), so the client still learns it got a fresh number.
            boolean approximate = false;
            long asOf = System.currentTimeMillis();

            if (includeAllTenants) {
                AppIdentifier appIdentifier = getAppIdentifier(req);
                Storage[] storages = enforcePublicTenantAndGetAllStoragesForApp(req);

                count = AuthRecipe.getUsersCountAcrossAllTenants(appIdentifier, storages, recipeIdsEnum);

            } else {
                TenantIdentifier tenantIdentifier = getTenantIdentifier(req);
                Storage storage = getTenantStorage(req);

                // The anchor + fold contract counts every user in the tenant; a recipe-id filter has no
                // ledger-fold equivalent, so fall back to an exact recompute in that case.
                if (ledgerServed && recipeIdsEnum.length == 0) {
                    ApproximateUserCount.ApproximateCountResult foldResult = ApproximateUserCount
                            .getInstance(main, getAppIdentifier(req)).serve(main, tenantIdentifier, storage);
                    count = foldResult.count;
                    approximate = foldResult.approximate;
                    asOf = foldResult.asOf;
                } else {
                    count = AuthRecipe.getUsersCountForTenant(tenantIdentifier, storage, recipeIdsEnum);
                }
            }
            JsonObject result = new JsonObject();
            result.addProperty("status", "OK");
            result.addProperty("count", count);
            if (ledgerServed) {
                result.addProperty("approximate", approximate);
                result.addProperty("asOf", asOf);
            }
            super.sendJsonResponse(200, result, resp);
        } catch (StorageQueryException | TenantOrAppNotFoundException | BadPermissionException e) {
            throw new ServletException(e);
        }
    }
}
