/*
 *    Copyright (c) 2020, VRAI Labs and/or its affiliates. All rights reserved.
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

package io.supertokens.webserver;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.supertokens.Main;
import io.supertokens.ProcessState;
import io.supertokens.backendAPI.Ping;
import io.supertokens.exceptions.QuitProgramException;
import io.supertokens.licenseKey.LicenseKey;
import io.supertokens.licenseKey.LicenseKey.MODE;
import io.supertokens.output.Logging;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

public abstract class WebserverAPI extends HttpServlet {

    private static final long serialVersionUID = 1L;
    protected final Main main;
    public static final Set<String> supportedVersions = new HashSet<>();

    static {
        supportedVersions.add("1.0");
        supportedVersions.add("2.0");
        supportedVersions.add("2.1");
        supportedVersions.add("2.2");
    }

    public WebserverAPI(Main main) {
        super();
        this.main = main;
    }

    public abstract String getPath();

    protected void saveDeviceDriverInfoIfPresent(JsonObject input) {
        try {
            InputParser.DeviceDriverInfo deviceDriverInfo = InputParser.parseDeviceDriverInfo(input);
            if (deviceDriverInfo != null) {
                deviceDriverInfo.frontendSDK.forEach(nameVersion -> {
                    Ping.getInstance(main).addFrontendSDK(nameVersion);
                });
                if (deviceDriverInfo.driver != null) {
                    Ping.getInstance(main).addDriver(deviceDriverInfo.driver);
                }
                ProcessState.getInstance(main).addState(ProcessState.PROCESS_STATE.DEVICE_DRIVER_INFO_SAVED, null);
            }
        } catch (Exception ignored) {
            // if any error occurs, it's OK since this information is only used for analytics purposes.
        }
    }

    protected void sendTextResponse(int statusCode, String message, HttpServletResponse resp) throws IOException {
        resp.setStatus(statusCode);
        resp.setHeader("Content-Type", "text/html; charset=UTF-8");
        resp.getWriter().println(message);
    }

    protected void sendJsonResponse(int statusCode, JsonElement json, HttpServletResponse resp) throws IOException {
        resp.setStatus(statusCode);
        resp.setHeader("Content-Type", "application/json; charset=UTF-8");
        resp.getWriter().println(json.toString());
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {
        this.sendTextResponse(405, "Method not supported", resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {
        this.sendTextResponse(405, "Method not supported", resp);
    }

    @Override
    protected void doHead(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        this.sendTextResponse(405, "Method not supported", resp);
    }

    @Override
    protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {
        this.sendTextResponse(405, "Method not supported", resp);
    }

    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {
        this.sendTextResponse(405, "Method not supported", resp);
    }

    @Override
    protected void doOptions(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        this.sendTextResponse(405, "Method not supported", resp);
    }

    @Override
    protected void doTrace(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        this.sendTextResponse(405, "Method not supported", resp);
    }

    private void assertThatVersionIsCompatible(String version) throws ServletException {
        if (version == null) {
            throw new ServletException(new BadRequestException(
                    "cdi-version not provided"));
        }
        if (!supportedVersions.contains(version)) {
            throw new ServletException(new BadRequestException(
                    "cdi-version " + version + " not supported"));
        }
    }

    protected boolean versionNeeded() {
        return true;
    }

    @Override
    protected void service(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        try {
            if (this.versionNeeded()) {
                String version = getVersionFromRequest(req);
                assertThatVersionIsCompatible(version);
                Logging.debug(main,
                        "API called: " + this.getPath() + ". Method: " + req.getMethod() + ". Version: " +
                                version);
            } else {
                Logging.debug(main,
                        "API called: " + this.getPath() + ". Method: " + req.getMethod());
            }
            super.service(req, resp);
        } catch (Exception e) {
            Logging.error(main, "API threw an exception: " + this.getPath(),
                    LicenseKey.get(main).getMode() != MODE.PRODUCTION, e);

            if (e instanceof QuitProgramException) {
                main.wakeUpMainThreadToShutdown();
            } else if (e instanceof ServletException) {
                ServletException se = (ServletException) e;
                Throwable rootCause = se.getRootCause();
                if (rootCause instanceof BadRequestException) {
                    sendTextResponse(400, rootCause.getMessage(), resp);
                } else {
                    sendTextResponse(500, "Internal Error", resp);
                }
            } else {
                sendTextResponse(500, "Internal Error", resp);
            }
        }
        Logging.debug(main, "API ended: " + this.getPath() + ". Method: " + req.getMethod());
        RPMCalculator.getInstance(main).updateRPM();
    }

    protected String getVersionFromRequest(HttpServletRequest req) {
        String version = req.getHeader("cdi-version");
        // in api spec 1.0.X, we did not have cdi-version header
        return version == null ? "1.0" : version;
    }

    protected static class BadRequestException extends Exception {
        private static final long serialVersionUID = -5014892660208978125L;

        public BadRequestException(String msg) {
            super(msg);
        }
    }

    // safeguard against misuse of DEV license key.
    protected boolean sendRandomUnauthorisedIfDevLicenseAndSomeTimeHasPassed(HttpServletResponse resp)
            throws IOException {
        int timeAfterWhichToThrowUnauthorised =
                WebserverAPITest.getInstance(main).getTimeAfterWhichToThrowUnauthorised() == null ? 3600 * 1000 :
                        WebserverAPITest.getInstance(main).getTimeAfterWhichToThrowUnauthorised();
        double randomnessThreshold = WebserverAPITest.getInstance(main).getRandomnessThreshold() == null ? 0.5 :
                WebserverAPITest.getInstance(main).getRandomnessThreshold();
        if (LicenseKey.get(main).getMode() == MODE.DEV &&
                ((System.currentTimeMillis() - main.getProcessStartTime()) > timeAfterWhichToThrowUnauthorised) &&
                Math.random() >= randomnessThreshold) {
            JsonObject reply = new JsonObject();
            reply.addProperty("status", "UNAUTHORISED");
            reply.addProperty("message", "");
            sendJsonResponse(200, reply, resp);
            return true;
        }
        return false;
    }

}
