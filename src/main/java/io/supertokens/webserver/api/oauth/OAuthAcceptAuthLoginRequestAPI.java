package io.supertokens.webserver.api.oauth;

import java.io.IOException;
import java.util.HashMap;

import com.google.gson.JsonObject;

import io.supertokens.Main;
import io.supertokens.pluginInterface.RECIPE_ID;
import io.supertokens.webserver.WebserverAPI;
import io.supertokens.multitenancy.exception.BadPermissionException;
import io.supertokens.oauth.HttpRequestForOry;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.webserver.InputParser;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class OAuthAcceptAuthLoginRequestAPI extends WebserverAPI {

    public OAuthAcceptAuthLoginRequestAPI(Main main) {
        super(main, RECIPE_ID.OAUTH.toString());
    }

    @Override
    public String getPath() {
        return "/recipe/oauth/auth/requests/login/accept";
    }

    @Override
    protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {
        JsonObject input = InputParser.parseJsonObjectOrThrowError(req);

        try {
            HttpRequestForOry.Response response = OAuthProxyHelper.proxyJsonPUT(
                main, req, resp,
                getAppIdentifier(req),
                enforcePublicTenantAndGetPublicTenantStorage(req),
                null, // clientIdToCheck
                "/admin/oauth2/auth/requests/login/accept",
                true,
                true,
                OAuthProxyHelper.defaultGetQueryParamsFromRequest(req),
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
