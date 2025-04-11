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

public class TestOptionsRegisterAPI_5_3 {
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
    public void testInvalidInput() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        JsonObject req = new JsonObject();
        try {
            JsonObject resp = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/webauthn/options/register", req, 1000, 1000, null,
                    SemVer.v5_3.get(), "webauthn");
            fail();
        } catch (HttpResponseException e) {
            assertEquals(400, e.statusCode);
            assertEquals("Http error. Status Code: 400. Message: Field name 'email' is invalid in JSON input", e.getMessage());
        }

        req.addProperty("email", "test@example.com");
        try {
            JsonObject resp = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/webauthn/options/register", req, 1000, 1000, null,
                    SemVer.v5_3.get(), "webauthn");
            fail();
        } catch (HttpResponseException e) {
            assertEquals(400, e.statusCode);
            assertEquals("Http error. Status Code: 400. Message: Field name 'relyingPartyName' is invalid in JSON input", e.getMessage());
        }

        req.addProperty("relyingPartyName", "Example");
        try {
            JsonObject resp = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/webauthn/options/register", req, 1000, 1000, null,
                    SemVer.v5_3.get(), "webauthn");
            fail();
        } catch (HttpResponseException e) {
            assertEquals(400, e.statusCode);
            assertEquals("Http error. Status Code: 400. Message: Field name 'relyingPartyId' is invalid in JSON input", e.getMessage());
        }

        req.addProperty("relyingPartyId", "example.com");
        try {
            JsonObject resp = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/webauthn/options/register", req, 1000, 1000, null,
                    SemVer.v5_3.get(), "webauthn");
            fail();
        } catch (HttpResponseException e) {
            assertEquals(400, e.statusCode);
            assertEquals("Http error. Status Code: 400. Message: Field name 'origin' is invalid in JSON input", e.getMessage());
        }

        req.addProperty("origin", "http://example.com");
        try {
            JsonObject resp = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/webauthn/options/register", req, 1000, 1000, null,
                    SemVer.v5_3.get(), "webauthn");
            checkResponseStructure(resp);
        } catch (HttpResponseException e) {
            fail(e.getMessage());
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testOptionalFields() throws Exception {
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
        req.addProperty("origin", "http://example.com");

        // Display name
        try {
            JsonObject resp = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/webauthn/options/register", req, 1000, 1000, null,
                    SemVer.v5_3.get(), "webauthn");
            checkResponseStructure(resp);

            assertEquals("test@example.com", resp.get("user").getAsJsonObject().get("displayName").getAsString());
        } catch (HttpResponseException e) {
            fail(e.getMessage());
        }

        req.addProperty("displayName", "Test");
        try {
            JsonObject resp = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/webauthn/options/register", req, 1000, 1000, null,
                    SemVer.v5_3.get(), "webauthn");
            checkResponseStructure(resp);

            assertEquals("Test", resp.get("user").getAsJsonObject().get("displayName").getAsString());
        } catch (HttpResponseException e) {
            fail(e.getMessage());
        }

        // timeout
        try {
            JsonObject resp = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/webauthn/options/register", req, 1000, 1000, null,
                    SemVer.v5_3.get(), "webauthn");
            checkResponseStructure(resp);

            assertEquals(6000, resp.get("timeout").getAsInt());
        } catch (HttpResponseException e) {
            fail(e.getMessage());
        }

        req.addProperty("timeout", 8000);
        try {
            JsonObject resp = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/webauthn/options/register", req, 1000, 1000, null,
                    SemVer.v5_3.get(), "webauthn");
            checkResponseStructure(resp);

            assertEquals(8000, resp.get("timeout").getAsInt());
        } catch (HttpResponseException e) {
            fail(e.getMessage());
        }

        // attestation
        try {
            JsonObject resp = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/webauthn/options/register", req, 1000, 1000, null,
                    SemVer.v5_3.get(), "webauthn");
            checkResponseStructure(resp);

            assertEquals("none", resp.get("attestation").getAsString());
        } catch (HttpResponseException e) {
            fail(e.getMessage());
        }

        req.addProperty("attestation", "direct");
        try {
            JsonObject resp = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/webauthn/options/register", req, 1000, 1000, null,
                    SemVer.v5_3.get(), "webauthn");
            checkResponseStructure(resp);

            assertEquals("direct", resp.get("attestation").getAsString());
        } catch (HttpResponseException e) {
            fail(e.getMessage());
        }

        // residentKey
        try {
            JsonObject resp = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/webauthn/options/register", req, 1000, 1000, null,
                    SemVer.v5_3.get(), "webauthn");
            checkResponseStructure(resp);

            assertEquals("required", resp.get("authenticatorSelection").getAsJsonObject().get("residentKey").getAsString());
        } catch (HttpResponseException e) {
            fail(e.getMessage());
        }

        req.addProperty("residentKey", "preferred");
        try {
            JsonObject resp = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/webauthn/options/register", req, 1000, 1000, null,
                    SemVer.v5_3.get(), "webauthn");
            checkResponseStructure(resp);

            assertEquals("preferred", resp.get("authenticatorSelection").getAsJsonObject().get("residentKey").getAsString());
        } catch (HttpResponseException e) {
            fail(e.getMessage());
        }

        // userVerification
        try {
            JsonObject resp = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/webauthn/options/register", req, 1000, 1000, null,
                    SemVer.v5_3.get(), "webauthn");
            checkResponseStructure(resp);

            assertEquals("preferred", resp.get("authenticatorSelection").getAsJsonObject().get("userVerification").getAsString());
        } catch (HttpResponseException e) {
            fail(e.getMessage());
        }

        req.addProperty("userVerification", "required");
        try {
            JsonObject resp = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/webauthn/options/register", req, 1000, 1000, null,
                    SemVer.v5_3.get(), "webauthn");
            checkResponseStructure(resp);

            assertEquals("required", resp.get("authenticatorSelection").getAsJsonObject().get("userVerification").getAsString());
        } catch (HttpResponseException e) {
            fail(e.getMessage());
        }

        // supportedAlgorithmIds

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    private void checkResponseStructure(JsonObject resp) throws Exception {
        assertEquals("OK", resp.get("status").getAsString());
        assertEquals(13, resp.entrySet().size());

        assertTrue(resp.has("webauthnGeneratedOptionsId"));

        assertTrue(resp.has("rp"));
        JsonObject rp = resp.get("rp").getAsJsonObject();
        assertEquals(2, rp.entrySet().size());
        assertTrue(rp.has("name"));
        assertTrue(rp.has("id"));

        assertTrue(resp.has("user"));
        JsonObject user = resp.get("user").getAsJsonObject();
        assertEquals(3, user.entrySet().size());
        assertTrue(user.has("name"));
        assertTrue(user.has("id"));
        assertTrue(user.has("displayName"));

        assertTrue(resp.has("email"));
        assertTrue(resp.has("timeout"));
        assertTrue(resp.has("challenge"));
        assertTrue(resp.has("attestation"));
        assertTrue(resp.has("createdAt"));
        assertTrue(resp.has("expiresAt"));
        assertTrue(resp.has("pubKeyCredParams"));
        assertTrue(resp.get("pubKeyCredParams").isJsonArray());

        assertTrue(resp.has("excludeCredentials"));
        assertTrue(resp.get("excludeCredentials").isJsonArray());

        assertTrue(resp.has("authenticatorSelection"));
        JsonObject authenticatorSelection = resp.get("authenticatorSelection").getAsJsonObject();
        assertEquals(3, authenticatorSelection.entrySet().size());
        assertTrue(authenticatorSelection.has("userVerification"));
        assertTrue(authenticatorSelection.has("requireResidentKey"));
        assertTrue(authenticatorSelection.has("residentKey"));
    }
}