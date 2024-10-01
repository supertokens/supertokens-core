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

import java.io.IOException;
import java.util.ArrayList;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import io.supertokens.Main;
import io.supertokens.dashboard.Dashboard;
import io.supertokens.pluginInterface.dashboard.DashboardSearchTags;
import io.supertokens.webserver.WebserverAPI;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class SearchTagsAPI extends WebserverAPI {

    public SearchTagsAPI(Main main) {
        super(main, "");
    }

    @Override
    public String getPath() {
        return "/user/search/tags";
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {

        JsonObject response = new JsonObject();
        response.addProperty("status", "OK");
        JsonArray tags = new JsonArray();
        for (DashboardSearchTags.SUPPORTED_SEARCH_TAGS tag : DashboardSearchTags.SUPPORTED_SEARCH_TAGS.values()) {
            tags.add(new JsonPrimitive(tag.toString()));
        }
        response.add("tags", tags);
        super.sendJsonResponse(200, response, resp);
    }
}
