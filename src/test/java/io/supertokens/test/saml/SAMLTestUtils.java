package io.supertokens.test.saml;

import java.nio.charset.StandardCharsets;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import io.supertokens.test.TestingProcessManager;
import io.supertokens.test.Utils;
import io.supertokens.test.httpRequest.HttpRequestForTesting;
import io.supertokens.utils.SemVer;

public class SAMLTestUtils {

    // Matches the core's default saml_sp_entity_id. Tests that set it explicitly use this value.
    public static final String TEST_SP_ENTITY_ID = "https://saml.supertokens.com";

    public static void setSAMLSPEntityIdConfig() {
        try {
            Utils.setValueInConfig("saml_sp_entity_id", "\"" + TEST_SP_ENTITY_ID + "\"");
        } catch (java.io.IOException e) {
            throw new RuntimeException(e);
        }
    }


    public static class CreatedClientInfo {
        public final String clientId;
        public final MockSAML.KeyMaterial keyMaterial;
        public final String defaultRedirectURI;
        public final String acsURL;
        public final String idpEntityId;
        public final String idpSsoUrl;

        public CreatedClientInfo(String clientId, MockSAML.KeyMaterial keyMaterial,
                                 String defaultRedirectURI, String acsURL, String idpEntityId, String idpSsoUrl) {
            this.clientId = clientId;
            this.keyMaterial = keyMaterial;
            this.defaultRedirectURI = defaultRedirectURI;
            this.acsURL = acsURL;
            this.idpEntityId = idpEntityId;
            this.idpSsoUrl = idpSsoUrl;
        }
    }

    public static CreatedClientInfo createClientWithGeneratedMetadata(TestingProcessManager.TestingProcess process,
                                                                      String defaultRedirectURI,
                                                                      String acsURL,
                                                                      String idpEntityId,
                                                                      String idpSsoUrl) throws Exception {
        return createClientWithGeneratedMetadata(process, defaultRedirectURI, acsURL, idpEntityId, idpSsoUrl, false);
    }

    public static CreatedClientInfo createClientWithGeneratedMetadata(TestingProcessManager.TestingProcess process,
                                                                      String defaultRedirectURI,
                                                                      String acsURL,
                                                                      String idpEntityId,
                                                                      String idpSsoUrl,
                                                                      boolean allowIDPInitiatedLogin) throws Exception {
        return createClientWithGeneratedMetadata(process, defaultRedirectURI, acsURL, idpEntityId, idpSsoUrl,
                allowIDPInitiatedLogin, defaultAcsURLs(acsURL, allowIDPInitiatedLogin), null);
    }

    public static CreatedClientInfo createClientWithGeneratedMetadata(TestingProcessManager.TestingProcess process,
                                                                      String defaultRedirectURI,
                                                                      String acsURL,
                                                                      String idpEntityId,
                                                                      String idpSsoUrl,
                                                                      boolean allowIDPInitiatedLogin,
                                                                      JsonArray acsURLs) throws Exception {
        return createClientWithGeneratedMetadata(process, defaultRedirectURI, acsURL, idpEntityId, idpSsoUrl,
                allowIDPInitiatedLogin, acsURLs, null);
    }

    public static CreatedClientInfo createClientWithGeneratedMetadata(TestingProcessManager.TestingProcess process,
                                                                      String defaultRedirectURI,
                                                                      String acsURL,
                                                                      String idpEntityId,
                                                                      String idpSsoUrl,
                                                                      boolean allowIDPInitiatedLogin,
                                                                      String requestedClientId) throws Exception {
        return createClientWithGeneratedMetadata(process, defaultRedirectURI, acsURL, idpEntityId, idpSsoUrl,
                allowIDPInitiatedLogin, defaultAcsURLs(acsURL, allowIDPInitiatedLogin), requestedClientId);
    }

    // IdP-initiated login requires an ACS allow-list, so default it to the client's ACS URL.
    private static JsonArray defaultAcsURLs(String acsURL, boolean allowIDPInitiatedLogin) {
        if (!allowIDPInitiatedLogin) {
            return null;
        }
        JsonArray acsURLs = new JsonArray();
        acsURLs.add(acsURL);
        return acsURLs;
    }

