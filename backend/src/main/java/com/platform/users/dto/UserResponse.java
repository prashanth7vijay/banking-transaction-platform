package com.platform.users.dto;

import com.platform.users.domain.UserStatus;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record UserResponse(
        UUID id,
        String email,
        String firstName,
        String lastName,
        UserStatus status,
        Set<String> roles,
        Instant createdAt
) {
}
