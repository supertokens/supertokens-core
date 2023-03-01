package io.supertokens.webserver.api.totp;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;

import com.google.gson.JsonObject;

import io.supertokens.Main;
import io.supertokens.pluginInterface.RECIPE_ID;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.totp.TOTPDevice;
import io.supertokens.pluginInterface.totp.exception.DeviceAlreadyExistsException;
import io.supertokens.totp.Totp;
import io.supertokens.webserver.InputParser;
import io.supertokens.webserver.WebserverAPI;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class CreateTotpDeviceAPI extends WebserverAPI {
    private static final long serialVersionUID = -4641988458637882374L;

    public CreateTotpDeviceAPI(Main main) {
        super(main, RECIPE_ID.TOTP.toString());
    }

    @Override
    public String getPath() {
        return "/recipe/totp/device";
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {
        JsonObject input = InputParser.parseJsonObjectOrThrowError(req);

        String userId = null;
        String deviceName = null;
        Integer skew = null;
        Integer period = null;

        // TODO: Should we also allow the user to change the hashing algo and totp
        // length (6-8) since we are already allowing them to change the period and skew
        // which are advanced options anyways?

        if (input.has("userId")) {
            userId = InputParser.parseStringOrThrowError(input, "userId", false);
        }
        if (input.has("deviceName")) {
            deviceName = InputParser.parseStringOrThrowError(input, "deviceName", false);
        }
        if (input.has("skew")) {
            // FIXME: No function to parse integer:
            skew = InputParser.parseLongOrThrowError(input, "skew", false).intValue();
        }
        if (input.has("period")) {
            // FIXME: No function to parse integer:
            period = InputParser.parseLongOrThrowError(input, "period", false).intValue();
        }

        if (userId.isEmpty()) {
            throw new ServletException(new IllegalArgumentException("userId cannot be empty"));
        }
        if (deviceName.isEmpty()) {
            throw new ServletException(new IllegalArgumentException("deviceName cannot be empty"));
        }
        if (skew < 0) {
            throw new ServletException(new IllegalArgumentException("skew must be >= 0"));
        }
        if (period <= 0) {
            throw new ServletException(new IllegalArgumentException("period must be > 0"));
        }

        // Should we do these as well?
        // - Assert period <= 60. Otherwise, it is a security risk. > 30 is also bad.
        // - Assert skew <= 2. Otherwise, it is a security risk.

        JsonObject result = new JsonObject();

        try {
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

}
