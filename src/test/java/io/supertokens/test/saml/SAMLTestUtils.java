package io.supertokens.test.saml;

import java.nio.charset.StandardCharsets;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import io.supertokens.test.TestingProcessManager;
import io.supertokens.test.httpRequest.HttpRequestForTesting;
import io.supertokens.utils.SemVer;

public class SAMLTestUtils {

    public static class CreatedClientInfo {
        public final String clientId;
        public final MockSAML.KeyMaterial keyMaterial;
        public final String spEntityId;
        public final String defaultRedirectURI;
        public final String acsURL;
        public final String idpEntityId;
        public final String idpSsoUrl;

        public CreatedClientInfo(String clientId, MockSAML.KeyMaterial keyMaterial, String spEntityId,
                                 String defaultRedirectURI, String acsURL, String idpEntityId, String idpSsoUrl) {
            this.clientId = clientId;
            this.keyMaterial = keyMaterial;
            this.spEntityId = spEntityId;
            this.defaultRedirectURI = defaultRedirectURI;
            this.acsURL = acsURL;
            this.idpEntityId = idpEntityId;
            this.idpSsoUrl = idpSsoUrl;
        }
    }

    public static CreatedClientInfo createClientWithGeneratedMetadata(TestingProcessManager.TestingProcess process,
                                                                      String spEntityId,
                                                                      String defaultRedirectURI,
                                                                      String acsURL,
                                                                      String idpEntityId,
                                                                      String idpSsoUrl) throws Exception {
        MockSAML.KeyMaterial keyMaterial = MockSAML.generateSelfSignedKeyMaterial();
        String metadataXML = MockSAML.generateIdpMetadataXML(idpEntityId, idpSsoUrl, keyMaterial.certificate);
        String metadataXMLBase64 = java.util.Base64.getEncoder().encodeToString(metadataXML.getBytes(StandardCharsets.UTF_8));

        JsonObject createClientInput = new JsonObject();
        createClientInput.addProperty("spEntityId", spEntityId);
        createClientInput.addProperty("defaultRedirectURI", defaultRedirectURI);
        JsonArray redirectURIs = new JsonArray();
        redirectURIs.add(defaultRedirectURI);
        createClientInput.add("redirectURIs", redirectURIs);
        createClientInput.addProperty("metadataXML", metadataXMLBase64);

        JsonObject createResp = HttpRequestForTesting.sendJsonPUTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/saml/clients", createClientInput, 1000, 1000, null, SemVer.v5_4.get(), "saml");

        String clientId = createResp.get("clientId").getAsString();
        return new CreatedClientInfo(clientId, keyMaterial, spEntityId, defaultRedirectURI, acsURL, idpEntityId, idpSsoUrl);
    }

    public static String createLoginRequestAndGetRelayState(TestingProcessManager.TestingProcess process,
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
        int idx = ssoRedirectURI.indexOf("RelayState=");
        if (idx == -1) {
            throw new IllegalStateException("RelayState not found in ssoRedirectURI");
        }
        String relayStatePart = ssoRedirectURI.substring(idx + "RelayState=".length());
        int amp = relayStatePart.indexOf('&');
        String relayState = amp == -1 ? relayStatePart : relayStatePart.substring(0, amp);
        return java.net.URLDecoder.decode(relayState, java.nio.charset.StandardCharsets.UTF_8);
    }
}
