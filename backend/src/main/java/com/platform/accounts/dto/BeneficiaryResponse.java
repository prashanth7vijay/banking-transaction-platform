package com.platform.accounts.dto;

import java.time.Instant;
import java.util.UUID;

public record BeneficiaryResponse(
        UUID id,
        String beneficiaryAccountNumber,
        String nickname,
        Instant createdAt
) {
}
