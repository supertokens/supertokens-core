package io.supertokens.saml.exceptions;

// A SAML client update left out a field (`acsURLs` or `attributeMapping`) that is set on the stored client. Once
// set, such a field must be sent on every update: its current value to keep it, or null to clear it.
public class SAMLClientFieldRequiredException extends Exception {
    public final String fieldName;

    public SAMLClientFieldRequiredException(String fieldName) {
        super(fieldName + " is set on this client: send its current value to keep it, or null to clear it");
        this.fieldName = fieldName;
    }
}
