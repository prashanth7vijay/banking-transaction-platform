package com.platform.transactions.service;

import com.platform.accounts.port.AccountLookupPort;
import com.platform.accounts.port.AccountSummary;
import com.platform.risk.domain.RiskLevel;
import com.platform.risk.port.RiskAssessmentPort;
import com.platform.risk.service.RiskAssessmentResult;
import com.platform.shared.exception.ConflictException;
import com.platform.shared.exception.ResourceNotFoundException;
import com.platform.shared.exception.UnauthorizedException;
import com.platform.transactions.domain.Transaction;
import com.platform.transactions.domain.TransactionStateHistory;
import com.platform.transactions.domain.TransactionStatus;
import com.platform.transactions.event.TransactionStatusChangedEvent;
import com.platform.transactions.repository.TransactionRepository;
import com.platform.transactions.repository.TransactionStateHistoryRepository;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TransferService {

    private final TransactionRepository transactionRepository;
    private final TransactionStateHistoryRepository transactionStateHistoryRepository;
    private final AccountLookupPort accountLookupPort;
    private final RiskAssessmentPort riskAssessmentPort;
    private final TransactionStateMachine transactionStateMachine;
    private final ApplicationEventPublisher eventPublisher;
    private final MeterRegistry meterRegistry;

    /**
     * Creates a transfer awaiting employee approval. No funds move yet - that
     * happens atomically at approval time (see ApprovalService), since the
     * account balance could change between now and then.
     * <p>
     * Idempotent: replaying the same (idempotencyKey, customer) pair returns the
     * transaction already created for it instead of creating a duplicate - this is
     * what lets a client safely retry a network-failed submission.
     * <p>
     * A risk assessment runs synchronously, right here, before the customer's
     * request returns - not as a decoupled side effect, since it gates whether
     * the transfer is even allowed to proceed. A blocked assessment (e.g. over a
     * hard per-transaction limit) short-circuits straight to FAILED with no
     * approval-queue entry created; everything else proceeds exactly as before
     * this phase existed.
     */
    @Transactional
    public Transaction createTransfer(UUID customerUserId, UUID sourceAccountId, String destinationAccountNumber,
                                       BigDecimal amount, String idempotencyKey, String note) {

        var existing = transactionRepository.findByIdempotencyKeyAndInitiatedByUserId(idempotencyKey, customerUserId);
        if (existing.isPresent()) {
            return existing.get();
        }

        AccountSummary source = accountLookupPort.getById(sourceAccountId);
        if (!source.ownerUserId().equals(customerUserId)) {
            throw new UnauthorizedException("You do not have access to this account");
        }

        AccountSummary destination = accountLookupPort.findByAccountNumber(destinationAccountNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Destination account not found"));

        if (destination.id().equals(source.id())) {
            throw new ConflictException("Source and destination accounts must be different");
        }

        Transaction transaction = new Transaction();
        transaction.setSourceAccountId(source.id());
        transaction.setDestinationAccountNumber(destinationAccountNumber);
        transaction.setAmount(amount);
        transaction.setIdempotencyKey(idempotencyKey);
        transaction.setInitiatedByUserId(customerUserId);
        transaction.setNote(note);
        transaction.setStatus(TransactionStatus.SUBMITTED);

        Transaction saved = transactionRepository.save(transaction);
        transactionStateMachine.recordInitialState(saved, customerUserId);

        RiskAssessmentResult riskResult = riskAssessmentPort.assessAndRecord(
                saved.getId(), source.id(), customerUserId, amount, saved.getCurrency());

        if (riskResult.blocked()) {
            saved.setNote(appendRiskNote(saved.getNote(), riskResult));
            transactionStateMachine.transition(saved, TransactionStatus.SUBMITTED, TransactionStatus.FAILED, customerUserId);
            saved = transactionRepository.save(saved);
        } else {
            transactionStateMachine.transition(saved, TransactionStatus.SUBMITTED, TransactionStatus.PENDING_APPROVAL, customerUserId);
            // SLA clock starts now, fixed at entry - never recomputed from "now" on
            // later reads. Window is keyed off this assessment's risk level: a
            // HIGH/CRITICAL transfer gets less time sitting in the queue than a
            // routine LOW one, mirroring how a real ops team would triage.
            saved.setSlaDueAt(Instant.now().plus(approvalSlaWindow(riskResult.riskLevel())));
            saved = transactionRepository.save(saved);
        }

        meterRegistry.counter("platform.transactions.created", "type", saved.getType().name()).increment();

        eventPublisher.publishEvent(new TransactionStatusChangedEvent(
                saved.getId(), saved.getStatus(), customerUserId, customerUserId, saved.getAmount()));

        return saved;
    }

    private String appendRiskNote(String existingNote, RiskAssessmentResult riskResult) {
        String riskSummary = "Blocked by risk check: " + String.join("; ", riskResult.reasons());
        return existingNote == null || existingNote.isBlank() ? riskSummary : existingNote + " | " + riskSummary;
    }

    /**
     * Deliberately simple, explainable thresholds - not a model. Tighter than the
     * exceptions module's investigation SLA windows (ExceptionPriority), since an
     * approval decision is a much smaller unit of work than resolving an
     * investigation.
     */
    private static final Map<RiskLevel, Duration> APPROVAL_SLA_WINDOWS = Map.of(
            RiskLevel.CRITICAL, Duration.ofHours(1),
            RiskLevel.HIGH, Duration.ofHours(4),
            RiskLevel.MEDIUM, Duration.ofHours(24),
            RiskLevel.LOW, Duration.ofHours(48)
    );

    public static Duration approvalSlaWindow(RiskLevel riskLevel) {
        return APPROVAL_SLA_WINDOWS.getOrDefault(riskLevel, APPROVAL_SLA_WINDOWS.get(RiskLevel.MEDIUM));
    }

    @Transactional(readOnly = true)
    public Transaction getById(UUID transactionId) {
        return transactionRepository.findById(transactionId)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found"));
    }

    /**
     * A customer's own transaction history, across all accounts they own.
     */
    @Transactional(readOnly = true)
    public List<Transaction> listMine(UUID customerUserId, List<UUID> ownedAccountIds) {
        if (ownedAccountIds.isEmpty()) {
            return List.of();
        }
        return transactionRepository.findBySourceAccountIdInOrderByCreatedAtDesc(ownedAccountIds);
    }

    @Transactional(readOnly = true)
    public List<Transaction> listPending() {
        return transactionRepository.findByStatusOrderByCreatedAtAsc(TransactionStatus.PENDING_APPROVAL);
    }

    /**
     * A generic status-filtered view for employees - what backs the Operations
     * Command Center's "click a metric, land on the filtered list" requirement
     * (e.g. "12 Failed Transactions" -> this, filtered to FAILED). Reuses the
     * exact same repository method {@link #listPending} already uses internally,
     * just without hardcoding the status.
     */
    @Transactional(readOnly = true)
    public List<Transaction> listByStatus(TransactionStatus status) {
        return transactionRepository.findByStatusOrderByCreatedAtAsc(status);
    }

    /**
     * The full ordered state-transition history for one transaction - backs the
     * Transaction 360 timeline (Phase 9's frontend, this phase's read endpoint).
     */
    @Transactional(readOnly = true)
    public List<TransactionStateHistory> getTimeline(UUID transactionId) {
        getById(transactionId); // 404s if the transaction itself doesn't exist
        return transactionStateHistoryRepository.findByTransactionIdOrderByOccurredAtAsc(transactionId);
    }

    @Transactional(readOnly = true)
    public Map<TransactionStatus, Long> countByStatus() {
        Map<TransactionStatus, Long> counts = new EnumMap<>(TransactionStatus.class);
        for (TransactionStatus status : TransactionStatus.values()) {
            counts.put(status, transactionRepository.countByStatus(status));
        }
        return counts;
    }
}
