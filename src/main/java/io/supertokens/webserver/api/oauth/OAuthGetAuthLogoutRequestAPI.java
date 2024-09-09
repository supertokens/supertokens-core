package io.supertokens.webserver.api.oauth;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import com.google.gson.JsonObject;

import io.supertokens.Main;
import io.supertokens.multitenancy.exception.BadPermissionException;
import io.supertokens.pluginInterface.RECIPE_ID;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.webserver.WebserverAPI;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class OAuthGetAuthLogoutRequestAPI extends WebserverAPI {

    public OAuthGetAuthLogoutRequestAPI(Main main) {
        super(main, RECIPE_ID.OAUTH.toString());
    }

    @Override
    public String getPath() {
        return "/recipe/oauth/auth/requests/logout";
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {
        try {
            OAuthProxyHelper.proxyGET(
                main, req, resp,
                getAppIdentifier(req),
                enforcePublicTenantAndGetPublicTenantStorage(req),
                "/admin/oauth2/auth/requests/logout", // proxyPath
                true, // proxyToAdmin
                true, // camelToSnakeCaseConversion
                () -> OAuthProxyHelper.defaultGetQueryParamsFromRequest(req),
                HashMap::new, // getHeadersForProxy
                (statusCode, headers, rawBody, jsonBody) -> { // handleResponse
                    JsonObject response = jsonBody.getAsJsonObject();
                    response.addProperty("status", "OK");
                    sendJsonResponse(200, response, resp);
                }
            );

        } catch (IOException | TenantOrAppNotFoundException | BadPermissionException e) {
            throw new ServletException(e);
        }
    }
}
