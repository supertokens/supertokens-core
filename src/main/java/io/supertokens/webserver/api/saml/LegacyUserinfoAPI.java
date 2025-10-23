package io.supertokens.webserver.api.saml;

import java.io.IOException;

import com.google.gson.JsonObject;

import io.supertokens.Main;
import io.supertokens.multitenancy.exception.BadPermissionException;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.exceptions.StorageTransactionLogicException;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.saml.SAML;
import io.supertokens.saml.exceptions.InvalidCodeException;
import io.supertokens.webserver.WebserverAPI;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class LegacyUserinfoAPI extends WebserverAPI {
    public LegacyUserinfoAPI(Main main) {
        super(main, "saml");
    }

    @Override
    public String getPath() {
        return "/recipe/saml/legacy/userinfo";
    }

    @Override
    protected boolean checkAPIKey(HttpServletRequest req) {
        return false;
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {
        String authorizationHeader = req.getHeader("Authorization");
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            throw new ServletException(new BadRequestException("Authorization header is required"));
        }

        String accessToken = authorizationHeader.substring("Bearer ".length());

        if (!accessToken.contains(".")) {
            super.sendTextResponse(400, "INVALID_TOKEN_ERROR", resp);
            return;
        }

        String clientId = accessToken.split("[.]")[1];
        accessToken = accessToken.split("[.]")[0];
        try {
            JsonObject userInfo = SAML.getUserInfo(
                main, getAppIdentifier(req).getAsPublicTenantIdentifier(), enforcePublicTenantAndGetPublicTenantStorage(req), accessToken, clientId, true
            );
            super.sendJsonResponse(200, userInfo, resp);
        } catch (InvalidCodeException e) {
            super.sendTextResponse(400, "INVALID_TOKEN_ERROR", resp);

        } catch (StorageQueryException | TenantOrAppNotFoundException | BadPermissionException |
                 StorageTransactionLogicException e) {
            throw new ServletException(e);
        }
    }
}
