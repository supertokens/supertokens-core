package io.supertokens.webserver.api.oauth;

import java.io.IOException;
import java.io.Serial;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import com.google.gson.JsonObject;

import io.supertokens.Main;
import io.supertokens.featureflag.exceptions.FeatureNotEnabledException;
import io.supertokens.oauth.HttpRequestForOry;
import io.supertokens.oauth.OAuth;
import io.supertokens.oauth.exceptions.OAuthAPIException;
import io.supertokens.pluginInterface.Storage;
import io.supertokens.pluginInterface.exceptions.InvalidConfigException;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.multitenancy.AppIdentifier;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.pluginInterface.oauth.exception.OAuthClientNotFoundException;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class OAuthProxyHelper {
    @Serial
    private static final long serialVersionUID = -8734479943734920904L;

    public static HttpRequestForOry.Response proxyGET(Main main, HttpServletRequest req, HttpServletResponse resp, AppIdentifier appIdentifier, Storage storage,
                                String clientIdToCheck, String path, boolean proxyToAdmin, boolean camelToSnakeCaseConversion,
                                Map<String, String> queryParams, Map<String, String> headers) throws IOException, ServletException {
        try {
            return OAuth.doOAuthProxyGET(main, appIdentifier, storage, clientIdToCheck, path, proxyToAdmin, camelToSnakeCaseConversion, queryParams, headers);

        } catch (OAuthClientNotFoundException e) {
            handleOAuthClientNotFoundException(resp);
        } catch (OAuthAPIException e) {
            handleOAuthAPIException(resp, e);
        } catch (StorageQueryException | TenantOrAppNotFoundException | FeatureNotEnabledException | InvalidConfigException e) {
            throw new ServletException(e);
        }
        return null;
    }

    public static HttpRequestForOry.Response proxyFormPOST(Main main, HttpServletRequest req, HttpServletResponse resp, AppIdentifier appIdentifier, Storage storage,
                                     String clientIdToCheck, String path, boolean proxyToAdmin, boolean camelToSnakeCaseConversion,
                                     Map<String, String> formFields, Map<String, String> headers) throws IOException, ServletException {
        try {
            return OAuth.doOAuthProxyFormPOST(main, appIdentifier, storage, clientIdToCheck, path, proxyToAdmin, camelToSnakeCaseConversion, formFields, headers);
        } catch (OAuthClientNotFoundException e) {
            handleOAuthClientNotFoundException(resp);
        } catch (OAuthAPIException e) {
            handleOAuthAPIException(resp, e);
        } catch (StorageQueryException | TenantOrAppNotFoundException | FeatureNotEnabledException | InvalidConfigException e) {
            throw new ServletException(e);
        }
        return null;
    }

    public static HttpRequestForOry.Response proxyJsonPOST(Main main, HttpServletRequest req, HttpServletResponse resp, AppIdentifier appIdentifier, Storage storage,
                                     String clientIdToCheck, String path, boolean proxyToAdmin, boolean camelToSnakeCaseConversion,
                                     JsonObject jsonInput, Map<String, String> headers) throws IOException, ServletException {
        try {
            return OAuth.doOAuthProxyJsonPOST(main, appIdentifier, storage, clientIdToCheck, path, proxyToAdmin, camelToSnakeCaseConversion, jsonInput, headers);
        } catch (OAuthClientNotFoundException e) {
            handleOAuthClientNotFoundException(resp);
        } catch (OAuthAPIException e) {
            handleOAuthAPIException(resp, e);
        } catch (StorageQueryException | TenantOrAppNotFoundException | FeatureNotEnabledException | InvalidConfigException e) {
            throw new ServletException(e);
        }
        return null;
    }

    public static HttpRequestForOry.Response proxyJsonPUT(Main main, HttpServletRequest req, HttpServletResponse resp, AppIdentifier appIdentifier, Storage storage,
                                    String clientIdToCheck, String path, boolean proxyToAdmin, boolean camelToSnakeCaseConversion,
                                    Map<String, String> queryParams, JsonObject jsonInput, Map<String, String> headers) throws IOException, ServletException {

        try {
            return OAuth.doOAuthProxyJsonPUT(main, appIdentifier, storage, clientIdToCheck, path, proxyToAdmin, camelToSnakeCaseConversion, queryParams,  jsonInput, headers);
        } catch (OAuthClientNotFoundException e) {
            handleOAuthClientNotFoundException(resp);
        } catch (OAuthAPIException e) {
            handleOAuthAPIException(resp, e);
        } catch (StorageQueryException | TenantOrAppNotFoundException | FeatureNotEnabledException | InvalidConfigException e) {
            throw new ServletException(e);
        }
        return null;
    }

    public static HttpRequestForOry.Response proxyJsonDELETE(Main main, HttpServletRequest req, HttpServletResponse resp, AppIdentifier appIdentifier, Storage storage,
                                       String clientIdToCheck, String path, boolean proxyToAdmin, boolean camelToSnakeCaseConversion,
                                       Map<String, String> queryParams, JsonObject jsonInput, Map<String, String> headers) throws IOException, ServletException {
        try {
            return OAuth.doOAuthProxyJsonDELETE(main, appIdentifier, storage, clientIdToCheck, path, proxyToAdmin, camelToSnakeCaseConversion, queryParams, jsonInput, headers);
        } catch (OAuthClientNotFoundException e) {
            handleOAuthClientNotFoundException(resp);
        } catch (OAuthAPIException e) {
            handleOAuthAPIException(resp, e);
        } catch (StorageQueryException | TenantOrAppNotFoundException | FeatureNotEnabledException | InvalidConfigException e) {
            throw new ServletException(e);
        }
        return null;
    }

    public static Map<String, String> defaultGetQueryParamsFromRequest(HttpServletRequest req) {
        Map<String, String> queryParams = new HashMap<>();

        String queryString = req.getQueryString();
        if (queryString != null) {
            String[] queryParamsParts = queryString.split("&");
            for (String queryParam : queryParamsParts) {
                String[] keyValue = queryParam.split("=");
                if (keyValue.length == 2) {
                    queryParams.put(keyValue[0], URLDecoder.decode(keyValue[1], StandardCharsets.UTF_8));
                }
            }
        }

        return queryParams;
    }

    public static void handleOAuthClientNotFoundException(HttpServletResponse resp) throws IOException {
        JsonObject response = new JsonObject();
        response.addProperty("status", "CLIENT_NOT_FOUND_ERROR");

        resp.setStatus(200);
        resp.setHeader("Content-Type", "application/json; charset=UTF-8");
        resp.getWriter().println(response.toString());
    }

    public static void handleOAuthAPIException(HttpServletResponse resp, OAuthAPIException e) throws IOException {
        JsonObject response = new JsonObject();
        response.addProperty("status", "OAUTH_ERROR");
        response.addProperty("error", e.error);
        response.addProperty("errorDescription", e.errorDescription);
        response.addProperty("statusCode", e.statusCode);

        resp.setStatus(200);
        resp.setHeader("Content-Type", "application/json; charset=UTF-8");
        resp.getWriter().println(response.toString());
    }
}
