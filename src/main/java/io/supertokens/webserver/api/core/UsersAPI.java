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

package io.supertokens.webserver.api.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.supertokens.Main;
import io.supertokens.authRecipe.AuthRecipe;
import io.supertokens.authRecipe.UserPaginationContainer;
import io.supertokens.authRecipe.UserPaginationToken;
import io.supertokens.output.Logging;
import io.supertokens.pluginInterface.RECIPE_ID;
import io.supertokens.pluginInterface.authRecipe.AuthRecipeUserInfo;
import io.supertokens.pluginInterface.dashboard.DashboardSearchTags;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifierWithStorage;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.useridmapping.UserIdMapping;
import io.supertokens.utils.SemVer;
import io.supertokens.utils.Utils;
import io.supertokens.webserver.InputParser;
import io.supertokens.webserver.WebserverAPI;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.stream.Stream;

public class UsersAPI extends WebserverAPI {

    private static final long serialVersionUID = -2225750492558064634L;

    public UsersAPI(Main main) {
        super(main, "");
    }

    @Override
    public String getPath() {
        return "/users";
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {
        // this API is tenant specific
        String[] recipeIds = InputParser.getCommaSeparatedStringArrayQueryParamOrThrowError(req, "includeRecipeIds",
                true);

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

        TenantIdentifierWithStorage tenantIdentifierWithStorage = null;
        try {
            tenantIdentifierWithStorage = this.getTenantIdentifierWithStorageFromRequest(req);
        } catch (TenantOrAppNotFoundException e) {
            throw new ServletException(e);
        }

        /*
         * pagination token can be null or string.
         * if string, it should be a base64 encoded JSON object.
         * pagination token will corresponds to the first item of the users' list.
         */
        String paginationToken = InputParser.getQueryParamOrThrowError(req, "paginationToken", true);
        /*
         * limit can be null or an integer with maximum value 1000.
         * default value will be 100.
         */
        Integer limit = InputParser.getIntQueryParamOrThrowError(req, "limit", true);
        /*
         * timeJoinedOrder can be null or string.
         * if not null, the value should be either "ASC" or "DESC".
         * default value will be "ASC"
         */
        String timeJoinedOrder = InputParser.getQueryParamOrThrowError(req, "timeJoinedOrder", true);

        if (timeJoinedOrder != null) {
            if (!timeJoinedOrder.equals("ASC") && !timeJoinedOrder.equals("DESC")) {
                throw new ServletException(new BadRequestException("timeJoinedOrder can be either ASC OR DESC"));
            }
        } else {
            timeJoinedOrder = "ASC";
        }

        DashboardSearchTags searchTags = null;

        String emails = InputParser.getQueryParamOrThrowError(req, "email", true);
        {
            if (emails != null) {
                ArrayList<String> emailArrayList = normalizeSearchTags(emails);

                if (emailArrayList.size() != 0) {
                    searchTags = new DashboardSearchTags(emailArrayList, null, null);
                }
            }
        }

        String phoneNumbers = InputParser.getQueryParamOrThrowError(req, "phone", true);
        {
            if (phoneNumbers != null) {
                ArrayList<String> phoneNumberArrayList = normalizeSearchTags(phoneNumbers);

                if (phoneNumberArrayList.size() != 0) {
                    if (searchTags == null) {
                        searchTags = new DashboardSearchTags(null, phoneNumberArrayList, null);
                    } else {
                        searchTags.phoneNumbers = phoneNumberArrayList;
                    }

                }
            }
        }

        String providers = InputParser.getQueryParamOrThrowError(req, "provider", true);
        {
            if (providers != null) {
                ArrayList<String> providerArrayList = normalizeSearchTags(providers);

                if (providerArrayList.size() != 0) {
                    if (searchTags == null) {
                        searchTags = new DashboardSearchTags(null, null, providerArrayList);
                    } else {
                        searchTags.providers = providerArrayList;
                    }
                }
            }
        }

        if (limit != null) {
            if (limit > AuthRecipe.USER_PAGINATION_LIMIT && searchTags == null) {
                throw new ServletException(
                        new BadRequestException("max limit allowed is " + AuthRecipe.USER_PAGINATION_LIMIT));
            } else if (limit < 1) {
                throw new ServletException(new BadRequestException("limit must a positive integer with min value 1"));
            }
        } else {
            limit = 100;
        }

        try {
            UserPaginationContainer users = AuthRecipe.getUsers(tenantIdentifierWithStorage,
                    limit, timeJoinedOrder, paginationToken,
                    recipeIdsEnumBuilder.build().toArray(RECIPE_ID[]::new), searchTags);

            UserIdMapping.populateExternalUserIdForUsers(tenantIdentifierWithStorage, users.users);

            JsonObject result = new JsonObject();
            result.addProperty("status", "OK");

            JsonArray usersJson = new JsonArray();
            for (AuthRecipeUserInfo user : users.users) {
                if (getVersionFromRequest(req).lesserThan(SemVer.v4_0)) {
                    JsonObject jsonObj = new JsonObject();
                    jsonObj.addProperty("recipeId", user.loginMethods[0].recipeId.toString());
                    JsonObject userJson = user.toJsonWithoutAccountLinking();
                    jsonObj.add("user", userJson);
                    usersJson.add(jsonObj);
                } else {
                    usersJson.add(user.toJson());
                }
            }

            if (getVersionFromRequest(req).lesserThan(SemVer.v3_0)) {
                for (JsonElement user : usersJson) {
                    user.getAsJsonObject().get("user").getAsJsonObject().remove("tenantIds");
                }
            }

            result.add("users", usersJson);

            if (users.nextPaginationToken != null) {
                result.addProperty("nextPaginationToken", users.nextPaginationToken);
            }
            super.sendJsonResponse(200, result, resp);
        } catch (UserPaginationToken.InvalidTokenException e) {
            Logging.debug(main, tenantIdentifierWithStorage, Utils.exceptionStacktraceToString(e));
            throw new ServletException(new BadRequestException("invalid pagination token"));
        } catch (StorageQueryException | TenantOrAppNotFoundException e) {
            throw new ServletException(e);
        }
    }

    private static ArrayList<String> normalizeSearchTags(String searchTag) {
        String[] searchTagArray = searchTag.split(";");
        ArrayList<String> searchTagArrayList = new ArrayList<>();
        for (String searchTagString : searchTagArray) {
            String normalizedSearchTag = searchTagString.toLowerCase().trim();
            if (normalizedSearchTag.length() != 0) {
                searchTagArrayList.add(normalizedSearchTag);
            }
        }
        return searchTagArrayList;
    }
}
