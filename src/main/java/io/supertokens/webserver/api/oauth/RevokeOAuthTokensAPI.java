package io.supertokens.webserver.api.oauth;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import com.google.gson.JsonObject;

import io.supertokens.Main;
import io.supertokens.multitenancy.exception.BadPermissionException;
import io.supertokens.oauth.HttpRequestForOAuthProvider;
import io.supertokens.oauth.OAuth;
import io.supertokens.pluginInterface.RECIPE_ID;
import io.supertokens.pluginInterface.Storage;
import io.supertokens.pluginInterface.StorageUtils;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.multitenancy.AppIdentifier;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.pluginInterface.oauth.exception.OAuthClientNotFoundException;
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

            try {
                StorageUtils.getOAuthStorage(storage).getOAuthClientById(appIdentifier, clientId);
            } catch (OAuthClientNotFoundException e) {
                // This app has no such client (it may belong to another app that uses the same OAuth provider),
                // so there are no tokens of it to revoke in this app. The OAuth provider is not called, since that
                // would delete the tokens of the other app's client.
                JsonObject finalResponse = new JsonObject();
                finalResponse.addProperty("status", "OK");
                super.sendJsonResponse(200, finalResponse, resp);
                return;
            }

            Map<String, String> queryParams = new HashMap<>();
            queryParams.put("client_id", clientId);

            HttpRequestForOAuthProvider.Response response = OAuthProxyHelper.proxyJsonDELETE(
                main, req, resp,
                appIdentifier,
                storage,
                clientId, // clientIdToCheck (already checked above, kept as a safeguard)
                "/admin/oauth2/tokens", // proxyPath
                true, // proxyToAdmin
                false, // camelToSnakeCaseConversion
                queryParams, // queryParams
                new JsonObject(), // jsonInput
                new HashMap<>() // headers
            );

            if (response != null) {
                if (response.statusCode == 204) {
                    JsonObject finalResponse = new JsonObject();
                    finalResponse.addProperty("status", "OK");
                    super.sendJsonResponse(200, finalResponse, resp);
                } else {
                    throw new IllegalStateException("should never come here");
                }
            }
        } catch (IOException | TenantOrAppNotFoundException | BadPermissionException | StorageQueryException e) {
            throw new ServletException(e);
        }
    }
}
