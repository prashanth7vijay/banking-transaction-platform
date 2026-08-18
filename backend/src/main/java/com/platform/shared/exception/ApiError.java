package com.platform.shared.exception;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path,
        String correlationId,
        Map<String, String> fieldErrors
) {
    public static ApiError of(int status, String error, String message, String path, String correlationId) {
        return new ApiError(Instant.now(), status, error, message, path, correlationId, null);
    }

    public static ApiError withFieldErrors(int status, String error, String message, String path,
                                            String correlationId, Map<String, String> fieldErrors) {
        return new ApiError(Instant.now(), status, error, message, path, correlationId, fieldErrors);
    }
}
