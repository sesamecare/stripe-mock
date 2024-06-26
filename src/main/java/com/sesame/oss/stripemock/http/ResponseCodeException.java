package com.sesame.oss.stripemock.http;

public class ResponseCodeException extends Exception {
    private final int responseCode;
    private final String errorMessage;
    private final String code;
    private final String errorType;
    private final String declineCode;
    private final String param;

    public ResponseCodeException(int responseCode, String errorMessage) {
        this(responseCode, errorMessage, null, null, null, null);
    }

    public ResponseCodeException(int responseCode, String errorMessage, String code, String errorType, String declineCode, String param) {
        super(errorMessage);
        this.responseCode = responseCode;
        this.errorMessage = errorMessage;
        this.code = code;
        this.errorType = errorType;
        this.declineCode = declineCode;
        this.param = param;
    }

    public int getResponseCode() {
        return responseCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public String getCode() {
        return code;
    }

    public String getErrorType() {
        return errorType;
    }

    public String getDeclineCode() {
        return declineCode;
    }

    public String getParam() {
        return param;
    }

    public static ResponseCodeException noSuchEntity(int code, String entityType, String entityId) {
        return new ResponseCodeException(code,
                                         String.format("No such %s: '%s'", entityType, entityId),
                                         "resource_missing",
                                         "invalid_request_error",
                                         null,
                                         null);
    }
}
