package io.supertokens.webserver.api.thirdparty;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.google.gson.JsonObject;

import io.supertokens.Main;
import io.supertokens.pluginInterface.RECIPE_ID;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.thirdparty.ThirdParty;
import io.supertokens.webserver.InputParser;
import io.supertokens.webserver.WebserverAPI;

public class DeleteTenantMappingAPI extends WebserverAPI {
    private static final long serialVersionUID = -2225750694558065634L;

    public DeleteTenantMappingAPI(Main main) {
        super(main, RECIPE_ID.THIRD_PARTY.toString());
    }

    @Override
    public String getPath() {
        return "/recipe/thirdparty/tenant/config/remove";
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {
        JsonObject input = InputParser.parseJsonObjectOrThrowError(req);

        String supertokensTenantId = InputParser.parseStringOrThrowError(input, "supertokensTenantId", false);

        // normalize supertokensTenantId
        supertokensTenantId = supertokensTenantId.trim();

        if (supertokensTenantId.length() == 0) {
            throw new ServletException(
                    new WebserverAPI.BadRequestException("Field name 'supertokensTenantId' cannot be an empty String"));
        }

        String thirdPartyId = InputParser.parseStringOrThrowError(input, "thirdPartyId", false);

        // normalize thirdPartyId
        thirdPartyId = thirdPartyId.trim();

        if (thirdPartyId.length() == 0) {
            throw new ServletException(
                    new WebserverAPI.BadRequestException("Field name 'thirdPartyId' cannot be an empty String"));
        }

        try {
            Boolean deleteResponse = ThirdParty.deleteTenantMapping(main, supertokensTenantId, thirdPartyId);
            JsonObject response = new JsonObject();
            response.addProperty("status", "OK");
            response.addProperty("didConfigExist", deleteResponse);
            super.sendJsonResponse(200, response, resp);
        } catch (StorageQueryException e) {
            throw new ServletException(e);
        }
    }
}
