package com.platform.exceptions.repository;

import com.platform.exceptions.domain.ExceptionStatus;
import com.platform.exceptions.domain.TransactionException;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TransactionExceptionRepository extends JpaRepository<TransactionException, UUID> {

    List<TransactionException> findByTransactionIdOrderByCreatedAtDesc(UUID transactionId);

    /** FAILED is terminal (Phase 4's state machine), so a transaction can reach it - and therefore get an auto-created exception - at most once in its lifetime. */
    Optional<TransactionException> findFirstByTransactionIdOrderByCreatedAtDesc(UUID transactionId);

    List<TransactionException> findByStatusInOrderByCreatedAtAsc(List<ExceptionStatus> statuses);

    List<TransactionException> findByAssignedToUserIdAndStatusNotInOrderByCreatedAtAsc(
            UUID assignedToUserId, List<ExceptionStatus> excludedStatuses);

    long countByStatusIn(List<ExceptionStatus> statuses);

    List<TransactionException> findByTransactionIdIn(List<UUID> transactionIds);
}
