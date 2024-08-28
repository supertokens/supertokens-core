package io.supertokens.webserver.api.oauth;

import java.io.IOException;
import java.io.Serial;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonObject;

import io.supertokens.Main;
import io.supertokens.featureflag.exceptions.FeatureNotEnabledException;
import io.supertokens.multitenancy.exception.BadPermissionException;
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

    public abstract ProxyProps getProxyProperties();

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {
        ProxyProps proxyProps = getProxyProperties();
        if (!Arrays.asList(proxyProps.apiMethods).contains(req.getMethod())) {
            this.sendTextResponse(405, "Method not supported", resp);
            return;
        }

        JsonObject input = InputParser.parseJsonObjectOrThrowError(req);

        if (proxyProps.method == "GET") {
            doProxyGetRequest(req, resp, proxyProps, input);
        } else if (proxyProps.method == "POST_FORM") {
            doProxyPostFormRequest(req, resp, proxyProps, input);
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
            OAuth.Response response = OAuth.handleOAuthProxyGET(main, appIdentifier, storage, proxyProps.path, queryParams, headers);

            handleResponseFromProxyGET(req, resp, response.statusCode, response.headers, response.rawResponse, response.jsonResponse);

        } catch (OAuthClientNotFoundException e) {
            JsonObject response = new JsonObject();
            response.addProperty("status", "CLIENT_NOT_FOUND_ERROR");

            this.sendJsonResponse(400, response, resp);

        } catch (OAuthAPIException e) {
            JsonObject response = new JsonObject();
            response.addProperty("status", "OAUTH_ERROR");
            response.addProperty("error", e.error);
            response.addProperty("error_debug", e.errorDebug);
            response.addProperty("error_description", e.errorDescription);
            response.addProperty("error_hint", e.errorHint);
            response.addProperty("status_code", e.statusCode);
            this.sendJsonResponse(200, response, resp);

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
            OAuth.Response response = OAuth.handleOAuthProxyFormPOST(main, appIdentifier, storage, proxyProps.path, formFields, headers);

            handleResponseFromProxyPOST(req, resp, input, response.statusCode, response.headers, response.rawResponse, response.jsonResponse);

        } catch (OAuthClientNotFoundException e) {
            JsonObject response = new JsonObject();
            response.addProperty("status", "CLIENT_NOT_FOUND_ERROR");

            this.sendJsonResponse(400, response, resp);

        } catch (OAuthAPIException e) {
            JsonObject response = new JsonObject();
            response.addProperty("status", "OAUTH_ERROR");
            response.addProperty("error", e.error);
            response.addProperty("error_debug", e.errorDebug);
            response.addProperty("error_description", e.errorDescription);
            response.addProperty("error_hint", e.errorHint);
            response.addProperty("status_code", e.statusCode);
            this.sendJsonResponse(200, response, resp);

        } catch (StorageQueryException | TenantOrAppNotFoundException | FeatureNotEnabledException | InvalidConfigException | BadPermissionException e) {
            throw new ServletException(e);
        }
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

    protected void handleResponseFromProxyGET(HttpServletRequest req, HttpServletResponse resp, int statusCode, Map<String, List<String>> headers, String rawBody, JsonObject jsonBody) throws IOException, ServletException {
        throw new IllegalStateException("Not implemented");
    }

    protected void handleResponseFromProxyPOST(HttpServletRequest req, HttpServletResponse resp, JsonObject input, int statusCode, Map<String, List<String>> headers, String rawBody, JsonObject jsonBody) throws IOException, ServletException {
        throw new IllegalStateException("Not implemented");
    }

    public static class ProxyProps {
        public final String[] apiMethods;
        public final String method;
        public final String path;
        public final boolean camelToSnakeCaseConversion;

        public ProxyProps(String[] apiMethods, String method, String path, boolean camelToSnakeCaseConversion) {
            this.apiMethods = apiMethods;
            this.method = method;
            this.path = path;
            this.camelToSnakeCaseConversion = camelToSnakeCaseConversion;
        }
    }
}
