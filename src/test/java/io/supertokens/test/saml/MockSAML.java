package io.supertokens.test.saml;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import javax.xml.namespace.QName;

import net.shibboleth.utilities.java.support.xml.SerializeSupport;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.opensaml.core.xml.XMLObject;
import org.opensaml.core.xml.config.XMLObjectProviderRegistrySupport;
import org.opensaml.core.xml.io.MarshallingException;
import org.opensaml.core.xml.util.XMLObjectSupport;
import org.opensaml.saml.common.SAMLVersion;
import org.opensaml.saml.common.xml.SAMLConstants;
import org.opensaml.saml.saml2.core.Assertion;
import org.opensaml.saml.saml2.core.Attribute;
import org.opensaml.saml.saml2.core.AttributeStatement;
import org.opensaml.saml.saml2.core.Audience;
import org.opensaml.saml.saml2.core.AudienceRestriction;
import org.opensaml.saml.saml2.core.AuthnContext;
import org.opensaml.saml.saml2.core.AuthnContextClassRef;
import org.opensaml.saml.saml2.core.AuthnStatement;
import org.opensaml.saml.saml2.core.Conditions;
import org.opensaml.saml.saml2.core.Issuer;
import org.opensaml.saml.saml2.core.NameID;
import org.opensaml.saml.saml2.core.NameIDType;
import org.opensaml.saml.saml2.core.Response;
import org.opensaml.saml.saml2.core.Status;
import org.opensaml.saml.saml2.core.StatusCode;
import org.opensaml.saml.saml2.core.Subject;
import org.opensaml.saml.saml2.core.SubjectConfirmation;
import org.opensaml.saml.saml2.core.SubjectConfirmationData;
import org.opensaml.saml.saml2.metadata.*;
import org.opensaml.security.credential.Credential;
import org.opensaml.security.credential.CredentialSupport;
import org.opensaml.security.credential.UsageType;
import org.opensaml.xmlsec.signature.KeyInfo;
import org.opensaml.xmlsec.signature.Signature;
import org.opensaml.xmlsec.signature.X509Data;
import org.opensaml.xmlsec.signature.impl.KeyInfoBuilder;
import org.opensaml.xmlsec.signature.impl.SignatureBuilder;
import org.opensaml.xmlsec.signature.impl.X509DataBuilder;
import org.opensaml.xmlsec.signature.support.SignatureConstants;
import org.opensaml.xmlsec.signature.support.Signer;
import org.w3c.dom.Element;

import javax.xml.namespace.QName;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.*;

// NOTE: This class provides helpers to mimic a minimal SAML IdP for tests.
public class MockSAML {
    public static class KeyMaterial {
        public final PrivateKey privateKey;
        public final X509Certificate certificate;

        public KeyMaterial(PrivateKey privateKey, X509Certificate certificate) {
            this.privateKey = privateKey;
            this.certificate = certificate;
        }

