package io.supertokens.oauth;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.supertokens.oauth.exceptions.OAuthClientNotFoundException;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class HttpRequest {
    private static final int CONNECTION_TIMEOUT = 5000;
    private static final int READ_TIMEOUT = 5000;

    public static Response doGet(String url, Map<String, String> headers, Map<String, String> queryParams) throws IOException {
        if (queryParams == null) {
            queryParams = new HashMap<>();
        }
        URL obj = new URL(url + "?" + queryParams.entrySet().stream()
                .map(e -> e.getKey() + "=" + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&")));
        HttpURLConnection con = (HttpURLConnection) obj.openConnection();
        con.setInstanceFollowRedirects(false); // Do not follow redirect
        con.setRequestMethod("GET");
        con.setConnectTimeout(CONNECTION_TIMEOUT);
        con.setReadTimeout(READ_TIMEOUT);
        if (headers != null) {
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                con.setRequestProperty(entry.getKey(), entry.getValue());
            }
        }
        return getResponse(con);
    }

    public static Response doFormPost(String url, Map<String, String> headers, Map<String, String> formFields) throws IOException, OAuthClientNotFoundException {
        try {
            URL obj = new URL(url);
            HttpURLConnection con = (HttpURLConnection) obj.openConnection();
            con.setRequestMethod("POST");
            con.setConnectTimeout(CONNECTION_TIMEOUT);
            con.setReadTimeout(READ_TIMEOUT);
            con.setDoOutput(true);
            con.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");

            if (headers != null) {
                for (Map.Entry<String, String> entry : headers.entrySet()) {
                    con.setRequestProperty(entry.getKey(), entry.getValue());
                }
            }

            try (DataOutputStream os = new DataOutputStream(con.getOutputStream())) {
                os.writeBytes(formFields.entrySet().stream()
                        .map(e -> e.getKey() + "=" + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
                        .collect(Collectors.joining("&")));
            }
            return getResponse(con);
        } catch (FileNotFoundException e) {
            throw new OAuthClientNotFoundException();
        }
    }

    public static Response doJsonPost(String url, Map<String, String> headers, JsonObject jsonInput) throws IOException, OAuthClientNotFoundException {
        try {
            URL obj = new URL(url);
            HttpURLConnection con = (HttpURLConnection) obj.openConnection();
            con.setRequestMethod("POST");
            con.setConnectTimeout(CONNECTION_TIMEOUT);
            con.setReadTimeout(READ_TIMEOUT);
            con.setDoOutput(true);
            con.setRequestProperty("Content-Type", "application/json");

            if (headers != null) {
                for (Map.Entry<String, String> entry : headers.entrySet()) {
                    con.setRequestProperty(entry.getKey(), entry.getValue());
                }
            }

            try (DataOutputStream os = new DataOutputStream(con.getOutputStream())) {
                os.writeBytes(jsonInput.toString());
            }
            return getResponse(con);
        } catch (FileNotFoundException e) {
            throw new OAuthClientNotFoundException();
        }
    }

    public static Response doJsonPut(String url, Map<String, String> queryParams, Map<String, String> headers, JsonObject jsonInput) throws IOException, OAuthClientNotFoundException {
        try {
            if (queryParams == null) {
                queryParams = new HashMap<>();
            }
            URL obj = new URL(url + "?" + queryParams.entrySet().stream()
                    .map(e -> e.getKey() + "=" + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
                    .collect(Collectors.joining("&")));
            HttpURLConnection con = (HttpURLConnection) obj.openConnection();
            con.setRequestMethod("PUT");
            con.setConnectTimeout(CONNECTION_TIMEOUT);
            con.setReadTimeout(READ_TIMEOUT);
            con.setDoOutput(true);
            con.setRequestProperty("Content-Type", "application/json");

            if (headers != null) {
                for (Map.Entry<String, String> entry : headers.entrySet()) {
                    con.setRequestProperty(entry.getKey(), entry.getValue());
                }
            }

            try (DataOutputStream os = new DataOutputStream(con.getOutputStream())) {
                os.writeBytes(jsonInput.toString());
            }
            return getResponse(con);
        } catch (FileNotFoundException e) {
            throw new OAuthClientNotFoundException();
        }
    }

    public static Response doJsonDelete(String url, Map<String, String> headers, Map<String, String> queryParams, JsonObject jsonInput) throws IOException, OAuthClientNotFoundException {
        try {
            if (queryParams == null) {
                queryParams = new HashMap<>();
            }

            URL obj = new URL(url + "?" + queryParams.entrySet().stream()
                    .map(e -> e.getKey() + "=" + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
                    .collect(Collectors.joining("&")));
            HttpURLConnection con = (HttpURLConnection) obj.openConnection();
            con.setRequestMethod("DELETE");
            con.setConnectTimeout(CONNECTION_TIMEOUT);
            con.setReadTimeout(READ_TIMEOUT);
            con.setDoOutput(true);
            con.setRequestProperty("Content-Type", "application/json");

            if (headers != null) {
                for (Map.Entry<String, String> entry : headers.entrySet()) {
                    con.setRequestProperty(entry.getKey(), entry.getValue());
                }
            }

            if (jsonInput != null) {
                try (DataOutputStream os = new DataOutputStream(con.getOutputStream())) {
                    os.writeBytes(jsonInput.toString());
                }
            }

            return getResponse(con);
        } catch (FileNotFoundException e) {
            throw new OAuthClientNotFoundException();
        }
    }

    private static Response getResponse(HttpURLConnection con) throws IOException {
        int responseCode = con.getResponseCode();
        InputStream inputStream;
        if (con.getErrorStream() != null) {
            inputStream = con.getErrorStream();
        } else {
            inputStream = con.getInputStream();
        }
        BufferedReader in = new BufferedReader(new InputStreamReader(inputStream));

        String inputLine;
        StringBuilder response = new StringBuilder();
        while ((inputLine = in.readLine()) != null) {
            response.append(inputLine);
        }
        in.close();
        JsonElement jsonResponse = null;
        if (con.getContentType() != null && con.getContentType().contains("application/json")) {
            Gson gson = new Gson();
            jsonResponse = gson.fromJson(response.toString(), JsonElement.class);
        }
        return new Response(responseCode, response.toString(), jsonResponse, con.getHeaderFields());
    }

    public static class Response {
        public int statusCode;
        public String rawResponse;
        public JsonElement jsonResponse;
        public Map<String, List<String>> headers;

        public Response(int statusCode, String rawResponse, JsonElement jsonResponse, Map<String, List<String>> headers) {
            this.statusCode = statusCode;
            this.rawResponse = rawResponse;
            this.jsonResponse = jsonResponse;
            this.headers = headers;
        }
    }
}
