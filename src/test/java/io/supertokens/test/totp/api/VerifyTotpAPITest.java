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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static io.supertokens.test.totp.TOTPRecipeTest.generateTotpCode;
import static org.junit.Assert.*;

public class VerifyTotpAPITest {

    @Rule
    public TestRule watchman = Utils.getOnFailure();

    @Rule
    public TestRule retryFlaky = Utils.retryFlakyTest();

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

        FeatureFlagTestContent.getInstance(process.getProcess())
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

    private JsonObject createDeviceForConcurrencyTest(TestingProcessManager.TestingProcess process, String userId)
            throws Exception {
        JsonObject createDeviceReq = new JsonObject();
        createDeviceReq.addProperty("userId", userId);
        createDeviceReq.addProperty("deviceName", "deviceName");
        createDeviceReq.addProperty("period", 30);
        createDeviceReq.addProperty("skew", 1);
        JsonObject res = HttpRequestForTesting.sendJsonPOSTRequest(
                process.getProcess(),
                "",
                "http://localhost:3567/recipe/totp/device",
                createDeviceReq,
                1000,
                1000,
                null,
                Utils.getCdiVersionStringLatestForTests(),
                "totp");
        assertEquals("OK", res.get("status").getAsString());
        return res;
    }

    /**
     * Sends all the requests to /recipe/totp/verify at the same time (each on its own thread, released together)
     * and returns how many responses had each status.
     */
    private Map<String, Integer> verifyConcurrently(TestingProcessManager.TestingProcess process, String userId,
                                                    List<String> codes) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(codes.size());
        try {
            CountDownLatch ready = new CountDownLatch(codes.size());
            CountDownLatch go = new CountDownLatch(1);
            List<Future<String>> futures = new ArrayList<>();
            for (String code : codes) {
                futures.add(executor.submit(() -> {
                    JsonObject body = new JsonObject();
                    body.addProperty("userId", userId);
                    body.addProperty("totp", code);
                    ready.countDown();
                    go.await();
                    JsonObject res = HttpRequestForTesting.sendJsonPOSTRequest(
                            process.getProcess(),
                            "",
                            "http://localhost:3567/recipe/totp/verify",
                            body,
                            30000,
                            30000,
                            null,
                            Utils.getCdiVersionStringLatestForTests(),
                            "totp");
                    return res.get("status").getAsString();
                }));
            }
            assertTrue(ready.await(30, TimeUnit.SECONDS));
            go.countDown();

            Map<String, Integer> statusCounts = new HashMap<>();
            for (Future<String> f : futures) {
                statusCounts.merge(f.get(60, TimeUnit.SECONDS), 1, Integer::sum);
            }
            return statusCounts;
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    public void testIssue11_concurrentVerifyCannotReuseCode() throws Exception {
        String[] args = {"../"};

        Utils.setValueInConfig("totp_max_attempts", "3");
        Utils.setValueInConfig("totp_rate_limit_cooldown_sec", "900");

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{EE_FEATURES.MFA});

        final int parallelRequests = 8; // below the default webserver and connection pool sizes (10)

        // 1. The same valid code sent concurrently is accepted at most once (exactly once: it is valid for the
        // whole test, as period = 30 and skew = 1, and the user has no failed attempts yet).
        {
            JsonObject deviceRes = createDeviceForConcurrencyTest(process, "user-replay");
            TOTPDevice device = new TOTPDevice("user-replay", "deviceName", deviceRes.get("secret").getAsString(),
                    30, 1, false, System.currentTimeMillis());
            // verify the device with the previous code, so that the current one is still unused
            JsonObject verifyDeviceReq = new JsonObject();
            verifyDeviceReq.addProperty("userId", "user-replay");
            verifyDeviceReq.addProperty("deviceName", "deviceName");
            verifyDeviceReq.addProperty("totp", generateTotpCode(process.getProcess(), device, -1));
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
            assertEquals("OK", verifyDeviceRes.get("status").getAsString());

            String code = generateTotpCode(process.getProcess(), device);
            List<String> codes = new ArrayList<>();
            for (int i = 0; i < parallelRequests; i++) {
                codes.add(code);
            }
            Map<String, Integer> statusCounts = verifyConcurrently(process, "user-replay", codes);
            assertEquals(statusCounts.toString(), Integer.valueOf(1), statusCounts.get("OK"));
            int rejected = statusCounts.getOrDefault("INVALID_TOTP_ERROR", 0)
                    + statusCounts.getOrDefault("LIMIT_REACHED_ERROR", 0);
            assertEquals(statusCounts.toString(), parallelRequests - 1, rejected);
        }

        // 2. Concurrent wrong codes are evaluated one after the other: exactly totp_max_attempts of them are
        // checked, the rest are rate limited.
        {
            JsonObject deviceRes = createDeviceForConcurrencyTest(process, "user-limit");
            TOTPDevice device = new TOTPDevice("user-limit", "deviceName", deviceRes.get("secret").getAsString(),
                    30, 1, false, System.currentTimeMillis());
            JsonObject verifyDeviceReq = new JsonObject();
            verifyDeviceReq.addProperty("userId", "user-limit");
            verifyDeviceReq.addProperty("deviceName", "deviceName");
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
            assertEquals("OK", verifyDeviceRes.get("status").getAsString());

            List<String> codes = new ArrayList<>();
            for (int i = 0; i < parallelRequests; i++) {
                codes.add("wrong" + i); // never a valid TOTP (not numeric)
            }
            Map<String, Integer> statusCounts = verifyConcurrently(process, "user-limit", codes);
            assertEquals(statusCounts.toString(), Integer.valueOf(3), statusCounts.get("INVALID_TOTP_ERROR"));
            assertEquals(statusCounts.toString(), Integer.valueOf(parallelRequests - 3),
                    statusCounts.get("LIMIT_REACHED_ERROR"));
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }
}
