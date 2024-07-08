package io.supertokens.webserver.api.totp;

import com.google.gson.JsonObject;
import io.supertokens.Main;
import io.supertokens.StorageAndUserIdMapping;
import io.supertokens.featureflag.exceptions.FeatureNotEnabledException;
import io.supertokens.multitenancy.exception.BadPermissionException;
import io.supertokens.pluginInterface.RECIPE_ID;
import io.supertokens.pluginInterface.Storage;
import io.supertokens.pluginInterface.emailpassword.exceptions.UnknownUserIdException;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.multitenancy.AppIdentifier;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.pluginInterface.totp.TOTPDevice;
import io.supertokens.pluginInterface.totp.exception.DeviceAlreadyExistsException;
import io.supertokens.pluginInterface.totp.exception.UnknownDeviceException;
import io.supertokens.totp.Totp;
import io.supertokens.useridmapping.UserIdType;
import io.supertokens.webserver.InputParser;
import io.supertokens.webserver.WebserverAPI;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;

public class CreateOrUpdateTotpDeviceAPI extends WebserverAPI {
    private static final long serialVersionUID = -4641988458637882374L;

    public CreateOrUpdateTotpDeviceAPI(Main main) {
        super(main, RECIPE_ID.TOTP.toString());
    }

    @Override
    public String getPath() {
        return "/recipe/totp/device";
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {
        // API is app specific
        JsonObject input = InputParser.parseJsonObjectOrThrowError(req);

        String userId = InputParser.parseStringOrThrowError(input, "userId", false);
        String deviceName = InputParser.parseStringOrThrowError(input, "deviceName", true);
        Integer skew = InputParser.parseIntOrThrowError(input, "skew", false);
        Integer period = InputParser.parseIntOrThrowError(input, "period", false);

        // Note: Not allowing the user to change the hashing algo and totp
        // length (6-8) at the moment because it's rare to change them

        if (userId.isEmpty()) {
            throw new ServletException(new BadRequestException("userId cannot be empty"));
        }
        if (deviceName != null && deviceName.isEmpty()) {
            // Only Null or valid device name are allowed
            throw new ServletException(new BadRequestException("deviceName cannot be empty"));
        }
        if (skew < 0) {
            throw new ServletException(new BadRequestException("skew must be >= 0"));
        }
        if (period <= 0) {
            throw new ServletException(new BadRequestException("period must be > 0"));
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

            TOTPDevice device = Totp.registerDevice(appIdentifier, storage, main, userId, deviceName, skew, period);

            result.addProperty("status", "OK");
            result.addProperty("deviceName", device.deviceName);
            result.addProperty("secret", device.secretKey);
            super.sendJsonResponse(200, result, resp);
        } catch (DeviceAlreadyExistsException e) {
            result.addProperty("status", "DEVICE_ALREADY_EXISTS_ERROR");
            super.sendJsonResponse(200, result, resp);
        } catch (StorageQueryException | NoSuchAlgorithmException | FeatureNotEnabledException |
                 TenantOrAppNotFoundException | BadPermissionException e) {
            throw new ServletException(e);
        }
    }

    @Override
    protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {
        // API is app specific
        JsonObject input = InputParser.parseJsonObjectOrThrowError(req);

        String userId = InputParser.parseStringOrThrowError(input, "userId", false);
        String existingDeviceName = InputParser.parseStringOrThrowError(input, "existingDeviceName", false);
        String newDeviceName = InputParser.parseStringOrThrowError(input, "newDeviceName", false);

        if (userId.isEmpty()) {
            throw new ServletException(new BadRequestException("userId cannot be empty"));
        }
        if (existingDeviceName.isEmpty()) {
            throw new ServletException(new BadRequestException("existingDeviceName cannot be empty"));
        }
        if (newDeviceName.isEmpty()) {
            throw new ServletException(new BadRequestException("newDeviceName cannot be empty"));
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

            Totp.updateDeviceName(appIdentifier, storage, userId, existingDeviceName, newDeviceName);

            result.addProperty("status", "OK");
            super.sendJsonResponse(200, result, resp);
        } catch (UnknownDeviceException e) {
            result.addProperty("status", "UNKNOWN_DEVICE_ERROR");
            super.sendJsonResponse(200, result, resp);
        } catch (DeviceAlreadyExistsException e) {
            result.addProperty("status", "DEVICE_ALREADY_EXISTS_ERROR");
            super.sendJsonResponse(200, result, resp);
        } catch (StorageQueryException | TenantOrAppNotFoundException | BadPermissionException e) {
            throw new ServletException(e);
        }
    }

}
