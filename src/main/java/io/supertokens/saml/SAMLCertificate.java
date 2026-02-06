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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.cert.CertIOException;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

import io.supertokens.Main;
import io.supertokens.ResourceDistributor;
import io.supertokens.output.Logging;
import io.supertokens.pluginInterface.KeyValueInfo;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.exceptions.StorageTransactionLogicException;
import io.supertokens.pluginInterface.multitenancy.AppIdentifier;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifier;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.pluginInterface.sqlStorage.SQLStorage;
import io.supertokens.storageLayer.StorageLayer;

public class SAMLCertificate extends ResourceDistributor.SingletonResource {
    private static final String RESOURCE_KEY = "io.supertokens.saml.SAMLCertificate";
    private final Main main;
    private final AppIdentifier appIdentifier;

    private static final String SAML_KEY_PAIR_NAME = "saml_key_pair";
    private static final String SAML_CERTIFICATE_NAME = "saml_certificate";

    // Static test certificate - generated once per JVM with smaller key size for speed
    private static volatile KeyPair staticTestKeyPair = null;
    private static volatile X509Certificate staticTestCertificate = null;
    private static final Object staticTestLock = new Object();

    private KeyPair spKeyPair = null;
    private X509Certificate spCertificate = null;

    private SAMLCertificate(AppIdentifier appIdentifier, Main main) throws
            TenantOrAppNotFoundException {
        this.main = main;
        this.appIdentifier = appIdentifier;
        try {
            if (!Main.isTesting) {
                // Creation of new certificate is slow, not really necessary to create one for each test
                this.getCertificate();
            }
        } catch (StorageQueryException | TenantOrAppNotFoundException e) {
            Logging.error(main, appIdentifier.getAsPublicTenantIdentifier(), "Error while fetching SAML key and certificate",
                    false, e);
        }
    }

    public synchronized X509Certificate getCertificate()
            throws StorageQueryException, TenantOrAppNotFoundException {
        // In testing mode, use static test certificate (generated once per JVM, with smaller key size)
        if (Main.isTesting) {
            ensureStaticTestCertificateExists();
            return staticTestCertificate;
        }

        if (this.spCertificate == null || this.spCertificate.getNotAfter().before(new Date())) {
            maybeGenerateNewCertificateAndUpdateInDb();
        }

        return this.spCertificate;
    }

    /**
     * Returns the private key for the SP certificate.
     * This is needed for signing SAML AuthnRequests.
     */
    public synchronized PrivateKey getPrivateKey()
            throws StorageQueryException, TenantOrAppNotFoundException {
        // In testing mode, use static test certificate (generated once per JVM, with smaller key size)
        if (Main.isTesting) {
            ensureStaticTestCertificateExists();
            return staticTestKeyPair.getPrivate();
        }

        if (this.spKeyPair == null || this.spCertificate == null || this.spCertificate.getNotAfter().before(new Date())) {
            maybeGenerateNewCertificateAndUpdateInDb();
        }

        return this.spKeyPair.getPrivate();
    }

    /**
     * Ensures the static test certificate exists, generating it once per JVM if needed.
     * Uses 2048-bit keys instead of 4096-bit for faster generation.
     */
    private static void ensureStaticTestCertificateExists() {
        if (staticTestCertificate != null) {
            return;
        }
        synchronized (staticTestLock) {
            if (staticTestCertificate != null) {
                return;
            }
            try {
                // Generate with smaller key size for speed (2048 instead of 4096)
                KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
                keyGen.initialize(2048);
                staticTestKeyPair = keyGen.generateKeyPair();
                staticTestCertificate = generateSelfSignedCertificateForKeyPair(staticTestKeyPair);
            } catch (Exception e) {
                throw new RuntimeException("Failed to generate static test certificate", e);
            }
        }
    }

    private static X509Certificate generateSelfSignedCertificateForKeyPair(KeyPair keyPair)
            throws CertIOException, OperatorCreationException, CertificateException {
        Date notBefore = new Date();
        Date notAfter = new Date(notBefore.getTime() + 10 * 365L * 24 * 60 * 60 * 1000); // 10 year validity

        X500Name subject = new X500Name("CN=SAML-SP-TEST, O=SuperTokens, C=US");
        X500Name issuer = subject;

        SecureRandom random = new SecureRandom();
        java.math.BigInteger serialNumber = new java.math.BigInteger(128, random);

        JcaX509v3CertificateBuilder certBuilder = new JcaX509v3CertificateBuilder(
                issuer, serialNumber, notBefore, notAfter, subject, keyPair.getPublic());

        KeyUsage keyUsage = new KeyUsage(KeyUsage.digitalSignature | KeyUsage.keyEncipherment);
        certBuilder.addExtension(Extension.keyUsage, true, keyUsage);

        BasicConstraints basicConstraints = new BasicConstraints(false);
        certBuilder.addExtension(Extension.basicConstraints, true, basicConstraints);

        ContentSigner contentSigner = new JcaContentSignerBuilder("SHA256withRSA")
                .build(keyPair.getPrivate());

        X509CertificateHolder certHolder = certBuilder.build(contentSigner);
        JcaX509CertificateConverter converter = new JcaX509CertificateConverter();
        return converter.getCertificate(certHolder);
    }

