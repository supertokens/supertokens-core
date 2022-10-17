package io.supertokens.webserver.api.thirdparty;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.fasterxml.jackson.databind.util.JSONPObject;
import com.google.gson.JsonObject;

import io.supertokens.Main;
import io.supertokens.pluginInterface.RECIPE_ID;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.thirdparty.UserInfo.ThirdParty;
import io.supertokens.thirdparty.ThirdParty.CreateOrUpdateTenantMappingResponse;
import io.supertokens.webserver.InputParser;
import io.supertokens.webserver.WebserverAPI;

public class CreateOrUpdateTenantMapping extends WebserverAPI {

    private static final long serialVersionUID = -2225750492558065634L;

    public CreateOrUpdateTenantMapping(Main main) {
        super(main, RECIPE_ID.THIRD_PARTY.toString());
    }

    @Override
    public String getPath() {
        return "/recipe/thirdparty/tenant/config";
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {
        JsonObject input = InputParser.parseJsonObjectOrThrowError(req);

        String supertokensTenantId = InputParser.parseStringOrThrowError(input, "supertokensTenantId", false);
        String thirdPartyId = InputParser.parseStringOrThrowError(input, "thirdPartyId", false);
        JsonObject config = InputParser.parseJsonObjectOrThrowError(input, "config", false);

        try {
            CreateOrUpdateTenantMappingResponse mappingResponse = io.supertokens.thirdparty.ThirdParty
                    .createOrUpdateThirdPartyTenantMapping(main, supertokensTenantId, thirdPartyId, config.toString());
            JsonObject response = new JsonObject();
            response.addProperty("status", "OK");
            response.addProperty("created", mappingResponse.wasCreated);
            response.addProperty("update", mappingResponse.wasUpdated);
            super.sendJsonResponse(200, response, resp);
        } catch (StorageQueryException e) {
            throw new ServletException(e);
        }
    }

}
