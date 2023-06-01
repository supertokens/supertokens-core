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

package io.supertokens.inmemorydb.queries.utils;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class JsonUtils {
    public static String jsonObjectToString(JsonObject obj) {
        if (obj == null) {
            return null;
        }
        return obj.toString();
    }

    public static JsonObject stringToJsonObject(String json) {
        if (json == null) {
            return null;
        }
        JsonParser jp = new JsonParser();
        return jp.parse(json).getAsJsonObject();
    }
}
