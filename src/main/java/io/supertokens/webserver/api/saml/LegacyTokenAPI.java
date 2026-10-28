package io.supertokens.webserver.api.saml;

import java.io.IOException;

import com.google.gson.JsonObject;

import io.supertokens.Main;
import io.supertokens.featureflag.exceptions.FeatureNotEnabledException;
import io.supertokens.multitenancy.exception.BadPermissionException;
import io.supertokens.pluginInterface.Storage;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifier;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.pluginInterface.saml.SAMLClient;
import io.supertokens.saml.SAML;
import io.supertokens.saml.exceptions.InvalidCodeException;
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

    // Called by the relying party's OAuth client (for example the backend SDK's boxy-saml provider), which does
    // not hold the core API key. The client authenticates with its client_id / client_secret instead.
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
            TenantIdentifier tenantIdentifier = getTenantIdentifier(req);
            Storage storage = enforcePublicTenantAndGetPublicTenantStorage(req);
            SAMLClient client = SAML.getClient(tenantIdentifier, storage, clientId);
            if (client == null) {
                throw new ServletException(new BadRequestException("Invalid client_id"));
            }
            if (!SAML.isClientSecretValid(client, clientSecret)) {
                throw new ServletException(new BadRequestException("Invalid client_secret"));
            }

            // Redeem the code (single use, bound to this client) and issue a fresh access token for it.
            String accessToken;
            try {
                accessToken = SAML.exchangeLegacyCodeForAccessToken(main, tenantIdentifier, storage, clientId, code);
            } catch (InvalidCodeException e) {
                throw new ServletException(new BadRequestException("Invalid code"));
            }

            JsonObject res = new JsonObject();
            res.addProperty("status", "OK");
            res.addProperty("access_token", accessToken);
            super.sendJsonResponse(200, res, resp);
        } catch (TenantOrAppNotFoundException | StorageQueryException | BadPermissionException |
                 FeatureNotEnabledException e) {
            throw new ServletException(e);
        }
    }
}
