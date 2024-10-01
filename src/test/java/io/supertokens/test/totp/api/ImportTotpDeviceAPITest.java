package io.supertokens.test.totp.api;

import com.google.gson.JsonObject;
import io.supertokens.ProcessState;
import io.supertokens.featureflag.EE_FEATURES;
import io.supertokens.featureflag.FeatureFlag;
import io.supertokens.featureflag.FeatureFlagTestContent;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.pluginInterface.totp.TOTPDevice;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.test.TestingProcessManager;
import io.supertokens.test.Utils;
import io.supertokens.test.httpRequest.HttpRequestForTesting;
import io.supertokens.test.httpRequest.HttpResponseException;
import io.supertokens.test.totp.TOTPRecipeTest;
import io.supertokens.test.totp.TotpLicenseTest;
import io.supertokens.totp.Totp;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import static io.supertokens.test.totp.TOTPRecipeTest.generateTotpCode;
import static org.junit.Assert.*;

public class ImportTotpDeviceAPITest {

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

    private Exception importDeviceRequest(TestingProcessManager.TestingProcess process, JsonObject body) {
        return assertThrows(
                HttpResponseException.class,
                () -> HttpRequestForTesting.sendJsonPOSTRequest(
                        process.getProcess(),
                        "",
                        "http://localhost:3567/recipe/totp/device/import",
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

        FeatureFlag.getInstance(process.main)
                .setLicenseKeyAndSyncFeatures(TotpLicenseTest.OPAQUE_KEY_WITH_MFA_FEATURE);
        FeatureFlagTestContent.getInstance(process.main)
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{EE_FEATURES.MFA});

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        String secret = "ZNPARPDTO6BFVSOFM3BPJGORPYTNTDSF";

        JsonObject body = new JsonObject();

        // Missing userId/skew/period
        {
            Exception e = importDeviceRequest(process, body);
            checkFieldMissingErrorResponse(e, "userId");

            body.addProperty("deviceName", "");

            body.addProperty("userId", "");
            e = importDeviceRequest(process, body);
            checkFieldMissingErrorResponse(e, "skew");

            body.addProperty("skew", -1);
            e = importDeviceRequest(process, body);
            checkFieldMissingErrorResponse(e, "period");

            body.addProperty("period", 0);
            e = importDeviceRequest(process, body);
            checkFieldMissingErrorResponse(e, "secretKey");

        }

        // Invalid userId/deviceName/skew/period
        {
            body.addProperty("secretKey", "");
            Exception e = importDeviceRequest(process, body);
            checkResponseErrorContains(e, "userId cannot be empty"); // Note that this is not a field missing error

            body.addProperty("userId", "user-id");
            e = importDeviceRequest(process, body);
            checkResponseErrorContains(e, "deviceName cannot be empty");

            body.addProperty("deviceName", "d1");
            e = importDeviceRequest(process, body);
            checkResponseErrorContains(e, "secretKey cannot be empty");

            body.addProperty("secretKey", secret);
            e = importDeviceRequest(process, body);
            checkResponseErrorContains(e, "skew must be >= 0");

            body.addProperty("skew", 0);
            e = importDeviceRequest(process, body);
            checkResponseErrorContains(e, "period must be > 0");

            body.addProperty("period", 30);

            // should pass now:
            JsonObject res = HttpRequestForTesting.sendJsonPOSTRequest(
                    process.getProcess(),
                    "",
                    "http://localhost:3567/recipe/totp/device/import",
                    body,
                    1000,
                    1000,
                    null,
                    Utils.getCdiVersionStringLatestForTests(),
                    "totp");
            assert res.get("status").getAsString().equals("OK");
            assertEquals("d1", res.get("deviceName").getAsString());

            // try again with same device name:
            JsonObject res2 = HttpRequestForTesting.sendJsonPOSTRequest(
                    process.getProcess(),
                    "",
                    "http://localhost:3567/recipe/totp/device/import",
                    body,
                    1000,
                    1000,
                    null,
                    Utils.getCdiVersionStringLatestForTests(),
                    "totp");
            assert res2.get("status").getAsString().equals("DEVICE_ALREADY_EXISTS_ERROR");
        }

        // Verify totp on the imported device
        TOTPDevice device = new TOTPDevice("user-id", "d1", secret, 30, 0, false, System.currentTimeMillis());

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

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testApiWithoutDeviceName() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        FeatureFlag.getInstance(process.main)
                .setLicenseKeyAndSyncFeatures(TotpLicenseTest.OPAQUE_KEY_WITH_MFA_FEATURE);
        FeatureFlagTestContent.getInstance(process.main)
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{EE_FEATURES.MFA});

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        {
            String secret = "ZNPARPDTO6BFVSOFM3BPJGORPYTNTDSF";

            JsonObject body = new JsonObject();
            body.addProperty("secretKey", "");
            body.addProperty("userId", "user-id");
            body.addProperty("secretKey", secret);
            body.addProperty("skew", 0);
            body.addProperty("period", 30);

            JsonObject res = HttpRequestForTesting.sendJsonPOSTRequest(
                    process.getProcess(),
                    "",
                    "http://localhost:3567/recipe/totp/device/import",
                    body,
                    1000,
                    1000,
                    null,
                    Utils.getCdiVersionStringLatestForTests(),
                    "totp");
            assert res.get("status").getAsString().equals("OK");
            assertEquals("TOTP Device 0", res.get("deviceName").getAsString());
        }

        { // Check for device already exists
            String secret = "ZNPARPDTO6BFVSOFM3BPJGORPYTNTDSF";

            JsonObject body = new JsonObject();
            body.addProperty("secretKey", "");
            body.addProperty("userId", "user-id");
            body.addProperty("secretKey", secret);
            body.addProperty("skew", 0);
            body.addProperty("period", 30);
            body.addProperty("deviceName", "TOTP Device 0");

            JsonObject res = HttpRequestForTesting.sendJsonPOSTRequest(
                    process.getProcess(),
                    "",
                    "http://localhost:3567/recipe/totp/device/import",
                    body,
                    1000,
                    1000,
                    null,
                    Utils.getCdiVersionStringLatestForTests(),
                    "totp");
            assert res.get("status").getAsString().equals("DEVICE_ALREADY_EXISTS_ERROR");
        }
    }
}
