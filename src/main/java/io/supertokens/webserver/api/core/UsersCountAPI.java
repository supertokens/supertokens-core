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
import io.supertokens.authRecipe.exception.UsersCountNotReadyException;
import io.supertokens.config.Config;
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

        // CDI 5.7+ serves the exact lifecycle-ledger `anchor + fold` value by default and always carries the
        // approximate/asOf metadata (PLAN-010). CDI 5.6 keeps its released behaviour: the ledger value is
        // opt-in via `allowApproximate=true`; without the opt-in (and on any CDI < 5.6) the count is an exact
        // recompute and the approximate/asOf fields are omitted. 5.6 must not be redefined — it already
        // shipped in core 12.2.0 with the opt-in contract.
        String allowApproximateStr = InputParser.getQueryParamOrThrowError(req, "allowApproximate", true);
        boolean allowApproximate = allowApproximateStr != null && allowApproximateStr.equalsIgnoreCase("true");
        SemVer cdiVersion = getVersionFromRequest(req);
        boolean ledgerServed = cdiVersion.greaterThanOrEqualTo(SemVer.v5_7)
                || (allowApproximate && cdiVersion.greaterThanOrEqualTo(SemVer.v5_6));

        RECIPE_ID[] recipeIdsEnum = recipeIdsEnumBuilder.build().toArray(RECIPE_ID[]::new);

        try {
            long count;
            boolean approximate;
            long asOf;

            RECIPE_ID[] recipes = recipeIdsEnum.length == 0 ? null : recipeIdsEnum;
            if (!ledgerServed) {
                // Older CDI versions (below 5.6, or 5.6 without the opt-in) keep their released contract: an exact
                // count on every request, with no approximate/asOf fields. Deliberately outside the user-count
                // pipeline - old CDI behaviour is not changed.
                if (includeAllTenants) {
                    AppIdentifier appIdentifier = getAppIdentifier(req);
                    Storage[] storages = enforcePublicTenantAndGetAllStoragesForApp(req);
                    count = AuthRecipe.getUsersCountAcrossAllTenants(appIdentifier, storages, recipeIdsEnum);
                } else {
                    count = AuthRecipe.getUsersCountForTenant(getTenantIdentifier(req), getTenantStorage(req),
                            recipeIdsEnum);
                }
                approximate = false;
                asOf = System.currentTimeMillis();
            } else {
                // CDI 5.7+ (or 5.6 with the opt-in): the user-count pipeline. Unfiltered counts are the shared full
                // count plus the lifecycle events since it. A recipe filter has no event rules, so it gets the stored
                // exact count of the users with those recipes (one stored count per filter, at most
                // users_count_max_age_ms old), with approximate/asOf saying how old it is.
                long waitMs = Config.getBaseConfig(main).getUsersCountRequestWaitMs();
                ApproximateUserCount.ApproximateCountResult served;
                try {
                    if (includeAllTenants) {
                        AppIdentifier appIdentifier = getAppIdentifier(req);
                        enforcePublicTenantAndGetAllStoragesForApp(req);
                        ApproximateUserCount counts = ApproximateUserCount.getInstance(main, appIdentifier);
                        served = recipes == null ? counts.serveApp(main, null, waitMs)
                                : counts.serveAppStoredExact(main, recipes, waitMs);
                    } else {
                        TenantIdentifier tenantIdentifier = getTenantIdentifier(req);
                        getTenantStorage(req);
                        ApproximateUserCount counts = ApproximateUserCount.getInstance(main, getAppIdentifier(req));
                        served = recipes == null ? counts.serveTenant(main, tenantIdentifier, null, waitMs)
                                : counts.serveTenantStoredExact(main, tenantIdentifier, recipes, waitMs);
                    }
                } catch (UsersCountNotReadyException e) {
                    // the count keeps running and serves the retry
                    resp.setHeader("Retry-After",
                            Integer.toString(Config.getBaseConfig(main).getUsersCountRetryAfterSeconds()));
                    sendTextResponse(503, "The user count is being computed; try again later.", resp);
                    return;
                }
                count = served.count;
                approximate = served.approximate;
                asOf = served.asOf;
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
