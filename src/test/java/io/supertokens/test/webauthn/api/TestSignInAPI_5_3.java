package io.supertokens.test.webauthn.api;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.webauthn4j.data.AuthenticatorAssertionResponse;
import com.webauthn4j.data.AuthenticatorAttestationResponse;
import com.webauthn4j.data.PublicKeyCredential;
import com.webauthn4j.data.PublicKeyCredentialCreationOptions;
import com.webauthn4j.data.extension.client.AuthenticationExtensionClientOutput;
import com.webauthn4j.data.extension.client.RegistrationExtensionClientOutput;
import com.webauthn4j.test.EmulatorUtil;
import com.webauthn4j.test.client.ClientPlatform;
import com.webauthn4j.util.Base64UrlUtil;
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

import java.util.Map;

import static org.junit.Assert.*;
import static org.junit.Assert.assertTrue;

public class TestSignInAPI_5_3 {
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
                    "http://localhost:3567/recipe/webauthn/signin", req, 1000, 1000, null,
                    SemVer.v5_3.get(), "webauthn");
            fail();
        } catch (HttpResponseException e) {
            assertEquals(400, e.statusCode);
            assertEquals("Http error. Status Code: 400. Message: Field name 'webauthnGeneratedOptionsId' is invalid in JSON input", e.getMessage());
        }

        req.addProperty("webauthnGeneratedOptionsId", "someId");
        try {
            JsonObject resp = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/webauthn/signin", req, 1000, 1000, null,
                    SemVer.v5_3.get(), "webauthn");
            fail();
        } catch (HttpResponseException e) {
            assertEquals(400, e.statusCode);
            assertEquals("Http error. Status Code: 400. Message: Field name 'credential' is invalid in JSON input", e.getMessage());
        }

        req.add("credential", new JsonObject());
        try {
            JsonObject resp = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/webauthn/signin", req, 1000, 1000, null,
                    SemVer.v5_3.get(), "webauthn");
        } catch (HttpResponseException e) {
            fail(e.getMessage());
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testOptionsNotFound() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        JsonObject req = new JsonObject();
        req.addProperty("webauthnGeneratedOptionsId", "someId");
        req.add("credential", new JsonObject());

        try {
            JsonObject resp = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/webauthn/signin", req, 1000, 1000, null,
                    SemVer.v5_3.get(), "webauthn");
            assertEquals("OPTIONS_NOT_FOUND_ERROR", resp.get("status").getAsString());
        } catch (HttpResponseException e) {
            fail(e.getMessage());
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testInvalidOptions() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        String credentialId = null;
        { // Sign up
            JsonObject optionsResponse = null;
            {
                JsonObject req = new JsonObject();
                req.addProperty("email", "test@example.com");
                req.addProperty("relyingPartyName", "Example");
                req.addProperty("relyingPartyId", "example.com");
                req.addProperty("origin", "http://example.com");

                optionsResponse = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                        "http://localhost:3567/recipe/webauthn/options/register", req, 1000, 1000, null,
                        SemVer.v5_3.get(), "webauthn");
            }

            JsonObject req = new JsonObject();
            req.addProperty("webauthnGeneratedOptionsId", optionsResponse.get("webauthnGeneratedOptionsId").getAsString());
            req.add("credential", generateCredentialForSignUp(optionsResponse));

            try {
                JsonObject resp = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                        "http://localhost:3567/recipe/webauthn/signup", req, 1000, 1000, null,
                        SemVer.v5_3.get(), "webauthn");
                credentialId = resp.get("webauthnCredentialId").getAsString();
            } catch (HttpResponseException e) {
                fail(e.getMessage());
            }
        }

        JsonObject optionsResponse = null;
        {
            JsonObject req = new JsonObject();
            req.addProperty("relyingPartyName", "Example");
            req.addProperty("relyingPartyId", "example.com");
            req.addProperty("origin", "http://example.com");
            req.addProperty("timeout", "1000");

            optionsResponse = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/webauthn/options/signin", req, 1000, 1000, null,
                    SemVer.v5_3.get(), "webauthn");
        }

        Thread.sleep(1100);

        JsonObject req = new JsonObject();
        req.addProperty("webauthnGeneratedOptionsId", optionsResponse.get("webauthnGeneratedOptionsId").getAsString());
        req.add("credential", generateCredential(optionsResponse, credentialId));

        try {
            JsonObject resp = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/webauthn/signin", req, 1000, 1000, null,
                    SemVer.v5_3.get(), "webauthn");
            assertEquals("INVALID_OPTIONS_ERROR", resp.get("status").getAsString());
        } catch (HttpResponseException e) {
            fail(e.getMessage());
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testInvalidCredentials() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        String optionsId = null;
        {
            JsonObject req = new JsonObject();
            req.addProperty("email", "test@example.com");
            req.addProperty("relyingPartyName", "Example");
            req.addProperty("relyingPartyId", "example.com");
            req.addProperty("origin", "http://example.com");

            JsonObject resp = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/webauthn/options/signin", req, 1000, 1000, null,
                    SemVer.v5_3.get(), "webauthn");
            optionsId = resp.get("webauthnGeneratedOptionsId").getAsString();
        }

        JsonObject req = new JsonObject();
        req.addProperty("webauthnGeneratedOptionsId", optionsId);
        req.add("credential", new JsonObject());

        try {
            JsonObject resp = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/webauthn/signin", req, 1000, 1000, null,
                    SemVer.v5_3.get(), "webauthn");
            assertEquals("INVALID_CREDENTIALS_ERROR", resp.get("status").getAsString());
        } catch (HttpResponseException e) {
            fail(e.getMessage());
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testValidInput() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        String credentialId = null;
        { // Sign up
            JsonObject optionsResponse = null;
            {
                JsonObject req = new JsonObject();
                req.addProperty("email", "test@example.com");
                req.addProperty("relyingPartyName", "Example");
                req.addProperty("relyingPartyId", "example.com");
                req.addProperty("origin", "http://example.com");

                optionsResponse = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                        "http://localhost:3567/recipe/webauthn/options/register", req, 1000, 1000, null,
                        SemVer.v5_3.get(), "webauthn");
            }

            JsonObject req = new JsonObject();
            req.addProperty("webauthnGeneratedOptionsId", optionsResponse.get("webauthnGeneratedOptionsId").getAsString());
            req.add("credential", generateCredentialForSignUp(optionsResponse));

            try {
                JsonObject resp = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                        "http://localhost:3567/recipe/webauthn/signup", req, 1000, 1000, null,
                        SemVer.v5_3.get(), "webauthn");
                credentialId = resp.get("webauthnCredentialId").getAsString();
            } catch (HttpResponseException e) {
                fail(e.getMessage());
            }
        }

        JsonObject optionsResponse = null;
        {
            JsonObject req = new JsonObject();
            req.addProperty("email", "test@example.com");
            req.addProperty("relyingPartyName", "Example");
            req.addProperty("relyingPartyId", "example.com");
            req.addProperty("origin", "http://example.com");

            optionsResponse = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/webauthn/options/signin", req, 1000, 1000, null,
                    SemVer.v5_3.get(), "webauthn");
        }

        JsonObject req = new JsonObject();
        req.addProperty("webauthnGeneratedOptionsId", optionsResponse.get("webauthnGeneratedOptionsId").getAsString());
        req.add("credential", generateCredential(optionsResponse, credentialId));

        try {
            JsonObject resp = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/webauthn/signin", req, 1000, 1000, null,
                    SemVer.v5_3.get(), "webauthn");
            checkResponseStructure(resp);
        } catch (HttpResponseException e) {
            fail(e.getMessage());
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    private JsonObject generateCredentialForSignUp(JsonObject registerOptionsResponse) throws Exception {
        ClientPlatform clientPlatform = EmulatorUtil.createClientPlatform(EmulatorUtil.FIDO_U2F_AUTHENTICATOR);

        Map<String, PublicKeyCredentialCreationOptions> options = io.supertokens.test.webauthn.Utils.createPublicKeyCreationOptions(
                registerOptionsResponse);
        PublicKeyCredential<AuthenticatorAttestationResponse, RegistrationExtensionClientOutput> credential = io.supertokens.test.webauthn.Utils.createPasskey(
                clientPlatform, options.values().stream().findFirst().get());

        String attestationObject = Base64UrlUtil.encodeToString(credential.getAuthenticatorResponse().getAttestationObject());
        String clientDataJson = Base64UrlUtil.encodeToString(credential.getAuthenticatorResponse().getClientDataJSON());
        String rawId = Base64UrlUtil.encodeToString(credential.getRawId());

        JsonObject credentialJson = new Gson().toJsonTree(credential).getAsJsonObject();

        credentialJson.getAsJsonObject("response").addProperty("attestationObject", attestationObject);
        credentialJson.getAsJsonObject("response").addProperty("clientDataJSON", clientDataJson);
        credentialJson.addProperty("type", credential.getType());
        credentialJson.getAsJsonObject("response").remove("transports");
        credentialJson.remove("clientExtensionResults");
        credentialJson.addProperty("rawId", rawId);

        return credentialJson;
    }

    private JsonObject generateCredential(JsonObject registerOptionsResponse, String credentialId) throws Exception {
        ClientPlatform clientPlatform = EmulatorUtil.createClientPlatform(EmulatorUtil.FIDO_U2F_AUTHENTICATOR);

        Map<String, PublicKeyCredential<AuthenticatorAssertionResponse, AuthenticationExtensionClientOutput>> options = io.supertokens.test.webauthn.Utils.createPublicKeyRequestOptions(
                registerOptionsResponse, clientPlatform, credentialId);
        PublicKeyCredential<AuthenticatorAssertionResponse, AuthenticationExtensionClientOutput> credential = options.values()
                .stream().findFirst().get();

        String signature = Base64UrlUtil.encodeToString(credential.getResponse().getSignature());
        String clientDataJson = Base64UrlUtil.encodeToString(credential.getAuthenticatorResponse().getClientDataJSON());
        String rawId = Base64UrlUtil.encodeToString(credential.getRawId());
        String authenticatorData = Base64UrlUtil.encodeToString(credential.getResponse().getAuthenticatorData());

        JsonObject credentialJson = new Gson().fromJson(new Gson().toJson(credential), JsonObject.class);
        credentialJson.getAsJsonObject("response").addProperty("signature", signature);
        credentialJson.getAsJsonObject("response").addProperty("clientDataJSON", clientDataJson);
        credentialJson.getAsJsonObject("response").addProperty("authenticatorData", authenticatorData);
        credentialJson.addProperty("type", credential.getType());
        credentialJson.getAsJsonObject("response").remove("transports");
        credentialJson.remove("clientExtensionResults");
        credentialJson.addProperty("rawId", rawId);

        return credentialJson;
    }

    private void checkResponseStructure(JsonObject resp) throws Exception {
        assertEquals("OK", resp.get("status").getAsString());

        assertEquals(3, resp.entrySet().size());

        assertTrue(resp.has("user"));
        JsonObject user = resp.get("user").getAsJsonObject();
        assertEquals(9, user.entrySet().size());
        assertTrue(user.has("id"));
        assertTrue(user.has("isPrimaryUser"));
        assertTrue(user.has("tenantIds"));
        assertTrue(user.has("timeJoined"));
        assertTrue(user.has("emails"));
        assertTrue(user.has("phoneNumbers"));
        assertTrue(user.has("thirdParty"));
        assertTrue(user.has("webauthn"));

        JsonObject webauthn = user.get("webauthn").getAsJsonObject();
        assertEquals(1, webauthn.entrySet().size());
        assertTrue(webauthn.has("credentialIds"));
        assertTrue(webauthn.get("credentialIds").isJsonArray());

        assertTrue(user.has("loginMethods"));

        assertTrue(resp.has("recipeUserId"));
    }
}
