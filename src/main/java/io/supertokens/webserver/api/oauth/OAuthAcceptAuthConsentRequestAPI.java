package io.supertokens.webserver.api.oauth;

import java.io.IOException;
import java.util.HashMap;
import java.util.UUID;

import com.google.gson.JsonObject;

import io.supertokens.Main;
import io.supertokens.multitenancy.exception.BadPermissionException;
import io.supertokens.oauth.HttpRequestForOry;
import io.supertokens.pluginInterface.RECIPE_ID;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.webserver.InputParser;
import io.supertokens.webserver.WebserverAPI;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class OAuthAcceptAuthConsentRequestAPI extends WebserverAPI {

    public OAuthAcceptAuthConsentRequestAPI(Main main) {
        super(main, RECIPE_ID.OAUTH.toString());
    }

    @Override
    public String getPath() {
        return "/recipe/oauth/auth/requests/consent/accept";
    }

    @Override
    protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {
        JsonObject input = InputParser.parseJsonObjectOrThrowError(req);
        String iss = InputParser.parseStringOrThrowError(input, "iss", false);
        String tId = InputParser.parseStringOrThrowError(input, "tId", false);
        String rsub = InputParser.parseStringOrThrowError(input, "rsub", false);
        String sessionHandle = InputParser.parseStringOrThrowError(input, "sessionHandle", false);
        JsonObject initialAccessTokenPayload = InputParser.parseJsonObjectOrThrowError(input, "initialAccessTokenPayload", false);
        JsonObject initialIdTokenPayload = InputParser.parseJsonObjectOrThrowError(input, "initialIdTokenPayload", false);

        JsonObject accessToken = new JsonObject();
        accessToken.addProperty("iss", iss);
        accessToken.addProperty("tId", tId);
        accessToken.addProperty("rsub", rsub);
        accessToken.addProperty("sessionHandle", sessionHandle);
        accessToken.addProperty("gid", UUID.randomUUID().toString());
        accessToken.add("initialPayload", initialAccessTokenPayload);

        JsonObject idToken = new JsonObject();
        idToken.add("initialPayload", initialIdTokenPayload);

        // remove the above from input
        input.remove("iss");
        input.remove("tId");
        input.remove("rsub");
        input.remove("sessionHandle");
        input.remove("initialAccessTokenPayload");
        input.remove("initialIdTokenPayload");

        JsonObject session = new JsonObject();
        session.add("access_token", accessToken);
        session.add("id_token", idToken);
        input.add("session", session);

        try {
            HttpRequestForOry.Response response = OAuthProxyHelper.proxyJsonPUT(
                main, req, resp,
                getAppIdentifier(req),
                enforcePublicTenantAndGetPublicTenantStorage(req),
                null, // clientIdToCheck
                "/admin/oauth2/auth/requests/consent/accept", // proxyPath
                true, // proxyToAdmin
                true, // camelToSnakeCaseConversion
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
