package com.platform.risk.repository;

import com.platform.risk.domain.RiskAssessment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface RiskAssessmentRepository extends JpaRepository<RiskAssessment, UUID> {
    Optional<RiskAssessment> findByTransactionId(UUID transactionId);
}
