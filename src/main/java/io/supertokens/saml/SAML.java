/*
 *    Copyright (c) 2025, VRAI Labs and/or its affiliates. All rights reserved.
 *
 *    This software is licensed under the Apache License, Version 2.0 (the
 *    "License") as published by the Apache Software Foundation.
 *
 *    You may not use this file except in compliance with the License. You may
 *    obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 *    WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 *    License for the specific language governing permissions and limitations
 *    under the License.
 */

package io.supertokens.saml;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.supertokens.Main;
import io.supertokens.jwt.JWTSigningFunctions;
import io.supertokens.jwt.exceptions.UnsupportedJWTSigningAlgorithmException;
import io.supertokens.pluginInterface.Storage;
import io.supertokens.pluginInterface.StorageUtils;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.exceptions.StorageTransactionLogicException;
import io.supertokens.pluginInterface.jwt.JWTSigningKeyInfo;
import io.supertokens.pluginInterface.multitenancy.AppIdentifier;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifier;

import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.pluginInterface.saml.SAMLClaimsInfo;
import io.supertokens.pluginInterface.saml.SAMLClient;
import io.supertokens.pluginInterface.saml.SAMLRelayStateInfo;
import io.supertokens.pluginInterface.saml.SAMLStorage;
import io.supertokens.saml.exceptions.InvalidClientException;
import io.supertokens.saml.exceptions.MalformedSAMLMetadataXMLException;
import io.supertokens.signingkeys.JWTSigningKey;
import io.supertokens.signingkeys.SigningKeys;
import net.shibboleth.utilities.java.support.xml.SerializeSupport;
import net.shibboleth.utilities.java.support.xml.XMLParserException;
import org.opensaml.core.xml.XMLObject;
import org.opensaml.core.xml.XMLObjectBuilderFactory;
import org.opensaml.core.xml.config.XMLObjectProviderRegistrySupport;
import org.opensaml.core.xml.io.UnmarshallingException;
import org.opensaml.core.xml.util.XMLObjectSupport;
import org.opensaml.saml.common.SAMLVersion;
import org.opensaml.saml.common.xml.SAMLConstants;
import org.opensaml.saml.saml2.core.*;
import org.opensaml.saml.saml2.metadata.EntityDescriptor;
import org.opensaml.saml.saml2.metadata.IDPSSODescriptor;
import org.opensaml.saml.saml2.metadata.SingleSignOnService;
import org.opensaml.security.credential.Credential;
import org.opensaml.security.credential.CredentialSupport;
import org.opensaml.xmlsec.signature.KeyInfo;
import org.opensaml.xmlsec.signature.Signature;
import org.opensaml.xmlsec.signature.X509Data;
import org.opensaml.xmlsec.signature.impl.KeyInfoBuilder;
import org.opensaml.xmlsec.signature.impl.SignatureBuilder;
import org.opensaml.xmlsec.signature.impl.X509DataBuilder;
import org.opensaml.xmlsec.signature.support.SignatureConstants;
import org.opensaml.xmlsec.signature.support.SignatureException;
import org.opensaml.xmlsec.signature.support.SignatureValidator;
import org.w3c.dom.Element;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.security.spec.InvalidKeySpecException;
import java.time.Instant;
import java.util.*;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;

public class SAML {
    public static SAMLClient createOrUpdateSAMLClient(
            TenantIdentifier tenantIdentifier, Storage storage,
            String clientId, String spEntityId, String defaultRedirectURI, JsonArray redirectURIs, String metadataXML)
            throws MalformedSAMLMetadataXMLException, StorageQueryException {
        SAMLStorage samlStorage = StorageUtils.getSAMLStorage(storage);

        var metadata = loadIdpMetadata(metadataXML);
        String idpSsoUrl = null;
        for (var roleDescriptor : metadata.getRoleDescriptors()) {
            if (roleDescriptor instanceof IDPSSODescriptor) {
                IDPSSODescriptor idpDescriptor = (IDPSSODescriptor) roleDescriptor;
                for (SingleSignOnService ssoService : idpDescriptor.getSingleSignOnServices()) {
                    if (SAMLConstants.SAML2_REDIRECT_BINDING_URI.equals(ssoService.getBinding())) {
                        idpSsoUrl = ssoService.getLocation();
                    }
                }
            }
        }
        if (idpSsoUrl == null) {
            throw new MalformedSAMLMetadataXMLException();
        }

        String idpSigningCertificate = extractIdpSigningCertificate(metadata);

        String idpEntityId = metadata.getEntityID();
        SAMLClient client = new SAMLClient(clientId, idpSsoUrl, redirectURIs, defaultRedirectURI, spEntityId, idpEntityId, idpSigningCertificate);
        return samlStorage.createOrUpdateSAMLClient(tenantIdentifier, client);
    }

