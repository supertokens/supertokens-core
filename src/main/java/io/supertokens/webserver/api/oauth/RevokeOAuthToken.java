package io.supertokens.webserver.api.oauth;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;

import com.google.gson.JsonObject;

import io.supertokens.Main;
import io.supertokens.jwt.exceptions.UnsupportedJWTSigningAlgorithmException;
import io.supertokens.multitenancy.exception.BadPermissionException;
import io.supertokens.oauth.OAuth;
import io.supertokens.pluginInterface.RECIPE_ID;
import io.supertokens.pluginInterface.Storage;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.exceptions.StorageTransactionLogicException;
import io.supertokens.pluginInterface.multitenancy.AppIdentifier;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.webserver.InputParser;
import io.supertokens.webserver.WebserverAPI;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class RevokeOAuthToken extends WebserverAPI {
    public RevokeOAuthToken(Main main){
        super(main, RECIPE_ID.OAUTH.toString());
    }

    @Override
    public String getPath() {
        return "/recipe/oauth/token/revoke";
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {
        JsonObject input = InputParser.parseJsonObjectOrThrowError(req);

        String token = InputParser.parseStringOrThrowError(input, "token", false);
        try {
            AppIdentifier appIdentifier = getAppIdentifier(req);
            Storage storage = enforcePublicTenantAndGetPublicTenantStorage(req);

            if (token.startsWith("st_rt_")) {
                // revoking refresh token
                String clientId = InputParser.parseStringOrThrowError(input, "client_id", false);
                String clientSecret = InputParser.parseStringOrThrowError(input, "client_secret", false);

                Map<String, String> formFields = new HashMap<>();
                formFields.put("token", token);
                formFields.put("client_id", clientId);
                formFields.put("client_secret", clientSecret);

                OAuthProxyHelper.proxyFormPOST(
                    main, req, resp,
                    getAppIdentifier(req),
                    enforcePublicTenantAndGetPublicTenantStorage(req),
                    null, //clientIdToCheck
                    "/oauth2/revoke", // path
                    false, // proxyToAdmin
                    true, // camelToSnakeCaseConversion
                    formFields, // formFields
                    new HashMap<>(), // headers
                    (statusCode, headers, rawBody, jsonBody) -> { // getJsonResponse
                        // Success response would mean that the clientId/secret has been validated
                        try {
                            OAuth.revokeRefreshToken(main, appIdentifier, storage, token);
                        } catch (StorageQueryException | NoSuchAlgorithmException e) {
                            throw new ServletException(e);
                        }

                        JsonObject response = new JsonObject();
                        response.addProperty("status", "OK");
                        return response;
                    }
                );
            } else {
                // revoking access token

                OAuth.revokeAccessToken(main, appIdentifier, storage, token);

                JsonObject response = new JsonObject();
                response.addProperty("status", "OK");
                super.sendJsonResponse(200, response, resp);
            }
        } catch (IOException | TenantOrAppNotFoundException | BadPermissionException | StorageQueryException |
                 UnsupportedJWTSigningAlgorithmException | StorageTransactionLogicException e) {
            throw new ServletException(e);
        }

        
    }
}
