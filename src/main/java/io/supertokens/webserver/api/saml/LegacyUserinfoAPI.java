package io.supertokens.webserver.api.saml;

import java.io.IOException;

import com.google.gson.JsonObject;

import io.supertokens.Main;
import io.supertokens.featureflag.exceptions.FeatureNotEnabledException;
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

    // Called by the relying party's OAuth client, which does not hold the core API key. The bearer access token
    // issued by the legacy token endpoint (after client authentication) is the credential.
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

        String accessToken = authorizationHeader.substring("Bearer ".length()).trim();

        // Only an access token issued by the legacy token endpoint is accepted (base64url, see
        // SAML.exchangeLegacyCodeForAccessToken). Anything else is rejected without a lookup.
        if (!isWellFormedAccessToken(accessToken)) {
            super.sendTextResponse(400, "INVALID_TOKEN_ERROR", resp);
            return;
        }

        try {
            JsonObject userInfo = SAML.getUserInfo(
                main, getAppIdentifier(req).getAsPublicTenantIdentifier(), enforcePublicTenantAndGetPublicTenantStorage(req), accessToken, null, true
            );
            super.sendJsonResponse(200, userInfo, resp);
        } catch (InvalidCodeException e) {
            super.sendTextResponse(400, "INVALID_TOKEN_ERROR", resp);

        } catch (StorageQueryException | TenantOrAppNotFoundException | BadPermissionException |
                 StorageTransactionLogicException | FeatureNotEnabledException e) {
            throw new ServletException(e);
        }
    }

    private static boolean isWellFormedAccessToken(String accessToken) {
        if (accessToken.isEmpty() || accessToken.length() > 128) {
            return false;
        }
        for (int i = 0; i < accessToken.length(); i++) {
            char c = accessToken.charAt(i);
            boolean ok = (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '-'
                    || c == '_';
            if (!ok) {
                return false;
            }
        }
        return true;
    }
}
