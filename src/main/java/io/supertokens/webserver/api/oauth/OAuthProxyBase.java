package io.supertokens.webserver.api.oauth;

import java.io.IOException;
import java.io.Serial;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonObject;

import io.supertokens.Main;
import io.supertokens.featureflag.exceptions.FeatureNotEnabledException;
import io.supertokens.multitenancy.exception.BadPermissionException;
import io.supertokens.oauth.HttpRequest;
import io.supertokens.oauth.OAuth;
import io.supertokens.oauth.exceptions.OAuthAPIException;
import io.supertokens.oauth.exceptions.OAuthClientNotFoundException;
import io.supertokens.pluginInterface.RECIPE_ID;
import io.supertokens.pluginInterface.Storage;
import io.supertokens.pluginInterface.exceptions.InvalidConfigException;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.multitenancy.AppIdentifier;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.webserver.InputParser;
import io.supertokens.webserver.WebserverAPI;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public abstract class OAuthProxyBase extends WebserverAPI {
    @Serial
    private static final long serialVersionUID = -8734479943734920904L;

    public OAuthProxyBase(Main main) {
        super(main, RECIPE_ID.OAUTH.toString());
    }

    public abstract ProxyProps[] getProxyProperties(HttpServletRequest req, JsonObject input) throws ServletException;

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {
        ProxyProps[] proxyPropsList = getProxyProperties(req, null);
        ProxyProps proxyProps = null;

        for (ProxyProps props : proxyPropsList) {
            if (props.apiMethod.equals(req.getMethod())) {
                proxyProps = props;
                break;
            }
        }

        if (proxyProps == null) {
            this.sendTextResponse(405, "Method not supported", resp);
            return;
        }

        if (proxyProps.method.equals("GET")) {
            doProxyGetRequest(req, resp, proxyProps, null);
        } else {
            this.sendTextResponse(405, "Method not supported", resp);
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {
        JsonObject input = InputParser.parseJsonObjectOrThrowError(req);

        ProxyProps[] proxyPropsList = getProxyProperties(req, input);
        ProxyProps proxyProps = null;

        for (ProxyProps props : proxyPropsList) {
            if (props.apiMethod.equals(req.getMethod())) {
                proxyProps = props;
                break;
            }
        }

        if (proxyProps == null) {
            this.sendTextResponse(405, "Method not supported", resp);
            return;
        }

        if (proxyProps.method.equals("GET")) {
            doProxyGetRequest(req, resp, proxyProps, input);
        } else if (proxyProps.method.equals("POST_FORM")) {
            doProxyPostFormRequest(req, resp, proxyProps, input);
        } else if (proxyProps.method.equals("POST_JSON")) {
            doProxyPostJsonRequest(req, resp, proxyProps, input);
        } else if (proxyProps.method.equals("DELETE_JSON")) {
            doProxyDeleteJsonRequest(req, resp, proxyProps, input);
        }
    }

    @Override
    protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {
        JsonObject input = InputParser.parseJsonObjectOrThrowError(req);

        ProxyProps[] proxyPropsList = getProxyProperties(req, input);
        ProxyProps proxyProps = null;

        for (ProxyProps props : proxyPropsList) {
            if (props.apiMethod.equals(req.getMethod())) {
                proxyProps = props;
                break;
            }
        }

        if (proxyProps == null) {
            this.sendTextResponse(405, "Method not supported", resp);
            return;
        }

        if (proxyProps.method.equals("PUT_JSON")) {
            doProxyPutJsonRequest(req, resp, proxyProps, input);
        } else {
            this.sendTextResponse(405, "Method not supported", resp);
        }
    }

    private void doProxyGetRequest(HttpServletRequest req, HttpServletResponse resp, ProxyProps proxyProps, JsonObject input)
            throws IOException, ServletException {
        Map<String, String> queryParams = getQueryParamsForProxy(req, input);

        if (proxyProps.camelToSnakeCaseConversion) {
            queryParams = OAuth.convertCamelToSnakeCase(queryParams);
        }

        Map<String, String> headers = getHeadersForProxy(req, input);

        try {
            AppIdentifier appIdentifier = getAppIdentifier(req);
            Storage storage = enforcePublicTenantAndGetPublicTenantStorage(req);
            HttpRequest.Response response = OAuth.handleOAuthProxyGET(main, appIdentifier, storage, proxyProps.path, proxyProps.proxyToAdmin, queryParams, headers);

            if (proxyProps.camelToSnakeCaseConversion) {
                response.jsonResponse = OAuth.convertSnakeCaseToCamelCaseRecursively(response.jsonResponse);
            }

            handleResponseFromProxyGET(req, resp, response.statusCode, response.headers, response.rawResponse, response.jsonResponse);

        } catch (OAuthClientNotFoundException e) {
            handleOAuthClientNotFoundException(resp);
        } catch (OAuthAPIException e) {
            handleOAuthAPIException(resp, e);
        } catch (StorageQueryException | TenantOrAppNotFoundException | FeatureNotEnabledException | InvalidConfigException | BadPermissionException e) {
            throw new ServletException(e);
        }
    }

    private void doProxyPostFormRequest(HttpServletRequest req, HttpServletResponse resp, ProxyProps proxyProps, JsonObject input)
            throws IOException, ServletException {
        Map<String, String> formFields = getFormFieldsForProxyPOST(req, input);

        if (proxyProps.camelToSnakeCaseConversion) {
            formFields = OAuth.convertCamelToSnakeCase(formFields);
        }

        Map<String, String> headers = getHeadersForProxy(req, input);

        try {
            AppIdentifier appIdentifier = getAppIdentifier(req);
            Storage storage = enforcePublicTenantAndGetPublicTenantStorage(req);
            HttpRequest.Response response = OAuth.handleOAuthProxyFormPOST(main, appIdentifier, storage, proxyProps.path, proxyProps.proxyToAdmin, formFields, headers);

            if (proxyProps.camelToSnakeCaseConversion) {
                response.jsonResponse = OAuth.convertSnakeCaseToCamelCaseRecursively(response.jsonResponse);
            }

            handleResponseFromProxyPOST(req, resp, input, response.statusCode, response.headers, response.rawResponse, response.jsonResponse);

        } catch (OAuthClientNotFoundException e) {
            handleOAuthClientNotFoundException(resp);
        } catch (OAuthAPIException e) {
            handleOAuthAPIException(resp, e);
        } catch (StorageQueryException | TenantOrAppNotFoundException | FeatureNotEnabledException | InvalidConfigException | BadPermissionException e) {
            throw new ServletException(e);
        }
    }

    private void doProxyPostJsonRequest(HttpServletRequest req, HttpServletResponse resp, ProxyProps proxyProps, JsonObject input)
            throws IOException, ServletException {
        JsonObject jsonInput = getJsonBodyForProxyPOST(req, input);

        if (proxyProps.camelToSnakeCaseConversion) {
            jsonInput = OAuth.convertCamelToSnakeCase(jsonInput);
        }

        Map<String, String> headers = getHeadersForProxy(req, input);

        try {
            AppIdentifier appIdentifier = getAppIdentifier(req);
            Storage storage = enforcePublicTenantAndGetPublicTenantStorage(req);
            HttpRequest.Response response = OAuth.handleOAuthProxyJsonPOST(main, appIdentifier, storage, proxyProps.path, proxyProps.proxyToAdmin, jsonInput, headers);

            if (proxyProps.camelToSnakeCaseConversion) {
                response.jsonResponse = OAuth.convertSnakeCaseToCamelCaseRecursively(response.jsonResponse);
            }

            handleResponseFromProxyPOST(req, resp, input, response.statusCode, response.headers, response.rawResponse, response.jsonResponse);

        } catch (OAuthClientNotFoundException e) {
            handleOAuthClientNotFoundException(resp);
        } catch (OAuthAPIException e) {
            handleOAuthAPIException(resp, e);
        } catch (StorageQueryException | TenantOrAppNotFoundException | FeatureNotEnabledException | InvalidConfigException | BadPermissionException e) {
            throw new ServletException(e);
        }
    }

    private void doProxyDeleteJsonRequest(HttpServletRequest req, HttpServletResponse resp, ProxyProps proxyProps, JsonObject input)
            throws IOException, ServletException {
        JsonObject jsonInput = getJsonBodyForProxyDELETE(req, input);

        if (proxyProps.camelToSnakeCaseConversion) {
            jsonInput = OAuth.convertCamelToSnakeCase(jsonInput);
        }

        Map<String, String> headers = getHeadersForProxy(req, input);

        try {
            AppIdentifier appIdentifier = getAppIdentifier(req);
            Storage storage = enforcePublicTenantAndGetPublicTenantStorage(req);
            HttpRequest.Response response = OAuth.handleOAuthProxyJsonDELETE(main, appIdentifier, storage, proxyProps.path, proxyProps.proxyToAdmin, jsonInput, headers);

            if (proxyProps.camelToSnakeCaseConversion) {
                response.jsonResponse = OAuth.convertSnakeCaseToCamelCaseRecursively(response.jsonResponse);
            }

            handleResponseFromProxyDELETE(req, resp, input, response.statusCode, response.headers, response.rawResponse, response.jsonResponse);

        } catch (OAuthClientNotFoundException e) {
            handleOAuthClientNotFoundException(resp);
        } catch (OAuthAPIException e) {
            handleOAuthAPIException(resp, e);
        } catch (StorageQueryException | TenantOrAppNotFoundException | FeatureNotEnabledException | InvalidConfigException | BadPermissionException e) {
            throw new ServletException(e);
        }
    }

    private void doProxyPutJsonRequest(HttpServletRequest req, HttpServletResponse resp, ProxyProps proxyProps, JsonObject input)
            throws IOException, ServletException {
        JsonObject jsonInput = getJsonBodyForProxyPUT(req, input);

        if (proxyProps.camelToSnakeCaseConversion) {
            jsonInput = OAuth.convertCamelToSnakeCase(jsonInput);
        }

        Map<String, String> headers = getHeadersForProxy(req, input);

        try {
            AppIdentifier appIdentifier = getAppIdentifier(req);
            Storage storage = enforcePublicTenantAndGetPublicTenantStorage(req);
            HttpRequest.Response response = OAuth.handleOAuthProxyJsonPUT(main, appIdentifier, storage, proxyProps.path, proxyProps.proxyToAdmin, jsonInput, headers);

            if (proxyProps.camelToSnakeCaseConversion) {
                response.jsonResponse = OAuth.convertSnakeCaseToCamelCaseRecursively(response.jsonResponse);
            }

            handleResponseFromProxyPUT(req, resp, input, response.statusCode, response.headers, response.rawResponse, response.jsonResponse);

        } catch (OAuthClientNotFoundException e) {
            handleOAuthClientNotFoundException(resp);
        } catch (OAuthAPIException e) {
            handleOAuthAPIException(resp, e);
        } catch (StorageQueryException | TenantOrAppNotFoundException | FeatureNotEnabledException | InvalidConfigException | BadPermissionException e) {
            throw new ServletException(e);
        }
    }

    private void handleOAuthClientNotFoundException(HttpServletResponse resp) throws IOException {
        JsonObject response = new JsonObject();
        response.addProperty("status", "CLIENT_NOT_FOUND_ERROR");
        this.sendJsonResponse(400, response, resp);
    }

    private void handleOAuthAPIException(HttpServletResponse resp, OAuthAPIException e) throws IOException {
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
        this.sendJsonResponse(200, response, resp);
    }

    protected Map<String, String> getQueryParamsForProxy(HttpServletRequest req, JsonObject input) throws IOException, ServletException {
        return null;
    }

    protected Map<String, String> getHeadersForProxy(HttpServletRequest req, JsonObject input) throws IOException, ServletException {
        return null;
    }

    protected Map<String, String> getFormFieldsForProxyPOST(HttpServletRequest req, JsonObject input) throws IOException, ServletException {
        return null;
    }

    protected JsonObject getJsonBodyForProxyPOST(HttpServletRequest req, JsonObject input) throws IOException, ServletException {
        return input;
    }

    protected JsonObject getJsonBodyForProxyPUT(HttpServletRequest req, JsonObject input) throws IOException, ServletException {
        return input;
    }

    protected JsonObject getJsonBodyForProxyDELETE(HttpServletRequest req, JsonObject input) throws IOException, ServletException {
        return input;
    }

    protected void handleResponseFromProxyGET(HttpServletRequest req, HttpServletResponse resp, int statusCode, Map<String, List<String>> headers, String rawBody, JsonObject jsonBody) throws IOException, ServletException {
        throw new IllegalStateException("Not implemented");
    }

    protected void handleResponseFromProxyPOST(HttpServletRequest req, HttpServletResponse resp, JsonObject input, int statusCode, Map<String, List<String>> headers, String rawBody, JsonObject jsonBody) throws IOException, ServletException {
        throw new IllegalStateException("Not implemented");
    }

    protected void handleResponseFromProxyPUT(HttpServletRequest req, HttpServletResponse resp, JsonObject input, int statusCode, Map<String, List<String>> headers, String rawBody, JsonObject jsonBody) throws IOException, ServletException {
        throw new IllegalStateException("Not implemented");
    }

    protected void handleResponseFromProxyDELETE(HttpServletRequest req, HttpServletResponse resp, JsonObject input, int statusCode, Map<String, List<String>> headers, String rawBody, JsonObject jsonBody) throws IOException, ServletException {
        throw new IllegalStateException("Not implemented");
    }

    public static class ProxyProps {
        public final String apiMethod;
        public final String method;
        public final String path;
        public final boolean proxyToAdmin;
        public final boolean camelToSnakeCaseConversion;

        public ProxyProps(String apiMethod, String method, String path, boolean proxyToAdmin, boolean camelToSnakeCaseConversion) {
            this.apiMethod = apiMethod;
            this.method = method;
            this.path = path;
            this.proxyToAdmin = proxyToAdmin;
            this.camelToSnakeCaseConversion = camelToSnakeCaseConversion;
        }
    }
}
