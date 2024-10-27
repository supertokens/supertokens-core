package io.supertokens.webserver.api.oauth;

import java.io.IOException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.HashMap;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;

import io.supertokens.Main;
import io.supertokens.multitenancy.exception.BadPermissionException;
import io.supertokens.oauth.HttpRequestForOAuthProvider;
import io.supertokens.oauth.OAuth;
import io.supertokens.oauth.Transformations;
import io.supertokens.pluginInterface.RECIPE_ID;
import io.supertokens.pluginInterface.Storage;
import io.supertokens.pluginInterface.exceptions.InvalidConfigException;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.multitenancy.AppIdentifier;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.pluginInterface.oauth.OAuthClient;
import io.supertokens.pluginInterface.oauth.exception.OAuthClientNotFoundException;
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
            AppIdentifier appIdentifier = getAppIdentifier(req);
            Storage storage = enforcePublicTenantAndGetPublicTenantStorage(req);
            HttpRequestForOAuthProvider.Response response = OAuthProxyHelper.proxyGET(
                    main, req, resp,
                    appIdentifier,
                    storage,
                    null, // clientIdToCheck
                    "/admin/oauth2/auth/requests/login", // proxyPath
                    true, // proxyToAdmin
                    true, // camelToSnakeCaseConversion
                    OAuthProxyHelper.defaultGetQueryParamsFromRequest(req),
                    new HashMap<>() // headers
            );

            if (response != null) {
                Transformations.applyClientPropsWhiteList(
                        response.jsonResponse.getAsJsonObject().get("client").getAsJsonObject());

                String clientId = response.jsonResponse.getAsJsonObject().get("client").getAsJsonObject()
                        .get("clientId").getAsString();
                OAuthClient client = OAuth.getOAuthClientById(main, appIdentifier, storage, clientId);

                response.jsonResponse.getAsJsonObject().get("client").getAsJsonObject()
                        .addProperty("enableRefreshTokenRotation", client.enableRefreshTokenRotation);
                response.jsonResponse.getAsJsonObject().get("client").getAsJsonObject().addProperty("clientSecret",
                        client.clientSecret);

                response.jsonResponse.getAsJsonObject().addProperty("status", "OK");
                super.sendJsonResponse(200, response.jsonResponse, resp);
            }

        } catch (IOException | TenantOrAppNotFoundException | BadPermissionException
                | InvalidKeyException | NoSuchAlgorithmException | InvalidKeySpecException | NoSuchPaddingException
                | InvalidAlgorithmParameterException | IllegalBlockSizeException | BadPaddingException
                | StorageQueryException | InvalidConfigException e) {
            throw new ServletException(e);
        } catch (OAuthClientNotFoundException e) {
            OAuthProxyHelper.handleOAuthClientNotFoundException(resp);
        }
    }
}
