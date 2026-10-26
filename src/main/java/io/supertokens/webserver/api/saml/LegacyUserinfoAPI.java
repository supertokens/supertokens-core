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

        // The legacy bearer token is "<code>.<clientId>". The code is always a dot-free UUID
        // (SAML#handleCallback mints it with UUID.randomUUID()), but the clientId is caller-supplied via
        // CreateOrUpdateSamlClientAPI and may itself contain dots. So split on the FIRST dot
        // only (limit 2): everything after it is the clientId, verbatim. This preserves dotted
        // clientIds that the previous split-on-every-dot parse silently truncated. The trailing
        // empty segment is kept by limit 2 as well, so require both leading parts to be non-empty.
        // The previous `contains(".")` guard was insufficient: a value like "x." still contains a
        // dot but splits to a single element, so accessing [1] threw an ArrayIndexOutOfBoundsException
        // that surfaced as an unauthenticated 500. Reject any malformed token with the same 400.
        String[] tokenParts = accessToken.split("[.]", 2);
        if (tokenParts.length < 2 || tokenParts[0].isEmpty() || tokenParts[1].isEmpty()) {
            super.sendTextResponse(400, "INVALID_TOKEN_ERROR", resp);
            return;
        }

        String clientId = tokenParts[1];
        accessToken = tokenParts[0];
        try {
            JsonObject userInfo = SAML.getUserInfo(
                main, getAppIdentifier(req).getAsPublicTenantIdentifier(), enforcePublicTenantAndGetPublicTenantStorage(req), accessToken, clientId, true
            );
            super.sendJsonResponse(200, userInfo, resp);
        } catch (InvalidCodeException e) {
            super.sendTextResponse(400, "INVALID_TOKEN_ERROR", resp);

        } catch (StorageQueryException | TenantOrAppNotFoundException | BadPermissionException |
                 StorageTransactionLogicException | FeatureNotEnabledException e) {
            throw new ServletException(e);
        }
    }
}
