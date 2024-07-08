package io.supertokens.webserver.api.totp;

import com.google.gson.JsonObject;
import io.supertokens.Main;
import io.supertokens.StorageAndUserIdMapping;
import io.supertokens.multitenancy.exception.BadPermissionException;
import io.supertokens.pluginInterface.RECIPE_ID;
import io.supertokens.pluginInterface.Storage;
import io.supertokens.pluginInterface.emailpassword.exceptions.UnknownUserIdException;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.exceptions.StorageTransactionLogicException;
import io.supertokens.pluginInterface.multitenancy.AppIdentifier;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.pluginInterface.totp.exception.UnknownDeviceException;
import io.supertokens.totp.Totp;
import io.supertokens.useridmapping.UserIdType;
import io.supertokens.webserver.InputParser;
import io.supertokens.webserver.WebserverAPI;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

public class RemoveTotpDeviceAPI extends WebserverAPI {
    private static final long serialVersionUID = -4641988458637882374L;

    public RemoveTotpDeviceAPI(Main main) {
        super(main, RECIPE_ID.TOTP.toString());
    }

    @Override
    public String getPath() {
        return "/recipe/totp/device/remove";
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {
        // API is app specific
        JsonObject input = InputParser.parseJsonObjectOrThrowError(req);

        String userId = InputParser.parseStringOrThrowError(input, "userId", false);
        String deviceName = InputParser.parseStringOrThrowError(input, "deviceName", false);

        if (userId.isEmpty()) {
            throw new ServletException(new BadRequestException("userId cannot be empty"));
        }
        if (deviceName.isEmpty()) {
            throw new ServletException(new BadRequestException("deviceName cannot be empty"));
        }

        JsonObject result = new JsonObject();

        try {
            AppIdentifier appIdentifier = getAppIdentifier(req);
            Storage storage;
            try {
                // This step is required only because user_last_active table stores supertokens internal user id.
                // While sending the usage stats we do a join, so totp tables also must use internal user id.

                // Try to find the appIdentifier with right storage based on the userId
                StorageAndUserIdMapping storageAndUserIdMapping =
                        enforcePublicTenantAndGetStorageAndUserIdMappingForAppSpecificApi(
                                req, userId, UserIdType.ANY, false);
                storage = storageAndUserIdMapping.storage;
            } catch (UnknownUserIdException e) {
                throw new IllegalStateException("should never happen");
            }

            Totp.removeDevice(appIdentifier, storage, userId, deviceName);

            result.addProperty("status", "OK");
            result.addProperty("didDeviceExist", true);
            super.sendJsonResponse(200, result, resp);
        } catch (UnknownDeviceException e) {
            result.addProperty("status", "OK");
            result.addProperty("didDeviceExist", false);
            super.sendJsonResponse(200, result, resp);
        } catch (StorageQueryException | StorageTransactionLogicException | TenantOrAppNotFoundException |
                 BadPermissionException e) {
            throw new ServletException(e);
        }
    }
}
