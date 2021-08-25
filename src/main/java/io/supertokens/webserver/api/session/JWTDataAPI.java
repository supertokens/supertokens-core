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

package io.supertokens.webserver.api.session;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.supertokens.Main;
import io.supertokens.exceptions.UnauthorisedException;
import io.supertokens.output.Logging;
import io.supertokens.pluginInterface.RECIPE_ID;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.session.Session;
import io.supertokens.utils.Utils;
import io.supertokens.webserver.InputParser;
import io.supertokens.webserver.WebserverAPI;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

public class JWTDataAPI extends WebserverAPI {
    private static final long serialVersionUID = -4989144736402314280L;

    public JWTDataAPI(Main main) {
        super(main, RECIPE_ID.SESSION.toString());
    }

    @Override
    public String getPath() {
        return "/recipe/jwt/data";
    }

    @Override
    protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {
        JsonObject input = InputParser.parseJsonObjectOrThrowError(req);

        String sessionHandle = InputParser.parseStringOrThrowError(input, "sessionHandle", false);
        assert sessionHandle != null;

        JsonObject userDataInJWT = InputParser.parseJsonObjectOrThrowError(input, "userDataInJWT", false);
        assert userDataInJWT != null;

        try {
            Session.updateSession(main, sessionHandle, null, userDataInJWT, null);

            JsonObject result = new JsonObject();

            result.addProperty("status", "OK");
            super.sendJsonResponse(200, result, resp);

        } catch (StorageQueryException e) {
            throw new ServletException(e);
        } catch (UnauthorisedException e) {
            Logging.debug(main, Utils.exceptionStacktraceToString(e));
            JsonObject reply = new JsonObject();
            reply.addProperty("status", "UNAUTHORISED");
            reply.addProperty("message", e.getMessage());
            super.sendJsonResponse(200, reply, resp);
        }
    }

    @Override
    @Deprecated
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {
        String sessionHandle = InputParser.getQueryParamOrThrowError(req, "sessionHandle", false);
        assert sessionHandle != null;

        try {
            JsonElement jwtPayload = Session.getJWTData(main, sessionHandle);

            JsonObject result = new JsonObject();

            result.addProperty("status", "OK");
            result.add("userDataInJWT", jwtPayload);
            super.sendJsonResponse(200, result, resp);

        } catch (StorageQueryException e) {
            throw new ServletException(e);
        } catch (UnauthorisedException e) {
            Logging.debug(main, Utils.exceptionStacktraceToString(e));
            JsonObject reply = new JsonObject();
            reply.addProperty("status", "UNAUTHORISED");
            reply.addProperty("message", e.getMessage());
            super.sendJsonResponse(200, reply, resp);
        }
    }

}
