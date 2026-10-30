package io.supertokens.webserver.api.oauth;

import java.io.IOException;
import java.util.HashMap;

import io.supertokens.Main;
import io.supertokens.multitenancy.exception.BadPermissionException;
import io.supertokens.oauth.HttpRequestForOAuthProvider;
import io.supertokens.oauth.OAuth;
import io.supertokens.oauth.Transformations;
import io.supertokens.pluginInterface.RECIPE_ID;
import io.supertokens.pluginInterface.Storage;
import io.supertokens.pluginInterface.StorageUtils;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.multitenancy.AppIdentifier;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.pluginInterface.oauth.exception.OAuthClientNotFoundException;
import io.supertokens.webserver.WebserverAPI;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class OAuthGetAuthConsentRequestAPI extends WebserverAPI {

    public OAuthGetAuthConsentRequestAPI(Main main) {
        super(main, RECIPE_ID.OAUTH.toString());
    }

    @Override
    public String getPath() {
        return "/recipe/oauth/auth/requests/consent";
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {
        try {
            AppIdentifier appIdentifier = getAppIdentifier(req);
            Storage storage = enforcePublicTenantAndGetPublicTenantStorage(req);
            HttpRequestForOAuthProvider.Response response = OAuthProxyHelper.proxyGET(
                main, req, resp,
                appIdentifier,
                storage,
                null, // clientIdToCheck (the client of the challenge is checked below)
                "/admin/oauth2/auth/requests/consent", // proxyPath
                true, // proxyToAdmin
                true, // camelToSnakeCaseConversion
                OAuthProxyHelper.defaultGetQueryParamsFromRequest(req),
                new HashMap<>() // headers
            );

            if (response != null) {
                // the challenge may belong to a client of another app that uses the same OAuth provider
                String clientId = OAuth.getChallengeClientId(response.jsonResponse);
                if (clientId == null) {
                    throw new OAuthClientNotFoundException();
                }
                StorageUtils.getOAuthStorage(storage).getOAuthClientById(appIdentifier, clientId);

                Transformations.applyClientPropsWhiteList(response.jsonResponse.getAsJsonObject().get("client").getAsJsonObject());

                response.jsonResponse.getAsJsonObject().addProperty("status", "OK");
                super.sendJsonResponse(200, response.jsonResponse, resp);
            }

        } catch (IOException | TenantOrAppNotFoundException | BadPermissionException | StorageQueryException e) {
            throw new ServletException(e);
        } catch (OAuthClientNotFoundException e) {
            // reported like an unknown challenge, so that challenges of other apps cannot be told apart
            OAuthProxyHelper.handleOAuthAPIException(resp, OAuth.newChallengeNotFoundException());
        }
    }
}
