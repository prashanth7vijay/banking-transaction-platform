package com.platform.customer360.dto;

import java.time.Instant;
import java.util.UUID;

public record CustomerProfileResponse(
        UUID id,
        String email,
        String firstName,
        String lastName,
        String status,
        /** When this customer's account was created - the closest honest proxy this system has for "onboarding date" (there's no separate onboarding/KYC record to draw from). */
        Instant customerSince
) {
}
