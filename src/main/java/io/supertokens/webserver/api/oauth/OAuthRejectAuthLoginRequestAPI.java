package io.supertokens.webserver.api.oauth;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;


import com.google.gson.JsonObject;

import io.supertokens.Main;
import io.supertokens.pluginInterface.multitenancy.AppIdentifier;
import io.supertokens.pluginInterface.Storage;
import io.supertokens.multitenancy.exception.BadPermissionException;
import io.supertokens.oauth.HttpRequestForOAuthProvider;
import io.supertokens.pluginInterface.RECIPE_ID;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.webserver.InputParser;
import io.supertokens.webserver.WebserverAPI;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class OAuthRejectAuthLoginRequestAPI extends WebserverAPI {

    public OAuthRejectAuthLoginRequestAPI(Main main) {
        super(main, RECIPE_ID.OAUTH.toString());
    }

    @Override
    public String getPath() {
        return "/recipe/oauth/auth/requests/login/reject";
    }

    @Override
    protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {
        JsonObject input = InputParser.parseJsonObjectOrThrowError(req);

        try {
            AppIdentifier appIdentifier = getAppIdentifier(req);
            Storage storage = enforcePublicTenantAndGetPublicTenantStorage(req);
            Map<String, String> queryParams = OAuthProxyHelper.defaultGetQueryParamsFromRequest(req);

            // the challenge may belong to a client of another app that uses the same OAuth provider
            if (!OAuthProxyHelper.checkChallengeBelongsToApp(main, resp, appIdentifier, storage,
                    "/admin/oauth2/auth/requests/login", queryParams)) {
                return;
            }

            HttpRequestForOAuthProvider.Response response = OAuthProxyHelper.proxyJsonPUT(
                main, req, resp,
                appIdentifier,
                storage,
                null, // clientIdToCheck (the client of the challenge is checked above)
                "/admin/oauth2/auth/requests/login/reject", // proxyPath
                true, // proxyToAdmin
                true, // camelToSnakeCaseConversion
                queryParams, // queryParams
                input, // jsonBody
                new HashMap<>() // headers
            );

            if (response != null) {
                response.jsonResponse.getAsJsonObject().addProperty("status", "OK");
                super.sendJsonResponse(200, response.jsonResponse, resp);
            }

        } catch (IOException | TenantOrAppNotFoundException | BadPermissionException e) {
            throw new ServletException(e);
        }
    }
}
