package io.supertokens.webserver.api.saml;

import java.io.IOException;
import java.security.cert.CertificateException;

import org.opensaml.core.xml.io.UnmarshallingException;

import io.supertokens.Main;
import io.supertokens.multitenancy.exception.BadPermissionException;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.saml.SAML;
import io.supertokens.saml.exceptions.IDPInitiatedLoginDisallowedException;
import io.supertokens.saml.exceptions.InvalidClientException;
import io.supertokens.saml.exceptions.InvalidRelayStateException;
import io.supertokens.saml.exceptions.SAMLResponseVerificationFailedException;
import io.supertokens.webserver.WebserverAPI;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import net.shibboleth.utilities.java.support.xml.XMLParserException;

public class LegacyCallbackAPI extends WebserverAPI {
    public LegacyCallbackAPI(Main main) {
        super(main, "saml");
    }

    @Override
    public String getPath() {
        return "/recipe/saml/legacy/callback";
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {
        String samlResponse = req.getParameter("SAMLResponse");
        if (samlResponse == null) {
            samlResponse = req.getParameter("samlResponse");
        }

        String relayState = req.getParameter("RelayState");
        if (relayState == null) {
            relayState = req.getParameter("relayState");
        }

        if (samlResponse == null || samlResponse.isBlank()) {
            throw new ServletException(new BadRequestException("Missing form field: SAMLResponse"));
        }

        try {
            String redirectURI = SAML.handleCallback(
                    main,
                    getTenantIdentifier(req),
                    enforcePublicTenantAndGetPublicTenantStorage(req),
                    samlResponse,
                    relayState
            );

            resp.sendRedirect(redirectURI, 302);
        } catch (InvalidRelayStateException e) {
            sendTextResponse(400, "INVALID_RELAY_STATE_ERROR", resp);
        } catch (InvalidClientException e) {
            sendTextResponse(400, "INVALID_CLIENT_ERROR", resp);
        } catch (SAMLResponseVerificationFailedException e) {
            sendTextResponse(400, "SAML_RESPONSE_VERIFICATION_FAILED_ERROR", resp);
        } catch (IDPInitiatedLoginDisallowedException e) {
            sendTextResponse(400, "IDP_LOGIN_DISALLOWED_ERROR", resp);
        } catch (TenantOrAppNotFoundException | StorageQueryException | UnmarshallingException | XMLParserException |
                 CertificateException | BadPermissionException e) {
            throw new ServletException(e);
        }
    }
}
