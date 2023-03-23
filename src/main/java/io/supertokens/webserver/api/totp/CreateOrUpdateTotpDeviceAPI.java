package io.supertokens.webserver.api.totp;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;

import com.google.gson.JsonObject;
import io.supertokens.Main;
import io.supertokens.pluginInterface.RECIPE_ID;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.totp.TOTPDevice;
import io.supertokens.pluginInterface.totp.exception.DeviceAlreadyExistsException;
import io.supertokens.pluginInterface.totp.exception.TotpNotEnabledException;
import io.supertokens.pluginInterface.totp.exception.UnknownDeviceException;
import io.supertokens.pluginInterface.useridmapping.UserIdMapping;
import io.supertokens.totp.Totp;
import io.supertokens.useridmapping.UserIdType;
import io.supertokens.webserver.InputParser;
import io.supertokens.webserver.WebserverAPI;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

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
        JsonObject input = InputParser.parseJsonObjectOrThrowError(req);

        String userId = InputParser.parseStringOrThrowError(input, "userId", false);
        String deviceName = InputParser.parseStringOrThrowError(input, "deviceName", false);
        Integer skew = InputParser.parseIntOrThrowError(input, "skew", false);
        Integer period = InputParser.parseIntOrThrowError(input, "period", false);

        // Note: Not allowing the user to change the hashing algo and totp
        // length (6-8) at the moment because it's rare to change them

        if (userId.isEmpty()) {
            throw new ServletException(new BadRequestException("userId cannot be empty"));
        }
        if (deviceName.isEmpty()) {
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
            UserIdMapping userIdMapping = io.supertokens.useridmapping.UserIdMapping.getUserIdMapping(super.main, userId, UserIdType.ANY);
            if (userIdMapping != null) {
                userId = userIdMapping.superTokensUserId;
            }

            TOTPDevice device = Totp.registerDevice(main, userId, deviceName, skew, period);

            result.addProperty("status", "OK");
            result.addProperty("secret", device.secretKey);
            super.sendJsonResponse(200, result, resp);
        } catch (DeviceAlreadyExistsException e) {
            result.addProperty("status", "DEVICE_ALREADY_EXISTS_ERROR");
            super.sendJsonResponse(200, result, resp);
        } catch (StorageQueryException | NoSuchAlgorithmException e) {
            throw new ServletException(e);
        }
    }

    @Override
    protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {
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
            UserIdMapping userIdMapping = io.supertokens.useridmapping.UserIdMapping.getUserIdMapping(super.main, userId, UserIdType.ANY);
            if (userIdMapping != null) {
                userId = userIdMapping.superTokensUserId;
            }

            Totp.updateDeviceName(main, userId, existingDeviceName, newDeviceName);

            result.addProperty("status", "OK");
            super.sendJsonResponse(200, result, resp);
        } catch (TotpNotEnabledException e) {
            result.addProperty("status", "TOTP_NOT_ENABLED_ERROR");
            super.sendJsonResponse(200, result, resp);
        } catch (UnknownDeviceException e) {
            result.addProperty("status", "UNKNOWN_DEVICE_ERROR");
            super.sendJsonResponse(200, result, resp);
        } catch (DeviceAlreadyExistsException e) {
            result.addProperty("status", "DEVICE_ALREADY_EXISTS_ERROR");
            super.sendJsonResponse(200, result, resp);
        } catch (StorageQueryException e) {
            throw new ServletException(e);
        }
    }

}
