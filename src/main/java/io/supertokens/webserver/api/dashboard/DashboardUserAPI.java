package io.supertokens.webserver.api.dashboard;

import java.io.IOException;
import java.io.Serial;

import com.google.gson.JsonObject;

import io.supertokens.Main;
import io.supertokens.dashboard.Dashboard;
import io.supertokens.pluginInterface.RECIPE_ID;
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

            if (!Dashboard.isFeatureFlagEnabledOrUserCountIsUnderThreshold(main)) {
                JsonObject response = new JsonObject();
                response.addProperty("status", "USER_LIMIT_REACHED");
                super.sendJsonResponse(200, response, resp);
                return;
            }

            JsonObject input = InputParser.parseJsonObjectOrThrowError(req);
            String email = InputParser.parseStringOrThrowError(input, "email", false);

            // normalize email
            email = normalizeStringParam(email, "email");

            // check if input email is invalid
            if (!Dashboard.isValidEmail(email)) {
                JsonObject response = new JsonObject();
                response.addProperty("status", "INVALID_EMAIL_ERROR");
                super.sendJsonResponse(200, response, resp);
                return;
            }

            String password = InputParser.parseStringOrThrowError(input, "password", false);

            // normalize password
            password = normalizeStringParam(password, "password");

            // check if input password is a strong password
            if (!Dashboard.isStrongPassword(password)) {
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
            newEmail = normalizeStringParam(newEmail, "newEmail");

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
            newPassword = normalizeStringParam(newPassword, "newPassword");
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
                userId = normalizeStringParam(userId, "userId");

                Dashboard.updateUsersCredentialsWithUserId(main, userId, newEmail, newPassword);
                JsonObject response = new JsonObject();

                response.addProperty("status", "OK");
                super.sendJsonResponse(200, response, resp);
            }

            String email = InputParser.parseStringOrThrowError(input, "email", true);
            if (email != null) {
                // normalize userId
                email = normalizeStringParam(email, "email");
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

    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {
        JsonObject input = InputParser.parseJsonObjectOrThrowError(req);

        String userId = InputParser.parseStringOrThrowError(input, "userId", true);
        try {
            if(userId != null){
                // normalize userId
                userId = normalizeStringParam(userId, "userId");
                boolean didUserExist = Dashboard.deleteUserWithUserId(main, userId);
                JsonObject response = new JsonObject();
                response.addProperty("status", "OK");
                response.addProperty("didUserExist", didUserExist);
                super.sendJsonResponse(200, response, resp);
                return;
            }

            String email = InputParser.parseStringOrThrowError(input, "email", true);

            if(email != null){
                // normalize email
                email = normalizeStringParam(email, "email");
                boolean didUserExist = Dashboard.deleteUserWithEmail(main, email);
                JsonObject response = new JsonObject();
                response.addProperty("status", "OK");
                response.addProperty("didUserExist", didUserExist);
                super.sendJsonResponse(200, response, resp);
                return;
            }
            
        } catch (StorageQueryException e) {
            throw new ServletException(e);
        }

        JsonObject response = new JsonObject();
        response.addProperty("status", "OK");
        response.addProperty("didUserExist", false);
        super.sendJsonResponse(200, response, resp);
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
