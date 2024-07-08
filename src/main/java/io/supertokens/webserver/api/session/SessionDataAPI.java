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

import com.google.gson.JsonObject;
import io.supertokens.Main;
import io.supertokens.exceptions.AccessTokenPayloadError;
import io.supertokens.exceptions.UnauthorisedException;
import io.supertokens.output.Logging;
import io.supertokens.pluginInterface.RECIPE_ID;
import io.supertokens.pluginInterface.Storage;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.multitenancy.AppIdentifier;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifier;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.session.Session;
import io.supertokens.session.accessToken.AccessToken;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.utils.SemVer;
import io.supertokens.utils.Utils;
import io.supertokens.webserver.InputParser;
import io.supertokens.webserver.WebserverAPI;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

public class SessionDataAPI extends WebserverAPI {
    private static final long serialVersionUID = -6901312482713647177L;

    public SessionDataAPI(Main main) {
        super(main, RECIPE_ID.SESSION.toString());
    }

    @Override
    public String getPath() {
        return "/recipe/session/data";
    }

    @Override
    @Deprecated
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {
        // API is app specific but tenant id is derived from the session handle
        String sessionHandle = InputParser.getQueryParamOrThrowError(req, "sessionHandle", false);
        assert sessionHandle != null;

        TenantIdentifier tenantIdentifier;
        Storage storage;
        try {
            AppIdentifier appIdentifier = getAppIdentifier(req);
            tenantIdentifier = new TenantIdentifier(appIdentifier.getConnectionUriDomain(), appIdentifier.getAppId(),
                    Session.getTenantIdFromSessionHandle(sessionHandle));
            storage = StorageLayer.getStorage(tenantIdentifier, main);
        } catch (TenantOrAppNotFoundException e) {
            throw new ServletException(e);
        }

        try {
            JsonObject userDataInDatabase = Session.getSessionData(tenantIdentifier, storage, sessionHandle);

            JsonObject result = new JsonObject();
            result.addProperty("status", "OK");
            result.add("userDataInDatabase", userDataInDatabase);
            super.sendJsonResponse(200, result, resp);

        } catch (StorageQueryException e) {
            throw new ServletException(e);
        } catch (UnauthorisedException e) {
            Logging.debug(main, tenantIdentifier, Utils.exceptionStacktraceToString(e));
            JsonObject reply = new JsonObject();
            reply.addProperty("status", "UNAUTHORISED");
            reply.addProperty("message", e.getMessage());
            super.sendJsonResponse(200, reply, resp);
        }
    }

    @Override
    protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {
        // API is app specific but tenant id is derived from the session handle
        JsonObject input = InputParser.parseJsonObjectOrThrowError(req);
        String sessionHandle = InputParser.parseStringOrThrowError(input, "sessionHandle", false);
        assert sessionHandle != null;
        JsonObject userDataInDatabase = InputParser.parseJsonObjectOrThrowError(input, "userDataInDatabase", false);
        assert userDataInDatabase != null;

        TenantIdentifier tenantIdentifier;
        Storage storage;
        try {
            AppIdentifier appIdentifier = getAppIdentifier(req);
            tenantIdentifier = new TenantIdentifier(appIdentifier.getConnectionUriDomain(), appIdentifier.getAppId(),
                    Session.getTenantIdFromSessionHandle(sessionHandle));
            storage = StorageLayer.getStorage(tenantIdentifier, main);
        } catch (TenantOrAppNotFoundException e) {
            throw new ServletException(e);
        }

        try {
            // This is only here for consistency: the difference between the two versions is the handling of jwtData
            // which is always null here
            if (getVersionFromRequest(req).greaterThanOrEqualTo(SemVer.v2_21)) {
                AccessToken.VERSION version = AccessToken.getAccessTokenVersionForCDI(getVersionFromRequest(req));
                Session.updateSession(tenantIdentifier, storage, sessionHandle,
                        userDataInDatabase, null, version);
            } else {
                Session.updateSessionBeforeCDI2_21(tenantIdentifier, storage, sessionHandle,
                        userDataInDatabase, null);
            }

            JsonObject result = new JsonObject();
            result.addProperty("status", "OK");
            super.sendJsonResponse(200, result, resp);

        } catch (StorageQueryException e) {
            throw new ServletException(e);
        } catch (AccessTokenPayloadError e) {
            throw new ServletException(new BadRequestException(e.getMessage()));
        } catch (UnauthorisedException e) {
            Logging.debug(main, tenantIdentifier, Utils.exceptionStacktraceToString(e));
            JsonObject reply = new JsonObject();
            reply.addProperty("status", "UNAUTHORISED");
            reply.addProperty("message", e.getMessage());
            super.sendJsonResponse(200, reply, resp);
        }
    }

}
