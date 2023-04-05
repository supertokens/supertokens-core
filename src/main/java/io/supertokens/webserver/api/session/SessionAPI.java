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
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.supertokens.ActiveUsers;
import io.supertokens.Main;
import io.supertokens.exceptions.UnauthorisedException;
import io.supertokens.output.Logging;
import io.supertokens.pluginInterface.RECIPE_ID;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.exceptions.StorageTransactionLogicException;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.pluginInterface.session.SessionInfo;
import io.supertokens.pluginInterface.useridmapping.UserIdMapping;
import io.supertokens.session.Session;
import io.supertokens.session.accessToken.AccessTokenSigningKey;
import io.supertokens.session.accessToken.AccessTokenSigningKey.KeyInfo;
import io.supertokens.session.info.SessionInformationHolder;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.useridmapping.UserIdType;
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
import java.util.List;

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
            SessionInformationHolder sessionInfo = Session.createNewSession(
                    this.getTenantIdentifierWithStorageFromRequest(req), main, userId, userDataInJWT,
                    userDataInDatabase, enableAntiCsrf);

            if (StorageLayer.getStorage(this.getTenantIdentifierWithStorageFromRequest(req), main).getType() ==
                    STORAGE_TYPE.SQL) {
                try {
                    UserIdMapping userIdMapping = io.supertokens.useridmapping.UserIdMapping.getUserIdMapping(
                            this.getAppIdentifierWithStorage(req),
                            sessionInfo.session.userId, UserIdType.ANY);
                    if (userIdMapping != null) {
                        ActiveUsers.updateLastActive(main, userIdMapping.superTokensUserId);
                    } else {
                        ActiveUsers.updateLastActive(main, sessionInfo.session.userId);
                    }
                } catch (StorageQueryException ignored) {
                }
            }

            JsonObject result = sessionInfo.toJsonObject();

            result.addProperty("status", "OK");

            result.addProperty("jwtSigningPublicKey",
                    new Utils.PubPriKey(
                            AccessTokenSigningKey.getInstance(
                                            this.getTenantIdentifierWithStorageFromRequest(req).toAppIdentifier(), main)
                                    .getLatestIssuedKey().value).publicKey);
            result.addProperty("jwtSigningPublicKeyExpiryTime",
                    AccessTokenSigningKey.getInstance(
                                    this.getTenantIdentifierWithStorageFromRequest(req).toAppIdentifier(), main)
                            .getKeyExpiryTime());

            if (!super.getVersionFromRequest(req).equals("2.7") && !super.getVersionFromRequest(req).equals("2.8")) {
                List<KeyInfo> keys = AccessTokenSigningKey.getInstance(
                                this.getTenantIdentifierWithStorageFromRequest(req).toAppIdentifier(),
                                main)
                        .getAllKeys();
                JsonArray jwtSigningPublicKeyListJSON = Utils.keyListToJson(keys);
                result.add("jwtSigningPublicKeyList", jwtSigningPublicKeyListJSON);
            }

            super.sendJsonResponse(200, result, resp);
        } catch (NoSuchAlgorithmException | StorageQueryException | InvalidKeyException | InvalidKeySpecException | StorageTransactionLogicException | SignatureException | IllegalBlockSizeException | BadPaddingException | InvalidAlgorithmParameterException | NoSuchPaddingException | TenantOrAppNotFoundException e) {
            throw new ServletException(e);
        }
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {
        // API is tenant specific
        String sessionHandle = InputParser.getQueryParamOrThrowError(req, "sessionHandle", false);
        assert sessionHandle != null;

        try {
            SessionInfo sessionInfo = Session.getSession(this.getTenantIdentifierWithStorageFromRequest(req), sessionHandle);

            JsonObject result = new Gson().toJsonTree(sessionInfo).getAsJsonObject();
            result.add("userDataInJWT", Utils.toJsonTreeWithNulls(sessionInfo.userDataInJWT));
            result.add("userDataInDatabase", Utils.toJsonTreeWithNulls(sessionInfo.userDataInDatabase));

            result.addProperty("status", "OK");

            super.sendJsonResponse(200, result, resp);

        } catch (StorageQueryException | TenantOrAppNotFoundException e) {
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
