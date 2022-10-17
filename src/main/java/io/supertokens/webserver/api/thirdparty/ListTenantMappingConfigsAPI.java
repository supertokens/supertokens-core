package io.supertokens.webserver.api.thirdparty;

import java.io.IOException;
import java.util.ArrayList;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import io.supertokens.Main;
import io.supertokens.pluginInterface.RECIPE_ID;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.thirdparty.ThirdPartyTenantConfig;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.thirdparty.ThirdParty;
import io.supertokens.webserver.InputParser;
import io.supertokens.webserver.WebserverAPI;

public class ListTenantMappingConfigsAPI extends WebserverAPI {
    private static final long serialVersionUID = -2225750494558065634L;

    public ListTenantMappingConfigsAPI(Main main) {
        super(main, RECIPE_ID.THIRD_PARTY.toString());
    }

    @Override
    public String getPath() {
        return "/recipe/thirdparty/tenant/config/list";
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {
        String supertokensTenantId = InputParser.getQueryParamOrThrowError(req, "supertokensTenantId", true);
        String thirdPartyId = InputParser.getQueryParamOrThrowError(req, "thirdPartyId", true);

        if ((supertokensTenantId == null && thirdPartyId == null)
                || (supertokensTenantId != null && thirdPartyId != null)) {
            throw new ServletException(
                    new WebserverAPI.BadRequestException("Pass either `supertokensTenantId` or `thirdPartyId`"));
        }

        if (supertokensTenantId != null) {
            // normalize supertokensTenantId
            supertokensTenantId = supertokensTenantId.trim();

            if (supertokensTenantId.length() == 0) {
                throw new ServletException(new WebserverAPI.BadRequestException(
                        "Field name 'supertokensTenantId' cannot be an empty String"));
            }
        } else {
            // normalize thirdPartyId
            thirdPartyId = thirdPartyId.trim();

            if (thirdPartyId.length() == 0) {
                throw new ServletException(
                        new WebserverAPI.BadRequestException("Field name 'thirdPartyId' cannot be an empty String"));
            }
        }

        try {
            ThirdPartyTenantConfig[] responseConfigs = ThirdParty.listThirdPartyTenantConfigs(main, supertokensTenantId,
                    thirdPartyId);
            JsonArray jsonArray = new JsonArray();

            for (int i = 0; i < responseConfigs.length; i++) {
                JsonObject element = new JsonObject();
                element.addProperty("supertokensTenantId", responseConfigs[i].supertokensTenantId);
                element.addProperty("thirdPartyId", responseConfigs[i].thirdPartyId);
                element.add("config", new JsonParser().parse(responseConfigs[i].config).getAsJsonObject());
                jsonArray.add(element);
            }

            JsonObject response = new JsonObject();
            response.addProperty("status", "OK");
            response.add("configs", jsonArray);
            super.sendJsonResponse(200, response, resp);
        } catch (StorageQueryException e) {
            throw new ServletException(e);
        }
    }

}
