package com.platform.audit.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.audit.domain.AuditLog;
import com.platform.audit.repository.AuditLogRepository;
import com.platform.shared.web.CorrelationIdFilter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuditService {

    private static final int MAX_PAGE_SIZE = 100;

    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public void record(UUID actorUserId, String action, String entityType, String entityId, Map<String, Object> metadata) {
        AuditLog log = new AuditLog();
        log.setCorrelationId(MDC.get(CorrelationIdFilter.MDC_KEY));
        log.setActorUserId(actorUserId);
        log.setAction(action);
        log.setEntityType(entityType);
        log.setEntityId(entityId);
        log.setMetadata(serialize(metadata));
        auditLogRepository.save(log);
    }

    @Transactional(readOnly = true)
    public Page<AuditLog> search(UUID actorUserId, String action, String entityType, int page, int size) {
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        int safePage = Math.max(page, 0);
        return auditLogRepository.search(actorUserId, action, entityType, PageRequest.of(safePage, safeSize));
    }

    private String serialize(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (Exception e) {
            log.warn("Failed to serialize audit metadata, storing null", e);
            return null;
        }
    }
}
