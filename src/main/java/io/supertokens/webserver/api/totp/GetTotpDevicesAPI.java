package io.supertokens.webserver.api.totp;

import java.io.IOException;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import io.supertokens.Main;
import io.supertokens.pluginInterface.RECIPE_ID;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.totp.TOTPDevice;
import io.supertokens.pluginInterface.totp.exception.TotpNotEnabledException;
import io.supertokens.totp.Totp;
import io.supertokens.webserver.InputParser;
import io.supertokens.webserver.WebserverAPI;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class GetTotpDevicesAPI extends WebserverAPI {
    private static final long serialVersionUID = -4641988458637882374L;

    public GetTotpDevicesAPI(Main main) {
        super(main, RECIPE_ID.TOTP.toString());
    }

    @Override
    public String getPath() {
        return "/recipe/totp/device/list";
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {
        JsonObject input = InputParser.parseJsonObjectOrThrowError(req);

        String userId = InputParser.parseStringOrThrowError(input, "userId", false);

        if (userId.isEmpty()) {
            throw new ServletException(new BadRequestException("userId cannot be empty"));
        }

        JsonObject result = new JsonObject();

        try {
            TOTPDevice[] devices = Totp.getDevices(main, userId);
            JsonArray devicesArray = new JsonArray();

            for (TOTPDevice d : devices) {
                JsonObject item = new JsonObject();
                item.addProperty("name", d.deviceName);
                item.addProperty("period", d.period);
                item.addProperty("skew", d.skew);
                item.addProperty("verified", d.verified);

                devicesArray.add(item);
            }

            result.addProperty("status", "OK");
            result.add("devices", devicesArray);
            super.sendJsonResponse(200, result, resp);
        } catch (TotpNotEnabledException e) {
            result.addProperty("status", "TOTP_NOT_ENABLED_ERROR");
            super.sendJsonResponse(200, result, resp);
        } catch (StorageQueryException e) {
            throw new ServletException(e);
        }
    }
}
