package io.supertokens.webserver.api.saml;

import java.io.IOException;
import java.util.Objects;

import com.google.gson.JsonObject;

import io.supertokens.Main;
import io.supertokens.multitenancy.exception.BadPermissionException;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.pluginInterface.saml.SAMLClient;
import io.supertokens.saml.SAML;
import io.supertokens.webserver.WebserverAPI;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class LegacyTokenAPI extends WebserverAPI {

    public LegacyTokenAPI(Main main) {
        super(main, "saml");
    }

    @Override
    public String getPath() {
        return "/recipe/saml/legacy/token";
    }

    @Override
    protected boolean checkAPIKey(HttpServletRequest req) {
        return false;
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {
        String clientId = req.getParameter("client_id");
        String clientSecret = req.getParameter("client_secret");
        String code = req.getParameter("code");

        if (clientId == null || clientId.isBlank()) {
            throw new ServletException(new BadRequestException("Missing form field: client_id"));
        }
        if (clientSecret == null || clientSecret.isBlank()) {
            throw new ServletException(new BadRequestException("Missing form field: client_secret"));
        }
        if (code == null || code.isBlank()) {
            throw new ServletException(new BadRequestException("Missing form field: code"));
        }

        try {
            SAMLClient client = SAML.getClient(
                getTenantIdentifier(req),
                enforcePublicTenantAndGetPublicTenantStorage(req),
                clientId
            );
            if (client == null) {
                throw new ServletException(new BadRequestException("Invalid client_id"));
            }
            if (!Objects.equals(client.clientSecret, clientSecret)) {
                throw new ServletException(new BadRequestException("Invalid client_secret"));
            }

            JsonObject res = new JsonObject();
            res.addProperty("status", "OK");
            res.addProperty("access_token", code + "." + clientId); // return code itself as access token
            super.sendJsonResponse(200, res, resp);
        } catch (TenantOrAppNotFoundException | StorageQueryException | BadPermissionException e) {
            throw new ServletException(e);
        }
    }
}
