package com.platform.transactions.repository;

import com.platform.transactions.domain.TransactionStateHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TransactionStateHistoryRepository extends JpaRepository<TransactionStateHistory, UUID> {

    List<TransactionStateHistory> findByTransactionIdOrderByOccurredAtAsc(UUID transactionId);
}
