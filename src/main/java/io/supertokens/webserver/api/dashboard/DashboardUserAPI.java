package io.supertokens.webserver.api.dashboard;

import java.io.IOException;
import java.io.Serial;

import com.google.gson.JsonObject;

import io.supertokens.Main;
import io.supertokens.dashboard.Dashboard;
import io.supertokens.pluginInterface.RECIPE_ID;
import io.supertokens.pluginInterface.dashboard.DashboardUser;
import io.supertokens.pluginInterface.emailpassword.exceptions.DuplicateEmailException;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.webserver.InputParser;
import io.supertokens.webserver.WebserverAPI;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class DashboardUserAPI extends WebserverAPI {

    @Serial
    private static final long serialVersionUID = -3243962619116144573L;

    public DashboardUserAPI(Main main) {
        super(main, RECIPE_ID.DASHBOARD.toString());
    }

    @Override
    public String getPath() {
        return "/recipe/dashboard/user";
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {

        try {
            if (!Dashboard.isDashboardFeatureFlagEnabled()) {
                // retrieve current dashboard users
                DashboardUser[] users = Dashboard.getAllDashboardUsers(main);
                // Prevent user creation if number of current dashboard users is above the free
                // limit
                if (users.length >= Dashboard.MAX_NUMBER_OF_FREE_DASHBOARD_USERS) {
                    JsonObject response = new JsonObject();
                    response.addProperty("status", "USER_LIMIT_REACHED");
                    // super.sendJsonResponse(200, result, resp);
                    super.sendJsonResponse(200, response, resp);
                }
            }

            JsonObject input = InputParser.parseJsonObjectOrThrowError(req);
            String email = InputParser.parseStringOrThrowError(input, "email", false);
            String password = InputParser.parseStringOrThrowError(input, "password", false);

            Dashboard.signUpDashboardUser(main, email, password);

        } catch (StorageQueryException e) {
            throw new ServletException(e);
        } catch (DuplicateEmailException e) {
            JsonObject response = new JsonObject();
            response.addProperty("status", "EMAIL_ALREADY_EXISTS_ERROR");
        }
    }

}
