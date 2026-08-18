package com.platform.exceptions.dto;

import java.time.Instant;
import java.util.UUID;

public record ExceptionNoteResponse(
        UUID id,
        PartyResponse author,
        String noteType,
        String content,
        Instant createdAt
) {
}
