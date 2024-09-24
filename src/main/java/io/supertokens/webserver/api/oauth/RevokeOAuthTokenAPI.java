package io.supertokens.webserver.api.oauth;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;

import com.google.gson.JsonObject;

import io.supertokens.Main;
import io.supertokens.featureflag.exceptions.FeatureNotEnabledException;
import io.supertokens.jwt.exceptions.UnsupportedJWTSigningAlgorithmException;
import io.supertokens.multitenancy.exception.BadPermissionException;
import io.supertokens.oauth.HttpRequestForOry;
import io.supertokens.oauth.OAuth;
import io.supertokens.pluginInterface.RECIPE_ID;
import io.supertokens.pluginInterface.Storage;
import io.supertokens.pluginInterface.exceptions.InvalidConfigException;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.exceptions.StorageTransactionLogicException;
import io.supertokens.pluginInterface.multitenancy.AppIdentifier;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.webserver.InputParser;
import io.supertokens.webserver.WebserverAPI;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class RevokeOAuthTokenAPI extends WebserverAPI {
    public RevokeOAuthTokenAPI(Main main){
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
                String gid = null;
                long exp = -1;
                {
                    // introspect token to get gid
                    Map<String, String> formFields = new HashMap<>();
                    formFields.put("token", token);
    
                    HttpRequestForOry.Response response = OAuthProxyHelper.proxyFormPOST(
                        main, req, resp,
                        appIdentifier,
                        storage,
                        null, // clientIdToCheck
                        "/admin/oauth2/introspect", // pathProxy
                        true, // proxyToAdmin
                        false, // camelToSnakeCaseConversion
                        formFields,
                        new HashMap<>() // headers
                    );

                    if (response != null) {
                        JsonObject finalResponse = response.jsonResponse.getAsJsonObject();

                        try {
                            OAuth.verifyAndUpdateIntrospectRefreshTokenPayload(main, appIdentifier, storage, finalResponse, token);
                            if (finalResponse.get("active").getAsBoolean()) {
                                gid = finalResponse.get("gid").getAsString();
                                exp = finalResponse.get("exp").getAsLong();
                            }
                        } catch (StorageQueryException | TenantOrAppNotFoundException |
                                    FeatureNotEnabledException | InvalidConfigException e) {
                            throw new ServletException(e);
                        }
                    }
                }

                // revoking refresh token
                String clientId = InputParser.parseStringOrThrowError(input, "client_id", false);
                String clientSecret = InputParser.parseStringOrThrowError(input, "client_secret", true);

                String authorizationHeader = InputParser.parseStringOrThrowError(input, "authorizationHeader", true);

                Map<String, String> headers = new HashMap<>();
                if (authorizationHeader != null) {
                    headers.put("Authorization", authorizationHeader);
                }

                Map<String, String> formFields = new HashMap<>();
                formFields.put("token", token);
                formFields.put("client_id", clientId);
                if (clientSecret != null) {
                    formFields.put("client_secret", clientSecret);
                }

                HttpRequestForOry.Response response = OAuthProxyHelper.proxyFormPOST(
                    main, req, resp,
                    getAppIdentifier(req),
                    enforcePublicTenantAndGetPublicTenantStorage(req),
                    null, //clientIdToCheck
                    "/oauth2/revoke", // path
                    false, // proxyToAdmin
                    false, // camelToSnakeCaseConversion
                    formFields, // formFields
                    headers // headers
                );

                if (response != null) {
                    // Success response would mean that the clientId/secret has been validated
                    if (gid != null) {
                        try {
                            OAuth.revokeRefreshToken(main, appIdentifier, storage, gid, exp);
                        } catch (StorageQueryException | NoSuchAlgorithmException e) {
                            throw new ServletException(e);
                        }
                    }

                    JsonObject finalResponse = new JsonObject();
                    finalResponse.addProperty("status", "OK");
                    super.sendJsonResponse(200, finalResponse, resp);
                }
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
