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

import static org.junit.Assert.*;

public class CreateTotpDeviceAPITest {

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

    private Exception createDeviceRequest(TestingProcessManager.TestingProcess process, JsonObject body) {
        return assertThrows(
                io.supertokens.test.httpRequest.HttpResponseException.class,
                () -> HttpRequestForTesting.sendJsonPOSTRequest(
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

        FeatureFlag.getInstance(process.main)
                .setLicenseKeyAndSyncFeatures(TotpLicenseTest.OPAQUE_KEY_WITH_MFA_FEATURE);
        FeatureFlagTestContent.getInstance(process.main)
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{EE_FEATURES.MFA});

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        JsonObject body = new JsonObject();

        // Missing userId/skew/period
        {
            Exception e = createDeviceRequest(process, body);
            checkFieldMissingErrorResponse(e, "userId");

            body.addProperty("deviceName", "");

            body.addProperty("userId", "");
            e = createDeviceRequest(process, body);
            checkFieldMissingErrorResponse(e, "skew");

            body.addProperty("skew", -1);
            e = createDeviceRequest(process, body);
            checkFieldMissingErrorResponse(e, "period");
        }

        // Invalid userId/deviceName/skew/period
        {
            body.addProperty("period", 0);
            Exception e = createDeviceRequest(process, body);
            checkResponseErrorContains(e, "userId cannot be empty"); // Note that this is not a field missing error

            body.addProperty("userId", "user-id");
            e = createDeviceRequest(process, body);
            checkResponseErrorContains(e, "deviceName cannot be empty");

            body.addProperty("deviceName", "d1");
            e = createDeviceRequest(process, body);
            checkResponseErrorContains(e, "skew must be >= 0");

            body.addProperty("skew", 0);
            e = createDeviceRequest(process, body);
            checkResponseErrorContains(e, "period must be > 0");

            body.addProperty("period", 30);

            // should pass now:
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
            assert res.get("deviceName").getAsString().equals("d1");

            // try again with same device name:
            // This should replace the previous device
            JsonObject res2 = HttpRequestForTesting.sendJsonPOSTRequest(
                    process.getProcess(),
                    "",
                    "http://localhost:3567/recipe/totp/device",
                    body,
                    1000,
                    1000,
                    null,
                    Utils.getCdiVersionStringLatestForTests(),
                    "totp");
            assert res2.get("status").getAsString().equals("OK");
            assert res.get("deviceName").getAsString().equals("d1");

            // verify d1
            {
                TOTPDevice device = Totp.getDevices(process.getProcess(), "user-id")[0];
                String validTotp = TOTPRecipeTest.generateTotpCode(process.getProcess(), device);
                Totp.verifyDevice(process.getProcess(), "user-id", "d1", validTotp);
            }

            // try again with same device name:
            res2 = HttpRequestForTesting.sendJsonPOSTRequest(
                    process.getProcess(),
                    "",
                    "http://localhost:3567/recipe/totp/device",
                    body,
                    1000,
                    1000,
                    null,
                    Utils.getCdiVersionStringLatestForTests(),
                    "totp");
            assert res2.get("status").getAsString().equals("DEVICE_ALREADY_EXISTS_ERROR");
            assert res.get("deviceName").getAsString().equals("d1");

            // try without passing deviceName:
            body.remove("deviceName");
            JsonObject res3 = HttpRequestForTesting.sendJsonPOSTRequest(
                    process.getProcess(),
                    "",
                    "http://localhost:3567/recipe/totp/device",
                    body,
                    1000,
                    1000,
                    null,
                    Utils.getCdiVersionStringLatestForTests(),
                    "totp");
            assert res3.get("status").getAsString().equals("OK");
            assert res3.get("deviceName").getAsString().equals("TOTP Device 1");
            String attempt1Secret = res3.get("secret").getAsString();

            // try again without passing deviceName:
            // should re-create the device since "TOTP Device 1" wasn't verified
            JsonObject res4 = HttpRequestForTesting.sendJsonPOSTRequest(
                    process.getProcess(),
                    "",
                    "http://localhost:3567/recipe/totp/device",
                    body,
                    1000,
                    1000,
                    null,
                    Utils.getCdiVersionStringLatestForTests(),
                    "totp");
            assert res4.get("status").getAsString().equals("OK");
            assert res3.get("deviceName").getAsString().equals("TOTP Device 1");
            String attempt2Secret = res4.get("secret").getAsString();
            assert !attempt1Secret.equals(attempt2Secret);

            // verify the device:
            TOTPDevice device = new TOTPDevice(
                    "user-id",
                    "TOTP Device 1",
                    attempt2Secret,
                    30,
                    0,
                    false,
                    System.currentTimeMillis()
            );
            JsonObject verifyDeviceBody = new JsonObject();
            verifyDeviceBody.addProperty("userId", device.userId);
            verifyDeviceBody.addProperty("deviceName", device.deviceName);
            verifyDeviceBody.addProperty("totp", TOTPRecipeTest.generateTotpCode(process.getProcess(), device));
            JsonObject res5 = HttpRequestForTesting.sendJsonPOSTRequest(
                    process.getProcess(),
                    "",
                    "http://localhost:3567/recipe/totp/device/verify",
                    verifyDeviceBody,
                    1000,
                    1000,
                    null,
                    Utils.getCdiVersionStringLatestForTests(),
                    "totp");
            assert res5.get("status").getAsString().equals("OK");

            // now try to create a device:
            // "TOTP Device 1" has been verified, it won't replace it
            JsonObject res6 = HttpRequestForTesting.sendJsonPOSTRequest(
                    process.getProcess(),
                    "",
                    "http://localhost:3567/recipe/totp/device",
                    body,
                    1000,
                    1000,
                    null,
                    Utils.getCdiVersionStringLatestForTests(),
                    "totp");
            assert res6.get("status").getAsString().equals("OK");
            assert res6.get("deviceName").getAsString().equals("TOTP Device 2");
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }
}
