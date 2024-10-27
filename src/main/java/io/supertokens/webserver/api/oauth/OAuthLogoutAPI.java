package io.supertokens.webserver.api.oauth;

import java.io.IOException;
import java.util.HashMap;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import io.supertokens.Main;
import io.supertokens.jwt.exceptions.UnsupportedJWTSigningAlgorithmException;
import io.supertokens.multitenancy.exception.BadPermissionException;
import io.supertokens.oauth.HttpRequestForOAuthProvider;
import io.supertokens.oauth.OAuth;
import io.supertokens.oauth.exceptions.OAuthAPIException;
import io.supertokens.pluginInterface.RECIPE_ID;
import io.supertokens.pluginInterface.Storage;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.exceptions.StorageTransactionLogicException;
import io.supertokens.pluginInterface.multitenancy.AppIdentifier;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.pluginInterface.oauth.exception.OAuthClientNotFoundException;
import io.supertokens.webserver.InputParser;
import io.supertokens.webserver.WebserverAPI;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class OAuthLogoutAPI extends WebserverAPI {
    public OAuthLogoutAPI(Main main){
        super(main, RECIPE_ID.OAUTH.toString());
    }

    @Override
    public String getPath() {
        return "/recipe/oauth/sessions/logout";
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {
        String clientId = InputParser.getQueryParamOrThrowError(req, "clientId", true);
        String idTokenHint = InputParser.getQueryParamOrThrowError(req, "idTokenHint", true);
        String postLogoutRedirectionUri = InputParser.getQueryParamOrThrowError(req, "postLogoutRedirectUri", true);
        String state = InputParser.getQueryParamOrThrowError(req, "state", true);

        try {
            AppIdentifier appIdentifier = getAppIdentifier(req);
            Storage storage = enforcePublicTenantAndGetPublicTenantStorage(req);

            // Validations
            if (idTokenHint == null) {
                if (postLogoutRedirectionUri != null ) {
                    throw new OAuthAPIException("invalid_request", "The request is missing a required parameter, includes an invalid parameter value, includes a parameter more than once, or is otherwise malformed. Logout failed because query parameter postLogoutRedirectionUri is set but idTokenHint is missing.", 400);
                }
    
                if (state != null) {
                    throw new OAuthAPIException("invalid_request", "The request is missing a required parameter, includes an invalid parameter value, includes a parameter more than once, or is otherwise malformed. Logout failed because query parameter state is set but idTokenHint is missing.", 400);
                }
            }
            // Verify id token and client id associations
            JsonObject idTokenPayload = null;
            String sessionHandle = null;
            if (idTokenHint != null) {
                idTokenPayload = OAuth.verifyIdTokenAndGetPayload(main, appIdentifier, storage, idTokenHint);
                if (idTokenPayload.has("sid")) {
                    sessionHandle = idTokenPayload.get("sid").getAsString();
                }

                if (clientId != null) {
                    String clientIdInIdTokenPayload = idTokenPayload.get("aud").getAsString();
                    if (!clientId.equals(clientIdInIdTokenPayload)) {
                        throw new OAuthAPIException("invalid_request", "The client_id in the id_token_hint does not match the client_id in the request.", 400);
                    }
                } else {
                    clientId = idTokenPayload.get("aud").getAsString();
                }
            }

            // Check if the post logout redirection URI is valid for the clientId
            if (postLogoutRedirectionUri != null) {
                HttpRequestForOAuthProvider.Response response = OAuthProxyHelper.proxyGET(
                    main, req, resp, 
                    appIdentifier, storage,
                    clientId, // clientIdToCheck
                    "/admin/clients/" + clientId, // path
                    true, // proxyToAdmin
                    true, // camelToSnakeCaseConversion
                    new HashMap<>(), // queryParams
                    new HashMap<>() // headers
                );

                if (response == null) {
                    return; // proxy would have already responded
                }

                String[] postLogoutRedirectUris = null;
                if (response.jsonResponse.getAsJsonObject().has("postLogoutRedirectUris") && response.jsonResponse.getAsJsonObject().get("postLogoutRedirectUris").isJsonArray()) {
                    JsonArray postLogoutRedirectUrisArray = response.jsonResponse.getAsJsonObject().get("postLogoutRedirectUris").getAsJsonArray();
                    postLogoutRedirectUris = new String[postLogoutRedirectUrisArray.size()];
                    for (int i = 0; i < postLogoutRedirectUrisArray.size(); i++) {
                        postLogoutRedirectUris[i] = postLogoutRedirectUrisArray.get(i).getAsString();
                    }
                }

                if (postLogoutRedirectUris == null || postLogoutRedirectUris.length == 0) {
                    throw new OAuthAPIException("", "", 400);
                }

                boolean isValidPostLogoutRedirectUri = false;
                for (String uri : postLogoutRedirectUris) {
                    if (uri.equals(postLogoutRedirectionUri)) {
                        isValidPostLogoutRedirectUri = true;
                        break;
                    }
                }

                if (!isValidPostLogoutRedirectUri) {
                    throw new OAuthAPIException("invalid_request", "The post_logout_redirect_uri is not valid for this client.", 400);
                }
            }

            // Validations are complete, time to respond
            String redirectTo = OAuth.createLogoutRequestAndReturnRedirectUri(main, appIdentifier, storage, clientId, postLogoutRedirectionUri, sessionHandle, state);

            JsonObject response = new JsonObject();
            response.addProperty("status", "OK");
            response.addProperty("redirectTo", redirectTo);
            super.sendJsonResponse(200, response, resp);

        } catch (OAuthAPIException e) {
            OAuthProxyHelper.handleOAuthAPIException(resp, e);
        } catch (OAuthClientNotFoundException e) {
            OAuthProxyHelper.handleOAuthClientNotFoundException(resp);
        } catch (IOException | TenantOrAppNotFoundException | BadPermissionException | StorageQueryException | UnsupportedJWTSigningAlgorithmException | StorageTransactionLogicException e) {
            throw new ServletException(e);
        }
    }
}
