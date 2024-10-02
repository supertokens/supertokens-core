package io.supertokens.test.totp.api;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.supertokens.ProcessState;
import io.supertokens.featureflag.EE_FEATURES;
import io.supertokens.featureflag.FeatureFlag;
import io.supertokens.featureflag.FeatureFlagTestContent;
import io.supertokens.test.httpRequest.HttpResponseException;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.test.TestingProcessManager;
import io.supertokens.test.Utils;
import io.supertokens.test.httpRequest.HttpRequestForTesting;
import io.supertokens.test.totp.TotpLicenseTest;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import static org.junit.Assert.*;

import java.util.HashMap;

public class GetTotpDevicesAPITest {

    @Rule
    public TestRule watchman = Utils.getOnFailure();

    @AfterClass
    public static void afterTesting() {
        Utils.afterTesting();
    }

    @Before
    public void beforeEach() {
        Utils.reset();
    }

    private Exception getDevicesRequestException(TestingProcessManager.TestingProcess process,
                                                 HashMap<String, String> params) {

        return assertThrows(
                io.supertokens.test.httpRequest.HttpResponseException.class,
                () -> HttpRequestForTesting.sendGETRequest(
                        process.getProcess(),
                        "",
                        "http://localhost:3567/recipe/totp/device/list",
                        params,
                        1000,
                        1000,
                        null,
                        Utils.getCdiVersionStringLatestForTests(),
                        "totp"));
    }

    private void checkFieldMissingErrorResponse(Exception ex, String fieldName) {
        assert ex instanceof HttpResponseException;
        HttpResponseException e = (HttpResponseException) ex;
        assert e.statusCode == 400;
        assertTrue(e.getMessage().contains(
                "Http error. Status Code: 400. Message: Field name '" + fieldName + "' is missing in GET request"));
    }

    private void checkResponseErrorContains(Exception ex, String msg) {
        assert ex instanceof HttpResponseException;
        HttpResponseException e = (HttpResponseException) ex;
        assert e.statusCode == 400;
        assertTrue(e.getMessage().contains(msg));
    }

    @Test
    public void testApi() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        FeatureFlagTestContent.getInstance(process.main)
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{EE_FEATURES.MFA});

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        // Setup to create a device (which also creates a user)
        {
            JsonObject body = new JsonObject();
            body.addProperty("userId", "user-id");
            body.addProperty("deviceName", "device-name");
            body.addProperty("skew", 0);
            body.addProperty("period", 30);
            JsonObject res = HttpRequestForTesting.sendJsonPOSTRequest(
                    process.getProcess(),
                    "",
                    "http://localhost:3567/recipe/totp/device",
                    body,
                    1000,
                    1000,
                    null,
                    Utils.getCdiVersionStringLatestForTests(),
                    "totp");
            assert res.get("status").getAsString().equals("OK");
        }

        HashMap<String, String> params = new HashMap<>();

        // Missing userId
        {
            Exception e = getDevicesRequestException(process, params);
            checkFieldMissingErrorResponse(e, "userId");
        }

        // Invalid userId
        {
            params.put("userId", "");
            Exception e = getDevicesRequestException(process, params);
            checkResponseErrorContains(e, "userId cannot be empty"); // Note that this is not a field missing error

            params.put("userId", "user-id");

            // should pass now:
            JsonObject res = HttpRequestForTesting.sendGETRequest(
                    process.getProcess(),
                    "",
                    "http://localhost:3567/recipe/totp/device/list",
                    params,
                    1000,
                    1000,
                    null,
                    Utils.getCdiVersionStringLatestForTests(),
                    "totp");
            assert res.get("status").getAsString().equals("OK");

            JsonArray devicesArr = res.get("devices").getAsJsonArray();
            JsonObject deviceJson = devicesArr.get(0).getAsJsonObject();

            assert devicesArr.size() == 1;
            assert deviceJson.get("name").getAsString().equals("device-name");
            assert deviceJson.get("period").getAsInt() == 30;
            assert deviceJson.get("skew").getAsInt() == 0;
            assert deviceJson.get("verified").getAsBoolean() == false;

            // try for non-existent user:
            params.put("userId", "non-existent-user-id");
            JsonObject res2 = HttpRequestForTesting.sendGETRequest(
                    process.getProcess(),
                    "",
                    "http://localhost:3567/recipe/totp/device/list",
                    params,
                    1000,
                    1000,
                    null,
                    Utils.getCdiVersionStringLatestForTests(),
                    "totp");
            assert res2.get("status").getAsString().equals("OK");
            assert res2.get("devices").getAsJsonArray().size() == 0;
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

}
