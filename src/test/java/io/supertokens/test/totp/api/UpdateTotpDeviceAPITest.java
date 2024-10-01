package io.supertokens.test.totp.api;

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

public class UpdateTotpDeviceAPITest {

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

    private Exception updateDeviceRequest(TestingProcessManager.TestingProcess process, JsonObject body) {
        return assertThrows(
                io.supertokens.test.httpRequest.HttpResponseException.class,
                () -> HttpRequestForTesting.sendJsonPUTRequest(
                        process.getProcess(),
                        "",
                        "http://localhost:3567/recipe/totp/device",
                        body,
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
                "Http error. Status Code: 400. Message: Field name '" + fieldName + "' is invalid in JSON input"));
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

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        FeatureFlagTestContent.getInstance(process.main)
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{EE_FEATURES.MFA});

        // Setup user and devices:
        JsonObject createDeviceReq = new JsonObject();
        createDeviceReq.addProperty("userId", "user-id");
        createDeviceReq.addProperty("deviceName", "d1");
        createDeviceReq.addProperty("period", 30);
        createDeviceReq.addProperty("skew", 0);

        JsonObject createDeviceRes = HttpRequestForTesting.sendJsonPOSTRequest(
                process.getProcess(),
                "",
                "http://localhost:3567/recipe/totp/device",
                createDeviceReq,
                1000,
                1000,
                null,
                Utils.getCdiVersionStringLatestForTests(),
                "totp");
        assertEquals(createDeviceRes.get("status").getAsString(), "OK");

        // create another device d2:
        createDeviceReq.addProperty("deviceName", "d2");
        JsonObject createDeviceRes2 = HttpRequestForTesting.sendJsonPOSTRequest(
                process.getProcess(),
                "",
                "http://localhost:3567/recipe/totp/device",
                createDeviceReq,
                1000,
                1000,
                null,
                Utils.getCdiVersionStringLatestForTests(),
                "totp");
        assertEquals(createDeviceRes2.get("status").getAsString(), "OK");

        // Start the actual tests for update device API:

        JsonObject body = new JsonObject();

        // Missing userId/deviceName/skew/period
        {
            Exception e = updateDeviceRequest(process, body);
            checkFieldMissingErrorResponse(e, "userId");

            body.addProperty("userId", "");
            e = updateDeviceRequest(process, body);
            checkFieldMissingErrorResponse(e, "existingDeviceName");

            body.addProperty("existingDeviceName", "");
            e = updateDeviceRequest(process, body);
            checkFieldMissingErrorResponse(e, "newDeviceName");

        }

        // Invalid userId/deviceName/skew/period
        {
            body.addProperty("newDeviceName", "");
            Exception e = updateDeviceRequest(process, body);
            checkResponseErrorContains(e, "userId cannot be empty"); // Note that this is not a field missing error

            body.addProperty("userId", "user-id");
            e = updateDeviceRequest(process, body);
            checkResponseErrorContains(e, "existingDeviceName cannot be empty");

            body.addProperty("existingDeviceName", "d1");
            e = updateDeviceRequest(process, body);
            checkResponseErrorContains(e, "newDeviceName cannot be empty");

            body.addProperty("newDeviceName", "d1-new");

            // should pass now:
            JsonObject res = HttpRequestForTesting.sendJsonPUTRequest(
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

            // try again with same device (has been renamed so should fail)
            JsonObject res2 = HttpRequestForTesting.sendJsonPUTRequest(
                    process.getProcess(),
                    "",
                    "http://localhost:3567/recipe/totp/device",
                    body,
                    1000,
                    1000,
                    null,
                    Utils.getCdiVersionStringLatestForTests(),
                    "totp");
            assert res2.get("status").getAsString().equals("UNKNOWN_DEVICE_ERROR");

            // try renaming to a device that already exists
            body.addProperty("existingDeviceName", "d1-new");
            body.addProperty("newDeviceName", "d2");
            JsonObject res3 = HttpRequestForTesting.sendJsonPUTRequest(
                    process.getProcess(),
                    "",
                    "http://localhost:3567/recipe/totp/device",
                    body,
                    1000,
                    1000,
                    null,
                    Utils.getCdiVersionStringLatestForTests(),
                    "totp");
            assert res3.get("status").getAsString().equals("DEVICE_ALREADY_EXISTS_ERROR");

            // try renaming to a device that already exists for a non-existent user
            body.addProperty("userId", "non-existent-user");
            JsonObject res4 = HttpRequestForTesting.sendJsonPUTRequest(
                    process.getProcess(),
                    "",
                    "http://localhost:3567/recipe/totp/device",
                    body,
                    1000,
                    1000,
                    null,
                    Utils.getCdiVersionStringLatestForTests(),
                    "totp");
            assert res4.get("status").getAsString().equals("UNKNOWN_DEVICE_ERROR");
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

}
