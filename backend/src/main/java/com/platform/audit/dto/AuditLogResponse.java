package com.platform.audit.dto;

import java.time.Instant;
import java.util.UUID;

public record AuditLogResponse(
        Long id,
        String correlationId,
        UUID actorUserId,
        String action,
        String entityType,
        String entityId,
        String metadata,
        Instant createdAt
) {
}
