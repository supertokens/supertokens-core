package io.supertokens.webserver.api.oauth;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import io.supertokens.Main;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class OAuthAcceptAuthLogoutRequestAPI extends OAuthProxyBase {

    public OAuthAcceptAuthLogoutRequestAPI(Main main) {
        super(main);
    }

    @Override
    public String getPath() {
        return "/recipe/oauth/auth/requests/logout/accept";
    }

    @Override
    public ProxyProps[] getProxyProperties(HttpServletRequest req, JsonObject input) {
        return new ProxyProps[] {
            new ProxyProps(
                "PUT", // apiMethod
                "PUT_JSON", // method
                "/admin/oauth2/auth/requests/logout/accept", // path
                true, // proxyToAdmin
                true // camelToSnakeCaseConversion
            )
        };
    }

    @Override
    protected void handleResponseFromProxyPUT(HttpServletRequest req, HttpServletResponse resp, JsonObject input, int statusCode, Map<String, List<String>> headers, String rawBody, JsonElement jsonBody) throws IOException, ServletException {
        JsonObject response = jsonBody.getAsJsonObject();
        response.addProperty("status", "OK");
        sendJsonResponse(200, response, resp);
    }
}