    /**
     * Warm-up method for SAML tests. Call this during test setup to pre-initialize
     * the OpenSAML library and test certificate, ensuring predictable timing during actual tests.
     * This method is idempotent and safe to call multiple times.
     */
    public static void warmUpForTesting() {
        if (!Main.isTesting) {
            return;
        }
        // Initialize OpenSAML library
        SAMLBootstrap.initialize();
        // Pre-generate the static test certificate
        ensureStaticTestCertificateExists();
    }

    private void maybeGenerateNewCertificateAndUpdateInDb() throws TenantOrAppNotFoundException {
         SQLStorage storage = (SQLStorage) StorageLayer.getStorage(
                this.appIdentifier.getAsPublicTenantIdentifier(), main);

         try {
             storage.startTransaction(con -> {
                 KeyValueInfo keyPairInfo = storage.getKeyValue_Transaction(this.appIdentifier.getAsPublicTenantIdentifier(), con, SAML_KEY_PAIR_NAME);
                 KeyValueInfo certInfo = storage.getKeyValue_Transaction(this.appIdentifier.getAsPublicTenantIdentifier(), con, SAML_CERTIFICATE_NAME);

                 if (keyPairInfo == null || certInfo == null) {
                     try {
                         generateNewCertificate();
                     } catch (Exception e) {
                         throw new RuntimeException(e);
                     }

                     try {
                         String keyPairStr = serializeKeyPair(spKeyPair);
                         String certStr = serializeCertificate(spCertificate);
                         keyPairInfo = new KeyValueInfo(keyPairStr);
                         certInfo = new KeyValueInfo(certStr);
                     } catch (IOException e) {
                         throw new RuntimeException("Failed to serialize key pair or certificate", e);
                     }
                     storage.setKeyValue_Transaction(this.appIdentifier.getAsPublicTenantIdentifier(), con, SAML_KEY_PAIR_NAME, keyPairInfo);
                     storage.setKeyValue_Transaction(this.appIdentifier.getAsPublicTenantIdentifier(), con, SAML_CERTIFICATE_NAME, certInfo);
                 }

                 String keyPairStr = keyPairInfo.value;
                 String certStr = certInfo.value;

                 try {
                     this.spKeyPair = deserializeKeyPair(keyPairStr);
                     this.spCertificate = deserializeCertificate(certStr);
                 } catch (Exception e) {
                     throw new RuntimeException("Failed to deserialize key pair or certificate", e);
                 }

                 // If the certificate has expired, generate and persist a new one
                 if (this.spCertificate.getNotAfter().before(new Date())) {
                     try {
                         generateNewCertificate();
                         String newKeyPairStr = serializeKeyPair(spKeyPair);
                         String newCertStr = serializeCertificate(spCertificate);
                         KeyValueInfo newKeyPairInfo = new KeyValueInfo(newKeyPairStr);
                         KeyValueInfo newCertInfo = new KeyValueInfo(newCertStr);
                         storage.setKeyValue_Transaction(this.appIdentifier.getAsPublicTenantIdentifier(), con, SAML_KEY_PAIR_NAME, newKeyPairInfo);
                         storage.setKeyValue_Transaction(this.appIdentifier.getAsPublicTenantIdentifier(), con, SAML_CERTIFICATE_NAME, newCertInfo);
                     } catch (Exception e) {
                         throw new RuntimeException("Failed to regenerate expired certificate", e);
                     }
                 }

                 return null;
             });
         } catch (StorageTransactionLogicException | StorageQueryException e) {
             throw new RuntimeException("Storage error", e);
         }
    }

    void generateNewCertificate()
            throws NoSuchAlgorithmException, CertificateException, OperatorCreationException, CertIOException {
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(4096);
        spKeyPair = keyGen.generateKeyPair();
        spCertificate = generateSelfSignedCertificate();
    }

    private X509Certificate generateSelfSignedCertificate()
            throws CertIOException, OperatorCreationException, CertificateException {
        // Create a production-ready self-signed X.509 certificate using BouncyCastle
        Date notBefore = new Date();
        Date notAfter = new Date(notBefore.getTime() + 10 * 365L * 24 * 60 * 60 * 1000); // 10 year validity

        // Create the certificate subject and issuer (same for self-signed)
        X500Name subject = new X500Name("CN=SAML-SP, O=SuperTokens, C=US");
        X500Name issuer = subject; // Self-signed

        // Generate a random serial number (128 bits for good uniqueness)
        SecureRandom random = new SecureRandom();
        java.math.BigInteger serialNumber = new java.math.BigInteger(128, random);

        // Create the certificate builder
        JcaX509v3CertificateBuilder certBuilder = new JcaX509v3CertificateBuilder(
                issuer,
                serialNumber,
                notBefore,
                notAfter,
                subject,
                spKeyPair.getPublic()
        );

        // Add extensions for proper SAML usage
        // Key Usage: digitalSignature and keyEncipherment
        KeyUsage keyUsage = new KeyUsage(KeyUsage.digitalSignature | KeyUsage.keyEncipherment);
        certBuilder.addExtension(Extension.keyUsage, true, keyUsage);

        // Basic Constraints: not a CA
        BasicConstraints basicConstraints = new BasicConstraints(false);
        certBuilder.addExtension(Extension.basicConstraints, true, basicConstraints);

        // Create the content signer
        ContentSigner contentSigner = new JcaContentSignerBuilder("SHA256withRSA")
                .build(spKeyPair.getPrivate());

        // Build the certificate
        X509CertificateHolder certHolder = certBuilder.build(contentSigner);

        // Convert to standard X509Certificate
        JcaX509CertificateConverter converter = new JcaX509CertificateConverter();
        return converter.getCertificate(certHolder);
    }

