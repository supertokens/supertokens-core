package io.supertokens.oauth;

import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import io.supertokens.Main;
import io.supertokens.config.Config;
import io.supertokens.oauth.exceptions.OAuthAPIException;
import io.supertokens.pluginInterface.exceptions.InvalidConfigException;
import io.supertokens.pluginInterface.multitenancy.AppIdentifier;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.utils.Utils;

public class Transformations {

    public static Map<String, String> transformQueryParamsForHydra(Map<String, String> queryParams) {
        return queryParams;
    }

    public static Map<String, String> transformRequestHeadersForHydra(Map<String, String> requestHeaders) {
        if (requestHeaders == null) {
            return requestHeaders;
        }

        if (requestHeaders.containsKey("Cookie")) {
            String cookieValue = requestHeaders.get("Cookie");
            cookieValue = cookieValue.replaceAll("st_oauth_", "ory_hydra_");
            requestHeaders.put("Cookie", cookieValue);
        }
        return requestHeaders;
    }

    private static String transformQueryParamsInURLFromHydra(String redirectTo) {
        try {
            URL url = new URL(redirectTo);
            String query = url.getQuery();
            String[] queryParams = query.split("&");
            StringBuilder updatedQuery = new StringBuilder();
            for (String param : queryParams) {
                String[] keyValue = param.split("=");
                if (keyValue.length > 1 && keyValue[1].startsWith("ory_")) {
                    String decodedValue = URLDecoder.decode(keyValue[1], StandardCharsets.UTF_8.name());
                    if (decodedValue.startsWith("ory_")) {
                        decodedValue = decodedValue.replaceFirst("ory_", "st_");
                    }
                    String encodedValue = URLEncoder.encode(decodedValue, StandardCharsets.UTF_8.name());
                    updatedQuery.append(keyValue[0]).append("=").append(encodedValue).append("&");
                } else {
                    updatedQuery.append(param).append("&");
                }
            }
            redirectTo = url.getProtocol() + "://" + url.getHost() + ":" + url.getPort() + url.getPath() + "?"
                    + updatedQuery.toString().trim();
        } catch (MalformedURLException | UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }

        return redirectTo;
    }

    public static List<String> transformCookiesFromHydra(List<String> cookies) {
        cookies = new ArrayList<>(cookies); // make it modifyable

        for (int i = 0; i < cookies.size(); i++) {
            String cookieStr = cookies.get(i);
            if (cookieStr.startsWith("ory_hydra_")) {
                if (cookieStr.startsWith("ory_hydra_")) {
                    cookieStr = cookieStr.replaceFirst("ory_hydra_", "st_oauth_");
                }
                cookies.set(i, cookieStr);
            }
        }

        return cookies;
    }

    public static Map<String, String> transformFormFieldsForHydra(Map<String, String> bodyParams) {
        Map<String, String> transformedBodyParams = new HashMap<>();
        for (Map.Entry<String, String> entry : bodyParams.entrySet()) {
            String value = entry.getValue();
            if (value.startsWith("st_")) {
                value = value.replaceFirst("st_", "ory_");
            }
            transformedBodyParams.put(entry.getKey(), value);
        }
        return transformedBodyParams;
    }

    public static JsonElement transformJsonResponseFromHydra(Main main, AppIdentifier appIdentifier, JsonElement jsonResponse) throws InvalidConfigException, TenantOrAppNotFoundException, OAuthAPIException {
        if (jsonResponse == null) {
            return jsonResponse;
        }

        if (jsonResponse.isJsonObject()) {
            if (jsonResponse.getAsJsonObject().has("redirect_to")) {
                String redirectTo = jsonResponse.getAsJsonObject().get("redirect_to").getAsString();
                redirectTo = transformRedirectUrlFromHydra(main, appIdentifier, redirectTo);
                jsonResponse.getAsJsonObject().addProperty("redirect_to", redirectTo);
            }

            for (Map.Entry<String, JsonElement> entry : jsonResponse.getAsJsonObject().entrySet()) {
                if (entry.getValue().isJsonPrimitive() && entry.getValue().getAsJsonPrimitive().isString()) {
                    String value = entry.getValue().getAsString();
                    if (value.startsWith("ory_")) {
                        value = value.replaceFirst("ory_", "st_");
                        jsonResponse.getAsJsonObject().addProperty(entry.getKey(), value);
                    }
                }
            }
        }

        return jsonResponse;
    }

