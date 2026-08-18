package com.platform.audit.controller;

import com.platform.audit.domain.AuditLog;
import com.platform.audit.dto.AuditLogResponse;
import com.platform.audit.service.AuditService;
import com.platform.shared.dto.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/audit/logs")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AuditController {

    private final AuditService auditService;

    @GetMapping
    public PageResponse<AuditLogResponse> search(
            @RequestParam(required = false) UUID actorUserId,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String entityType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size
    ) {
        return PageResponse.from(
                auditService.search(actorUserId, action, entityType, page, size).map(this::toResponse)
        );
    }

    private AuditLogResponse toResponse(AuditLog log) {
        return new AuditLogResponse(
                log.getId(),
                log.getCorrelationId(),
                log.getActorUserId(),
                log.getAction(),
                log.getEntityType(),
                log.getEntityId(),
                log.getMetadata(),
                log.getCreatedAt()
        );
    }
}