    /**
     * Serializes a KeyPair to a Base64 encoded string format
     */
    private String serializeKeyPair(KeyPair keyPair) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        
        // Write private key
        byte[] privateKeyBytes = keyPair.getPrivate().getEncoded();
        baos.write(Base64.getEncoder().encode(privateKeyBytes));
        baos.write('\n');
        
        // Write public key
        byte[] publicKeyBytes = keyPair.getPublic().getEncoded();
        baos.write(Base64.getEncoder().encode(publicKeyBytes));
        
        return baos.toString();
    }

    /**
     * Deserializes a KeyPair from a Base64 encoded string format
     */
    private KeyPair deserializeKeyPair(String keyPairStr) throws Exception {
        String[] parts = keyPairStr.split("\n");
        if (parts.length != 2) {
            throw new IllegalArgumentException("Invalid key pair string format");
        }
        
        // Decode private key
        byte[] privateKeyBytes = Base64.getDecoder().decode(parts[0]);
        PKCS8EncodedKeySpec privateKeySpec = new PKCS8EncodedKeySpec(privateKeyBytes);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        PrivateKey privateKey = keyFactory.generatePrivate(privateKeySpec);
        
        // Decode public key
        byte[] publicKeyBytes = Base64.getDecoder().decode(parts[1]);
        X509EncodedKeySpec publicKeySpec = new X509EncodedKeySpec(publicKeyBytes);
        PublicKey publicKey = keyFactory.generatePublic(publicKeySpec);
        
        return new KeyPair(publicKey, privateKey);
    }

    /**
     * Serializes an X509Certificate to a Base64 encoded string format
     */
    private String serializeCertificate(X509Certificate certificate) throws IOException {
        try {
            byte[] certBytes = certificate.getEncoded();
            return Base64.getEncoder().encodeToString(certBytes);
        } catch (CertificateException e) {
            throw new IOException("Failed to encode certificate", e);
        }
    }

    /**
     * Deserializes an X509Certificate from a Base64 encoded string format
     */
    private X509Certificate deserializeCertificate(String certStr) throws Exception {
        try {
            byte[] certBytes = Base64.getDecoder().decode(certStr);
            CertificateFactory certFactory = CertificateFactory.getInstance("X.509");
            ByteArrayInputStream bais = new ByteArrayInputStream(certBytes);
            return (X509Certificate) certFactory.generateCertificate(bais);
        } catch (CertificateException e) {
            throw new Exception("Failed to decode certificate", e);
        }
    }

    public static SAMLCertificate getInstance(AppIdentifier appIdentifier, Main main)
            throws TenantOrAppNotFoundException {
        return (SAMLCertificate) main.getResourceDistributor()
                .getResource(appIdentifier, RESOURCE_KEY);
    }

    public static void loadForAllTenants(Main main, List<AppIdentifier> apps,
                                         List<TenantIdentifier> tenantsThatChanged) {
        try {
            main.getResourceDistributor().withResourceDistributorLock(() -> {
                Map<ResourceDistributor.KeyClass, ResourceDistributor.SingletonResource> existingResources =
                        main.getResourceDistributor()
                                .getAllResourcesWithResourceKey(RESOURCE_KEY);
                main.getResourceDistributor().clearAllResourcesWithResourceKey(RESOURCE_KEY);
                for (AppIdentifier app : apps) {
                    ResourceDistributor.SingletonResource resource = existingResources.get(
                            new ResourceDistributor.KeyClass(app, RESOURCE_KEY));
                    if (resource != null && !tenantsThatChanged.contains(app.getAsPublicTenantIdentifier())) {
                        main.getResourceDistributor().setResource(app, RESOURCE_KEY,
                                resource);
                    } else {
                        try {
                            main.getResourceDistributor()
                                    .setResource(app, RESOURCE_KEY,
                                            new SAMLCertificate(app, main));
                        } catch (TenantOrAppNotFoundException e) {
                            Logging.error(main, app.getAsPublicTenantIdentifier(), e.getMessage(), false);
                            // continue loading other resources
                        }
                    }
                }
                return null;
            });
        } catch (ResourceDistributor.FuncException e) {
            throw new IllegalStateException("should never happen", e);
        }
    }
}
