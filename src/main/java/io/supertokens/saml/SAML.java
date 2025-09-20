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
import io.supertokens.Main;
import io.supertokens.pluginInterface.Storage;
import io.supertokens.pluginInterface.StorageUtils;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.multitenancy.AppIdentifier;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifier;

import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.pluginInterface.saml.SAMLClient;
import io.supertokens.pluginInterface.saml.SAMLRelayStateInfo;
import io.supertokens.pluginInterface.saml.SAMLStorage;
import io.supertokens.saml.exceptions.InvalidClientException;
import io.supertokens.saml.exceptions.MalformedSAMLMetadataXMLException;
import net.shibboleth.utilities.java.support.xml.SerializeSupport;
import org.opensaml.core.xml.XMLObject;
import org.opensaml.core.xml.XMLObjectBuilderFactory;
import org.opensaml.core.xml.config.XMLObjectProviderRegistrySupport;
import org.opensaml.core.xml.util.XMLObjectSupport;
import org.opensaml.saml.common.SAMLVersion;
import org.opensaml.saml.common.xml.SAMLConstants;
import org.opensaml.saml.saml2.core.AuthnContext;
import org.opensaml.saml.saml2.core.AuthnContextClassRef;
import org.opensaml.saml.saml2.core.AuthnRequest;
import org.opensaml.saml.saml2.core.Issuer;
import org.opensaml.saml.saml2.core.NameIDPolicy;
import org.opensaml.saml.saml2.core.RequestedAuthnContext;
import org.opensaml.saml.saml2.metadata.EntityDescriptor;
import org.opensaml.saml.saml2.metadata.IDPSSODescriptor;
import org.opensaml.saml.saml2.metadata.SingleSignOnService;
import org.opensaml.xmlsec.signature.KeyInfo;
import org.opensaml.xmlsec.signature.Signature;
import org.opensaml.xmlsec.signature.X509Data;
import org.opensaml.xmlsec.signature.impl.KeyInfoBuilder;
import org.opensaml.xmlsec.signature.impl.SignatureBuilder;
import org.opensaml.xmlsec.signature.impl.X509DataBuilder;
import org.opensaml.xmlsec.signature.support.SignatureConstants;
import org.w3c.dom.Element;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.UUID;
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

        SAMLClient client = new SAMLClient(clientId, idpSsoUrl, redirectURIs, defaultRedirectURI, spEntityId);
        return samlStorage.createOrUpdateSAMLClient(tenantIdentifier, client);
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
                client.spEntityId, acsURL + "?client_id=" + URLEncoder.encode(clientId, StandardCharsets.UTF_8));
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

    public static String handleCallback(TenantIdentifier tenantIdentifier, Storage storage, String clientId, String samlResponse, String relayState) throws StorageQueryException {
        SAMLStorage samlStorage = StorageUtils.getSAMLStorage(storage);

        if (relayState != null) {
            // sp initiated
            var relayStateInfo = samlStorage.getRelayStateInfo(tenantIdentifier, relayState);

            if (relayStateInfo == null) {
                throw new IllegalStateException("INVALID_RELAY_STATE");
            }

            if (clientId == null) {
                throw new IllegalStateException("CLIENT_ID_IS_REQUIRED");
            }

            SAMLClient client = samlStorage.getSAMLClient(tenantIdentifier, clientId);
            String code = UUID.randomUUID().toString();
            String state = relayStateInfo.state;

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
}
