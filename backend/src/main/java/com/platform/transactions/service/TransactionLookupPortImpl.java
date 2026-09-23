package com.platform.transactions.service;

import com.platform.shared.exception.ResourceNotFoundException;
import com.platform.transactions.domain.Transaction;
import com.platform.transactions.domain.TransactionStatus;
import com.platform.transactions.port.TransactionActivityAggregate;
import com.platform.transactions.port.TransactionLookupPort;
import com.platform.transactions.port.TransactionSummary;
import com.platform.transactions.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TransactionLookupPortImpl implements TransactionLookupPort {

    private final TransactionRepository transactionRepository;

    @Override
    @Transactional(readOnly = true)
    public TransactionSummary getById(UUID transactionId) {
        Transaction transaction = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found"));
        return toSummary(transaction);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TransactionSummary> findRecentForCustomer(UUID customerUserId, Instant since, int limit) {
        return transactionRepository
                .findByInitiatedByUserIdAndCreatedAtAfterOrderByCreatedAtDesc(customerUserId, since, PageRequest.of(0, limit))
                .stream()
                .map(this::toSummary)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<UUID> findTransactionIdsForCustomerSince(UUID customerUserId, Instant since) {
        return transactionRepository.findTransactionIdsForCustomerSince(customerUserId, since);
    }

    @Override
    @Transactional(readOnly = true)
    public TransactionActivityAggregate getActivityAggregate(UUID customerUserId, Instant since90, Instant since30) {
    Object[] row = transactionRepository.aggregateActivityRaw(
            customerUserId, since90, since30, TransactionStatus.PENDING_APPROVAL, TransactionStatus.FAILED)
            .get(0);
    return new TransactionActivityAggregate(
            ((Number) row[0]).longValue(),
            (BigDecimal) row[1],
            ((Number) row[2]).longValue(),
            (BigDecimal) row[3],
            ((Number) row[4]).longValue(),
            ((Number) row[5]).longValue());
    }

    private TransactionSummary toSummary(Transaction transaction) {
        return new TransactionSummary(
                transaction.getId(),
                transaction.getSourceAccountId(),
                transaction.getDestinationAccountNumber(),
                transaction.getAmount(),
                transaction.getCurrency(),
                transaction.getStatus(),
                transaction.getInitiatedByUserId(),
                transaction.getCreatedAt()
        );
    }
}
