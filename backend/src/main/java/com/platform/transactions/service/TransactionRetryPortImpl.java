package com.platform.transactions.service;

import com.platform.shared.exception.ConflictException;
import com.platform.shared.exception.ResourceNotFoundException;
import com.platform.transactions.domain.Transaction;
import com.platform.transactions.domain.TransactionStatus;
import com.platform.transactions.port.TransactionRetryPort;
import com.platform.transactions.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TransactionRetryPortImpl implements TransactionRetryPort {

    private final TransactionRepository transactionRepository;
    private final TransferService transferService;

    @Override
    @Transactional
    public UUID retryAsNewTransfer(UUID originalTransactionId, UUID triggeredByEmployeeUserId) {
        Transaction original = transactionRepository.findById(originalTransactionId)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found"));

        if (original.getStatus() != TransactionStatus.FAILED) {
            throw new ConflictException("Only a FAILED transaction can be retried (currently " + original.getStatus() + ")");
        }

        // A fresh idempotency key every retry, deliberately not derived from the
        // original transaction's key - a retry is a genuinely new attempt, not a
        // replay of the same request, so it must not collide with
        // TransferService's own idempotency-key dedup and silently return the
        // original failed transaction instead of creating a new one.
        String idempotencyKey = "retry-" + originalTransactionId + "-" + UUID.randomUUID();

        // Initiated as the original customer, not the employee - the money still
        // belongs to (and is being moved on behalf of) the customer; the employee's
        // role in triggering this is recorded by the exceptions module's own audit
        // trail (ExceptionNote), not by rewriting who owns the transfer.
        Transaction retry = transferService.createTransfer(
                original.getInitiatedByUserId(),
                original.getSourceAccountId(),
                original.getDestinationAccountNumber(),
                original.getAmount(),
                idempotencyKey,
                "Retry of failed transaction " + originalTransactionId
        );

        return retry.getId();
    }
}
