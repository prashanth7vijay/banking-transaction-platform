package com.platform.shared.exception;

import org.springframework.http.HttpStatus;

/**
 * Base class for exceptions the API deliberately raises (as opposed to unexpected
 * runtime failures). Carries an HTTP status and a stable error code so the frontend
 * can branch on `error` without parsing `message` text.
 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String errorCode;

    public ApiException(HttpStatus status, String errorCode, String message) {
        super(message);
        this.status = status;
        this.errorCode = errorCode;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
