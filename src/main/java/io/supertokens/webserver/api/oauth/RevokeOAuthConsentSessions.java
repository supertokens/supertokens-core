package io.supertokens.webserver.api.oauth;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import com.google.gson.JsonObject;

import io.supertokens.Main;
import io.supertokens.multitenancy.exception.BadPermissionException;
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

public class RevokeOAuthConsentSessions extends WebserverAPI {

    public RevokeOAuthConsentSessions(Main main){
        super(main, RECIPE_ID.OAUTH.toString());
    }

    @Override
    public String getPath() {
        return "/recipe/oauth/sessions/consent/revoke";
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {
        JsonObject input = InputParser.parseJsonObjectOrThrowError(req);

        try {
            Map<String, String> queryParams = input.entrySet().stream().collect(Collectors.toMap(
                Map.Entry::getKey,
                e -> e.getValue().isJsonPrimitive() && e.getValue().getAsJsonPrimitive().isString() ? e.getValue().getAsString() : e.getValue().toString()
            ));

            AppIdentifier appIdentifier = getAppIdentifier(req);
            Storage storage = enforcePublicTenantAndGetPublicTenantStorage(req);

            OAuthProxyHelper.proxyJsonDELETE(
                main, req, resp,
                appIdentifier,
                storage,
                null, // clientIdToCheck
                "/admin/oauth2/auth/sessions/consent", // proxyPath
                true, // proxyToAdmin
                true, // camelToSnakeCaseConversion
                queryParams, // queryParams
                new JsonObject(), // jsonInput
                new HashMap<>(), // headers
                (statusCode, headers, rawBody, jsonBody) -> { // handleResponse
                    boolean all = queryParams.get("all") != null && queryParams.get("all").equals("true");
                    if (all) {
                        try {
                            OAuth.revokeAllConsentSessions(main, appIdentifier, storage, queryParams.get("subject"), queryParams.get("client"));
                        } catch (StorageQueryException e) {
                            throw new ServletException(e);
                        }
                    }

                    JsonObject response = new JsonObject();
                    response.addProperty("status", "OK");
                    return response;
                }
            );
        } catch (IOException | TenantOrAppNotFoundException | BadPermissionException e) {
            throw new ServletException(e);
        }
    }
}
