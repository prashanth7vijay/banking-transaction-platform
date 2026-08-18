package com.platform.transactions.service;

import com.platform.shared.exception.ResourceNotFoundException;
import com.platform.transactions.domain.Transaction;
import com.platform.transactions.port.TransactionLookupPort;
import com.platform.transactions.port.TransactionSummary;
import com.platform.transactions.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    public List<TransactionSummary> listForCustomerSince(UUID customerUserId, Instant since) {
        return transactionRepository.findByInitiatedByUserIdAndCreatedAtAfterOrderByCreatedAtDesc(customerUserId, since)
                .stream()
                .map(this::toSummary)
                .toList();
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
