package com.platform.risk.repository;

import com.platform.risk.domain.RiskAssessment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RiskAssessmentRepository extends JpaRepository<RiskAssessment, UUID> {
    Optional<RiskAssessment> findByTransactionId(UUID transactionId);

    /**
     * Batch lookup for Customer 360's risk section - one IN-clause query for a
     * customer's whole transaction window instead of one
     * {@link #findByTransactionId} call per transaction (the N+1 the previous
     * implementation had). {@code risk_assessments.transaction_id} is unique,
     * so this returns at most one row per id, same cardinality contract as
     * calling {@link #findByTransactionId} in a loop.
     */
    List<RiskAssessment> findByTransactionIdIn(Collection<UUID> transactionIds);
}
