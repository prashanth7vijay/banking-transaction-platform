package com.platform.users.dto;

import com.platform.users.domain.UserStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateUserStatusRequest(
        @NotNull UserStatus status
) {
}