    private static String transformRedirectUrlFromHydra(Main main, AppIdentifier appIdentifier,String redirectTo) throws InvalidConfigException, TenantOrAppNotFoundException, OAuthAPIException {
        String hydraInternalAddress = Config.getConfig(appIdentifier.getAsPublicTenantIdentifier(), main)
                .getOauthProviderUrlConfiguredInHydra();
        String hydraBaseUrlForConsentAndLogin = Config
                .getConfig(appIdentifier.getAsPublicTenantIdentifier(), main)
                .getOauthProviderConsentLoginBaseUrl();
        
        if (!redirectTo.startsWith("/")) {
            redirectTo = transformQueryParamsInURLFromHydra(redirectTo);

            try {
                if (Utils.containsUrl(redirectTo, hydraInternalAddress, true)) {
                    try {
                        URL url = new URL(redirectTo);
                        String query = url.getQuery();
                        Map<String, String> urlQueryParams = new HashMap<>();
                        if (query != null) {
                            String[] pairs = query.split("&");
                            for (String pair : pairs) {
                                int idx = pair.indexOf("=");
                                urlQueryParams.put(pair.substring(0, idx), URLDecoder.decode(pair.substring(idx + 1), StandardCharsets.UTF_8));
                            }
                        }
                        String error = urlQueryParams.getOrDefault("error", null);
                        String errorDebug = urlQueryParams.getOrDefault("error_debug", null);
                        String errorDescription = urlQueryParams.getOrDefault("error_description", null);
                        String errorHint = urlQueryParams.getOrDefault("error_hint", null);
                        if (error != null) {
                            throw new OAuthAPIException(error, errorDebug, errorDescription, errorHint, 400);
                        }
                        redirectTo = redirectTo.replace(hydraInternalAddress, "{apiDomain}");
                        redirectTo = redirectTo.replace("oauth2/", "oauth/");

                    } catch (MalformedURLException e) {
                        throw new IllegalStateException(e);
                    }
                } else if (Utils.containsUrl(redirectTo, hydraBaseUrlForConsentAndLogin, true)) {
                    redirectTo = redirectTo.replace(hydraBaseUrlForConsentAndLogin, "{apiDomain}");
                }
            } catch (MalformedURLException e) {
                throw new IllegalStateException(e);
            }
        }

        return redirectTo;
    }

    public static Map<String, List<String>> transformResponseHeadersFromHydra(Main main, AppIdentifier appIdentifier,
            Map<String, List<String>> headers)
            throws InvalidConfigException, TenantOrAppNotFoundException, OAuthAPIException {
        if (headers == null) {
            return headers;
        }

        headers = new HashMap<>(headers); // make it modifyable

        // Location transformation
        final String LOCATION_HEADER_NAME = "Location";

        if (headers.containsKey(LOCATION_HEADER_NAME)) {
            String redirectTo = headers.get(LOCATION_HEADER_NAME).get(0);
            redirectTo = transformRedirectUrlFromHydra(main, appIdentifier, redirectTo);
            headers.put(LOCATION_HEADER_NAME, List.of(redirectTo));
        }

        final String COOKIES_HEADER_NAME = "Set-Cookie";
        if (headers.containsKey(COOKIES_HEADER_NAME)) {
            // Cookie transformation
            List<String> cookies = headers.get(COOKIES_HEADER_NAME);
            cookies = Transformations.transformCookiesFromHydra(cookies);
            headers.put(COOKIES_HEADER_NAME, cookies);
        }

        return headers;
    }

    public static JsonObject transformJsonForHydra(JsonObject jsonInput) {
        JsonObject transformedJsonInput = new JsonObject();
        for (Map.Entry<String, com.google.gson.JsonElement> entry : jsonInput.entrySet()) {
            String key = entry.getKey();
            com.google.gson.JsonElement value = entry.getValue();
            if (value.isJsonPrimitive() && ((com.google.gson.JsonPrimitive) value).isString()) {
                String stringValue = ((com.google.gson.JsonPrimitive) value).getAsString();
                if (stringValue.startsWith("st_")) {
                    stringValue = stringValue.replaceFirst("st_", "ory_");
                    transformedJsonInput.addProperty(key, stringValue);
                } else {
                    transformedJsonInput.add(key, value);
                }
            } else {
                transformedJsonInput.add(key, value);
            }
        }
        return transformedJsonInput;
    }
}
