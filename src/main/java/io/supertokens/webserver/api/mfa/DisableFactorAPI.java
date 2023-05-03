package io.supertokens.webserver.api.mfa;

import com.google.gson.JsonObject;
import io.supertokens.Main;
import io.supertokens.TenantIdentifierWithStorageAndUserIdMapping;
import io.supertokens.featureflag.exceptions.FeatureNotEnabledException;
import io.supertokens.mfa.Mfa;
import io.supertokens.pluginInterface.RECIPE_ID;
import io.supertokens.pluginInterface.emailpassword.exceptions.UnknownUserIdException;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifierWithStorage;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.useridmapping.UserIdType;
import io.supertokens.webserver.InputParser;
import io.supertokens.webserver.WebserverAPI;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

public class DisableFactorAPI extends WebserverAPI {
    private static final long serialVersionUID = -4641988458637882374L;

    public DisableFactorAPI(Main main) {
        super(main, RECIPE_ID.MFA.toString());
    }

    @Override
    public String getPath() {
        return "/recipe/mfa/factors/disable";
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {
        // API is tenant specific
        JsonObject input = InputParser.parseJsonObjectOrThrowError(req);

        String userId = InputParser.parseStringOrThrowError(input, "userId", false);
        String factor = InputParser.parseStringOrThrowError(input, "factor", false).trim().toLowerCase();

        if (userId.isEmpty()) {
            throw new ServletException(new BadRequestException("userId cannot be empty"));
        }
        if (factor.isEmpty()) {
            throw new ServletException(new BadRequestException("factor cannot be empty"));
        }

        JsonObject result = new JsonObject();

        try {
            TenantIdentifierWithStorage tenantIdentifierWithStorage;
            try {
                // This step is required only because user_last_active table stores supertokens internal user id.
                // While sending the usage stats we do a join, so mfa tables must also use internal user id.

                // Try to find the tenantIdentifier with right storage based on the userId
                TenantIdentifierWithStorageAndUserIdMapping mappingAndStorage =
                        getTenantIdentifierWithStorageAndUserIdMappingFromRequest(
                                req, userId, UserIdType.ANY);

                if (mappingAndStorage.userIdMapping != null) {
                    userId = mappingAndStorage.userIdMapping.superTokensUserId;
                }
                tenantIdentifierWithStorage = mappingAndStorage.tenantIdentifierWithStorage;
            } catch (UnknownUserIdException e) {
                // if the user is not found, just use the storage of the tenant of interest
                tenantIdentifierWithStorage = getTenantIdentifierWithStorageFromRequest(req);
            }

            boolean actuallyDeleted = Mfa.disableFactor(tenantIdentifierWithStorage, main, userId, factor);

            result.addProperty("status", "OK");
            result.addProperty("didExist", actuallyDeleted);
            super.sendJsonResponse(200, result, resp);
        } catch (StorageQueryException | FeatureNotEnabledException | TenantOrAppNotFoundException e) {
            throw new ServletException(e);
        }
    }

}
