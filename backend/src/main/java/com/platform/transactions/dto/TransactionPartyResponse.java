package com.platform.transactions.dto;

import java.util.UUID;

public record TransactionPartyResponse(
        UUID id,
        String firstName,
        String email
) {
}
