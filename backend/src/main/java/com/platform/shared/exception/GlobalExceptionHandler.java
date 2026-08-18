package com.platform.shared.exception;

import com.platform.shared.web.CorrelationIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiError> handleApiException(ApiException ex, HttpServletRequest request) {
        return build(ex.getStatus(), ex.getErrorCode(), ex.getMessage(), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        Map<String, String> fieldErrors = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(fe ->
                fieldErrors.put(fe.getField(), fe.getDefaultMessage()));
        ApiError error = ApiError.withFieldErrors(
                HttpStatus.BAD_REQUEST.value(), "VALIDATION_ERROR", "Request validation failed",
                request.getRequestURI(), correlationId(), fieldErrors);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiError> handleBadCredentials(HttpServletRequest request) {
        return build(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Invalid email or password", request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleAccessDenied(HttpServletRequest request) {
        return build(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "You do not have permission to perform this action", request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception ex, HttpServletRequest request) {
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "An unexpected error occurred", request);
    }

    private ResponseEntity<ApiError> build(HttpStatus status, String code, String message, HttpServletRequest request) {
        ApiError error = ApiError.of(status.value(), code, message, request.getRequestURI(), correlationId());
        return ResponseEntity.status(status).body(error);
    }

    private String correlationId() {
        return MDC.get(CorrelationIdFilter.MDC_KEY);
    }
}
