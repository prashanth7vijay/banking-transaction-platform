package com.platform.shared.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.shared.exception.ApiError;
import com.platform.shared.web.CorrelationIdFilter;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Without these, Spring Security's default behavior for an unauthenticated vs
 * wrong-role request in a stateless API is easy to get wrong (or leave
 * implementation-defined) - explicit beans here guarantee 401 for "no/invalid
 * token" and 403 for "valid token, insufficient role", matching what the rest of
 * the API already promises via {@link com.platform.shared.exception.GlobalExceptionHandler}.
 */
@Component
public class SecurityResponseHandlers implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    public SecurityResponseHandlers(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(jakarta.servlet.http.HttpServletRequest request, HttpServletResponse response,
                          org.springframework.security.core.AuthenticationException authException) throws IOException {
        writeError(request, response, HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Authentication is required");
    }

    @Override
    public void handle(jakarta.servlet.http.HttpServletRequest request, HttpServletResponse response,
                        org.springframework.security.access.AccessDeniedException accessDeniedException) throws IOException {
        writeError(request, response, HttpStatus.FORBIDDEN, "ACCESS_DENIED", "You do not have permission to perform this action");
    }

    private void writeError(jakarta.servlet.http.HttpServletRequest request, HttpServletResponse response,
                             HttpStatus status, String code, String message) throws IOException {
        ApiError error = ApiError.of(status.value(), code, message, request.getRequestURI(), MDC.get(CorrelationIdFilter.MDC_KEY));
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(objectMapper.writeValueAsString(error));
    }
}
