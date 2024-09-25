package io.supertokens.webserver.api.oauth;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import com.google.gson.JsonObject;

import io.supertokens.Main;
import io.supertokens.jwt.exceptions.UnsupportedJWTSigningAlgorithmException;
import io.supertokens.multitenancy.exception.BadPermissionException;
import io.supertokens.oauth.HttpRequestForOry;
import io.supertokens.oauth.OAuth;
import io.supertokens.oauth.exceptions.OAuthAPIException;
import io.supertokens.pluginInterface.RECIPE_ID;
import io.supertokens.pluginInterface.Storage;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.exceptions.StorageTransactionLogicException;
import io.supertokens.pluginInterface.multitenancy.AppIdentifier;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.webserver.WebserverAPI;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class OAuthLogoutAPI extends WebserverAPI {
    public OAuthLogoutAPI(Main main){
        super(main, RECIPE_ID.OAUTH.toString());
    }

    @Override
    public String getPath() {
        return "/recipe/oauth/sessions/logout";
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {
        try {
            AppIdentifier appIdentifier = getAppIdentifier(req);
            Storage storage = enforcePublicTenantAndGetPublicTenantStorage(req);

            Map<String, String> queryParams = OAuthProxyHelper.defaultGetQueryParamsFromRequest(req);
            OAuth.verifyIdTokenHintClientIdAndUpdateQueryParamsForLogout(main, appIdentifier, storage, queryParams);

            HttpRequestForOry.Response response = OAuthProxyHelper.proxyGET(
                main, req, resp,
                appIdentifier,
                storage,
                queryParams.get("clientId"), // clientIdToCheck
                "/oauth2/sessions/logout", // proxyPath
                false, // proxyToAdmin
                true, // camelToSnakeCaseConversion
                queryParams,
                new HashMap<>() // headers
            );

            if (response != null) {
                JsonObject finalResponse = new JsonObject();
                String redirectTo = response.headers.get("Location").get(0);

                finalResponse.addProperty("status", "OK");
                finalResponse.addProperty("redirectTo", redirectTo);

                super.sendJsonResponse(200, finalResponse, resp);
            }

        } catch (OAuthAPIException e) {
            OAuthProxyHelper.handleOAuthAPIException(resp, e);
        } catch (IOException | TenantOrAppNotFoundException | BadPermissionException | StorageQueryException | UnsupportedJWTSigningAlgorithmException | StorageTransactionLogicException e) {
            throw new ServletException(e);
        }
    }
}
