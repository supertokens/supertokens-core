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

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import io.supertokens.ActiveUsers;
import io.supertokens.Main;
import io.supertokens.config.Config;
import io.supertokens.exceptions.AccessTokenPayloadError;
import io.supertokens.exceptions.UnauthorisedException;
import io.supertokens.jwt.exceptions.UnsupportedJWTSigningAlgorithmException;
import io.supertokens.output.Logging;
import io.supertokens.pluginInterface.RECIPE_ID;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.exceptions.StorageTransactionLogicException;
import io.supertokens.pluginInterface.multitenancy.AppIdentifierWithStorage;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifier;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifierWithStorage;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.pluginInterface.session.SessionInfo;
import io.supertokens.session.Session;
import io.supertokens.session.accessToken.AccessToken;
import io.supertokens.session.info.SessionInformationHolder;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.useridmapping.UserIdType;
import io.supertokens.utils.SemVer;
import io.supertokens.utils.Utils;
import io.supertokens.webserver.InputParser;
import io.supertokens.webserver.WebserverAPI;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import java.io.IOException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SignatureException;
import java.security.spec.InvalidKeySpecException;

public class SessionAPI extends WebserverAPI {
    private static final long serialVersionUID = 7142317017402226537L;

    public SessionAPI(Main main) {
        super(main, RECIPE_ID.SESSION.toString());
    }

    @Override
    public String getPath() {
        return "/recipe/session";
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {
        // API is tenant specific
        SemVer version = super.getVersionFromRequest(req);

        JsonObject input = InputParser.parseJsonObjectOrThrowError(req);
        String userId = InputParser.parseStringOrThrowError(input, "userId", false);
        assert userId != null;
        Boolean enableAntiCsrf = InputParser.parseBooleanOrThrowError(input, "enableAntiCsrf", false);
        assert enableAntiCsrf != null;
        JsonObject userDataInJWT = InputParser.parseJsonObjectOrThrowError(input, "userDataInJWT", false);
        assert userDataInJWT != null;
        JsonObject userDataInDatabase = InputParser.parseJsonObjectOrThrowError(input, "userDataInDatabase", false);
        assert userDataInDatabase != null;

        try {
            boolean useStaticSigningKey = !Config.getConfig(this.getTenantIdentifierWithStorageFromRequest(req), main)
                    .getAccessTokenSigningKeyDynamic();
            if (version.greaterThanOrEqualTo(SemVer.v2_21)) {
                Boolean useDynamicSigningKey = InputParser.parseBooleanOrThrowError(input, "useDynamicSigningKey",
                        true);

                // useDynamicSigningKey defaults to true, so we check if it has been explicitly set to true
                useStaticSigningKey = Boolean.FALSE.equals(useDynamicSigningKey);
            }

            AccessToken.VERSION accessTokenVersion = AccessToken.getAccessTokenVersionForCDI(version);

            SessionInformationHolder sessionInfo = Session.createNewSession(
                    this.getTenantIdentifierWithStorageFromRequest(req), main, userId, userDataInJWT,
                    userDataInDatabase, enableAntiCsrf, accessTokenVersion,
                    useStaticSigningKey);

            if (StorageLayer.getStorage(this.getTenantIdentifierWithStorageFromRequest(req), main).getType() ==
                    STORAGE_TYPE.SQL) {
                try {
                    io.supertokens.pluginInterface.useridmapping.UserIdMapping userIdMapping =
                            io.supertokens.useridmapping.UserIdMapping.getUserIdMapping(
                                    this.getAppIdentifierWithStorage(req),
                                    sessionInfo.session.userId, UserIdType.ANY);
                    if (userIdMapping != null) {
                        ActiveUsers.updateLastActive(this.getAppIdentifierWithStorage(req), main,
                                userIdMapping.superTokensUserId);
                    } else {
                        ActiveUsers.updateLastActive(this.getAppIdentifierWithStorage(req), main,
                                sessionInfo.session.userId);
                    }
                } catch (StorageQueryException ignored) {
                }
            }

            JsonObject result = sessionInfo.toJsonObject();

            if (getVersionFromRequest(req).lesserThan(SemVer.v3_0)) {
                result.get("session").getAsJsonObject().remove("tenantId");
            }
            if (version.lesserThan(SemVer.v4_0)) {
                result.get("session").getAsJsonObject().remove("recipeUserId");
            }

            result.addProperty("status", "OK");

            if (super.getVersionFromRequest(req).greaterThanOrEqualTo(SemVer.v2_21)) {
                result.remove("idRefreshToken");
            } else {
                Utils.addLegacySigningKeyInfos(this.getAppIdentifierWithStorage(req), main, result,
                        super.getVersionFromRequest(req).betweenInclusive(SemVer.v2_9, SemVer.v2_21));
            }

            super.sendJsonResponse(200, result, resp);
        } catch (AccessTokenPayloadError e) {
            throw new ServletException(new BadRequestException(e.getMessage()));
        } catch (NoSuchAlgorithmException | StorageQueryException | InvalidKeyException | InvalidKeySpecException |
                 StorageTransactionLogicException | SignatureException | IllegalBlockSizeException |
                 BadPaddingException | InvalidAlgorithmParameterException | NoSuchPaddingException |
                 TenantOrAppNotFoundException | UnsupportedJWTSigningAlgorithmException e) {
            throw new ServletException(e);
        }
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {
        // API is app specific but tenant id is derived from the session handle
        String sessionHandle = InputParser.getQueryParamOrThrowError(req, "sessionHandle", false);
        assert sessionHandle != null;

        TenantIdentifierWithStorage tenantIdentifierWithStorage = null;
        try {
            AppIdentifierWithStorage appIdentifier = getAppIdentifierWithStorage(req);
            TenantIdentifier tenantIdentifier = new TenantIdentifier(appIdentifier.getConnectionUriDomain(),
                    appIdentifier.getAppId(), Session.getTenantIdFromSessionHandle(sessionHandle));
            tenantIdentifierWithStorage = tenantIdentifier.withStorage(StorageLayer.getStorage(tenantIdentifier, main));
        } catch (TenantOrAppNotFoundException e) {
            throw new ServletException(e);
        }

        try {
            SessionInfo sessionInfo = Session.getSession(tenantIdentifierWithStorage, sessionHandle);

            JsonObject result = new Gson().toJsonTree(sessionInfo).getAsJsonObject();
            result.add("userDataInJWT", Utils.toJsonTreeWithNulls(sessionInfo.userDataInJWT));
            result.add("userDataInDatabase", Utils.toJsonTreeWithNulls(sessionInfo.userDataInDatabase));

            result.addProperty("status", "OK");

            if (getVersionFromRequest(req).greaterThanOrEqualTo(SemVer.v3_0)) {
                result.addProperty("tenantId", tenantIdentifierWithStorage.getTenantId());
            }
            if (getVersionFromRequest(req).lesserThan(SemVer.v4_0)) {
                result.remove("recipeUserId");
            }

            super.sendJsonResponse(200, result, resp);

        } catch (StorageQueryException e) {
            throw new ServletException(e);
        } catch (UnauthorisedException e) {
            Logging.debug(main, tenantIdentifierWithStorage, Utils.exceptionStacktraceToString(e));
            JsonObject reply = new JsonObject();
            reply.addProperty("status", "UNAUTHORISED");
            reply.addProperty("message", e.getMessage());
            super.sendJsonResponse(200, reply, resp);
        }
    }
}
