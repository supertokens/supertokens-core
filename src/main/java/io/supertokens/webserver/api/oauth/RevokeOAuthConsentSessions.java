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
        Boolean all = InputParser.parseBooleanOrThrowError(input, "all", true);
        String subject = InputParser.parseStringOrThrowError(input, "subject", false);
        String clientId = InputParser.parseStringOrThrowError(input, "client", false);

        try {
            AppIdentifier appIdentifier = getAppIdentifier(req);
            Storage storage = enforcePublicTenantAndGetPublicTenantStorage(req);

            Map<String, String> queryParams = new HashMap<>();
            queryParams.put("subject", subject);
            if (clientId != null) {
                queryParams.put("client", clientId);
            }
            if (all != null) {
                queryParams.put("all", all.toString());
            }

            OAuth.revokeAllConsentSessions(main, appIdentifier, storage, subject, clientId);

            if (Boolean.TRUE.equals(all)) {
                OAuthProxyHelper.proxyJsonDELETE(
                    main, req, resp,
                    appIdentifier,
                    storage,
                    null, // clientIdToCheck
                    "/oauth2/revoke", // proxyPath
                    true, // proxyToAdmin
                    true, // camelToSnakeCaseConversion
                    queryParams, // queryParams
                    new JsonObject(), // jsonInput
                    new HashMap<>(), // headers
                    (statusCode, headers, rawBody, jsonBody) -> { // handleResponse
                        JsonObject response = new JsonObject();
                        response.addProperty("status", "OK");
                        return response;
                    }
                );
                return;
            }

            JsonObject response = new JsonObject();
            response.addProperty("status", "OK");
            super.sendJsonResponse(200, response, resp);
            
        } catch (IOException | TenantOrAppNotFoundException | BadPermissionException | StorageQueryException e) {
            throw new ServletException(e);
        }
    }
}
