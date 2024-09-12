package io.supertokens.webserver.api.oauth;

import java.io.IOException;
import java.util.HashMap;

import com.google.gson.JsonObject;

import io.supertokens.Main;
import io.supertokens.multitenancy.exception.BadPermissionException;
import io.supertokens.pluginInterface.RECIPE_ID;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.webserver.WebserverAPI;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class OAuthGetAuthLoginRequestAPI extends WebserverAPI {

    public OAuthGetAuthLoginRequestAPI(Main main) {
        super(main, RECIPE_ID.OAUTH.toString());
    }

    @Override
    public String getPath() {
        return "/recipe/oauth/auth/requests/login";
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {
        try {
            OAuthProxyHelper.proxyGET(
                main, req, resp,
                getAppIdentifier(req),
                enforcePublicTenantAndGetPublicTenantStorage(req),
                "/admin/oauth2/auth/requests/login", // proxyPath
                true, // proxyToAdmin
                true, // camelToSnakeCaseConversion
                OAuthProxyHelper.defaultGetQueryParamsFromRequest(req),
                new HashMap<>(), // headers
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
