package com.platform.transactions.service;

import com.platform.accounts.exception.InsufficientFundsException;
import com.platform.accounts.port.AccountLookupPort;
import com.platform.accounts.port.AccountSummary;
import com.platform.ledger.port.LedgerPostingPort;
import com.platform.shared.exception.ApiException;
import com.platform.shared.exception.ConflictException;
import com.platform.shared.exception.ResourceNotFoundException;
import com.platform.transactions.domain.Transaction;
import com.platform.transactions.domain.TransactionStatus;
import com.platform.transactions.event.TransactionStatusChangedEvent;
import com.platform.transactions.repository.TransactionRepository;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ApprovalService {

    private final TransactionRepository transactionRepository;
    private final AccountLookupPort accountLookupPort;
    private final LedgerPostingPort ledgerPostingPort;
    private final TransactionStateMachine transactionStateMachine;
    private final ApplicationEventPublisher eventPublisher;
    private final MeterRegistry meterRegistry;

    /**
     * Maker-checker: an employee can never approve or reject a transaction they
     * themselves initiated. In this system employees don't normally initiate
     * customer transfers, so this check rarely fires today - but it's enforced
     * unconditionally here rather than assumed, so it holds even if a future
     * employee-initiated transaction type is added.
     * <p>
     * Cascades PENDING_APPROVAL -&gt; APPROVED -&gt; PROCESSING -&gt; COMPLETED/FAILED in one
     * call - still synchronous end to end (architecture doc §6: PROCESSING is
     * modeled as its own state so a genuinely async posting step could be
     * introduced later without another state-machine redesign, not because
     * posting is actually deferred today). Per §6's transition table, APPROVED ->
     * PROCESSING and PROCESSING -> COMPLETED/FAILED are "system" transitions with
     * no separate human trigger; this codebase has no system-actor concept yet, so
     * they're attributed to the approving employee for traceability rather than
     * left blank - a deliberate simplification, not an oversight.
     */
    @Transactional
    public Transaction approve(UUID employeeUserId, UUID transactionId) {
        Transaction transaction = getAwaitingApprovalOrThrow(transactionId);
        assertNotSelfApproval(transaction, employeeUserId);

        transactionStateMachine.transition(transaction, TransactionStatus.PENDING_APPROVAL, TransactionStatus.APPROVED, employeeUserId);
        transaction.setApprovedByUserId(employeeUserId);
        transaction.setApprovedAt(Instant.now());

        transactionStateMachine.transition(transaction, TransactionStatus.APPROVED, TransactionStatus.PROCESSING, employeeUserId);

        try {
            accountLookupPort.debit(transaction.getSourceAccountId(), transaction.getAmount());

            AccountSummary destination = accountLookupPort.findByAccountNumber(transaction.getDestinationAccountNumber())
                    .orElseThrow(() -> new ResourceNotFoundException("Destination account no longer exists"));
            accountLookupPort.credit(destination.id(), transaction.getAmount());

            ledgerPostingPort.postTransfer(
                    transaction.getId(),
                    transaction.getSourceAccountId(),
                    destination.id(),
                    transaction.getAmount(),
                    transaction.getCurrency(),
                    "Transfer " + transaction.getId()
            );

            transactionStateMachine.transition(transaction, TransactionStatus.PROCESSING, TransactionStatus.COMPLETED, employeeUserId);
        } catch (InsufficientFundsException e) {
            transactionStateMachine.transition(transaction, TransactionStatus.PROCESSING, TransactionStatus.FAILED, employeeUserId);
        }

        Transaction saved = transactionRepository.save(transaction);

        meterRegistry.counter("platform.transactions.approved", "outcome", saved.getStatus().name()).increment();

        eventPublisher.publishEvent(new TransactionStatusChangedEvent(
                saved.getId(), saved.getStatus(), employeeUserId, saved.getInitiatedByUserId(), saved.getAmount()));

        return saved;
    }

    @Transactional
    public Transaction reject(UUID employeeUserId, UUID transactionId, String reason) {
        Transaction transaction = getAwaitingApprovalOrThrow(transactionId);
        assertNotSelfApproval(transaction, employeeUserId);

        transactionStateMachine.transition(transaction, TransactionStatus.PENDING_APPROVAL, TransactionStatus.REJECTED, employeeUserId);
        transaction.setApprovedByUserId(employeeUserId);
        transaction.setApprovedAt(Instant.now());
        transaction.setNote(reason != null ? reason : transaction.getNote());
        Transaction saved = transactionRepository.save(transaction);

        meterRegistry.counter("platform.transactions.rejected").increment();

        eventPublisher.publishEvent(new TransactionStatusChangedEvent(
                saved.getId(), saved.getStatus(), employeeUserId, saved.getInitiatedByUserId(), saved.getAmount()));

        return saved;
    }

    private Transaction getAwaitingApprovalOrThrow(UUID transactionId) {
        Transaction transaction = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found"));
        if (transaction.getStatus() != TransactionStatus.PENDING_APPROVAL) {
            throw new ConflictException("Transaction is not pending approval");
        }
        return transaction;
    }

    private void assertNotSelfApproval(Transaction transaction, UUID employeeUserId) {
        if (transaction.getInitiatedByUserId().equals(employeeUserId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "SELF_APPROVAL_FORBIDDEN",
                    "You cannot approve or reject a transaction you initiated");
        }
    }
}
