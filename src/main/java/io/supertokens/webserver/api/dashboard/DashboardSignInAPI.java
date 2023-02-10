package io.supertokens.webserver.api.dashboard;

import java.io.IOException;

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

public class DashboardSignInAPI extends WebserverAPI {

    public DashboardSignInAPI(Main main) {
        super(main, RECIPE_ID.DASHBOARD.toString());
    }

    @Override
    public String getPath() {
        return "/recipe/dashboard/signin";
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {
        JsonObject input = InputParser.parseJsonObjectOrThrowError(req);
        String email = InputParser.parseStringOrThrowError(input, "email", false);

        // normalize email
        email = normalizeStringParam(email, "email");

        String password = InputParser.parseStringOrThrowError(input, "password", false);

        // normalize password
        password = normalizeStringParam(password, "password");

        try {
            String sessionId = Dashboard.signInDashboardUser(main, email, password);
            JsonObject response = new JsonObject();
           response.addProperty("status", "OK");
           response.addProperty("sessionId", sessionId);
        } catch (DashboardFeatureFlagException e) {
           JsonObject response = new JsonObject();
           response.addProperty("status", "USER_SUSPENDED_ERROR");
           response.addProperty("message", e.getMessage());
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
