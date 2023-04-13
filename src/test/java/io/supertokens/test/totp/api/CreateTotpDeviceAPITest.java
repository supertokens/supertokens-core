package io.supertokens.test.totp.api;

import com.google.gson.JsonObject;
import io.supertokens.ProcessState;
import io.supertokens.featureflag.EE_FEATURES;
import io.supertokens.featureflag.FeatureFlag;
import io.supertokens.featureflag.FeatureFlagTestContent;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.test.TestingProcessManager;
import io.supertokens.test.Utils;
import io.supertokens.test.httpRequest.HttpRequestForTesting;
import io.supertokens.test.httpRequest.HttpResponseException;
import io.supertokens.test.totp.TotpLicenseTest;
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
                .setLicenseKeyAndSyncFeatures(TotpLicenseTest.OPAQUE_KEY_WITH_TOTP_FEATURE);
        FeatureFlagTestContent.getInstance(process.main)
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{EE_FEATURES.TOTP});

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        JsonObject body = new JsonObject();

        // Missing userId/deviceName/skew/period
        {
            Exception e = createDeviceRequest(process, body);
            checkFieldMissingErrorResponse(e, "userId");

            body.addProperty("userId", "");
            e = createDeviceRequest(process, body);
            checkFieldMissingErrorResponse(e, "deviceName");

            body.addProperty("deviceName", "");
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

            // try again with same device:
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
            assert res2.get("status").getAsString().equals("DEVICE_ALREADY_EXISTS_ERROR");
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }
}
