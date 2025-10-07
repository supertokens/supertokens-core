package io.supertokens.webserver.api.saml;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;

import com.google.gson.JsonObject;

import io.supertokens.Main;
import io.supertokens.jwt.exceptions.UnsupportedJWTSigningAlgorithmException;
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
        String code = req.getParameter("code");
        if (code == null || code.isBlank()) {
            throw new ServletException(new BadRequestException("Missing form field: code"));
        }

        try {
            String token = SAML.getTokenForCode(
                    main,
                    getTenantIdentifier(req),
                    enforcePublicTenantAndGetPublicTenantStorage(req),
                    code
            );

            JsonObject res = new JsonObject();
            res.addProperty("status", "OK");
            res.addProperty("access_token", token);
            super.sendJsonResponse(200, res, resp);
        } catch (InvalidCodeException e) {
            JsonObject res = new JsonObject();
            res.addProperty("status", "INVALID_CODE_ERROR");
            super.sendJsonResponse(200, res, resp);
        } catch (TenantOrAppNotFoundException | StorageQueryException | UnsupportedJWTSigningAlgorithmException |
                 NoSuchAlgorithmException | StorageTransactionLogicException | InvalidKeySpecException |
                 BadPermissionException e) {
            throw new ServletException(e);
        }
    }
}
