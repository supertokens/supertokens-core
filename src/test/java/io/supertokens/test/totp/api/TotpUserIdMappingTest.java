package io.supertokens.test.totp.api;

import com.google.gson.JsonObject;
import io.supertokens.ProcessState;
import io.supertokens.emailpassword.EmailPassword;
import io.supertokens.featureflag.EE_FEATURES;
import io.supertokens.featureflag.FeatureFlagTestContent;
import io.supertokens.pluginInterface.authRecipe.AuthRecipeUserInfo;
import io.supertokens.pluginInterface.totp.TOTPDevice;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.test.TestingProcessManager;
import io.supertokens.test.Utils;
import io.supertokens.test.httpRequest.HttpRequestForTesting;
import io.supertokens.test.totp.TOTPRecipeTest;
import io.supertokens.useridmapping.UserIdMapping;

import static org.junit.Assert.assertNotNull;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

public class TotpUserIdMappingTest {
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

    @Test
    public void testExternalUserIdTranslation() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        FeatureFlagTestContent.getInstance(process.main)
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{EE_FEATURES.MFA});

        JsonObject body = new JsonObject();

        AuthRecipeUserInfo user = EmailPassword.signUp(process.main, "test@example.com", "testPass123");
        String superTokensUserId = user.getSupertokensUserId();
        String externalUserId = "external-user-id";

        // Create user id mapping first:
        UserIdMapping.createUserIdMapping(process.main, superTokensUserId, externalUserId, null, false);

        body.addProperty("userId", externalUserId);
        body.addProperty("deviceName", "d1");
        body.addProperty("skew", 1);
        body.addProperty("period", 30);

        // Register 1st device
        JsonObject res1 = HttpRequestForTesting.sendJsonPOSTRequest(
                process.getProcess(),
                "",
                "http://localhost:3567/recipe/totp/device",
                body,
                1000,
                1000,
                null,
                Utils.getCdiVersionStringLatestForTests(),
                "totp");
        assert res1.get("status").getAsString().equals("OK");
        String d1Secret = res1.get("secret").getAsString();
        TOTPDevice device1 = new TOTPDevice(externalUserId, "d1", d1Secret, 30, 0, false, System.currentTimeMillis());

        body.addProperty("deviceName", "d2");

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
        String d2Secret = res2.get("secret").getAsString();
        TOTPDevice device2 = new TOTPDevice(externalUserId, "d2", d2Secret, 30, 0, false, System.currentTimeMillis());

        // Verify d1 but not d2:
        JsonObject verifyD1Input = new JsonObject();
        verifyD1Input.addProperty("userId", externalUserId);
        String d1VerifyTotp = TOTPRecipeTest.generateTotpCode(process.getProcess(), device1);
        verifyD1Input.addProperty("deviceName", "d1");
        verifyD1Input.addProperty("totp", d1VerifyTotp);

        JsonObject verifyD1Res = HttpRequestForTesting.sendJsonPOSTRequest(
                process.getProcess(),
                "",
                "http://localhost:3567/recipe/totp/device/verify",
                verifyD1Input,
                1000,
                1000,
                null,
                Utils.getCdiVersionStringLatestForTests(),
                "totp");

        assert verifyD1Res.get("status").getAsString().equals("OK");
        assert verifyD1Res.get("wasAlreadyVerified").getAsBoolean() == false;

        // use d2 to login in totp: (should fail coz it's not verified)
        JsonObject d2LoginInput = new JsonObject();
        d2LoginInput.addProperty("userId", externalUserId);
        String d2Totp = TOTPRecipeTest.generateTotpCode(process.getProcess(), device2, 1);
        d2LoginInput.addProperty("totp", d2Totp); // use code from d2 which is unverified

        JsonObject d2LoginRes = HttpRequestForTesting.sendJsonPOSTRequest(
                process.getProcess(),
                "",
                "http://localhost:3567/recipe/totp/verify",
                d2LoginInput,
                1000,
                1000,
                null,
                Utils.getCdiVersionStringLatestForTests(),
                "totp");

        assert d2LoginRes.get("status").getAsString().equals("INVALID_TOTP_ERROR");

        // use d1 to login in totp: (should pass)
        JsonObject d1LoginInput = new JsonObject();
        d1LoginInput.addProperty("userId", externalUserId);
        String d1Totp = TOTPRecipeTest.generateTotpCode(process.getProcess(), device1, 1);
        d1LoginInput.addProperty("totp", d1Totp); // use code from d2 which is unverified

        JsonObject d1LoginRes = HttpRequestForTesting.sendJsonPOSTRequest(
                process.getProcess(),
                "",
                "http://localhost:3567/recipe/totp/verify",
                d1LoginInput,
                1000,
                1000,
                null,
                Utils.getCdiVersionStringLatestForTests(),
                "totp");

        assert d1LoginRes.get("status").getAsString().equals("OK");

        // Change the name of d1 to d3:
        JsonObject updateDeviceNameInput = new JsonObject();
        updateDeviceNameInput.addProperty("userId", externalUserId);
        updateDeviceNameInput.addProperty("existingDeviceName", "d1");
        updateDeviceNameInput.addProperty("newDeviceName", "d3");

        JsonObject updateDeviceNameRes = HttpRequestForTesting.sendJsonPUTRequest(
                process.getProcess(),
                "",
                "http://localhost:3567/recipe/totp/device",
                updateDeviceNameInput,
                1000,
                1000,
                null,
                Utils.getCdiVersionStringLatestForTests(),
                "totp");

        assert updateDeviceNameRes.get("status").getAsString().equals("OK");

        // Delete d3:
        JsonObject deleteDeviceInput = new JsonObject();
        deleteDeviceInput.addProperty("userId", externalUserId);
        deleteDeviceInput.addProperty("deviceName", "d3");

        JsonObject deleteDeviceRes = HttpRequestForTesting.sendJsonPOSTRequest(
                process.getProcess(),
                "",
                "http://localhost:3567/recipe/totp/device/remove",
                deleteDeviceInput,
                1000,
                1000,
                null,
                Utils.getCdiVersionStringLatestForTests(),
                "totp");


        assert deleteDeviceRes.get("status").getAsString().equals("OK");
        assert deleteDeviceRes.get("didDeviceExist").getAsBoolean() == true;

    }
}
