package io.supertokens.webserver.api.oauth;

import java.io.IOException;
import java.util.HashMap;

import com.google.gson.JsonObject;

import io.supertokens.Main;
import io.supertokens.multitenancy.exception.BadPermissionException;
import io.supertokens.pluginInterface.RECIPE_ID;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.webserver.InputParser;
import io.supertokens.webserver.WebserverAPI;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class OAuthRejectAuthConsentRequestAPI extends WebserverAPI {

    public OAuthRejectAuthConsentRequestAPI(Main main) {
        super(main, RECIPE_ID.OAUTH.toString());
    }

    @Override
    public String getPath() {
        return "/recipe/oauth/auth/requests/consent/reject";
    }

    @Override
    protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {
        JsonObject input = InputParser.parseJsonObjectOrThrowError(req);

        try {
            OAuthProxyHelper.proxyJsonPUT(
                main, req, resp,
                getAppIdentifier(req),
                enforcePublicTenantAndGetPublicTenantStorage(req),
                "/admin/oauth2/auth/requests/consent/reject", // proxyPath
                true, // proxyToAdmin
                true, // camelToSnakeCaseConversion
                OAuthProxyHelper.defaultGetQueryParamsFromRequest(req),
                input, // getJsonBody
                new HashMap<>(), // getHeadersForProxy
                (statusCode, headers, rawBody, jsonBody) -> { // handleResponse
                    JsonObject response = jsonBody.getAsJsonObject();
                    response.addProperty("status", "OK");
                    return response;
                }
            );
        } catch (IOException | TenantOrAppNotFoundException | BadPermissionException e) {
            throw new ServletException(e);
        }
    }
}
