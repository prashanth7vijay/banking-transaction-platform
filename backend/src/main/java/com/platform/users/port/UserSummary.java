package com.platform.users.port;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record UserSummary(
        UUID id,
        String email,
        String firstName,
        String lastName,
        String status,
        Instant createdAt,
        Set<String> roles
) {
}
