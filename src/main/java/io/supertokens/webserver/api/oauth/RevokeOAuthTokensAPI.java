package io.supertokens.webserver.api.oauth;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import com.google.gson.JsonObject;

import io.supertokens.Main;
import io.supertokens.multitenancy.exception.BadPermissionException;
import io.supertokens.oauth.HttpRequestForOry;
import io.supertokens.oauth.OAuth;
import io.supertokens.pluginInterface.RECIPE_ID;
import io.supertokens.pluginInterface.Storage;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.multitenancy.AppIdentifier;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.webserver.InputParser;
import io.supertokens.webserver.WebserverAPI;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class RevokeOAuthTokensAPI extends WebserverAPI {

    public RevokeOAuthTokensAPI(Main main){
        super(main, RECIPE_ID.OAUTH.toString());
    }

    @Override
    public String getPath() {
        return "/recipe/oauth/tokens/revoke";
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {
        JsonObject input = InputParser.parseJsonObjectOrThrowError(req);
        String clientId = InputParser.parseStringOrThrowError(input, "client_id", false);

        try {
            AppIdentifier appIdentifier = getAppIdentifier(req);
            Storage storage = enforcePublicTenantAndGetPublicTenantStorage(req);

            OAuth.revokeTokensForClientId(main, appIdentifier, storage, clientId);

            Map<String, String> queryParams = new HashMap<>();
            queryParams.put("client_id", clientId);

            HttpRequestForOry.Response response = OAuthProxyHelper.proxyJsonDELETE(
                main, req, resp,
                appIdentifier,
                storage,
                null, // clientIdToCheck
                "/admin/oauth2/tokens", // proxyPath
                true, // proxyToAdmin
                false, // camelToSnakeCaseConversion
                queryParams, // queryParams
                new JsonObject(), // jsonInput
                new HashMap<>() // headers
            );

            if (response != null) {
                response.jsonResponse.getAsJsonObject().addProperty("status", "OK");
                super.sendJsonResponse(200, response.jsonResponse, resp);
            }
        } catch (IOException | TenantOrAppNotFoundException | BadPermissionException | StorageQueryException e) {
            throw new ServletException(e);
        }
    }
}