        public String getCertificateBase64Der() {
            try {
                return Base64.getEncoder().encodeToString(certificate.getEncoded());
            } catch (CertificateEncodingException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public static KeyMaterial generateSelfSignedKeyMaterial() {
        try {
            KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
            keyGen.initialize(2048);
            KeyPair keyPair = keyGen.generateKeyPair();

            Date notBefore = new Date();
            Date notAfter = new Date(notBefore.getTime() + 365L * 24 * 60 * 60 * 1000); // 1 year

            X500Name subject = new X500Name("CN=Mock IdP, O=SuperTokens, C=US");

            java.math.BigInteger serialNumber = java.math.BigInteger.valueOf(System.currentTimeMillis());

            JcaX509v3CertificateBuilder certBuilder = new JcaX509v3CertificateBuilder(
                    subject,
                    serialNumber,
                    notBefore,
                    notAfter,
                    subject,
                    keyPair.getPublic()
            );

            KeyUsage keyUsage = new KeyUsage(KeyUsage.digitalSignature | KeyUsage.keyEncipherment);
            certBuilder.addExtension(Extension.keyUsage, true, keyUsage);

            BasicConstraints basicConstraints = new BasicConstraints(false);
            certBuilder.addExtension(Extension.basicConstraints, true, basicConstraints);

            ContentSigner contentSigner = new JcaContentSignerBuilder("SHA256withRSA")
                    .build(keyPair.getPrivate());

            X509CertificateHolder certHolder = certBuilder.build(contentSigner);
            JcaX509CertificateConverter converter = new JcaX509CertificateConverter();
            X509Certificate certificate = converter.getCertificate(certHolder);

            return new KeyMaterial(keyPair.getPrivate(), certificate);
        } catch (OperatorCreationException | CertificateException | java.security.NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        } catch (org.bouncycastle.cert.CertIOException e) {
            throw new RuntimeException(e);
        }
    }

    // Tests should provide their own PEM materials; helpers below parse PEM into usable objects.
    public static KeyMaterial createKeyMaterialFromPEM(String privateKeyPEM, String certificatePEM) {
        return new KeyMaterial(parsePrivateKeyFromPEM(privateKeyPEM), parseCertificateFromPEM(certificatePEM));
    }

    public static String generateIdpMetadataXML(String idpEntityId, String ssoRedirectUrl, X509Certificate cert) {
        EntityDescriptor entityDescriptor = build(EntityDescriptor.DEFAULT_ELEMENT_NAME);
        entityDescriptor.setEntityID(idpEntityId);

        IDPSSODescriptor idp = build(IDPSSODescriptor.DEFAULT_ELEMENT_NAME);
        idp.addSupportedProtocol(SAMLConstants.SAML20P_NS);
        idp.setWantAuthnRequestsSigned(true);

        // Add both Redirect and POST bindings pointing to the same SSO URL
        SingleSignOnService ssoRedirect = build(SingleSignOnService.DEFAULT_ELEMENT_NAME);
        ssoRedirect.setBinding(SAMLConstants.SAML2_REDIRECT_BINDING_URI);
        ssoRedirect.setLocation(ssoRedirectUrl);
        idp.getSingleSignOnServices().add(ssoRedirect);

        SingleSignOnService ssoPost = build(SingleSignOnService.DEFAULT_ELEMENT_NAME);
        ssoPost.setBinding(SAMLConstants.SAML2_POST_BINDING_URI);
        ssoPost.setLocation(ssoRedirectUrl);
        idp.getSingleSignOnServices().add(ssoPost);

        KeyDescriptor keyDesc = build(KeyDescriptor.DEFAULT_ELEMENT_NAME);
        keyDesc.setUse(UsageType.SIGNING);

        KeyInfo keyInfo = buildKeyInfoWithCert(cert);
        keyDesc.setKeyInfo(keyInfo);
        idp.getKeyDescriptors().add(keyDesc);

        // NameIDFormat: emailAddress
        NameIDFormat nameIdFormat = build(NameIDFormat.DEFAULT_ELEMENT_NAME);
        nameIdFormat.setFormat("urn:oasis:names:tc:SAML:1.1:nameid-format:emailAddress");
        idp.getNameIDFormats().add(nameIdFormat);

        entityDescriptor.getRoleDescriptors().add(idp);
        return toXmlString(entityDescriptor);
    }

    public static String generateSignedSAMLResponseBase64(
            String issuerEntityId,
            String audience,
            String acsUrl,
            String nameId,
            Map<String, List<String>> attributes,
            String inResponseTo,
            KeyMaterial keyMaterial,
            int notOnOrAfterSeconds
    ) {
        Instant now = Instant.now();
        Instant notOnOrAfter = now.plusSeconds(Math.max(60, notOnOrAfterSeconds));

        Response response = build(Response.DEFAULT_ELEMENT_NAME);
        response.setID(randomId());
        response.setVersion(SAMLVersion.VERSION_20);
        response.setIssueInstant(now);
        response.setDestination(acsUrl);
        if (inResponseTo != null) {
            response.setInResponseTo(inResponseTo);
        }

        Issuer issuer = build(Issuer.DEFAULT_ELEMENT_NAME);
        issuer.setValue(issuerEntityId);
        response.setIssuer(issuer);

        Status status = build(Status.DEFAULT_ELEMENT_NAME);
        StatusCode statusCode = build(StatusCode.DEFAULT_ELEMENT_NAME);
        statusCode.setValue(StatusCode.SUCCESS);
        status.setStatusCode(statusCode);
        response.setStatus(status);

        Assertion assertion = buildAssertion(
                issuerEntityId, nameId, audience, acsUrl, inResponseTo,
                now, notOnOrAfter, true /* withConditions */);

        if (attributes != null && !attributes.isEmpty()) {
            AttributeStatement attrStatement = build(AttributeStatement.DEFAULT_ELEMENT_NAME);
            for (Map.Entry<String, List<String>> e : attributes.entrySet()) {
                Attribute attr = build(Attribute.DEFAULT_ELEMENT_NAME);
                attr.setName(e.getKey());
                for (String v : e.getValue()) {
                    XMLObject val = build(new QName(SAMLConstants.SAML20_NS, "AttributeValue", SAMLConstants.SAML20_PREFIX));
                    // Represent as simple string text node
                    val.getDOM();
                    // Fallback: use anyType with text via builder marshaling
                    // Instead, we can use XSString builder:
                    org.opensaml.core.xml.schema.impl.XSStringBuilder sb = new org.opensaml.core.xml.schema.impl.XSStringBuilder();
                    org.opensaml.core.xml.schema.XSString xs = sb.buildObject(
                            new QName(SAMLConstants.SAML20_NS, "AttributeValue", SAMLConstants.SAML20_PREFIX),
                            org.opensaml.core.xml.schema.XSString.TYPE_NAME);
                    xs.setValue(v);
                    attr.getAttributeValues().add(xs);
                }
                attrStatement.getAttributes().add(attr);
            }
            assertion.getAttributeStatements().add(attrStatement);
        }

        signAssertion(assertion, keyMaterial);
        response.getAssertions().add(assertion);

        String xml = toXmlString(response);
        return Base64.getEncoder().encodeToString(xml.getBytes(StandardCharsets.UTF_8));
    }

    public static KeyInfo buildKeyInfoWithCert(X509Certificate cert) {
        KeyInfoBuilder keyInfoBuilder = new KeyInfoBuilder();
        KeyInfo keyInfo = keyInfoBuilder.buildObject();
        X509DataBuilder x509DataBuilder = new X509DataBuilder();
        X509Data x509Data = x509DataBuilder.buildObject();
        org.opensaml.xmlsec.signature.X509Certificate x509CertElem =
                (org.opensaml.xmlsec.signature.X509Certificate) XMLObjectSupport.buildXMLObject(
                        org.opensaml.xmlsec.signature.X509Certificate.DEFAULT_ELEMENT_NAME);
        try {
            x509CertElem.setValue(Base64.getEncoder().encodeToString(cert.getEncoded()));
        } catch (CertificateEncodingException e) {
            throw new RuntimeException(e);
        }
        x509Data.getX509Certificates().add(x509CertElem);
        keyInfo.getX509Datas().add(x509Data);
        return keyInfo;
    }

    private static <T> T build(QName qName) {
        return (T) Objects.requireNonNull(
                XMLObjectProviderRegistrySupport.getBuilderFactory().getBuilder(qName)).buildObject(qName);
    }

    private static String toXmlString(XMLObject xmlObject) {
        try {
            Element el = XMLObjectSupport.marshall(xmlObject);
            return SerializeSupport.nodeToString(el);
        } catch (MarshallingException e) {
            throw new RuntimeException(e);
        }
    }

    private static void signAssertion(Assertion assertion, KeyMaterial km) {
        try {
            Credential cred = CredentialSupport.getSimpleCredential(km.certificate, km.privateKey);
            SignatureBuilder signatureBuilder = new SignatureBuilder();
            Signature signature = signatureBuilder.buildObject();
            signature.setSigningCredential(cred);
            signature.setSignatureAlgorithm(SignatureConstants.ALGO_ID_SIGNATURE_RSA_SHA256);
            signature.setCanonicalizationAlgorithm(SignatureConstants.ALGO_ID_C14N_EXCL_OMIT_COMMENTS);
            signature.setKeyInfo(buildKeyInfoWithCert(km.certificate));

            assertion.setSignature(signature);
            XMLObjectSupport.marshall(assertion);
            Signer.signObject(signature);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static void signResponse(Response response, KeyMaterial km) {
        try {
            Credential cred = CredentialSupport.getSimpleCredential(km.certificate, km.privateKey);
            SignatureBuilder signatureBuilder = new SignatureBuilder();
            Signature signature = signatureBuilder.buildObject();
            signature.setSigningCredential(cred);
            signature.setSignatureAlgorithm(SignatureConstants.ALGO_ID_SIGNATURE_RSA_SHA256);
            signature.setCanonicalizationAlgorithm(SignatureConstants.ALGO_ID_C14N_EXCL_OMIT_COMMENTS);
            signature.setKeyInfo(buildKeyInfoWithCert(km.certificate));

            response.setSignature(signature);
            XMLObjectSupport.marshall(response);
            Signer.signObject(signature);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static Response buildSingleAssertionResponse(
            String issuerEntityId, String audience, String acsUrl, String nameId,
            String inResponseTo, Instant now, Instant notOnOrAfter, boolean withConditions) {
        Response response = build(Response.DEFAULT_ELEMENT_NAME);
        response.setID(randomId());
        response.setVersion(SAMLVersion.VERSION_20);
        response.setIssueInstant(now);
        response.setDestination(acsUrl);
        if (inResponseTo != null) {
            response.setInResponseTo(inResponseTo);
        }

        Issuer issuer = build(Issuer.DEFAULT_ELEMENT_NAME);
        issuer.setValue(issuerEntityId);
        response.setIssuer(issuer);

        Status status = build(Status.DEFAULT_ELEMENT_NAME);
        StatusCode statusCode = build(StatusCode.DEFAULT_ELEMENT_NAME);
        statusCode.setValue(StatusCode.SUCCESS);
        status.setStatusCode(statusCode);
        response.setStatus(status);

        response.getAssertions().add(buildAssertion(
                issuerEntityId, nameId, audience, acsUrl, inResponseTo, now, notOnOrAfter, withConditions));
        return response;
    }

    /**
     * XML Signature Wrapping — response-level variant (advisory variant A).
     *
     * The IdP legitimately signs a Response for the attacker's own account (response-level
     * signature, Reference URI = that Response's ID). The attacker lifts that ds:Signature out
     * and reparents it onto a freshly authored Response whose assertion names the victim, then
     * parks the original signed Response — untouched apart from the removed signature, so its ID
     * still matches the Reference — inside a samlp:Extensions element of the new Response.
     *
     * SignatureValidator.validate() alone accepts this: the Reference still resolves (to the
     * parked Response) and the cryptography checks out. Only SAMLSignatureProfileValidator, which
     * requires the Reference to name the enclosing Response's own ID, rejects it.
     */
    public static String generateResponseLevelXSWWrappedSAMLResponseBase64(
            String issuerEntityId, String audience, String acsUrl,
            String legitimateNameId, String forgedNameId, String inResponseTo,
            KeyMaterial keyMaterial, int notOnOrAfterSeconds) {
        Instant now = Instant.now();
        Instant notOnOrAfter = now.plusSeconds(Math.max(60, notOnOrAfterSeconds));

        Response legit = buildSingleAssertionResponse(
                issuerEntityId, audience, acsUrl, legitimateNameId, inResponseTo, now, notOnOrAfter, true);
        signResponse(legit, keyMaterial);
        org.w3c.dom.Document legitDoc = parseXml(toXmlString(legit));
        org.w3c.dom.Element legitResp = legitDoc.getDocumentElement();
        org.w3c.dom.Element sig = firstChildNS(legitResp, DS_NS, "Signature");
        legitResp.removeChild(sig);

        Response evil = buildSingleAssertionResponse(
                issuerEntityId, audience, acsUrl, forgedNameId, inResponseTo, now, notOnOrAfter, true);
        org.w3c.dom.Document evilDoc = parseXml(toXmlString(evil));
        org.w3c.dom.Element evilResp = evilDoc.getDocumentElement();

        // Reparent the borrowed signature onto the forged Response (right after Issuer).
        org.w3c.dom.Node importedSig = evilDoc.importNode(sig, true);
        insertAfter(evilResp, importedSig, firstChildNS(evilResp, SAML_NS, "Issuer"));

        // Park the genuinely-signed (now signature-less) Response under samlp:Extensions,
        // where its ID keeps the borrowed signature's Reference resolvable.
        org.w3c.dom.Element extensions = evilDoc.createElementNS(SAMLP_NS, "samlp:Extensions");
        extensions.appendChild(evilDoc.importNode(legitResp, true));
        insertAfter(evilResp, extensions, importedSig);

        return Base64.getEncoder().encodeToString(serialize(evilResp).getBytes(StandardCharsets.UTF_8));
    }

    /**
     * XML Signature Wrapping — assertion-level variant (advisory variant B).
     *
     * The IdP legitimately signs an assertion for the attacker's own account. The attacker lifts
     * that assertion's ds:Signature onto a forged assertion naming the victim, and parks the
     * genuinely-signed (now signature-less) attacker assertion inside the forged assertion's
     * saml:Advice element, where its ID keeps the Reference resolvable. This survives the
     * per-assertion "every assertion must carry a valid signature" loop — the forged assertion
     * does carry one — but SAMLSignatureProfileValidator rejects it because the Reference names
     * the parked assertion's ID, not the forged assertion's own ID.
     */
    public static String generateAssertionLevelXSWWrappedSAMLResponseBase64(
            String issuerEntityId, String audience, String acsUrl,
            String legitimateNameId, String forgedNameId, String inResponseTo,
            KeyMaterial keyMaterial, int notOnOrAfterSeconds) {
        Instant now = Instant.now();
        Instant notOnOrAfter = now.plusSeconds(Math.max(60, notOnOrAfterSeconds));

        // Attacker's own response with an assertion-level signature.
        Response legit = buildSingleAssertionResponse(
                issuerEntityId, audience, acsUrl, legitimateNameId, inResponseTo, now, notOnOrAfter, true);
        signAssertion(legit.getAssertions().get(0), keyMaterial);
        org.w3c.dom.Document legitDoc = parseXml(toXmlString(legit));
        org.w3c.dom.Element legitAssertion = firstChildNS(legitDoc.getDocumentElement(), SAML_NS, "Assertion");
        org.w3c.dom.Element aSig = firstChildNS(legitAssertion, DS_NS, "Signature");
        legitAssertion.removeChild(aSig);

        // Forged, unsigned response naming the victim.
        Response evil = buildSingleAssertionResponse(
                issuerEntityId, audience, acsUrl, forgedNameId, inResponseTo, now, notOnOrAfter, true);
        org.w3c.dom.Document evilDoc = parseXml(toXmlString(evil));
        org.w3c.dom.Element evilResp = evilDoc.getDocumentElement();
        org.w3c.dom.Element forgedAssertion = firstChildNS(evilResp, SAML_NS, "Assertion");

        // Reparent the borrowed signature onto the forged assertion (right after its Issuer).
        org.w3c.dom.Node importedSig = evilDoc.importNode(aSig, true);
        insertAfter(forgedAssertion, importedSig, firstChildNS(forgedAssertion, SAML_NS, "Issuer"));

        // Park the genuinely-signed (now signature-less) attacker assertion in saml:Advice.
        org.w3c.dom.Element advice = evilDoc.createElementNS(SAML_NS, "saml:Advice");
        advice.appendChild(evilDoc.importNode(legitAssertion, true));
        forgedAssertion.appendChild(advice);

        return Base64.getEncoder().encodeToString(serialize(evilResp).getBytes(StandardCharsets.UTF_8));
    }

    private static final String SAMLP_NS = "urn:oasis:names:tc:SAML:2.0:protocol";
    private static final String SAML_NS = "urn:oasis:names:tc:SAML:2.0:assertion";
    private static final String DS_NS = "http://www.w3.org/2000/09/xmldsig#";

    private static org.w3c.dom.Document parseXml(String xml) {
        try {
            javax.xml.parsers.DocumentBuilderFactory dbf = javax.xml.parsers.DocumentBuilderFactory.newInstance();
            dbf.setNamespaceAware(true);
            return dbf.newDocumentBuilder().parse(
                    new java.io.ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static org.w3c.dom.Element firstChildNS(org.w3c.dom.Element parent, String ns, String localName) {
        org.w3c.dom.NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            org.w3c.dom.Node n = children.item(i);
            if (n.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE
                    && ns.equals(n.getNamespaceURI()) && localName.equals(n.getLocalName())) {
                return (org.w3c.dom.Element) n;
            }
        }
        throw new IllegalStateException("child element {" + ns + "}" + localName + " not found");
    }

    private static void insertAfter(org.w3c.dom.Element parent, org.w3c.dom.Node newNode, org.w3c.dom.Node refNode) {
        org.w3c.dom.Node next = refNode.getNextSibling();
        if (next == null) {
            parent.appendChild(newNode);
        } else {
            parent.insertBefore(newNode, next);
        }
    }

    private static String serialize(org.w3c.dom.Node node) {
        try {
            javax.xml.transform.Transformer t = javax.xml.transform.TransformerFactory.newInstance().newTransformer();
            java.io.StringWriter sw = new java.io.StringWriter();
            t.transform(new javax.xml.transform.dom.DOMSource(node),
                    new javax.xml.transform.stream.StreamResult(sw));
            return sw.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Builds a SAML Response that demonstrates the XML Signature Wrapping (XSW) attack:
     *
     *   - Assertion 1 (signed):   legitimateNameId — the attacker's own IdP-issued identity.
     *   - Assertion 2 (unsigned): forgedNameId     — the victim identity the attacker wants to impersonate.
     *
     * The signed assertion carries full conditions/audience so it passes all validation checks.
     * The forged assertion deliberately has no Conditions, so the timestamp and audience checks
     * skip it (both iterate assertions and branch on conditions == null → continue).
     *
     * extractAllClaims() iterates both assertions in order.  Because Gson's JsonObject.add()
     * is last-writer-wins, the second assertion's NameID overwrites the first, and the returned
     * claims carry forgedNameId — even though only legitimateNameId was ever signed by the IdP.
     *
     * Without a fix the callback returns OK and getUserInfo resolves to forgedNameId.
     * With a fix the callback must reject the response (or only consume the signed assertion).
     */
    public static String generateXSWWrappedSAMLResponseBase64(
            String issuerEntityId,
            String audience,
            String acsUrl,
            String legitimateNameId,
            String forgedNameId,
            String inResponseTo,
            KeyMaterial keyMaterial,
            int notOnOrAfterSeconds
    ) {
        Instant now = Instant.now();
        Instant notOnOrAfter = now.plusSeconds(Math.max(60, notOnOrAfterSeconds));

        Response response = build(Response.DEFAULT_ELEMENT_NAME);
        response.setID(randomId());
        response.setVersion(SAMLVersion.VERSION_20);
        response.setIssueInstant(now);
        response.setDestination(acsUrl);
        if (inResponseTo != null) {
            response.setInResponseTo(inResponseTo);
        }

        Issuer responseIssuer = build(Issuer.DEFAULT_ELEMENT_NAME);
        responseIssuer.setValue(issuerEntityId);
        response.setIssuer(responseIssuer);

        Status status = build(Status.DEFAULT_ELEMENT_NAME);
        StatusCode statusCode = build(StatusCode.DEFAULT_ELEMENT_NAME);
        statusCode.setValue(StatusCode.SUCCESS);
        status.setStatusCode(statusCode);
        response.setStatus(status);

        // Assertion 1: legitimate, signed by the real IdP key.
        Assertion legitimateAssertion = buildAssertion(
                issuerEntityId, legitimateNameId, audience, acsUrl, inResponseTo,
                now, notOnOrAfter, true /* withConditions */);
        signAssertion(legitimateAssertion, keyMaterial);

        // Assertion 2: forged, unsigned.  No Conditions so that timestamp and
        // audience validation loops skip it (both guard on conditions == null).
        Assertion forgedAssertion = buildAssertion(
                issuerEntityId, forgedNameId, null /* audience */, acsUrl, inResponseTo,
                now, notOnOrAfter, false /* withConditions */);
        // intentionally left unsigned

        // Order matters: forged comes last so its NameID wins in extractAllClaims().
        response.getAssertions().add(legitimateAssertion);
        response.getAssertions().add(forgedAssertion);

        String xml = toXmlString(response);
        return Base64.getEncoder().encodeToString(xml.getBytes(StandardCharsets.UTF_8));
    }

    private static Assertion buildAssertion(
            String issuerEntityId,
            String nameId,
            String audience,
            String acsUrl,
            String inResponseTo,
            Instant now,
            Instant notOnOrAfter,
            boolean withConditions
    ) {
        Assertion assertion = build(Assertion.DEFAULT_ELEMENT_NAME);
        assertion.setID(randomId());
        assertion.setIssueInstant(now);
        assertion.setVersion(SAMLVersion.VERSION_20);

        Issuer assertionIssuer = build(Issuer.DEFAULT_ELEMENT_NAME);
        assertionIssuer.setValue(issuerEntityId);
        assertion.setIssuer(assertionIssuer);

        Subject subject = build(Subject.DEFAULT_ELEMENT_NAME);
        NameID nameIdObj = build(NameID.DEFAULT_ELEMENT_NAME);
        nameIdObj.setValue(nameId);
        nameIdObj.setFormat(NameIDType.PERSISTENT);
        subject.setNameID(nameIdObj);

        SubjectConfirmation sc = build(SubjectConfirmation.DEFAULT_ELEMENT_NAME);
        sc.setMethod(SubjectConfirmation.METHOD_BEARER);
        SubjectConfirmationData scd = build(SubjectConfirmationData.DEFAULT_ELEMENT_NAME);
        scd.setRecipient(acsUrl);
        scd.setNotOnOrAfter(notOnOrAfter);
        if (inResponseTo != null) {
            scd.setInResponseTo(inResponseTo);
        }
        sc.setSubjectConfirmationData(scd);
        subject.getSubjectConfirmations().add(sc);
        assertion.setSubject(subject);

        if (withConditions && audience != null) {
            Conditions conditions = build(Conditions.DEFAULT_ELEMENT_NAME);
            conditions.setNotBefore(now.minusSeconds(1));
            conditions.setNotOnOrAfter(notOnOrAfter);
            AudienceRestriction ar = build(AudienceRestriction.DEFAULT_ELEMENT_NAME);
            Audience aud = build(Audience.DEFAULT_ELEMENT_NAME);
            aud.setURI(audience);
            ar.getAudiences().add(aud);
            conditions.getAudienceRestrictions().add(ar);
            assertion.setConditions(conditions);
        }

        AuthnStatement authnStatement = build(AuthnStatement.DEFAULT_ELEMENT_NAME);
        authnStatement.setAuthnInstant(now);
        AuthnContext authnContext = build(AuthnContext.DEFAULT_ELEMENT_NAME);
        AuthnContextClassRef classRef = build(AuthnContextClassRef.DEFAULT_ELEMENT_NAME);
        classRef.setURI(AuthnContext.PASSWORD_AUTHN_CTX);
        authnContext.setAuthnContextClassRef(classRef);
        authnStatement.setAuthnContext(authnContext);
        assertion.getAuthnStatements().add(authnStatement);

        return assertion;
    }

    private static String randomId() {
        return "_" + new BigInteger(160, new SecureRandom()).toString(16);
    }

    public static X509Certificate parseCertificateFromPEM(String pem) {
        try {
            String base64 = pem.replace("-----BEGIN CERTIFICATE-----", "")
                    .replace("-----END CERTIFICATE-----", "")
                    .replaceAll("\n|\r", "").trim();
            byte[] der = Base64.getDecoder().decode(base64);
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            return (X509Certificate) cf.generateCertificate(new java.io.ByteArrayInputStream(der));
        } catch (CertificateException e) {
            throw new RuntimeException(e);
        }
    }

    public static PrivateKey parsePrivateKeyFromPEM(String pem) {
        try {
            String base64 = pem.replace("-----BEGIN PRIVATE KEY-----", "")
                    .replace("-----END PRIVATE KEY-----", "")
                    .replaceAll("[\\n\\r\\s]", "");
            byte[] pkcs8 = Base64.getDecoder().decode(base64);
            PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(pkcs8);
            return KeyFactory.getInstance("RSA").generatePrivate(spec);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
