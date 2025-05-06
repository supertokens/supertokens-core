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

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;

public class TestGetGeneratedOptionsAPI_5_3 {
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

        try {
            JsonObject resp = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/webauthn/options", new HashMap<>(), 1000, 1000, null,
                    SemVer.v5_3.get(), "webauthn");
            fail();
        } catch (HttpResponseException e) {
            assertEquals(400, e.statusCode);
            assertEquals("Http error. Status Code: 400. Message: Field name 'webauthnGeneratedOptionsId' is missing in GET request", e.getMessage());
        }
    }

    @Test
    public void testNonExistantId() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        try {
            Map<String, String> params = new HashMap<>();
            params.put("webauthnGeneratedOptionsId", "nonExistantId");
            JsonObject resp = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/webauthn/options", params, 1000, 1000, null,
                    SemVer.v5_3.get(), "webauthn");
            assertEquals("OPTIONS_NOT_FOUND_ERROR", resp.get("status").getAsString());
        } catch (HttpResponseException e) {
            fail(e.getMessage());
        }
    }

    @Test
    public void testValidInput() throws Exception {
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
        JsonObject registerOptionsResp = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/webauthn/options/register", req, 1000, 1000, null,
                SemVer.v5_3.get(), "webauthn");

        try {
            Map<String, String> params = new HashMap<>();
            params.put("webauthnGeneratedOptionsId", registerOptionsResp.get("webauthnGeneratedOptionsId").getAsString());
            JsonObject resp = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/webauthn/options", params, 1000, 1000, null,
                    SemVer.v5_3.get(), "webauthn");
            checkResponseStructure(resp);
        } catch (HttpResponseException e) {
            fail(e.getMessage());
        }
    }

    private void checkResponseStructure(JsonObject resp) throws Exception {
        assertEquals("OK", resp.get("status").getAsString());

        assertEquals(12, resp.entrySet().size());

        assertTrue(resp.has("webauthnGeneratedOptionsId"));

        assertTrue(resp.has("relyingPartyName"));
        assertTrue(resp.has("relyingPartyId"));

        assertTrue(resp.has("challenge"));
        assertTrue(resp.has("timeout"));
        assertTrue(resp.has("origin"));
        assertTrue(resp.has("email"));

        assertTrue(resp.has("createdAt"));
        assertTrue(resp.has("expiresAt"));

        assertTrue(resp.has("userVerification"));
        assertTrue(resp.has("userPresence"));
    }
}