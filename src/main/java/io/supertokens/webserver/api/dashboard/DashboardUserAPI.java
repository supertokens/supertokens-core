package io.supertokens.webserver.api.dashboard;

import java.io.IOException;
import java.io.Serial;

import com.google.gson.JsonObject;

import io.supertokens.Main;
import io.supertokens.dashboard.Dashboard;
import io.supertokens.pluginInterface.RECIPE_ID;
import io.supertokens.pluginInterface.dashboard.DashboardUser;
import io.supertokens.pluginInterface.dashboard.exceptions.DuplicateEmailException;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.exceptions.StorageTransactionLogicException;
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
                    super.sendJsonResponse(200, response, resp);
                    return;
                }
            }

            JsonObject input = InputParser.parseJsonObjectOrThrowError(req);
            String email = InputParser.parseStringOrThrowError(input, "email", false);

            // normalize email
            String normalizedEmail = email.trim();
            if (normalizedEmail.length() == 0) {
                throw new ServletException(
                        new WebserverAPI.BadRequestException("Field name 'email' cannot be an empty String"));
            }

            // check if input email is invalid
            if (!Dashboard.isValidEmail(email)) {
                JsonObject response = new JsonObject();
                response.addProperty("status", "INVALID_EMAIL_ERROR");
                super.sendJsonResponse(200, response, resp);
                return;
            }

            String password = InputParser.parseStringOrThrowError(input, "password", false);

            // normalize password
            String normalizedPassword = password.trim();
            if (normalizedPassword.length() == 0) {
                throw new ServletException(
                        new WebserverAPI.BadRequestException("Field name 'password' cannot be an empty String"));
            }

            // check if input password is a strong password
            if (!Dashboard.isStrongPassword(normalizedPassword)) {
                JsonObject response = new JsonObject();
                response.addProperty("status", "PASSWORD_WEAK_ERROR");
                super.sendJsonResponse(200, response, resp);
                return;
            }

            Dashboard.signUpDashboardUser(main, email, password);

            JsonObject response = new JsonObject();
            response.addProperty("status", "OK");
            super.sendJsonResponse(200, response, resp);

        } catch (DuplicateEmailException e) {
            JsonObject response = new JsonObject();
            response.addProperty("status", "EMAIL_ALREADY_EXISTS_ERROR");
            super.sendJsonResponse(200, response, resp);
        } catch (StorageQueryException e) {
            throw new ServletException(e);
        }
    }

    @Override
    protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {
        JsonObject input = InputParser.parseJsonObjectOrThrowError(req);

        String newEmail = InputParser.parseStringOrThrowError(input, "newEmail", true);
        if (newEmail != null) {
            // normalize new email
            newEmail = newEmail.trim();
            if (newEmail.length() == 0) {
                throw new ServletException(
                        new WebserverAPI.BadRequestException("Field name 'newEmail' cannot be an empty String"));
            }

            // check if the newEmail is in valid format
            if (!Dashboard.isValidEmail(newEmail)) {
                JsonObject response = new JsonObject();
                response.addProperty("status", "INVALID_EMAIL_ERROR");
                super.sendJsonResponse(200, response, resp);
                return;
            }
        }

        String newPassword = InputParser.parseStringOrThrowError(input, "newPassword", true);
        if (newPassword != null) {
            // normalize new password
            newPassword = newPassword.trim();
            if (newPassword.length() == 0) {
                throw new ServletException(
                        new WebserverAPI.BadRequestException("Field name 'newPassword' cannot be an empty String"));
            }

            // check if the new password is strong
            if (Dashboard.isStrongPassword(newPassword)) {
                JsonObject response = new JsonObject();
                response.addProperty("status", "PASSWORD_WEAK_ERROR");
                super.sendJsonResponse(200, response, resp);
                return;
            }
        }

        try {
            String userId = InputParser.parseStringOrThrowError(input, "userId", true);
            if (userId != null) {
                // normalize userId
                userId = userId.trim();
                if (userId.length() == 0) {
                    throw new ServletException(
                            new WebserverAPI.BadRequestException("Field name 'userId' cannot be an empty String"));
                }

                Dashboard.updateUsersCredentialsWithUserId(main, userId, newEmail, newPassword);
                JsonObject response = new JsonObject();

                response.addProperty("status", "OK");
                super.sendJsonResponse(200, response, resp);
            }

            String email = InputParser.parseStringOrThrowError(input, "email", true);
            if (email != null) {
                // normalize userId
                email = email.trim();
                if (email.length() == 0) {
                    throw new ServletException(
                            new WebserverAPI.BadRequestException("Field name 'email' cannot be an empty String"));
                }
                Dashboard.updateUsersCredentialsWithEmail(main, email, newEmail, newPassword);

                JsonObject response = new JsonObject();
                response.addProperty("status", "OK");
                super.sendJsonResponse(200, response, resp);
            }
        } catch (DuplicateEmailException e) {
            JsonObject response = new JsonObject();
            response.addProperty("status", "EMAIL_ALREADY_EXISTS_ERROR");
            super.sendJsonResponse(200, response, resp);

        } catch (StorageQueryException | StorageTransactionLogicException e) {
            throw new ServletException(e);
        }

        JsonObject response = new JsonObject();
        response.addProperty("status", "OK");
        super.sendJsonResponse(200, response, resp);
    }

}
