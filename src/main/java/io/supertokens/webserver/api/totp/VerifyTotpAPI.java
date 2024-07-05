package io.supertokens.webserver.api.totp;

import java.io.IOException;

import com.google.gson.JsonObject;

import io.supertokens.Main;
import io.supertokens.featureflag.exceptions.FeatureNotEnabledException;
import io.supertokens.pluginInterface.RECIPE_ID;
import io.supertokens.pluginInterface.Storage;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.exceptions.StorageTransactionLogicException;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.pluginInterface.totp.exception.UnknownTotpUserIdException;
import io.supertokens.totp.Totp;
import io.supertokens.totp.exceptions.InvalidTotpException;
import io.supertokens.totp.exceptions.LimitReachedException;
import io.supertokens.utils.SemVer;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifier;
import io.supertokens.webserver.InputParser;
import io.supertokens.webserver.WebserverAPI;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class VerifyTotpAPI extends WebserverAPI {
    private static final long serialVersionUID = -4641988458637882374L;

    public VerifyTotpAPI(Main main) {
        super(main, RECIPE_ID.TOTP.toString());
    }

    @Override
    public String getPath() {
        return "/recipe/totp/verify";
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {
        // API is tenant specific
        JsonObject input = InputParser.parseJsonObjectOrThrowError(req);

        String userId = InputParser.parseStringOrThrowError(input, "userId", false);
        String totp = InputParser.parseStringOrThrowError(input, "totp", false);

        if (userId.isEmpty()) {
            throw new ServletException(new BadRequestException("userId cannot be empty"));
        }

        JsonObject result = new JsonObject();

        try {
            TenantIdentifier tenantIdentifier = getTenantIdentifier(req);
            Storage storage = getTenantStorage(req);

            Totp.verifyCode(tenantIdentifier, storage, main, userId, totp);

            result.addProperty("status", "OK");
            super.sendJsonResponse(200, result, resp);
        } catch (InvalidTotpException e) {
            result.addProperty("status", "INVALID_TOTP_ERROR");
            if (getVersionFromRequest(req).greaterThanOrEqualTo(SemVer.v5_0)) {
                result.addProperty("currentNumberOfFailedAttempts", e.currentAttempts);
                result.addProperty("maxNumberOfFailedAttempts", e.maxAttempts);
            }
            super.sendJsonResponse(200, result, resp);
        } catch (UnknownTotpUserIdException e) {
            result.addProperty("status", "UNKNOWN_USER_ID_ERROR");
            super.sendJsonResponse(200, result, resp);
        } catch (LimitReachedException e) {
            result.addProperty("status", "LIMIT_REACHED_ERROR");
            result.addProperty("retryAfterMs", e.retryAfterMs);
            if (getVersionFromRequest(req).greaterThanOrEqualTo(SemVer.v5_0)) {
                result.addProperty("currentNumberOfFailedAttempts", e.currentAttempts);
                result.addProperty("maxNumberOfFailedAttempts", e.maxAttempts);
            }
            super.sendJsonResponse(200, result, resp);
        } catch (StorageQueryException | StorageTransactionLogicException | FeatureNotEnabledException |
                 TenantOrAppNotFoundException e) {
            throw new ServletException(e);
        }
    }
}
