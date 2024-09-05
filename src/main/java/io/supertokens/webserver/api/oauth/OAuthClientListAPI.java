package io.supertokens.webserver.api.oauth;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import io.supertokens.Main;
import io.supertokens.multitenancy.exception.BadPermissionException;
import io.supertokens.oauth.OAuth;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class OAuthClientListAPI extends OAuthProxyBase {

    public OAuthClientListAPI(Main main) {
        super(main);
    }
    
    @Override
    public String getPath() {
        return "/recipe/oauth/clients/list";
    }

    @Override
    public ProxyProps[] getProxyProperties(HttpServletRequest req, JsonObject input) {
        return new ProxyProps[] {
            new ProxyProps(
                "GET", // apiMethod
                "GET", // method
                "/admin/clients", // path
                true, // proxyToAdmin
                true // camelToSnakeCaseConversion
            )
        };
    }

    @Override
    protected void handleResponseFromProxyGET(HttpServletRequest req, HttpServletResponse resp, int statusCode,
            Map<String, List<String>> headers, String rawBody, JsonElement jsonBody)
            throws IOException, ServletException {

        JsonObject response = new JsonObject();
        response.addProperty("status", "OK");

        // Filter out the clients for app
        List<String> clientIds;
		try {
			clientIds = OAuth.listClientIds(main, getAppIdentifier(req), enforcePublicTenantAndGetPublicTenantStorage(req));
		} catch (StorageQueryException | TenantOrAppNotFoundException | BadPermissionException e) {
            throw new ServletException(e);
        }

        Set<String> clientIdsSet = new HashSet<>(clientIds);

        JsonArray clients = new JsonArray();
        
        for (JsonElement clientElem : jsonBody.getAsJsonArray()) {
            if (clientIdsSet.contains(clientElem.getAsJsonObject().get("clientId").getAsString())) {
                clients.add(clientElem);
            }
        }

        response.add("clients", clients);
        sendJsonResponse(200, response, resp);
    }
}
