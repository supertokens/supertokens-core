package io.supertokens.test.webauthn.api;

import com.google.gson.JsonObject;
import io.supertokens.ProcessState;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.test.TestingProcessManager;
import io.supertokens.test.Utils;
import io.supertokens.test.httpRequest.HttpRequestForTesting;
import io.supertokens.test.httpRequest.HttpResponseException;
import io.supertokens.utils.SemVer;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import static org.junit.Assert.*;

public class TestAndroidOriginValidation {
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

    @Test
    public void testValidAndroidOrigin() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        JsonObject req = new JsonObject();
        req.addProperty("email", "test@example.com");
        req.addProperty("relyingPartyName", "Example");
        req.addProperty("relyingPartyId", "example.com");
        req.addProperty("origin", "android:apk-key-hash:47DEQpj8HBSa-_TImW-5JCeuQeRkm5NMpJWZG3hSuFU");

        try {
            JsonObject resp = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/webauthn/options/register", req, 1000, 1000, null,
                    SemVer.v5_3.get(), "webauthn");
            assertEquals("OK", resp.get("status").getAsString());
        } catch (HttpResponseException e) {
            fail("Valid Android origin should be accepted: " + e.getMessage());
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testValidAndroidOriginWithAlternativeHash() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        JsonObject req = new JsonObject();
        req.addProperty("email", "test@example.com");
        req.addProperty("relyingPartyName", "Example");
        req.addProperty("relyingPartyId", "example.com");
        req.addProperty("origin", "android:apk-key-hash:sYUC8p5I9SxqFernBPHmDxz_YVZXmVJdW8s-m3RTTqE");

        try {
            JsonObject resp = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/webauthn/options/register", req, 1000, 1000, null,
                    SemVer.v5_3.get(), "webauthn");
            assertEquals("OK", resp.get("status").getAsString());
        } catch (HttpResponseException e) {
            fail("Valid Android origin with alternative hash should be accepted: " + e.getMessage());
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testAndroidOriginWithEmptyHash() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        JsonObject req = new JsonObject();
        req.addProperty("email", "test@example.com");
        req.addProperty("relyingPartyName", "Example");
        req.addProperty("relyingPartyId", "example.com");
        req.addProperty("origin", "android:apk-key-hash:");

        JsonObject resp = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/webauthn/options/register", req, 1000, 1000, null,
                SemVer.v5_3.get(), "webauthn");
        assertEquals("INVALID_OPTIONS_ERROR", resp.get("status").getAsString());
        assertTrue(resp.get("reason").getAsString().contains("Android origin must contain a valid base64 hash"));

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testAndroidOriginWithInvalidCharacters() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        JsonObject req = new JsonObject();
        req.addProperty("email", "test@example.com");
        req.addProperty("relyingPartyName", "Example");
        req.addProperty("relyingPartyId", "example.com");
        req.addProperty("origin", "android:apk-key-hash:invalid@hash#with$special!");

        JsonObject resp = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/webauthn/options/register", req, 1000, 1000, null,
                SemVer.v5_3.get(), "webauthn");
        assertEquals("INVALID_OPTIONS_ERROR", resp.get("status").getAsString());
        assertTrue(resp.get("reason").getAsString().contains("Android origin hash must be valid URL-safe base64"));

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testAndroidOriginWithInvalidLength() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        JsonObject req = new JsonObject();
        req.addProperty("email", "test@example.com");
        req.addProperty("relyingPartyName", "Example");
        req.addProperty("relyingPartyId", "example.com");
        req.addProperty("origin", "android:apk-key-hash:abc");

        JsonObject resp = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/webauthn/options/register", req, 1000, 1000, null,
                SemVer.v5_3.get(), "webauthn");
        assertEquals("INVALID_OPTIONS_ERROR", resp.get("status").getAsString());
        assertTrue(resp.get("reason").getAsString().contains("Android origin hash must be 43 characters"));

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testAndroidOriginForSignInOptions() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        JsonObject req = new JsonObject();
        req.addProperty("relyingPartyName", "Example");
        req.addProperty("relyingPartyId", "example.com");
        req.addProperty("origin", "android:apk-key-hash:47DEQpj8HBSa-_TImW-5JCeuQeRkm5NMpJWZG3hSuFU");
        req.addProperty("timeout", 10000);
        req.addProperty("userVerification", "preferred");
        req.addProperty("userPresence", false);

        try {
            JsonObject resp = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/webauthn/options/signin", req, 1000, 1000, null,
                    SemVer.v5_3.get(), "webauthn");
            assertEquals("OK", resp.get("status").getAsString());
        } catch (HttpResponseException e) {
            fail("Valid Android origin should be accepted for signin options: " + e.getMessage());
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testMixedOriginsSupport() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        // Test that regular HTTP origins still work
        JsonObject req1 = new JsonObject();
        req1.addProperty("email", "test1@example.com");
        req1.addProperty("relyingPartyName", "Example");
        req1.addProperty("relyingPartyId", "example.com");
        req1.addProperty("origin", "http://example.com");

        try {
            JsonObject resp = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/webauthn/options/register", req1, 1000, 1000, null,
                    SemVer.v5_3.get(), "webauthn");
            assertEquals("OK", resp.get("status").getAsString());
        } catch (HttpResponseException e) {
            fail("Regular HTTP origin should still work: " + e.getMessage());
        }

        // Test that HTTPS origins still work
        JsonObject req2 = new JsonObject();
        req2.addProperty("email", "test2@example.com");
        req2.addProperty("relyingPartyName", "Example");
        req2.addProperty("relyingPartyId", "example.com");
        req2.addProperty("origin", "https://example.com");

        try {
            JsonObject resp = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/webauthn/options/register", req2, 1000, 1000, null,
                    SemVer.v5_3.get(), "webauthn");
            assertEquals("OK", resp.get("status").getAsString());
        } catch (HttpResponseException e) {
            fail("Regular HTTPS origin should still work: " + e.getMessage());
        }

        // Test that Android origins work
        JsonObject req3 = new JsonObject();
        req3.addProperty("email", "test3@example.com");
        req3.addProperty("relyingPartyName", "Example");
        req3.addProperty("relyingPartyId", "example.com");
        req3.addProperty("origin", "android:apk-key-hash:47DEQpj8HBSa-_TImW-5JCeuQeRkm5NMpJWZG3hSuFU");

        try {
            JsonObject resp = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/webauthn/options/register", req3, 1000, 1000, null,
                    SemVer.v5_3.get(), "webauthn");
            assertEquals("OK", resp.get("status").getAsString());
        } catch (HttpResponseException e) {
            fail("Android origin should work alongside HTTP(S) origins: " + e.getMessage());
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }
}
