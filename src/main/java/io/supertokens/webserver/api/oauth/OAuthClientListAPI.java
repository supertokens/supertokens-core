package io.supertokens.webserver.api.oauth;

import java.io.IOException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import io.supertokens.Main;
import io.supertokens.multitenancy.exception.BadPermissionException;
import io.supertokens.oauth.HttpRequestForOAuthProvider;
import io.supertokens.oauth.OAuth;
import io.supertokens.pluginInterface.RECIPE_ID;
import io.supertokens.pluginInterface.Storage;
import io.supertokens.pluginInterface.exceptions.InvalidConfigException;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.multitenancy.AppIdentifier;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.pluginInterface.oauth.OAuthClient;
import io.supertokens.webserver.WebserverAPI;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;

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
            queryParams.put("owner", appIdentifier.getConnectionUriDomain() + "_" + appIdentifier.getAppId());

            HttpRequestForOAuthProvider.Response response = OAuthProxyHelper.proxyGET(
                main, req, resp,
                appIdentifier,
                storage,
                null, // clientIdToCheck
                "/admin/clients", // proxyPath
                true, // proxyToAdmin
                true, // camelToSnakeCaseConversion
                queryParams,
                new HashMap<>() // headers
            );

            if (response != null) {
                JsonObject finalResponse = new JsonObject();
                finalResponse.addProperty("status", "OK");

                // Filter out the clients for app
                Map<String, OAuthClient> clientsMap = new HashMap<>();
                try {
                    List<String> clientIds = new ArrayList<>();
                    for (JsonElement clientElem : response.jsonResponse.getAsJsonArray()) {
                        clientIds.add(clientElem.getAsJsonObject().get("clientId").getAsString());
                    }

                    List<OAuthClient> clients = OAuth.getClients(main, getAppIdentifier(req), enforcePublicTenantAndGetPublicTenantStorage(req), clientIds);
                    for (OAuthClient client : clients) {
                        clientsMap.put(client.clientId, client);
                    }
                } catch (StorageQueryException | TenantOrAppNotFoundException | BadPermissionException |
                         InvalidKeyException | NoSuchAlgorithmException | InvalidKeySpecException |
                         NoSuchPaddingException | InvalidAlgorithmParameterException | IllegalBlockSizeException |
                         BadPaddingException | InvalidConfigException e) {
                    throw new ServletException(e);
                }

                JsonArray clients = new JsonArray();
                
                for (JsonElement clientElem : response.jsonResponse.getAsJsonArray()) {
                    if (clientsMap.containsKey(clientElem.getAsJsonObject().get("clientId").getAsString())) {
                        clientElem.getAsJsonObject().addProperty("clientSecret", clientsMap.get(clientElem.getAsJsonObject().get("clientId").getAsString()).clientSecret);
                        clientElem.getAsJsonObject().addProperty("enableRefreshTokenRotation", clientsMap.get(clientElem.getAsJsonObject().get("clientId").getAsString()).enableRefreshTokenRotation);
                        clients.add(clientElem);
                    }
                }

                finalResponse.add("clients", clients);

                // pagination
                List<String> linkHeader = response.headers.get("Link");
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
                            finalResponse.addProperty("nextPaginationToken", pageToken);
                        }
                    }
                }

                super.sendJsonResponse(200, finalResponse, resp);
            }
        } catch (IOException | TenantOrAppNotFoundException | BadPermissionException e) {
            throw new ServletException(e);
        }
    }
}
