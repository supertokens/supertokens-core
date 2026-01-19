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

package io.supertokens.test.httpRequest;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import io.supertokens.Main;
import io.supertokens.ResourceDistributor;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifier;

public class HttpRequestForTesting {
    private static final int STATUS_CODE_ERROR_THRESHOLD = 400;
    public static boolean disableAddingAppId = false;
    public static Integer corePort = null;

    private static URL getURL(Main main, String requestID, String url) throws MalformedURLException {
        URL obj = new URL(url);
        if (Main.isTesting) {
            URL mock = HttpRequestMocking.getInstance(main).getMockURL(requestID, url);
            if (mock != null) {
                obj = mock;
            }
        }
        return obj;
    }

    private static boolean isJsonValid(String jsonInString) {
        JsonElement el = null;
        try {
            el = new JsonParser().parse(jsonInString);
            el.getAsJsonObject();
            return true;
        } catch (Exception ex) {
            try {
                assert el != null;
                el.getAsJsonArray();
                return true;
            } catch (Throwable e) {
                return false;
            }
        }
    }

    public static <T> T sendGETRequest(Main main, String requestID, String url, Map<String, String> params,
                                       int connectionTimeoutMS, int readTimeoutMS, Integer version, String cdiVersion,
                                       String rid)
            throws IOException, io.supertokens.test.httpRequest.HttpResponseException {
        return sendGETRequest(main, requestID, url, params, connectionTimeoutMS, readTimeoutMS, version, cdiVersion, rid, true);
    }

    @SuppressWarnings("unchecked")
    public static <T> T sendGETRequest(Main main, String requestID, String url, Map<String, String> params,
                                       int connectionTimeoutMS, int readTimeoutMS, Integer version, String cdiVersion,
                                       String rid, boolean followRedirects)
            throws IOException, io.supertokens.test.httpRequest.HttpResponseException {

        if (!disableAddingAppId && !url.contains("appid-") && !url.contains(":3567/config")) {
            String appId = ResourceDistributor.getAppForTesting().getAppId();
            url = url.replace(":3567", ":3567/appid-" + appId);
        }

        if (corePort != null) {
            url = url.replace(":3567", ":" + corePort);
        }

        StringBuilder paramBuilder = new StringBuilder();

        if (params != null) {
            for (Map.Entry<String, String> entry : params.entrySet()) {
                paramBuilder.append(entry.getKey()).append("=")
                        .append(URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8)).append("&");
            }
        }
        String paramsStr = paramBuilder.toString();
        if (!paramsStr.equals("")) {
            paramsStr = paramsStr.substring(0, paramsStr.length() - 1);
            url = url + "?" + paramsStr;
        }
        URL obj = getURL(main, requestID, url);
        InputStream inputStream = null;
        HttpURLConnection con = null;

