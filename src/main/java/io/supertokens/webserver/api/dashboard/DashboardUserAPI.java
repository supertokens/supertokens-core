/*
 *    Copyright (c) 2023, VRAI Labs and/or its affiliates. All rights reserved.
 *
 *    This software is licensed under the Apache License, Version 2.0 (the
 *    "License") as published by the Apache Software Foundation.
 *
 *    You may not use this file except in compliance with the License. You may
 *    obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 *    WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 *    License for the specific language governing permissions and limitations
 *    under the License.
 */

package io.supertokens.webserver.api.dashboard;

import java.io.IOException;
import java.io.Serial;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import io.supertokens.Main;
import io.supertokens.dashboard.Dashboard;
import io.supertokens.featureflag.exceptions.FeatureNotEnabledException;
import io.supertokens.pluginInterface.RECIPE_ID;
import io.supertokens.pluginInterface.dashboard.DashboardUser;
import io.supertokens.pluginInterface.dashboard.exceptions.DuplicateEmailException;
import io.supertokens.pluginInterface.dashboard.exceptions.UserIdNotFoundException;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.exceptions.StorageTransactionLogicException;
import io.supertokens.webserver.InputParser;
import io.supertokens.webserver.Utils;
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

            JsonObject input = InputParser.parseJsonObjectOrThrowError(req);
            String email = InputParser.parseStringOrThrowError(input, "email", false);

            // normalize email
            email = Utils.normalizeAndValidateStringParam(email, "email");

            // check if input email is invalid
            if (!Dashboard.isValidEmail(email)) {
                JsonObject response = new JsonObject();
                response.addProperty("status", "INVALID_EMAIL_ERROR");
                super.sendJsonResponse(200, response, resp);
                return;
            }

            String password = InputParser.parseStringOrThrowError(input, "password", false);

            // normalize password
            password = Utils.normalizeAndValidateStringParam(password, "password");

            // check if input password is a strong password
            String passwordErrorMessage = Dashboard.validatePassword(password);
            if (passwordErrorMessage != null) {
                JsonObject response = new JsonObject();
                response.addProperty("status", "PASSWORD_WEAK_ERROR");
                response.addProperty("message", passwordErrorMessage);
                super.sendJsonResponse(200, response, resp);
                return;
            }

            DashboardUser user = Dashboard.signUpDashboardUser(main, email, password);
            JsonObject userAsJsonObject = new JsonParser().parse(new Gson().toJson(user)).getAsJsonObject();

            JsonObject response = new JsonObject();
            response.addProperty("status", "OK");
            response.add("user", userAsJsonObject);
            super.sendJsonResponse(200, response, resp);

        } catch (DuplicateEmailException e) {
            JsonObject response = new JsonObject();
            response.addProperty("status", "EMAIL_ALREADY_EXISTS_ERROR");
            super.sendJsonResponse(200, response, resp);
        } catch (StorageQueryException | FeatureNotEnabledException e) {
            throw new ServletException(e);
        }
    }

    @Override
    protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {
        JsonObject input = InputParser.parseJsonObjectOrThrowError(req);

        String newEmail = InputParser.parseStringOrThrowError(input, "newEmail", true);
        if (newEmail != null) {
            // normalize new email
            newEmail = Utils.normalizeAndValidateStringParam(newEmail, "newEmail");

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
            newPassword = Utils.normalizeAndValidateStringParam(newPassword, "newPassword");
            // check if the new password is strong
            String passwordErrorMessage = Dashboard.validatePassword(newPassword);
            if (passwordErrorMessage != null) {
                JsonObject response = new JsonObject();
                response.addProperty("status", "PASSWORD_WEAK_ERROR");
                response.addProperty("message", passwordErrorMessage);
                super.sendJsonResponse(200, response, resp);
                return;
            }
        }

        try {
            String userId = InputParser.parseStringOrThrowError(input, "userId", true);
            if (userId != null) {
                // normalize userId
                userId = Utils.normalizeAndValidateStringParam(userId, "userId");

                // retrieve updated user details
                DashboardUser user = Dashboard.updateUsersCredentialsWithUserId(main, userId, newEmail, newPassword);
                JsonObject userJsonObject = new JsonParser().parse(new Gson().toJson(user))
                        .getAsJsonObject();
                JsonObject response = new JsonObject();
                response.addProperty("status", "OK");
                response.add("user", userJsonObject);
                super.sendJsonResponse(200, response, resp);
                return;
            }

            String email = InputParser.parseStringOrThrowError(input, "email", true);
            if (email != null) {
                // normalize email
                email = Utils.normalizeAndValidateStringParam(email, "email");

                // check if the user exists
                DashboardUser user = Dashboard.getDashboardUserByEmail(main, email);
                if (user == null) {
                    throw new UserIdNotFoundException();
                }

                // retrieve updated user details
                DashboardUser updatedUser = Dashboard.updateUsersCredentialsWithUserId(main, user.userId, newEmail,
                        newPassword);
                JsonObject userJsonObject = new JsonParser().parse(new Gson().toJson(updatedUser)).getAsJsonObject();
                JsonObject response = new JsonObject();
                response.addProperty("status", "OK");
                response.add("user", userJsonObject);
                super.sendJsonResponse(200, response, resp);
                return;
            }
        } catch (DuplicateEmailException e) {
            JsonObject response = new JsonObject();
            response.addProperty("status", "EMAIL_ALREADY_EXISTS_ERROR");
            super.sendJsonResponse(200, response, resp);
            return;

        } catch (UserIdNotFoundException e) {
            JsonObject response = new JsonObject();
            response.addProperty("status", "UNKNOWN_USER_ERROR");
            super.sendJsonResponse(200, response, resp);
            return;
        } catch (StorageQueryException | StorageTransactionLogicException e) {
            throw new ServletException(e);
        }
        // Both email and userId are null
        throw new ServletException(
                new WebserverAPI.BadRequestException("Either field 'email' or 'userId' must be present"));
    }

    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {

        String userId = InputParser.getQueryParamOrThrowError(req, "userId", true);
        try {
            if (userId != null) {
                // normalize userId
                userId = Utils.normalizeAndValidateStringParam(userId, "userId");
                boolean didUserExist = Dashboard.deleteUserWithUserId(main, userId);
                JsonObject response = new JsonObject();
                response.addProperty("status", "OK");
                response.addProperty("didUserExist", didUserExist);
                super.sendJsonResponse(200, response, resp);
                return;
            }

            String email = InputParser.getQueryParamOrThrowError(req, "email", true);

            if (email != null) {
                // normalize email
                email = Utils.normalizeAndValidateStringParam(email, "email");
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

        // Both email and userId are null
        throw new ServletException(
                new WebserverAPI.BadRequestException("Either field 'email' or 'userId' must be present"));
    }

}