    private static String extractIdpSigningCertificate(EntityDescriptor idpMetadata) {
        for (var roleDescriptor : idpMetadata.getRoleDescriptors()) {
            if (roleDescriptor instanceof IDPSSODescriptor) {
                IDPSSODescriptor idpDescriptor = (IDPSSODescriptor) roleDescriptor;
                for (org.opensaml.saml.saml2.metadata.KeyDescriptor keyDescriptor : idpDescriptor.getKeyDescriptors()) {
                    if (keyDescriptor.getUse() == null ||
                            "SIGNING".equals(keyDescriptor.getUse().toString())) {
                        org.opensaml.xmlsec.signature.KeyInfo keyInfo = keyDescriptor.getKeyInfo();
                        if (keyInfo != null) {
                            for (org.opensaml.xmlsec.signature.X509Data x509Data : keyInfo.getX509Datas()) {
                                for (org.opensaml.xmlsec.signature.X509Certificate x509Cert : x509Data.getX509Certificates()) {
                                    try {
                                        String certString = x509Cert.getValue();
                                        if (certString != null && !certString.trim().isEmpty()) {
                                            certString = certString.replaceAll("\\s", "");
                                            return certString;
                                        }
                                    } catch (Exception e) {
                                        // Continue to next certificate if this one fails
                                        continue;
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        return null;

    }

    public static String createRedirectURL(Main main, TenantIdentifier tenantIdentifier, Storage storage,
                                           String clientId, String redirectURI, String state, String acsURL)
            throws StorageQueryException, InvalidClientException, TenantOrAppNotFoundException,
            CertificateEncodingException {
        SAMLStorage samlStorage = StorageUtils.getSAMLStorage(storage);

        SAMLClient client = samlStorage.getSAMLClient(tenantIdentifier, clientId);

        if (client == null) {
            throw new InvalidClientException();
        }

        String idpSsoUrl = client.ssoLoginURL;
        AuthnRequest request = buildAuthnRequest(
                main,
                tenantIdentifier.toAppIdentifier(),
                idpSsoUrl,
                client.spEntityId, acsURL);
        String samlRequest = deflateAndBase64RedirectMessage(request);
        String relayState = UUID.randomUUID().toString();

        // TODO handle duplicate relayState
        samlStorage.saveRelayStateInfo(tenantIdentifier, new SAMLRelayStateInfo(relayState, clientId, state, redirectURI));

        return idpSsoUrl + "?SAMLRequest=" + samlRequest + "&RelayState=" + URLEncoder.encode(relayState, StandardCharsets.UTF_8);
    }

    public static EntityDescriptor loadIdpMetadata(String metadataXML) throws MalformedSAMLMetadataXMLException {
        try {
            byte[] bytes = metadataXML.getBytes(StandardCharsets.UTF_8);
            try (InputStream inputStream = new java.io.ByteArrayInputStream(bytes)) {
                XMLObject xmlObject = XMLObjectSupport.unmarshallFromInputStream(
                        XMLObjectProviderRegistrySupport.getParserPool(), inputStream);
                if (xmlObject instanceof EntityDescriptor) {
                    return (EntityDescriptor) xmlObject;
                } else {
                    throw new RuntimeException("Expected EntityDescriptor but got: " + xmlObject.getClass());
                }
            }
        } catch (Exception e) {
            throw new MalformedSAMLMetadataXMLException();
        }
    }

    private static AuthnRequest buildAuthnRequest(Main main, AppIdentifier appIdentifier, String idpSsoUrl, String spEntityId, String acsUrl)
            throws TenantOrAppNotFoundException, StorageQueryException, CertificateEncodingException {
        XMLObjectBuilderFactory builders = XMLObjectProviderRegistrySupport.getBuilderFactory();

        AuthnRequest authnRequest = (AuthnRequest) builders
                .<AuthnRequest>getBuilder(AuthnRequest.DEFAULT_ELEMENT_NAME)
                .buildObject(AuthnRequest.DEFAULT_ELEMENT_NAME);
        authnRequest.setID("_" + UUID.randomUUID());
        authnRequest.setIssueInstant(Instant.now());
        authnRequest.setVersion(SAMLVersion.VERSION_20);
        authnRequest.setDestination(idpSsoUrl);
        authnRequest.setProtocolBinding(SAMLConstants.SAML2_POST_BINDING_URI);

        Issuer issuer = (Issuer) builders.getBuilder(Issuer.DEFAULT_ELEMENT_NAME)
                .buildObject(Issuer.DEFAULT_ELEMENT_NAME);
        issuer.setValue(spEntityId);
        authnRequest.setIssuer(issuer);

        NameIDPolicy nameIDPolicy = (NameIDPolicy) builders.getBuilder(NameIDPolicy.DEFAULT_ELEMENT_NAME)
                .buildObject(NameIDPolicy.DEFAULT_ELEMENT_NAME);
        nameIDPolicy.setAllowCreate(true);
        authnRequest.setNameIDPolicy(nameIDPolicy);

        RequestedAuthnContext rac = (RequestedAuthnContext) builders.getBuilder(RequestedAuthnContext.DEFAULT_ELEMENT_NAME)
                .buildObject(RequestedAuthnContext.DEFAULT_ELEMENT_NAME);
        rac.setComparison(org.opensaml.saml.saml2.core.AuthnContextComparisonTypeEnumeration.EXACT);
        AuthnContextClassRef classRef = (AuthnContextClassRef) builders.getBuilder(AuthnContextClassRef.DEFAULT_ELEMENT_NAME)
                .buildObject(AuthnContextClassRef.DEFAULT_ELEMENT_NAME);
        classRef.setURI(AuthnContext.PASSWORD_AUTHN_CTX);
        rac.getAuthnContextClassRefs().add(classRef);
        authnRequest.setRequestedAuthnContext(rac);

        authnRequest.setAssertionConsumerServiceURL(acsUrl);

        if (true) { // TODO Add option to enable/disable request signing in the client
            Signature signature = new SignatureBuilder().buildObject();
            signature.setSignatureAlgorithm(SignatureConstants.ALGO_ID_SIGNATURE_RSA_SHA256);
            signature.setCanonicalizationAlgorithm(SignatureConstants.ALGO_ID_C14N_EXCL_OMIT_COMMENTS);

            // Create KeyInfo
            KeyInfo keyInfo = new KeyInfoBuilder().buildObject();
            X509Data x509Data = new X509DataBuilder().buildObject();
            org.opensaml.xmlsec.signature.X509Certificate x509CertElement = new org.opensaml.xmlsec.signature.impl.X509CertificateBuilder().buildObject();

            X509Certificate spCertificate = SAMLCertificate.getInstance(appIdentifier, main).getCertificate();
            String certString = java.util.Base64.getEncoder().encodeToString(spCertificate.getEncoded());
            x509CertElement.setValue(certString);
            x509Data.getX509Certificates().add(x509CertElement);
            keyInfo.getX509Datas().add(x509Data);
            signature.setKeyInfo(keyInfo);

            authnRequest.setSignature(signature);
        }

        return authnRequest;
    }

    private static String deflateAndBase64RedirectMessage(XMLObject xmlObject) {
        try {
            String xml = toXmlString(xmlObject);
            byte[] xmlBytes = xml.getBytes(StandardCharsets.UTF_8);

            // DEFLATE compression as per SAML Redirect binding spec
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DeflaterOutputStream dos = new DeflaterOutputStream(baos, new Deflater(Deflater.DEFLATED, true));
            dos.write(xmlBytes);
            dos.close();

            byte[] deflated = baos.toByteArray();
            String base64 = java.util.Base64.getEncoder().encodeToString(deflated);
            return URLEncoder.encode(base64, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Failed to deflate SAML message", e);
        }
    }

    private static String toXmlString(XMLObject xmlObject) {
        try {
            Element el = XMLObjectSupport.marshall(xmlObject);
            return SerializeSupport.nodeToString(el);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize XML", e);
        }
    }

    private static Response parseSamlResponse(String samlResponseBase64)
            throws IOException, XMLParserException, UnmarshallingException {
        byte[] decoded = java.util.Base64.getDecoder().decode(samlResponseBase64);
        String xml = new String(decoded, StandardCharsets.UTF_8);

        try (InputStream inputStream = new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8))) {
            return (Response) XMLObjectSupport.unmarshallFromInputStream(
                    XMLObjectProviderRegistrySupport.getParserPool(), inputStream);
        }
    }

    private static void verifySamlResponseSignature(Response samlResponse, X509Certificate idpCertificate)
            throws SignatureException {
        Signature responseSignature = samlResponse.getSignature();
        if (responseSignature != null) {
            Credential credential = CredentialSupport.getSimpleCredential(idpCertificate, null);
            SignatureValidator.validate(responseSignature, credential);
            return;
        }

        boolean foundSignedAssertion = false;
        for (Assertion assertion : samlResponse.getAssertions()) {
            Signature assertionSignature = assertion.getSignature();
            if (assertionSignature != null) {
                Credential credential = CredentialSupport.getSimpleCredential(idpCertificate, null);
                SignatureValidator.validate(assertionSignature, credential);
                foundSignedAssertion = true;
            }
        }

        if (!foundSignedAssertion) {
            throw new RuntimeException("Neither SAML Response nor any Assertion is signed");
        }
    }

    private static void validateSamlResponseTimestamps(Response samlResponse) {
        Instant now = Instant.now();

        // Validate response issue instant (should be recent)
        if (samlResponse.getIssueInstant() != null) {
            Instant responseTime = samlResponse.getIssueInstant();
            // Allow 5 minutes clock skew
            if (responseTime.isAfter(now.plusSeconds(300)) || responseTime.isBefore(now.minusSeconds(300))) {
                throw new RuntimeException("SAML Response timestamp is outside acceptable range"); // TODO
            }
        }

        // Validate assertion timestamps
        for (Assertion assertion : samlResponse.getAssertions()) {
            // Check NotBefore
            if (assertion.getConditions() != null && assertion.getConditions().getNotBefore() != null) {
                if (now.isBefore(assertion.getConditions().getNotBefore())) {
                    throw new RuntimeException("SAML Assertion is not yet valid (NotBefore)"); // TODO
                }
            }

            // Check NotOnOrAfter
            if (assertion.getConditions() != null && assertion.getConditions().getNotOnOrAfter() != null) {
                if (now.isAfter(assertion.getConditions().getNotOnOrAfter())) {
                    throw new RuntimeException("SAML Assertion has expired (NotOnOrAfter)"); // TODO
                }
            }
        }
    }

    public static String handleCallback(TenantIdentifier tenantIdentifier, Storage storage, String samlResponse, String relayState)
            throws StorageQueryException, XMLParserException, IOException, UnmarshallingException, SignatureException,
            CertificateException {
        SAMLStorage samlStorage = StorageUtils.getSAMLStorage(storage);

        if (relayState != null) {
            // sp initiated
            var relayStateInfo = samlStorage.getRelayStateInfo(tenantIdentifier, relayState);
            String clientId = relayStateInfo.clientId;

            if (relayStateInfo == null) {
                throw new IllegalStateException("INVALID_RELAY_STATE"); // TODO
            }

            SAMLClient client = samlStorage.getSAMLClient(tenantIdentifier, clientId);
            String code = UUID.randomUUID().toString();
            String state = relayStateInfo.state;

            // SAML parsing and verification
            Response response = parseSamlResponse(samlResponse);
            X509Certificate idpSigningCertificate = getCertificateFromString(client.idpSigningCertificate);
            verifySamlResponseSignature(response, idpSigningCertificate);
            validateSamlResponseTimestamps(response);

            var claims = extractAllClaims(response);
            samlStorage.saveSAMLClaims(tenantIdentifier, clientId, code, claims);

            try {
                java.net.URI uri = new java.net.URI(relayStateInfo.redirectURI);
                String query = uri.getQuery();
                StringBuilder newQuery = new StringBuilder();
                if (query != null && !query.isEmpty()) {
                    newQuery.append(query).append("&");
                }
                newQuery.append("code=").append(java.net.URLEncoder.encode(code, java.nio.charset.StandardCharsets.UTF_8));
                if (state != null) {
                    newQuery.append("&state=").append(java.net.URLEncoder.encode(state, java.nio.charset.StandardCharsets.UTF_8));
                }
                java.net.URI newUri = new java.net.URI(
                        uri.getScheme(),
                        uri.getAuthority(),
                        uri.getPath(),
                        newQuery.toString(),
                        uri.getFragment()
                );
                return newUri.toString();
            } catch (Exception e) {
                throw new RuntimeException("Failed to append code and state to redirect URI", e);
            }
        }

        // idp initiated
        return "https://sattvik.me";
    }

    private static JsonObject extractAllClaims(Response samlResponse) {
        JsonObject claims = new JsonObject();

        for (Assertion assertion : samlResponse.getAssertions()) {
            // Extract NameID as a claim
            Subject subject = assertion.getSubject();
            if (subject != null && subject.getNameID() != null) {
                String nameId = subject.getNameID().getValue();
                String nameIdFormat = subject.getNameID().getFormat();
                JsonArray nameIdArr = new JsonArray();
                nameIdArr.add(nameId);
                claims.add("NameID", nameIdArr);
                if (nameIdFormat != null) {
                    JsonArray nameIdFormatArr = new JsonArray();
                    nameIdFormatArr.add(nameIdFormat);
                    claims.add("NameIDFormat", nameIdFormatArr);
                }
            }

            // Extract all attributes from AttributeStatements
            for (AttributeStatement attributeStatement : assertion.getAttributeStatements()) {
                for (Attribute attribute : attributeStatement.getAttributes()) {
                    String attributeName = attribute.getName();
                    JsonArray attributeValues = new JsonArray();

                    for (XMLObject attributeValue : attribute.getAttributeValues()) {
                        if (attributeValue instanceof org.opensaml.saml.saml2.core.AttributeValue) {
                            org.opensaml.saml.saml2.core.AttributeValue attrValue =
                                    (org.opensaml.saml.saml2.core.AttributeValue) attributeValue;

                            if (attrValue.getDOM() != null) {
                                String value = attrValue.getDOM().getTextContent();
                                if (value != null && !value.trim().isEmpty()) {
                                    attributeValues.add(value.trim());
                                }
                            } else if (attrValue.getTextContent() != null) {
                                String value = attrValue.getTextContent();
                                if (!value.trim().isEmpty()) {
                                    attributeValues.add(value.trim());
                                }
                            }
                        }
                    }

                    if (!attributeValues.isEmpty()) {
                        claims.add(attributeName, attributeValues);
                    }
                }
            }
        }

        return claims;
    }

    private static X509Certificate getCertificateFromString(String certString) throws CertificateException {
        byte[] certBytes = java.util.Base64.getDecoder().decode(certString);
        java.security.cert.CertificateFactory certFactory =
                java.security.cert.CertificateFactory.getInstance("X.509");
        return (X509Certificate) certFactory.generateCertificate(
                new ByteArrayInputStream(certBytes));
    }

    public static String getTokenForCode(Main main, TenantIdentifier tenantIdentifier, Storage storage, String code)
            throws StorageQueryException, TenantOrAppNotFoundException, UnsupportedJWTSigningAlgorithmException,
            StorageTransactionLogicException, NoSuchAlgorithmException, InvalidKeySpecException {

        SAMLStorage samlStorage = StorageUtils.getSAMLStorage(storage);

        SAMLClaimsInfo claimsInfo = samlStorage.getSAMLClaimsAndRemoveCode(tenantIdentifier, code);
        if (claimsInfo == null) {
            throw new IllegalStateException("INVALID_CODE");
        }

        JWTSigningKeyInfo keyToUse = SigningKeys.getInstance(tenantIdentifier.toAppIdentifier(), main)
                .getStaticKeyForAlgorithm(JWTSigningKey.SupportedAlgorithms.RS256);

        String sub = null;
        String email = null;

        JsonObject claims = claimsInfo.claims;

        if (claims.has("http://schemas.microsoft.com/identity/claims/objectidentifier")) {
            sub = claims.getAsJsonArray("http://schemas.microsoft.com/identity/claims/objectidentifier")
                    .get(0).getAsString();
        } else if (claims.has("NameID")) {
            sub = claims.getAsJsonArray("NameID").get(0).getAsString();
        } else if (claims.has("http://schemas.xmlsoap.org/ws/2005/05/identity/claims/name")) {
            sub = claims.getAsJsonArray("http://schemas.xmlsoap.org/ws/2005/05/identity/claims/name")
                    .get(0).getAsString();
        }

        if (claims.has("http://schemas.xmlsoap.org/ws/2005/05/identity/claims/emailaddress")) {
            email = claims.getAsJsonArray("http://schemas.xmlsoap.org/ws/2005/05/identity/claims/emailaddress")
                    .get(0).getAsString();
        } else if (claims.has("NameID")) {
            String nameIdValue = claims.getAsJsonArray("NameID").get(0).getAsString();
            if (nameIdValue.contains("@")) {
                email = nameIdValue;
            }
        }

        JsonObject payload = new JsonObject();
        payload.addProperty("stt", 3); // TODO update the constant
        payload.add("claims", claims);
        payload.addProperty("sub", sub);
        payload.addProperty("email", email);
        payload.addProperty("aud", claimsInfo.clientId);

        long iat = System.currentTimeMillis();
        long exp = iat + 1000 * 3600;

        return JWTSigningFunctions.createJWTToken(JWTSigningKey.SupportedAlgorithms.RS256, new HashMap<>(),
                payload, null, exp, iat, keyToUse);
    }
}
