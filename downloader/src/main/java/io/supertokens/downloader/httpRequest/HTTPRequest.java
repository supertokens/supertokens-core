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

package io.supertokens.downloader.httpRequest;

import io.supertokens.downloader.Main;
import io.supertokens.downloader.exception.QuitProgramException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Arrays;

public class HTTPRequest {

    private static final int STATUS_CODE_ERROR_THRESHOLD = 400;

    public static String getDependencyJarLinksURLForCore(String packageVersion, String planType,
                                                         Boolean withSource, String mode) {
        return "https://api.supertokens.io/0/core/dependency/jars?planType=" + planType + "&version=" +
                packageVersion + "&mode=" + mode + "&withSource=" + withSource.toString();
    }

    public static String getDependencyJarLinksURLForPluginInterface(String packageVersion, String planType,
                                                                    Boolean withSource, String mode) {
        return "https://api.supertokens.io/0/plugin-interface/dependency/jars?planType=" + planType + "&version=" +
                packageVersion + "&mode=" + mode + "&withSource=" + withSource.toString();
    }

    public static String getDependencyJarLinksURLForPlugin(String packageVersion, String planType,
                                                           Boolean withSource, String mode, String name) {
        return "https://api.supertokens.io/0/plugin/dependency/jars?planType=" + planType + "&version=" +
                packageVersion + "&mode=" + mode + "&withSource=" + withSource.toString() + "&name=" + name;
    }

    public static String getDependencyJarLinksURLForCLI(String packageVersion, String planType,
                                                        Boolean withSource, String mode) {
        return "https://api.supertokens.io/0/cli/dependency/jars?planType=" + planType + "&version=" +
                packageVersion + "&mode=" + mode + "&withSource=" + withSource.toString();
    }

    private static String makeGETRequest(String url, Integer version)
            throws IOException, HTTPResponseException {
        URL obj = new URL(url);
        InputStream inputStream = null;
        HttpURLConnection con = null;

        try {
            con = (HttpURLConnection) obj.openConnection();
            if (version != null) {
                con.setRequestProperty("api-version", version + "");
            }

            int responseCode = con.getResponseCode();

            if (responseCode < STATUS_CODE_ERROR_THRESHOLD) {
                inputStream = con.getInputStream();
            } else {
                inputStream = con.getErrorStream();
            }

            StringBuilder response = new StringBuilder();
            try (BufferedReader in = new BufferedReader(new InputStreamReader(inputStream))) {
                String inputLine;
                while ((inputLine = in.readLine()) != null) {
                    response.append(inputLine);
                }
            }
            if (responseCode < STATUS_CODE_ERROR_THRESHOLD) {
                return response.toString();
            }
            throw new HTTPResponseException(responseCode, response.toString());
        } finally {
            if (inputStream != null) {
                inputStream.close();
            }

            if (con != null) {
                con.disconnect();
            }
        }
    }

    public static String[] getDependencyJarLinks(String url) throws IOException, HTTPResponseException {
        try {
            String resultJarLinks = makeGETRequest(url, 0);
            resultJarLinks = resultJarLinks.split("\\[")[1].split("]")[0];
            String[] result = {};
            if (resultJarLinks.equals("")) {
                return result;
            }
            result = Arrays.stream(resultJarLinks.split(",")).map(x -> x.substring(1, x.length() - 1))
                    .toArray(String[]::new);
            return result;
        } catch (Exception e) {
            if (Main.isTesting) {
                throw e;
            } else {
                throw new QuitProgramException(
                        "Error while fetching list of jar dependencies. Is your internet connection " +
                                "working?", e);
            }
        }
    }
}
