package io.supertokens.webserver.api.saml;

import io.supertokens.Main;
import io.supertokens.saml.SAML;
import io.supertokens.webserver.WebserverAPI;
import io.supertokens.featureflag.exceptions.FeatureNotEnabledException;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

public class SPMetadataAPI extends WebserverAPI {

    public SPMetadataAPI(Main main) {
        super(main, "saml");
    }

    @Override
    protected boolean checkAPIKey(HttpServletRequest req) {
        return false;
    }

    @Override
    public String getPath() {
        return "/.well-known/sp-metadata";
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {
        
        try {
            String metadataXML = SAML.getMetadataXML(
                    main, getTenantIdentifier(req)
            );

            super.sendXMLResponse(200, metadataXML, resp);

        } catch (TenantOrAppNotFoundException | StorageQueryException | FeatureNotEnabledException e) {
            throw new ServletException(e);
        }
    }
}
