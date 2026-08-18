package com.platform.exceptions.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record AssignExceptionRequest(
        @NotNull UUID assigneeUserId
) {
}
