package io.supertokens.ee.test.httpRequest;

public class HttpResponseException extends Exception {

    private static final long serialVersionUID = 1L;
    public final int statusCode;

    HttpResponseException(int statusCode, String message) {
        super("Http error. Status Code: " + statusCode + ". Message: " + message);
        this.statusCode = statusCode;
    }
}
