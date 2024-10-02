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

import java.io.IOException;

import static io.supertokens.test.totp.TOTPRecipeTest.generateTotpCode;
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

    private Exception verifyTotpCodeRequest(TestingProcessManager.TestingProcess process, JsonObject body) {
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

    private void verifyTotpRequestThatReturnsInvalidCode(TestingProcessManager.TestingProcess process, JsonObject body)
            throws HttpResponseException, IOException {
        JsonObject resp = HttpRequestForTesting.sendJsonPOSTRequest(
                process.getProcess(),
                "",
                "http://localhost:3567/recipe/totp/verify",
                body,
                1000,
                1000,
                null,
                Utils.getCdiVersionStringLatestForTests(),
                "totp");
        assertEquals("INVALID_TOTP_ERROR", resp.get("status").getAsString());
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

        // Trigger rate limiting on 1 wrong attempts:
        Utils.setValueInConfig("totp_max_attempts", "2");
        // Set cooldown to 1 second:
        Utils.setValueInConfig("totp_rate_limit_cooldown_sec", "1");

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
        createDeviceReq.addProperty("deviceName", "deviceName");
        createDeviceReq.addProperty("period", 2);
        createDeviceReq.addProperty("skew", 1);

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

        TOTPDevice device = new TOTPDevice("user-id", "deviceName", secretKey, 2, 0, false, System.currentTimeMillis());

        JsonObject verifyDeviceReq = new JsonObject();
        verifyDeviceReq.addProperty("userId", device.userId);
        verifyDeviceReq.addProperty("deviceName", device.deviceName);
        verifyDeviceReq.addProperty("totp", generateTotpCode(process.getProcess(), device));

        JsonObject verifyDeviceRes = HttpRequestForTesting.sendJsonPOSTRequest(
                process.getProcess(),
                "",
                "http://localhost:3567/recipe/totp/device/verify",
                verifyDeviceReq,
                1000,
                1000,
                null,
                Utils.getCdiVersionStringLatestForTests(),
                "totp");
        assertEquals(verifyDeviceRes.get("status").getAsString(), "OK");

        // Start the actual tests for update device API:
        JsonObject body = new JsonObject();

        // Missing userId/deviceName/skew/period
        {
            Exception e = verifyTotpCodeRequest(process, body);
            checkFieldMissingErrorResponse(e, "userId");

            body.addProperty("userId", "");
            e = verifyTotpCodeRequest(process, body);
            checkFieldMissingErrorResponse(e, "totp");
        }

        // Invalid userId/deviceName/skew/period
        {
            body.addProperty("totp", "");
            Exception e = verifyTotpCodeRequest(process, body);
            checkResponseErrorContains(e, "userId cannot be empty"); // Note that this is not a field missing error

            body.addProperty("userId", device.userId);
            verifyTotpRequestThatReturnsInvalidCode(process, body);

            Thread.sleep(1100);

            // test totp of length 5:
            body.addProperty("totp", "12345");
            verifyTotpRequestThatReturnsInvalidCode(process, body);

            Thread.sleep(1100);

            // test totp of alphabets:
            body.addProperty("totp", "abcd");
            verifyTotpRequestThatReturnsInvalidCode(process, body);

            Thread.sleep(1100);

            // test totp of length 8:
            body.addProperty("totp", "12345678");
            verifyTotpRequestThatReturnsInvalidCode(process, body);

            Thread.sleep(1100);

            // test totp of more than length 8:
            body.addProperty("totp", "123456781234");
            verifyTotpRequestThatReturnsInvalidCode(process, body);

            Thread.sleep(1100);

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

            res0 = HttpRequestForTesting.sendJsonPOSTRequest(
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
            Thread.sleep(2000);

            // should pass now on valid code
            String validTotp = generateTotpCode(process.getProcess(), device);
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

            res2 = HttpRequestForTesting.sendJsonPOSTRequest(
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
            body.addProperty("totp", generateTotpCode(process.getProcess(), device));
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
            assert res5.get("status").getAsString().equals("UNKNOWN_USER_ID_ERROR");
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

}
