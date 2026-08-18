package com.platform.risk.dto;

import com.platform.risk.domain.LimitScope;
import com.platform.risk.domain.LimitType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record LimitPolicyRequest(
        @NotBlank String name,
        @NotNull LimitScope scope,
        UUID scopeReference,
        @NotNull LimitType limitType,
        BigDecimal maxAmount,
        Integer maxCount,
        Integer windowMinutes,
        boolean active
) {
}