        try {
            con = (HttpURLConnection) obj.openConnection();
            con.setConnectTimeout(connectionTimeoutMS);
            con.setReadTimeout(readTimeoutMS + 1000);
            con.setInstanceFollowRedirects(followRedirects);
            if (version != null) {
                con.setRequestProperty("api-version", version + "");
            }
            if (cdiVersion != null) {
                con.setRequestProperty("cdi-version", cdiVersion);
            }
            if (rid != null) {
                con.setRequestProperty("rId", rid);
            }

            int responseCode = con.getResponseCode();

            // Handle redirects specially
            if (responseCode >= 300 && responseCode < 400) {
                String location = con.getHeaderField("Location");
                if (location != null) {
                    throw new io.supertokens.test.httpRequest.HttpResponseException(responseCode, location);
                }
            }

            if (responseCode < STATUS_CODE_ERROR_THRESHOLD) {
                inputStream = con.getInputStream();
            } else {
                inputStream = con.getErrorStream();
            }

            StringBuilder response = new StringBuilder();
            try (BufferedReader in = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
                String inputLine;
                while ((inputLine = in.readLine()) != null) {
                    response.append(inputLine);
                }
            }
            if (responseCode < STATUS_CODE_ERROR_THRESHOLD) {
                if (!isJsonValid(response.toString())) {
                    return (T) response.toString();
                }
                return (T) (new JsonParser().parse(response.toString()));
            }
            throw new io.supertokens.test.httpRequest.HttpResponseException(responseCode, response.toString());
        } finally {
            if (inputStream != null) {
                inputStream.close();
            }

            if (con != null) {
                con.disconnect();
            }
        }
    }

    public static <T> T sendGETRequestWithHeaders(Main main, String requestID, String url, Map<String, String> params,
                                                  Map<String, String> headers, int connectionTimeoutMS, int readTimeoutMS, Integer version, String cdiVersion,
                                                  String rid)
            throws IOException, io.supertokens.test.httpRequest.HttpResponseException {
        return sendGETRequestWithHeaders(main, requestID, url, params, headers, connectionTimeoutMS, readTimeoutMS, version, cdiVersion, rid, true);
    }

    @SuppressWarnings("unchecked")
    public static <T> T sendGETRequestWithHeaders(Main main, String requestID, String url, Map<String, String> params,
                                       Map<String, String> headers, int connectionTimeoutMS, int readTimeoutMS, Integer version, String cdiVersion,
                                       String rid, boolean followRedirects)
            throws IOException, io.supertokens.test.httpRequest.HttpResponseException {

        if (!disableAddingAppId && !url.contains("appid-") && !url.contains(":3567/config")) {
            String appId = ResourceDistributor.getAppForTesting().getAppId();
            url = url.replace(":3567", ":3567/appid-" + appId);
        }

        if (corePort != null) {
            url = url.replace(":3567", ":" + corePort);
        }

        StringBuilder paramBuilder = new StringBuilder();

        if (params != null) {
            for (Map.Entry<String, String> entry : params.entrySet()) {
                paramBuilder.append(entry.getKey()).append("=")
                        .append(URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8)).append("&");
            }
        }
        String paramsStr = paramBuilder.toString();
        if (!paramsStr.equals("")) {
            paramsStr = paramsStr.substring(0, paramsStr.length() - 1);
            url = url + "?" + paramsStr;
        }
        URL obj = getURL(main, requestID, url);
        InputStream inputStream = null;
        HttpURLConnection con = null;

        try {
            con = (HttpURLConnection) obj.openConnection();
            con.setConnectTimeout(connectionTimeoutMS);
            con.setReadTimeout(readTimeoutMS + 1000);
            con.setInstanceFollowRedirects(followRedirects);
            if (headers != null) {
                for (Map.Entry<String, String> entry : headers.entrySet()) {
                    con.setRequestProperty(entry.getKey(), entry.getValue());
                }
            }
            if (version != null) {
                con.setRequestProperty("api-version", version + "");
            }
            if (cdiVersion != null) {
                con.setRequestProperty("cdi-version", cdiVersion);
            }
            if (rid != null) {
                con.setRequestProperty("rId", rid);
            }

            int responseCode = con.getResponseCode();

            // Handle redirects specially
            if (responseCode >= 300 && responseCode < 400) {
                String location = con.getHeaderField("Location");
                if (location != null) {
                    throw new io.supertokens.test.httpRequest.HttpResponseException(responseCode, location);
                }
            }

            if (responseCode < STATUS_CODE_ERROR_THRESHOLD) {
                inputStream = con.getInputStream();
            } else {
                inputStream = con.getErrorStream();
            }

            StringBuilder response = new StringBuilder();
            try (BufferedReader in = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
                String inputLine;
                while ((inputLine = in.readLine()) != null) {
                    response.append(inputLine);
                }
            }
            if (responseCode < STATUS_CODE_ERROR_THRESHOLD) {
                if (!isJsonValid(response.toString())) {
                    return (T) response.toString();
                }
                return (T) (new JsonParser().parse(response.toString()));
            }
            throw new io.supertokens.test.httpRequest.HttpResponseException(responseCode, response.toString());
        } finally {
            if (inputStream != null) {
                inputStream.close();
            }

            if (con != null) {
                con.disconnect();
            }
        }
    }

    public static <T> T sendJsonRequest(Main main, String requestID, String url, JsonElement requestBody,
                                        int connectionTimeoutMS, int readTimeoutMS, Integer version, String cdiVersion,
                                        String method,
                                        String apiKey, String rid)
            throws IOException, io.supertokens.test.httpRequest.HttpResponseException {
        return sendJsonRequest(main, requestID, url, requestBody, connectionTimeoutMS, readTimeoutMS, version, cdiVersion, method, apiKey, rid, true);
    }

    @SuppressWarnings("unchecked")
    public static <T> T sendJsonRequest(Main main, String requestID, String url, JsonElement requestBody,
                                        int connectionTimeoutMS, int readTimeoutMS, Integer version, String cdiVersion,
                                        String method,
                                        String apiKey, String rid, boolean followRedirects)
            throws IOException, io.supertokens.test.httpRequest.HttpResponseException {
        // If the url doesn't contain the app id deliberately, add app id used for testing
        if (!disableAddingAppId && !url.contains("appid-")) {
            String appId = ResourceDistributor.getAppForTesting().getAppId();
            url = url.replace(":3567", ":3567/appid-" + appId);
        }

        if (corePort != null) {
            url = url.replace(":3567", ":" + corePort);
        }

        URL obj = getURL(main, requestID, url);
        InputStream inputStream = null;
        HttpURLConnection con = null;

        try {
            con = (HttpURLConnection) obj.openConnection();
            con.setRequestMethod(method);
            con.setConnectTimeout(connectionTimeoutMS);
            con.setReadTimeout(readTimeoutMS + 1000);
            con.setInstanceFollowRedirects(followRedirects);
            con.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            if (version != null) {
                con.setRequestProperty("api-version", version + "");
            }
            if (cdiVersion != null) {
                con.setRequestProperty("cdi-version", cdiVersion);
            }
            if (apiKey != null) {
                con.setRequestProperty("Authorization", "Bearer " + apiKey);
            }
            if (rid != null) {
                con.setRequestProperty("rId", rid);
            }

            if (requestBody != null) {
                con.setDoOutput(true);
                try (OutputStream os = con.getOutputStream()) {
                    byte[] input = requestBody.toString().getBytes(StandardCharsets.UTF_8);
                    os.write(input, 0, input.length);
                }
            }

            int responseCode = con.getResponseCode();

            // Handle redirects specially
            if (responseCode >= 300 && responseCode < 400) {
                String location = con.getHeaderField("Location");
                if (location != null) {
                    throw new io.supertokens.test.httpRequest.HttpResponseException(responseCode, location);
                }
            }

            if (responseCode < STATUS_CODE_ERROR_THRESHOLD) {
                inputStream = con.getInputStream();
            } else {
                inputStream = con.getErrorStream();
            }

            StringBuilder response = new StringBuilder();
            try (BufferedReader in = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
                String inputLine;
                while ((inputLine = in.readLine()) != null) {
                    response.append(inputLine);
                }
            }

            if (responseCode < STATUS_CODE_ERROR_THRESHOLD) {
                if (!isJsonValid(response.toString())) {
                    return (T) response.toString();
                }
                return (T) (new JsonParser().parse(response.toString()));
            }
            throw new io.supertokens.test.httpRequest.HttpResponseException(responseCode, response.toString());
        } finally {
            if (inputStream != null) {
                inputStream.close();
            }

            if (con != null) {
                con.disconnect();
            }
        }
    }

    public static <T> T sendJsonPOSTRequest(Main main, String requestID, String url, JsonElement requestBody,
                                            int connectionTimeoutMS, int readTimeoutMS, Integer version,
                                            String cdiVersion, String rid)
            throws IOException, io.supertokens.test.httpRequest.HttpResponseException {
        return sendJsonRequest(main, requestID, url, requestBody, connectionTimeoutMS, readTimeoutMS, version,
                cdiVersion, "POST", null, rid);
    }

    public static <T> T sendJsonPOSTRequest(Main main, String requestID, String url, JsonElement requestBody,
                                            int connectionTimeoutMS, int readTimeoutMS, Integer version,
                                            String cdiVersion, String apiKey, String rid)
            throws IOException, io.supertokens.test.httpRequest.HttpResponseException {
        return sendJsonRequest(main, requestID, url, requestBody, connectionTimeoutMS, readTimeoutMS, version,
                cdiVersion, "POST", apiKey, rid);
    }

    public static <T> T sendJsonPUTRequest(Main main, String requestID, String url, JsonElement requestBody,
                                           int connectionTimeoutMS, int readTimeoutMS, Integer version,
                                           String cdiVersion, String rid)
            throws IOException, io.supertokens.test.httpRequest.HttpResponseException {
        return sendJsonRequest(main, requestID, url, requestBody, connectionTimeoutMS, readTimeoutMS, version,
                cdiVersion, "PUT", null, rid);
    }

    public static <T> T sendJsonDELETERequest(Main main, String requestID, String url, JsonElement requestBody,
                                              int connectionTimeoutMS, int readTimeoutMS, Integer version,
                                              String cdiVersion, String rid)
            throws IOException, HttpResponseException {
        return sendJsonRequest(main, requestID, url, requestBody, connectionTimeoutMS, readTimeoutMS, version,
                cdiVersion, "DELETE", null, rid);
    }

    public static <T> T sendJsonDELETERequestWithQueryParams(Main main, String requestID, String url,
                                                             Map<String, String> params,
                                                             int connectionTimeoutMS, int readTimeoutMS,
                                                             Integer version, String cdiVersion, String rid)
            throws IOException, HttpResponseException {
        return sendJsonDELETERequestWithQueryParams(main, requestID, url, params, connectionTimeoutMS, readTimeoutMS, version, cdiVersion, rid, true);
    }

    @SuppressWarnings("unchecked")
    public static <T> T sendJsonDELETERequestWithQueryParams(Main main, String requestID, String url,
                                                             Map<String, String> params,
                                                             int connectionTimeoutMS, int readTimeoutMS,
                                                             Integer version, String cdiVersion, String rid,
                                                             boolean followRedirects)
            throws IOException, HttpResponseException {
        // If the url doesn't contain the app id deliberately, add app id used for testing
        if (!disableAddingAppId && !url.contains("appid-")) {
            String appId = ResourceDistributor.getAppForTesting().getAppId();
            url = url.replace(":3567", ":3567/appid-" + appId);
        }

        if (corePort != null) {
            url = url.replace(":3567", ":" + corePort);
        }

        StringBuilder paramBuilder = new StringBuilder();

        if (params != null) {
            for (Map.Entry<String, String> entry : params.entrySet()) {
                paramBuilder.append(entry.getKey()).append("=")
                        .append(URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8)).append("&");
            }
        }
        String paramsStr = paramBuilder.toString();
        if (!paramsStr.equals("")) {
            paramsStr = paramsStr.substring(0, paramsStr.length() - 1);
            url = url + "?" + paramsStr;
        }
        URL obj = getURL(main, requestID, url);
        InputStream inputStream = null;
        HttpURLConnection con = null;

        try {
            con = (HttpURLConnection) obj.openConnection();
            con.setRequestMethod("DELETE");
            con.setConnectTimeout(connectionTimeoutMS);
            con.setReadTimeout(readTimeoutMS + 1000);
            con.setInstanceFollowRedirects(followRedirects);
            if (version != null) {
                con.setRequestProperty("api-version", version + "");
            }
            if (cdiVersion != null) {
                con.setRequestProperty("cdi-version", cdiVersion);
            }
            if (rid != null) {
                con.setRequestProperty("rId", rid);
            }

            int responseCode = con.getResponseCode();

            // Handle redirects specially
            if (responseCode >= 300 && responseCode < 400) {
                String location = con.getHeaderField("Location");
                if (location != null) {
                    throw new io.supertokens.test.httpRequest.HttpResponseException(responseCode, location);
                }
            }

            if (responseCode < STATUS_CODE_ERROR_THRESHOLD) {
                inputStream = con.getInputStream();
            } else {
                inputStream = con.getErrorStream();
            }

            StringBuilder response = new StringBuilder();
            try (BufferedReader in = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
                String inputLine;
                while ((inputLine = in.readLine()) != null) {
                    response.append(inputLine);
                }
            }
            if (responseCode < STATUS_CODE_ERROR_THRESHOLD) {
                if (!isJsonValid(response.toString())) {
                    return (T) response.toString();
                }
                return (T) (new JsonParser().parse(response.toString()));
            }
            throw new io.supertokens.test.httpRequest.HttpResponseException(responseCode, response.toString());
        } finally {
            if (inputStream != null) {
                inputStream.close();
            }

            if (con != null) {
                con.disconnect();
            }
        }
    }

    public static <T> T sendFormDataPOSTRequest(Main main, String requestID, String url, JsonObject formData,
                                                int connectionTimeoutMS, int readTimeoutMS, Integer version,
                                                String cdiVersion, String rid)
            throws IOException, io.supertokens.test.httpRequest.HttpResponseException {
        return sendFormDataPOSTRequest(main, requestID, url, formData, connectionTimeoutMS, readTimeoutMS, version, cdiVersion, rid, true);
    }

    @SuppressWarnings("unchecked")
    public static <T> T sendFormDataPOSTRequest(Main main, String requestID, String url, JsonObject formData,
                                                int connectionTimeoutMS, int readTimeoutMS, Integer version,
                                                String cdiVersion, String rid, boolean followRedirects)
            throws IOException, io.supertokens.test.httpRequest.HttpResponseException {
        // If the url doesn't contain the app id deliberately, add app id used for testing
        if (!disableAddingAppId && !url.contains("appid-")) {
            String appId = ResourceDistributor.getAppForTesting().getAppId();
            url = url.replace(":3567", ":3567/appid-" + appId);
        }

        if (corePort != null) {
            url = url.replace(":3567", ":" + corePort);
        }

        URL obj = getURL(main, requestID, url);
        InputStream inputStream = null;
        HttpURLConnection con = null;

        try {
            con = (HttpURLConnection) obj.openConnection();
            con.setRequestMethod("POST");
            con.setConnectTimeout(connectionTimeoutMS);
            con.setReadTimeout(readTimeoutMS + 1000);
            con.setInstanceFollowRedirects(followRedirects);
            con.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
            if (version != null) {
                con.setRequestProperty("api-version", version + "");
            }
            if (cdiVersion != null) {
                con.setRequestProperty("cdi-version", cdiVersion);
            }
            if (rid != null) {
                con.setRequestProperty("rId", rid);
            }

            if (formData != null) {
                con.setDoOutput(true);
                StringBuilder formDataStr = new StringBuilder();
                for (Map.Entry<String, JsonElement> entry : formData.entrySet()) {
                    if (formDataStr.length() > 0) {
                        formDataStr.append("&");
                    }
                    formDataStr.append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8))
                            .append("=")
                            .append(URLEncoder.encode(entry.getValue().getAsString(), StandardCharsets.UTF_8));
                }
                try (OutputStream os = con.getOutputStream()) {
                    byte[] input = formDataStr.toString().getBytes(StandardCharsets.UTF_8);
                    os.write(input, 0, input.length);
                }
            }

            int responseCode = con.getResponseCode();

            // Handle redirects specially
            if (responseCode >= 300 && responseCode < 400) {
                String location = con.getHeaderField("Location");
                if (location != null) {
                    throw new io.supertokens.test.httpRequest.HttpResponseException(responseCode, location);
                }
            }

            if (responseCode < STATUS_CODE_ERROR_THRESHOLD) {
                inputStream = con.getInputStream();
            } else {
                inputStream = con.getErrorStream();
            }

            StringBuilder response = new StringBuilder();
            try (BufferedReader in = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
                String inputLine;
                while ((inputLine = in.readLine()) != null) {
                    response.append(inputLine);
                }
            }

            if (responseCode < STATUS_CODE_ERROR_THRESHOLD) {
                if (!isJsonValid(response.toString())) {
                    return (T) response.toString();
                }
                return (T) (new JsonParser().parse(response.toString()));
            }
            throw new io.supertokens.test.httpRequest.HttpResponseException(responseCode, response.toString());
        } finally {
            if (inputStream != null) {
                inputStream.close();
            }

            if (con != null) {
                con.disconnect();
            }
        }
    }

    public static String getMultitenantUrl(TenantIdentifier tenantIdentifier, String path) {
        StringBuilder sb = new StringBuilder();
        if (tenantIdentifier.getConnectionUriDomain() == TenantIdentifier.DEFAULT_CONNECTION_URI) {
            sb.append("http://localhost:3567");
        } else {
            sb.append("http://");
            sb.append(tenantIdentifier.getConnectionUriDomain());
            sb.append(":3567");
        }

        if (!tenantIdentifier.getAppId().equals(TenantIdentifier.DEFAULT_APP_ID)) {
            sb.append("/appid-");
            sb.append(tenantIdentifier.getAppId());
        }

        if (!tenantIdentifier.getTenantId().equals(TenantIdentifier.DEFAULT_TENANT_ID)) {
            sb.append("/");
            sb.append(tenantIdentifier.getTenantId());
        }
        sb.append(path);
        return sb.toString();
    }
}
