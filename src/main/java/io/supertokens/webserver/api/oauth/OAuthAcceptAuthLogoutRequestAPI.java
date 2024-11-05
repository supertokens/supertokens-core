package io.supertokens.webserver.api.oauth;

import java.io.IOException;

import com.google.gson.JsonObject;

import io.supertokens.Main;
import io.supertokens.multitenancy.exception.BadPermissionException;
import io.supertokens.oauth.OAuth;
import io.supertokens.oauth.exceptions.OAuthAPIException;
import io.supertokens.pluginInterface.RECIPE_ID;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.webserver.InputParser;
import io.supertokens.webserver.WebserverAPI;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class OAuthAcceptAuthLogoutRequestAPI extends WebserverAPI {

    public OAuthAcceptAuthLogoutRequestAPI(Main main) {
        super(main, RECIPE_ID.OAUTH.toString());
    }

    @Override
    public String getPath() {
        return "/recipe/oauth/auth/requests/logout/accept";
    }

    @Override
    protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {
        JsonObject input = InputParser.parseJsonObjectOrThrowError(req);
        String challenge = InputParser.parseStringOrThrowError(input, "challenge", false);
        
        try {
            String redirectTo = OAuth.consumeLogoutChallengeAndGetRedirectUri(main, getAppIdentifier(req), enforcePublicTenantAndGetPublicTenantStorage(req), challenge);

            JsonObject response = new JsonObject();
            response.addProperty("status", "OK");
            response.addProperty("redirectTo", redirectTo);
            super.sendJsonResponse(200, response, resp);

        } catch (OAuthAPIException e) {
            OAuthProxyHelper.handleOAuthAPIException(resp, e);
        } catch (IOException | TenantOrAppNotFoundException | BadPermissionException | StorageQueryException e) {
            throw new ServletException(e);
        }
    }
}
