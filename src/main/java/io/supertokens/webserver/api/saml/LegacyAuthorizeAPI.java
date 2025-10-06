package io.supertokens.webserver.api.saml;

import java.io.IOException;
import java.security.cert.CertificateEncodingException;

import com.google.gson.JsonObject;

import io.supertokens.Main;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.saml.SAML;
import io.supertokens.saml.exceptions.InvalidClientException;
import io.supertokens.webserver.InputParser;
import io.supertokens.webserver.WebserverAPI;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class LegacyAuthorizeAPI extends WebserverAPI  {

    public LegacyAuthorizeAPI(Main main) {
        super(main, "saml");
    }

    @Override
    public String getPath() {
        return "/recipe/saml/legacy/authorize";
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {
        String clientId = InputParser.getQueryParamOrThrowError(req, "client_id", false);
        String redirectURI = InputParser.getQueryParamOrThrowError(req, "redirect_uri", false);
        String state = InputParser.getQueryParamOrThrowError(req, "state", true);
        String acsURL = "http://localhost:5225/api/oauth/saml"; // TODO get from settings

        try {
            String ssoRedirectURI = SAML.createRedirectURL(
                    main,
                    getTenantIdentifier(req),
                    getTenantStorage(req),
                    clientId,
                    redirectURI,
                    state,
                    acsURL);
            
            resp.sendRedirect(ssoRedirectURI, 307);

        } catch (InvalidClientException e) {
            JsonObject res = new JsonObject();
            res.addProperty("status", "INVALID_CLIENT_ERROR");
            super.sendJsonResponse(200, res, resp);
        } catch (TenantOrAppNotFoundException | StorageQueryException | CertificateEncodingException e) {
            throw new ServletException(e);
        }
    }
}
