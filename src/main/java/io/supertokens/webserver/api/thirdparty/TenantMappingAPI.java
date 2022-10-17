package io.supertokens.webserver.api.thirdparty;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.fasterxml.jackson.databind.util.JSONPObject;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import io.supertokens.Main;
import io.supertokens.pluginInterface.RECIPE_ID;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.thirdparty.ThirdPartyTenantConfig;
import io.supertokens.pluginInterface.thirdparty.UserInfo.ThirdParty;
import io.supertokens.thirdparty.ThirdParty.CreateOrUpdateTenantMappingResponse;
import io.supertokens.webserver.InputParser;
import io.supertokens.webserver.WebserverAPI;

public class TenantMappingAPI extends WebserverAPI {

    private static final long serialVersionUID = -2225750492558065634L;

    public TenantMappingAPI(Main main) {
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

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {

        String supertokensTenantId = InputParser.getQueryParamOrThrowError(req, "supertokensTenantId", false);

        // normalize supertokensTenantId
        supertokensTenantId = supertokensTenantId.trim();

        if (supertokensTenantId.length() == 0) {
            throw new ServletException(
                    new WebserverAPI.BadRequestException("Field name 'supertokensTenantId' cannot be an empty String"));
        }

        String thirdPartyId = InputParser.getQueryParamOrThrowError(req, "thirdPartyId", false);

        // normalize thirdPartyId
        thirdPartyId = thirdPartyId.trim();

        if (thirdPartyId.length() == 0) {
            throw new ServletException(
                    new WebserverAPI.BadRequestException("Field name 'thirdPartyId' cannot be an empty String"));
        }

        try {
            ThirdPartyTenantConfig responseConfig = io.supertokens.thirdparty.ThirdParty.getThirdPartyTenantConfig(main,
                    supertokensTenantId, thirdPartyId);
            if (responseConfig == null) {
                JsonObject response = new JsonObject();
                response.addProperty("status", "CONFIG_NOT_FOUND_ERROR");
                super.sendJsonResponse(200, response, resp);
            }

            JsonObject response = new JsonObject();
            response.addProperty("status", "OK");
            response.add("config", new JsonParser().parse(responseConfig.config).getAsJsonObject());
            super.sendJsonResponse(200, response, resp);
        } catch (StorageQueryException e) {
            throw new ServletException(e);
        }
    }

}
