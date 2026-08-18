package com.platform.users.event;

import java.util.UUID;

public record UserActionEvent(
        UUID userId,
        String action
) {
    public static final String PASSWORD_CHANGED = "PASSWORD_CHANGED";
    public static final String PROFILE_UPDATED = "PROFILE_UPDATED";
}
