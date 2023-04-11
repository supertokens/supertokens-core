package io.supertokens.webserver.api.totp;

import java.io.IOException;

import com.google.gson.JsonObject;

import io.supertokens.Main;
import io.supertokens.TenantIdentifierWithStorageAndUserIdMapping;
import io.supertokens.pluginInterface.RECIPE_ID;
import io.supertokens.pluginInterface.emailpassword.exceptions.UnknownUserIdException;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.exceptions.StorageTransactionLogicException;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifierWithStorage;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.pluginInterface.totp.exception.TotpNotEnabledException;
import io.supertokens.pluginInterface.totp.exception.UnknownDeviceException;
import io.supertokens.pluginInterface.useridmapping.UserIdMapping;
import io.supertokens.totp.Totp;
import io.supertokens.totp.exceptions.InvalidTotpException;
import io.supertokens.totp.exceptions.LimitReachedException;
import io.supertokens.useridmapping.UserIdType;
import io.supertokens.webserver.InputParser;
import io.supertokens.webserver.WebserverAPI;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class VerifyTotpDeviceAPI extends WebserverAPI {
    private static final long serialVersionUID = -4641988458637882374L;

    public VerifyTotpDeviceAPI(Main main) {
        super(main, RECIPE_ID.TOTP.toString());
    }

    @Override
    public String getPath() {
        return "/recipe/totp/device/verify";
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {
        // API is tenant specific
        JsonObject input = InputParser.parseJsonObjectOrThrowError(req);

        String userId = InputParser.parseStringOrThrowError(input, "userId", false);
        String deviceName = InputParser.parseStringOrThrowError(input, "deviceName", false);
        String totp = InputParser.parseStringOrThrowError(input, "totp", false);

        if (userId.isEmpty()) {
            throw new ServletException(new BadRequestException("userId cannot be empty"));
        }
        if (deviceName.isEmpty()) {
            throw new ServletException(new BadRequestException("deviceName cannot be empty"));
        }
        if (totp.length() != 6) {
            throw new ServletException(new BadRequestException("totp must be 6 characters long"));
        }

        JsonObject result = new JsonObject();

        try {
            TenantIdentifierWithStorage tenantIdentifierWithStorage;
            try {
                // This step is required only because user_last_active table stores supertokens internal user id.
                // While sending the usage stats we do a join, so totp tables also must use internal user id.

                TenantIdentifierWithStorageAndUserIdMapping mappingAndStorage = getTenantIdentifierWithStorageAndUserIdMappingFromRequest(
                        req, userId, UserIdType.ANY);

                if (mappingAndStorage.userIdMapping != null) {
                    userId = mappingAndStorage.userIdMapping.superTokensUserId;
                }
                tenantIdentifierWithStorage = mappingAndStorage.tenantIdentifierWithStorage;
            } catch (UnknownUserIdException e) {
                // if the user is not found, just use the storage of the tenant of interest
                tenantIdentifierWithStorage = getTenantIdentifierWithStorageFromRequest(req);
            }
            boolean isNewlyVerified = Totp.verifyDevice(tenantIdentifierWithStorage, main, userId, deviceName, totp);

            result.addProperty("status", "OK");
            result.addProperty("wasAlreadyVerified", !isNewlyVerified);
            super.sendJsonResponse(200, result, resp);
        } catch (TotpNotEnabledException e) {
            result.addProperty("status", "TOTP_NOT_ENABLED_ERROR");
            super.sendJsonResponse(200, result, resp);
        } catch (UnknownDeviceException e) {
            result.addProperty("status", "UNKNOWN_DEVICE_ERROR");
            super.sendJsonResponse(200, result, resp);
        } catch (InvalidTotpException e) {
            result.addProperty("status", "INVALID_TOTP_ERROR");
            super.sendJsonResponse(200, result, resp);
        } catch (LimitReachedException e) {
            result.addProperty("status", "LIMIT_REACHED_ERROR");
            result.addProperty("retryAfterMs", e.retryAfterMs);
            super.sendJsonResponse(200, result, resp);
        } catch (StorageQueryException | StorageTransactionLogicException | TenantOrAppNotFoundException e) {
            throw new ServletException(e);
        }
    }
}
