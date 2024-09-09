package io.supertokens.webserver.api.oauth;

import java.io.IOException;
import java.io.Serial;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import io.supertokens.Main;
import io.supertokens.featureflag.exceptions.FeatureNotEnabledException;
import io.supertokens.oauth.HttpRequest;
import io.supertokens.oauth.OAuth;
import io.supertokens.oauth.exceptions.OAuthAPIException;
import io.supertokens.oauth.exceptions.OAuthClientNotFoundException;
import io.supertokens.pluginInterface.Storage;
import io.supertokens.pluginInterface.exceptions.InvalidConfigException;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.multitenancy.AppIdentifier;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class OAuthProxyHelper {
    @Serial
    private static final long serialVersionUID = -8734479943734920904L;

    public static void proxyGET(Main main, HttpServletRequest req, HttpServletResponse resp, AppIdentifier appIdentifier, Storage storage,
                                String path, boolean proxyToAdmin, boolean camelToSnakeCaseConversion,
                                GetQueryParamsForProxy getQueryParamsForProxy, GetHeadersForProxy getHeadersForProxy,
                                HandleResponse handleResponse) throws IOException, ServletException {
        Map<String, String> queryParams = getQueryParamsForProxy.apply();

        if (camelToSnakeCaseConversion) {
            queryParams = OAuth.convertCamelToSnakeCase(queryParams);
        }

        Map<String, String> headers = getHeadersForProxy.apply();

        try {
            HttpRequest.Response response = OAuth.handleOAuthProxyGET(main, appIdentifier, storage, path, proxyToAdmin, queryParams, headers);

            if (camelToSnakeCaseConversion) {
                response.jsonResponse = OAuth.convertSnakeCaseToCamelCaseRecursively(response.jsonResponse);
            }

            handleResponse.apply(
                response.statusCode,
                response.headers,
                response.rawResponse,
                response.jsonResponse
            );

        } catch (OAuthClientNotFoundException e) {
            handleOAuthClientNotFoundException(resp);
        } catch (OAuthAPIException e) {
            handleOAuthAPIException(resp, e);
        } catch (StorageQueryException | TenantOrAppNotFoundException | FeatureNotEnabledException | InvalidConfigException e) {
            throw new ServletException(e);
        }
    }

    public static void proxyFormPOST(Main main, HttpServletRequest req, HttpServletResponse resp, AppIdentifier appIdentifier, Storage storage,
                                     String path, boolean proxyToAdmin, boolean camelToSnakeCaseConversion,
                                     GetFormFieldsForProxy getFormFieldsForProxy, GetHeadersForProxy getHeadersForProxy,
                                     HandleResponse handleResponse) throws IOException, ServletException {
        Map<String, String> formFields = getFormFieldsForProxy.apply();

        if (camelToSnakeCaseConversion) {
            formFields = OAuth.convertCamelToSnakeCase(formFields);
        }

        Map<String, String> headers = getHeadersForProxy.apply();

        try {
            HttpRequest.Response response = OAuth.handleOAuthProxyFormPOST(main, appIdentifier, storage, path, proxyToAdmin, formFields, headers);

            if (camelToSnakeCaseConversion) {
                response.jsonResponse = OAuth.convertSnakeCaseToCamelCaseRecursively(response.jsonResponse);
            }

            handleResponse.apply(
                response.statusCode,
                response.headers,
                response.rawResponse,
                response.jsonResponse
            );

        } catch (OAuthClientNotFoundException e) {
            handleOAuthClientNotFoundException(resp);
        } catch (OAuthAPIException e) {
            handleOAuthAPIException(resp, e);
        } catch (StorageQueryException | TenantOrAppNotFoundException | FeatureNotEnabledException | InvalidConfigException e) {
            throw new ServletException(e);
        }
    }

    public static void proxyJsonPOST(Main main, HttpServletRequest req, HttpServletResponse resp, AppIdentifier appIdentifier, Storage storage,
                                     String path, boolean proxyToAdmin, boolean camelToSnakeCaseConversion,
                                     GetJsonBody getJsonBody, GetHeadersForProxy getHeadersForProxy,
                                     HandleResponse handleResponse) throws IOException, ServletException {
        JsonObject jsonInput = getJsonBody.apply();

        if (camelToSnakeCaseConversion) {
            jsonInput = OAuth.convertCamelToSnakeCase(jsonInput);
        }

        Map<String, String> headers = getHeadersForProxy.apply();

        try {
            HttpRequest.Response response = OAuth.handleOAuthProxyJsonPOST(main, appIdentifier, storage, path, proxyToAdmin, jsonInput, headers);

            if (camelToSnakeCaseConversion) {
                response.jsonResponse = OAuth.convertSnakeCaseToCamelCaseRecursively(response.jsonResponse);
            }

            handleResponse.apply(
                response.statusCode,
                response.headers,
                response.rawResponse,
                response.jsonResponse
            );

        } catch (OAuthClientNotFoundException e) {
            handleOAuthClientNotFoundException(resp);
        } catch (OAuthAPIException e) {
            handleOAuthAPIException(resp, e);
        } catch (StorageQueryException | TenantOrAppNotFoundException | FeatureNotEnabledException | InvalidConfigException e) {
            throw new ServletException(e);
        }
    }

    public static void proxyJsonPUT(Main main, HttpServletRequest req, HttpServletResponse resp, AppIdentifier appIdentifier, Storage storage,
                                    String path, boolean proxyToAdmin, boolean camelToSnakeCaseConversion,
                                    GetQueryParamsForProxy getQueryParamsForProxy, GetJsonBody getJsonBodyForProxyPUT,
                                    GetHeadersForProxy getHeadersForProxy, HandleResponse handleResponse) throws IOException, ServletException {
        Map<String, String> queryParams = getQueryParamsForProxy.apply();

        if (camelToSnakeCaseConversion) {
            queryParams = OAuth.convertCamelToSnakeCase(queryParams);
        }

        JsonObject jsonInput = getJsonBodyForProxyPUT.apply();

        if (camelToSnakeCaseConversion) {
            jsonInput = OAuth.convertCamelToSnakeCase(jsonInput);
        }

        Map<String, String> headers = getHeadersForProxy.apply();

        try {
            HttpRequest.Response response = OAuth.handleOAuthProxyJsonPUT(main, appIdentifier, storage, path, queryParams, proxyToAdmin, jsonInput, headers);

            if (camelToSnakeCaseConversion) {
                response.jsonResponse = OAuth.convertSnakeCaseToCamelCaseRecursively(response.jsonResponse);
            }

            handleResponse.apply(
                response.statusCode,
                response.headers,
                response.rawResponse,
                response.jsonResponse
            );

        } catch (OAuthClientNotFoundException e) {
            handleOAuthClientNotFoundException(resp);
        } catch (OAuthAPIException e) {
            handleOAuthAPIException(resp, e);
        } catch (StorageQueryException | TenantOrAppNotFoundException | FeatureNotEnabledException | InvalidConfigException e) {
            throw new ServletException(e);
        }
    }

    public static void proxyJsonDELETE(Main main, HttpServletRequest req, HttpServletResponse resp, AppIdentifier appIdentifier, Storage storage,
                                       String path, boolean proxyToAdmin, boolean camelToSnakeCaseConversion,
                                       GetJsonBody getJsonBodyForProxyDELETE, GetHeadersForProxy getHeadersForProxy,
                                       HandleResponse handleResponse) throws IOException, ServletException {
        JsonObject jsonInput = getJsonBodyForProxyDELETE.apply();

        if (camelToSnakeCaseConversion) {
            jsonInput = OAuth.convertCamelToSnakeCase(jsonInput);
        }

        Map<String, String> headers = getHeadersForProxy.apply();

        try {
            HttpRequest.Response response = OAuth.handleOAuthProxyJsonDELETE(main, appIdentifier, storage, path, proxyToAdmin, jsonInput, headers);

            if (camelToSnakeCaseConversion) {
                response.jsonResponse = OAuth.convertSnakeCaseToCamelCaseRecursively(response.jsonResponse);
            }

            handleResponse.apply(
                response.statusCode,
                response.headers,
                response.rawResponse,
                response.jsonResponse
            );

        } catch (OAuthClientNotFoundException e) {
            handleOAuthClientNotFoundException(resp);
        } catch (OAuthAPIException e) {
            handleOAuthAPIException(resp, e);
        } catch (StorageQueryException | TenantOrAppNotFoundException | FeatureNotEnabledException | InvalidConfigException e) {
            throw new ServletException(e);
        }
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

    @FunctionalInterface
    public interface GetQueryParamsForProxy {
        Map<String, String> apply() throws IOException, ServletException;
    }

    @FunctionalInterface
    public interface GetFormFieldsForProxy {
        Map<String, String> apply() throws IOException, ServletException;
    }

    @FunctionalInterface
    public interface GetJsonBody {
        JsonObject apply() throws IOException, ServletException;
    }

    @FunctionalInterface
    public interface GetHeadersForProxy {
        Map<String, String> apply() throws IOException, ServletException;
    }

    @FunctionalInterface
    public interface HandleResponse {
        void apply(int statusCode, Map<String, List<String>> headers, String rawBody, JsonElement jsonBody) throws IOException, ServletException;
    }

    private static void handleOAuthClientNotFoundException(HttpServletResponse resp) throws IOException {
        JsonObject response = new JsonObject();
        response.addProperty("status", "CLIENT_NOT_FOUND_ERROR");

        resp.setStatus(200);
        resp.setHeader("Content-Type", "application/json; charset=UTF-8");
        resp.getWriter().println(response.toString());
    }

    private static void handleOAuthAPIException(HttpServletResponse resp, OAuthAPIException e) throws IOException {
        JsonObject response = new JsonObject();
        response.addProperty("status", "OAUTH_ERROR");
        response.addProperty("error", e.error);
        if (e.errorDebug != null) {
            response.addProperty("errorDebug", e.errorDebug);
        }
        if (e.errorDescription != null) {
            response.addProperty("errorDescription", e.errorDescription);
        }
        if (e.errorHint != null) {
            response.addProperty("errorHint", e.errorHint);
        }
        response.addProperty("statusCode", e.statusCode);

        resp.setStatus(200);
        resp.setHeader("Content-Type", "application/json; charset=UTF-8");
        resp.getWriter().println(response.toString());
    }
}
