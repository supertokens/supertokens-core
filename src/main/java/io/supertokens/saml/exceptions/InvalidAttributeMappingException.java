package io.supertokens.saml.exceptions;

// An `attributeMapping` value on a SAML client write is invalid; the message names the offending key.
public class InvalidAttributeMappingException extends Exception {
    public InvalidAttributeMappingException(String message) {
        super(message);
    }
}