    // requestedClientId != null forces a caller-supplied clientId (verbatim, dots allowed);
    // null lets the core generate the usual "st_saml_<uuid>".
    public static CreatedClientInfo createClientWithGeneratedMetadata(TestingProcessManager.TestingProcess process,
                                                                      String defaultRedirectURI,
                                                                      String acsURL,
                                                                      String idpEntityId,
                                                                      String idpSsoUrl,
                                                                      boolean allowIDPInitiatedLogin,
                                                                      JsonArray acsURLs,
                                                                      String requestedClientId) throws Exception {
        MockSAML.KeyMaterial keyMaterial = MockSAML.generateSelfSignedKeyMaterial();
        String metadataXML = MockSAML.generateIdpMetadataXML(idpEntityId, idpSsoUrl, keyMaterial.certificate);
        String metadataXMLBase64 = java.util.Base64.getEncoder().encodeToString(metadataXML.getBytes(StandardCharsets.UTF_8));

        JsonObject createClientInput = new JsonObject();
        if (requestedClientId != null) {
            createClientInput.addProperty("clientId", requestedClientId);
        }
        createClientInput.addProperty("clientSecret", "secret");
        createClientInput.addProperty("defaultRedirectURI", defaultRedirectURI);
        JsonArray redirectURIs = new JsonArray();
        redirectURIs.add(defaultRedirectURI);
        createClientInput.add("redirectURIs", redirectURIs);
        createClientInput.addProperty("metadataXML", metadataXMLBase64);
        createClientInput.addProperty("allowIDPInitiatedLogin", allowIDPInitiatedLogin);
        if (acsURLs != null) {
            createClientInput.add("acsURLs", acsURLs);
        }

        JsonObject createResp = HttpRequestForTesting.sendJsonPUTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/saml/clients", createClientInput, 1000, 1000, null, SemVer.v5_4.get(), "saml");

        String clientId = createResp.get("clientId").getAsString();
        return new CreatedClientInfo(clientId, keyMaterial, defaultRedirectURI, acsURL, idpEntityId, idpSsoUrl);
    }

    // Both values a login produces that a callback needs to echo back: the RelayState and the
    // AuthnRequest ID. Since the InResponseTo binding was added (GHSA-3r7x-rhvc-fw28), a SAML Response
    // fabricated in a test must set InResponseTo to requestId, not to the RelayState, to be accepted.
    public static class LoginInfo {
        public final String relayState;
        public final String requestId;

        public LoginInfo(String relayState, String requestId) {
            this.relayState = relayState;
            this.requestId = requestId;
        }
    }

    public static String createLoginRequestAndGetRelayState(TestingProcessManager.TestingProcess process,
                                                            String clientId,
                                                            String redirectURI,
                                                            String acsURL,
                                                            String state) throws Exception {
        return createLoginRequestAndGetLoginInfo(process, clientId, redirectURI, acsURL, state).relayState;
    }

    // RelayState (a per-login UUID) -> the AuthnRequest ID issued for that same login. Lets a test that
    // only kept the RelayState still recover the request ID to set as the SAML Response InResponseTo, which
    // the callback now requires to match (GHSA-3r7x-rhvc-fw28). Keyed by the unique RelayState, so it is
    // safe across parallel test processes.
    private static final java.util.Map<String, String> REQUEST_ID_BY_RELAY_STATE =
            new java.util.concurrent.ConcurrentHashMap<>();

    // The AuthnRequest ID issued for the login that produced this RelayState. Pass this (not the RelayState)
    // as a fabricated SAML Response's InResponseTo so the callback accepts it.
    public static String requestIdFor(String relayState) {
        return REQUEST_ID_BY_RELAY_STATE.get(relayState);
    }

    public static LoginInfo createLoginRequestAndGetLoginInfo(TestingProcessManager.TestingProcess process,
                                                              String clientId,
                                                              String redirectURI,
                                                              String acsURL,
                                                              String state) throws Exception {
        JsonObject body = new JsonObject();
        body.addProperty("clientId", clientId);
        body.addProperty("redirectURI", redirectURI);
        body.addProperty("acsURL", acsURL);
        if (state != null) {
            body.addProperty("state", state);
        }

        JsonObject resp = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/saml/login", body, 1000, 1000, null, SemVer.v5_4.get(), "saml");

        String ssoRedirectURI = resp.get("ssoRedirectURI").getAsString();
        String relayState = extractQueryParam(ssoRedirectURI, "RelayState");
        String requestId = extractAuthnRequestId(extractQueryParam(ssoRedirectURI, "SAMLRequest"));
        REQUEST_ID_BY_RELAY_STATE.put(relayState, requestId);
        return new LoginInfo(relayState, requestId);
    }

    private static String extractQueryParam(String url, String name) {
        int idx = url.indexOf(name + "=");
        if (idx == -1) {
            throw new IllegalStateException(name + " not found in " + url);
        }
        String part = url.substring(idx + (name + "=").length());
        int amp = part.indexOf('&');
        String raw = amp == -1 ? part : part.substring(0, amp);
        return java.net.URLDecoder.decode(raw, StandardCharsets.UTF_8);
    }

    // Mirrors SAML.deflateAndBase64RedirectMessage: the SAMLRequest is raw-DEFLATEd then base64-encoded,
    // per the SAML HTTP-Redirect binding. Inflate it back and pull the AuthnRequest's ID attribute out.
    private static String extractAuthnRequestId(String base64Deflated) throws Exception {
        byte[] deflated = java.util.Base64.getDecoder().decode(base64Deflated);
        java.util.zip.Inflater inflater = new java.util.zip.Inflater(true); // nowrap = raw DEFLATE
        inflater.setInput(deflated);
        byte[] buf = new byte[8192];
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        while (!inflater.finished()) {
            int n = inflater.inflate(buf);
            if (n == 0) {
                break;
            }
            out.write(buf, 0, n);
        }
        inflater.end();
        String xml = new String(out.toByteArray(), StandardCharsets.UTF_8);
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\bID=\"([^\"]+)\"").matcher(xml);
        if (!m.find()) {
            throw new IllegalStateException("no ID attribute in AuthnRequest: " + xml);
        }
        return m.group(1);
    }
}
