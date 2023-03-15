package io.supertokens.webserver.api.totp;

import java.io.IOException;

import com.google.gson.JsonObject;

import io.supertokens.Main;
import io.supertokens.pluginInterface.RECIPE_ID;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.exceptions.StorageTransactionLogicException;
import io.supertokens.pluginInterface.totp.exception.TotpNotEnabledException;
import io.supertokens.totp.Totp;
import io.supertokens.totp.exceptions.InvalidTotpException;
import io.supertokens.totp.exceptions.LimitReachedException;
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
        JsonObject input = InputParser.parseJsonObjectOrThrowError(req);

        String userId = InputParser.parseStringOrThrowError(input, "userId", false);
        String totp = InputParser.parseStringOrThrowError(input, "totp", false);
        Boolean allowUnverifiedDevices = InputParser.parseBooleanOrThrowError(input, "allowUnverifiedDevices", false);

        if (userId.isEmpty()) {
            throw new ServletException(new BadRequestException("userId cannot be empty"));
        }
        if (totp.length() != 6) {
            throw new ServletException(new BadRequestException("totp must be 6 characters long"));
        }
        // Already checked that allowUnverifiedDevices is not null.

        JsonObject result = new JsonObject();

        try {
            Totp.verifyCode(main, userId, totp, allowUnverifiedDevices);

            result.addProperty("status", "OK");
            super.sendJsonResponse(200, result, resp);
        } catch (TotpNotEnabledException e) {
            result.addProperty("status", "TOTP_NOT_ENABLED_ERROR");
            super.sendJsonResponse(200, result, resp);
        } catch (InvalidTotpException e) {
            result.addProperty("status", "INVALID_TOTP_ERROR");
            super.sendJsonResponse(200, result, resp);
        } catch (LimitReachedException e) {
            result.addProperty("status", "LIMIT_REACHED_ERROR");
            result.addProperty("retryAfterSec", e.retryInSeconds);
            super.sendJsonResponse(200, result, resp);
        } catch (StorageQueryException | StorageTransactionLogicException e) {
            throw new ServletException(e);
        }
    }
}
