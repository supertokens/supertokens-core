package io.supertokens.webserver.api.dashboard;

import java.io.IOException;
import java.io.Serial;

import com.google.gson.JsonObject;

import io.supertokens.Main;
import io.supertokens.dashboard.Dashboard;
import io.supertokens.dashboard.exceptions.DashboardFeatureFlagException;
import io.supertokens.pluginInterface.RECIPE_ID;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.webserver.InputParser;
import io.supertokens.webserver.WebserverAPI;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class VerifyDashboardUserSessionAPI extends WebserverAPI {

    @Serial
    private static final long serialVersionUID = -3243992629116144574L;

    public VerifyDashboardUserSessionAPI(Main main) {
        super(main, RECIPE_ID.DASHBOARD.toString());
    }

    @Override
    public String getPath() {
        return "/recipe/dashboard/verify";
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {
        JsonObject input = InputParser.parseJsonObjectOrThrowError(req);

        String sessionId = InputParser.parseStringOrThrowError(input, "sessionId", false);

        sessionId = normalizeStringParam(sessionId, "sessionId");
        try {
            if (Dashboard.isValidUserSession(main, sessionId)) {
                JsonObject response = new JsonObject();
                response.addProperty("status", "OK");
                super.sendJsonResponse(200, response, resp);
            } else {
                JsonObject response = new JsonObject();
                response.addProperty("status", "INVALID_SESSION_ERROR");
                super.sendJsonResponse(200, response, resp);
            }

        } catch (DashboardFeatureFlagException e) {
            JsonObject response = new JsonObject();
            response.addProperty("status", "USER_SUSPENDED_ERROR");
            response.addProperty("message", e.getMessage());
            super.sendJsonResponse(200, response, resp);
        } catch (StorageQueryException e) {
            throw new ServletException(e);
        }

    }

    private static String normalizeStringParam(String param, String paramName) throws ServletException {
        param = param.trim();
        if (param.length() == 0) {
            throw new ServletException(
                    new WebserverAPI.BadRequestException("Field name " + paramName + " cannot be an empty String"));
        }
        return param;
    }

}
