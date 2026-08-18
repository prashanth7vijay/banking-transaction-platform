package com.platform.auth.event;

import java.util.UUID;

/**
 * Published on meaningful auth actions (login, logout, password reset) for the
 * `audit` module to record. Not tied to HTTP or any particular consumer.
 */
public record AuthActionEvent(
        UUID userId,
        String email,
        String action
) {
    public static final String LOGIN = "LOGIN";
    public static final String LOGOUT = "LOGOUT";
    public static final String PASSWORD_RESET = "PASSWORD_RESET";
}
