package io.supertokens.webserver.api.oauth;

import java.io.IOException;
import java.util.HashMap;
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
import io.supertokens.pluginInterface.RECIPE_ID;
import io.supertokens.pluginInterface.Storage;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.multitenancy.AppIdentifier;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.webserver.WebserverAPI;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class OAuthClientListAPI extends WebserverAPI {

    public OAuthClientListAPI(Main main) {
        super(main, RECIPE_ID.OAUTH.toString());
    }
    
    @Override
    public String getPath() {
        return "/recipe/oauth/clients/list";
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {
        try {
            AppIdentifier appIdentifier = getAppIdentifier(req);
            Storage storage = enforcePublicTenantAndGetPublicTenantStorage(req);
            Map<String, String> queryParams = OAuthProxyHelper.defaultGetQueryParamsFromRequest(req);
            queryParams.put("owner", appIdentifier.getAppId());

            OAuthProxyHelper.proxyGET(
                main, req, resp,
                appIdentifier,
                storage,
                null, // clientIdToCheck
                "/admin/clients", // proxyPath
                true, // proxyToAdmin
                true, // camelToSnakeCaseConversion
                queryParams,
                new HashMap<>(), // headers
                (statusCode, headers, rawBody, jsonBody) -> { // getJsonResponse
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

                    // pagination
                    List<String> linkHeader = headers.get("Link");
                    if (linkHeader != null && !linkHeader.isEmpty()) {
                        for (String nextLink : linkHeader.get(0).split(",")) {
                            if (!nextLink.contains("rel=\"next\"")) {
                                continue;
                            }

                            String pageToken = null;
                            if (nextLink.contains("page_token=")) {
                                int startIndex = nextLink.indexOf("page_token=") + "page_token=".length();
                                int endIndex = nextLink.indexOf('>', startIndex);
                                if (endIndex != -1) {
                                    pageToken = nextLink.substring(startIndex, endIndex);
                                }
                            }
                            if (pageToken != null) {
                                response.addProperty("nextPaginationToken", pageToken);
                            }
                        }
                    }

                    return response;
                }
            );
        } catch (IOException | TenantOrAppNotFoundException | BadPermissionException e) {
            throw new ServletException(e);
        }
    }
}
