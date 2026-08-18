package com.platform.risk.dto;

import com.platform.risk.domain.LimitScope;
import com.platform.risk.domain.LimitType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record LimitPolicyResponse(
        UUID id,
        String name,
        LimitScope scope,
        UUID scopeReference,
        LimitType limitType,
        BigDecimal maxAmount,
        Integer maxCount,
        Integer windowMinutes,
        boolean active,
        Instant createdAt
) {
}
