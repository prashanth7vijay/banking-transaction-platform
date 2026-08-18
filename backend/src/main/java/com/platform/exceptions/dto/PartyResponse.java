package com.platform.exceptions.dto;

import java.util.UUID;

public record PartyResponse(
        UUID id,
        String firstName,
        String email
) {
}
