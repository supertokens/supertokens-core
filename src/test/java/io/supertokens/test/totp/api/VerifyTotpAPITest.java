package io.supertokens.test.totp.api;

import com.google.gson.JsonObject;

import io.supertokens.ProcessState;
import io.supertokens.featureflag.EE_FEATURES;
import io.supertokens.featureflag.FeatureFlag;
import io.supertokens.featureflag.FeatureFlagTestContent;
import io.supertokens.test.httpRequest.HttpResponseException;
import io.supertokens.test.totp.TOTPRecipeTest;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.pluginInterface.totp.TOTPDevice;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.test.TestingProcessManager;
import io.supertokens.test.Utils;
import io.supertokens.test.httpRequest.HttpRequestForTesting;
import io.supertokens.test.httpRequest.HttpResponseException;
import io.supertokens.test.totp.TotpLicenseTest;
import io.supertokens.test.totp.TOTPRecipeTest;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import static org.junit.Assert.*;

public class VerifyTotpAPITest {

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
                () -> HttpRequestForTesting.sendJsonPOSTRequest(
                        process.getProcess(),
                        "",
                        "http://localhost:3567/recipe/totp/verify",
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
        String[] args = { "../" };

        // Trigger rate limiting on 1 wrong attempts:
        Utils.setValueInConfig("totp_max_attempts", "1");
        // Set cooldown to 1 second:
        Utils.setValueInConfig("totp_rate_limit_cooldown_sec", "1");

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        FeatureFlagTestContent.getInstance(process.main)
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{EE_FEATURES.TOTP});

        // Setup user and devices:
        JsonObject createDeviceReq = new JsonObject();
        createDeviceReq.addProperty("userId", "user-id");
        createDeviceReq.addProperty("deviceName", "deviceName");
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
        String secretKey = createDeviceRes.get("secret").getAsString();

        TOTPDevice device = new TOTPDevice("user-id", "deviceName", secretKey, 30, 0, false);

        // Start the actual tests for update device API:

        JsonObject body = new JsonObject();

        // Missing userId/deviceName/skew/period
        {
            Exception e = updateDeviceRequest(process, body);
            checkFieldMissingErrorResponse(e, "userId");

            body.addProperty("userId", "");
            e = updateDeviceRequest(process, body);
            checkFieldMissingErrorResponse(e, "totp");

            body.addProperty("totp", "");
            e = updateDeviceRequest(process, body);
            checkFieldMissingErrorResponse(e, "allowUnverifiedDevices");
        }

        // Invalid userId/deviceName/skew/period
        {
            body.addProperty("allowUnverifiedDevices", true);
            Exception e = updateDeviceRequest(process, body);
            checkResponseErrorContains(e, "userId cannot be empty"); // Note that this is not a field missing error

            body.addProperty("userId", device.userId);
            e = updateDeviceRequest(process, body);
            checkResponseErrorContains(e, "totp must be 6 characters long");

            // test totp of length 5:
            body.addProperty("totp", "12345");
            e = updateDeviceRequest(process, body);
            checkResponseErrorContains(e, "totp must be 6 characters long");

            // test totp of length 8:
            body.addProperty("totp", "12345678");
            e = updateDeviceRequest(process, body);
            checkResponseErrorContains(e, "totp must be 6 characters long");

            // but let's pass invalid code first
            body.addProperty("totp", "123456");
            JsonObject res0 = HttpRequestForTesting.sendJsonPOSTRequest(
                    process.getProcess(),
                    "",
                    "http://localhost:3567/recipe/totp/verify",
                    body,
                    1000,
                    1000,
                    null,
                    Utils.getCdiVersionStringLatestForTests(),
                    "totp");
            assert res0.get("status").getAsString().equals("INVALID_TOTP_ERROR");

            // Check that rate limiting is triggered for the user:
            JsonObject res3 = HttpRequestForTesting.sendJsonPOSTRequest(
                    process.getProcess(),
                    "",
                    "http://localhost:3567/recipe/totp/verify",
                    body,
                    1000,
                    1000,
                    null,
                    Utils.getCdiVersionStringLatestForTests(),
                    "totp");
            assert res3.get("status").getAsString().equals("LIMIT_REACHED_ERROR");
            assert res3.get("retryAfterMs") != null;

            // wait for cooldown to end (1s)
            Thread.sleep(1000);

            // should pass now on valid code
            String validTotp = TOTPRecipeTest.generateTotpCode(process.getProcess(), device);
            body.addProperty("totp", validTotp);
            JsonObject res = HttpRequestForTesting.sendJsonPOSTRequest(
                    process.getProcess(),
                    "",
                    "http://localhost:3567/recipe/totp/verify",
                    body,
                    1000,
                    1000,
                    null,
                    Utils.getCdiVersionStringLatestForTests(),
                    "totp");
            assert res.get("status").getAsString().equals("OK");

            // try to reuse the same code (replay attack)
            body.addProperty("totp", "mycode");
            JsonObject res2 = HttpRequestForTesting.sendJsonPOSTRequest(
                    process.getProcess(),
                    "",
                    "http://localhost:3567/recipe/totp/verify",
                    body,
                    1000,
                    1000,
                    null,
                    Utils.getCdiVersionStringLatestForTests(),
                    "totp");
            assert res2.get("status").getAsString().equals("INVALID_TOTP_ERROR");

            // Try with a new valid code during rate limiting:
            body.addProperty("totp", TOTPRecipeTest.generateTotpCode(process.getProcess(), device));
            res = HttpRequestForTesting.sendJsonPOSTRequest(
                    process.getProcess(),
                    "",
                    "http://localhost:3567/recipe/totp/verify",
                    body,
                    1000,
                    1000,
                    null,
                    Utils.getCdiVersionStringLatestForTests(),
                    "totp");
            assert res.get("status").getAsString().equals("LIMIT_REACHED_ERROR");

            // try verifying device for a non-existent user
            body.addProperty("userId", "non-existent-user");
            JsonObject res5 = HttpRequestForTesting.sendJsonPOSTRequest(
                    process.getProcess(),
                    "",
                    "http://localhost:3567/recipe/totp/verify",
                    body,
                    1000,
                    1000,
                    null,
                    Utils.getCdiVersionStringLatestForTests(),
                    "totp");
            assert res5.get("status").getAsString().equals("TOTP_NOT_ENABLED_ERROR");
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

}
