package com.platform.audit.repository;

import com.platform.audit.domain.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    @Query(
            value = """
                    SELECT a FROM AuditLog a
                    WHERE (:actorUserId IS NULL OR a.actorUserId = :actorUserId)
                      AND (:action IS NULL OR a.action = :action)
                      AND (:entityType IS NULL OR a.entityType = :entityType)
                    ORDER BY a.createdAt DESC
                    """,
            countQuery = """
                    SELECT COUNT(a) FROM AuditLog a
                    WHERE (:actorUserId IS NULL OR a.actorUserId = :actorUserId)
                      AND (:action IS NULL OR a.action = :action)
                      AND (:entityType IS NULL OR a.entityType = :entityType)
                    """
    )
    Page<AuditLog> search(
            @Param("actorUserId") UUID actorUserId,
            @Param("action") String action,
            @Param("entityType") String entityType,
            Pageable pageable
    );
}
