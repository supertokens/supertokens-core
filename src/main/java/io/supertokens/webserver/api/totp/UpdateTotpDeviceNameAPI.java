package io.supertokens.webserver.api.totp;

import java.io.IOException;

import com.google.gson.JsonObject;

import io.supertokens.Main;
import io.supertokens.pluginInterface.RECIPE_ID;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.totp.exception.DeviceAlreadyExistsException;
import io.supertokens.pluginInterface.totp.exception.TotpNotEnabledException;
import io.supertokens.pluginInterface.totp.exception.UnknownDeviceException;
import io.supertokens.totp.Totp;
import io.supertokens.webserver.InputParser;
import io.supertokens.webserver.WebserverAPI;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class UpdateTotpDeviceNameAPI extends WebserverAPI {
    private static final long serialVersionUID = -4641988458637882374L;

    public UpdateTotpDeviceNameAPI(Main main) {
        super(main, RECIPE_ID.TOTP.toString());
    }

    @Override
    public String getPath() {
        return "/recipe/totp/device";
    }

    @Override
    protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {
        JsonObject input = InputParser.parseJsonObjectOrThrowError(req);

        String userId = null;
        String existingDeviceName = null;
        String newDeviceName = null;

        if (input.has("userId")) {
            userId = InputParser.parseStringOrThrowError(input, "userId", false);
        }
        if (input.has("existingDeviceName")) {
            existingDeviceName = InputParser.parseStringOrThrowError(input, "existingDeviceName", false);
        }
        if (input.has("newDeviceName")) {
            newDeviceName = InputParser.parseStringOrThrowError(input, "newDeviceName", false);
        }

        if (userId.isEmpty()) {
            throw new ServletException(new IllegalArgumentException("userId cannot be empty"));
        }
        if (existingDeviceName.isEmpty()) {
            throw new ServletException(new IllegalArgumentException("existingDeviceName cannot be empty"));
        }
        if (newDeviceName.isEmpty()) {
            throw new ServletException(new IllegalArgumentException("newDeviceName cannot be empty"));
        }

        JsonObject result = new JsonObject();

        try {
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
