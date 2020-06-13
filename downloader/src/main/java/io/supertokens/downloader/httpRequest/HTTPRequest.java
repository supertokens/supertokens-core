/*
 *    Copyright (c) 2020, VRAI Labs and/or its affiliates. All rights reserved.
 *
 *    This program is licensed under the SuperTokens Community License (the
 *    "License") as published by VRAI Labs. You may not use this file except in
 *    compliance with the License. You are not permitted to transfer or
 *    redistribute this file without express written permission from VRAI Labs.
 *
 *    A copy of the License is available in the file titled
 *    "SuperTokensLicense.pdf" inside this repository or included with your copy of
 *    the software or its source code. If you have not received a copy of the
 *    License, please write to VRAI Labs at team@supertokens.io.
 *
 *    Please read the License carefully before accessing, downloading, copying,
 *    using, modifying, merging, transferring or sharing this software. By
 *    undertaking any of these activities, you indicate your agreement to the terms
 *    of the License.
 *
 *    This program is distributed with certain software that is licensed under
 *    separate terms, as designated in a particular file or component or in
 *    included license documentation. VRAI Labs hereby grants you an additional
 *    permission to link the program and your derivative works with the separately
 *    licensed software that they have included with this program, however if you
 *    modify this program, you shall be solely liable to ensure compliance of the
 *    modified program with the terms of licensing of the separately licensed
 *    software.
 *
 *    Unless required by applicable law or agreed to in writing, this program is
 *    distributed under the License on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 *    CONDITIONS OF ANY KIND, either express or implied. See the License for the
 *    specific language governing permissions and limitations under the License.
 *
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
