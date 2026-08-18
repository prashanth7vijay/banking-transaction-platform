package com.platform.exceptions.service;

import com.platform.exceptions.domain.ExceptionNote;
import com.platform.exceptions.domain.ExceptionNoteType;
import com.platform.exceptions.domain.ExceptionPriority;
import com.platform.exceptions.domain.ExceptionStatus;
import com.platform.exceptions.domain.TransactionException;
import com.platform.exceptions.repository.ExceptionNoteRepository;
import com.platform.exceptions.repository.TransactionExceptionRepository;
import com.platform.risk.port.RiskAssessmentPort;
import com.platform.risk.port.RiskAssessmentSummary;
import com.platform.shared.exception.ConflictException;
import com.platform.shared.exception.ResourceNotFoundException;
import com.platform.transactions.domain.TransactionStatus;
import com.platform.transactions.port.TransactionLookupPort;
import com.platform.transactions.port.TransactionRetryPort;
import com.platform.transactions.port.TransactionSummary;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * All business logic for the Exception &amp; Investigation Management feature.
 * Depends on `transactions` (via {@link TransactionLookupPort}/{@link TransactionRetryPort})
 * and `risk` (via {@link RiskAssessmentPort}) - one direction only. Nothing in
 * `transactions` or `risk` knows this module exists, which is deliberate: see
 * {@code ExceptionController}'s javadoc for how Transaction 360 still links to
 * an exception without creating the reverse dependency.
 */
@Service
@RequiredArgsConstructor
public class ExceptionService {

    private final TransactionExceptionRepository transactionExceptionRepository;
    private final ExceptionNoteRepository exceptionNoteRepository;
    private final TransactionLookupPort transactionLookupPort;
    private final TransactionRetryPort transactionRetryPort;
    private final RiskAssessmentPort riskAssessmentPort;

    private static final Set<ExceptionStatus> TERMINAL = EnumSet.of(ExceptionStatus.RESOLVED, ExceptionStatus.CLOSED);
    private static final Set<ExceptionStatus> ASSIGNABLE_FROM = EnumSet.of(
            ExceptionStatus.OPEN, ExceptionStatus.ASSIGNED, ExceptionStatus.INVESTIGATING, ExceptionStatus.ACTION_REQUIRED);
    private static final Set<ExceptionStatus> RESOLVABLE_FROM = EnumSet.of(
            ExceptionStatus.INVESTIGATING, ExceptionStatus.ACTION_REQUIRED);

    /**
     * Called only by {@code ExceptionAutoCreationListener} when a transaction
     * transitions to FAILED. Since FAILED is terminal (Phase 4's state machine),
     * a given transaction can trigger this at most once in its lifetime - no
     * dedup check needed.
     */
    @Transactional
    public TransactionException createFromFailedTransaction(UUID transactionId, TransactionStatus failedFrom, UUID actorUserId) {
        TransactionSummary transaction = transactionLookupPort.getById(transactionId);

        String reason = deriveReason(transactionId, failedFrom);
        ExceptionPriority priority = derivePriority(transactionId, failedFrom, transaction.amount());

        TransactionException exception = new TransactionException();
        exception.setTransactionId(transactionId);
        exception.setStatus(ExceptionStatus.OPEN);
        exception.setPriority(priority);
        exception.setReason(reason);
        exception.setSlaDueAt(Instant.now().plus(priority.getSlaWindow()));
        TransactionException saved = transactionExceptionRepository.save(exception);

        addNote(saved.getId(), actorUserId, ExceptionNoteType.STATUS_CHANGE,
                "Exception opened automatically: " + reason);

        return saved;
    }

    /**
     * Failed at creation (blocked by risk) vs. failed at posting (approved, then
     * couldn't move funds) are genuinely different situations - the reason text
     * reflects which one actually happened, read from the real state transition
     * that triggered this, not guessed.
     */
    private String deriveReason(UUID transactionId, TransactionStatus failedFrom) {
        if (failedFrom == TransactionStatus.SUBMITTED) {
            List<String> riskReasons = riskAssessmentPort.getAssessment(transactionId)
                    .map(RiskAssessmentSummary::reasons)
                    .orElse(List.of());
            return riskReasons.isEmpty()
                    ? "Blocked by risk evaluation"
                    : "Blocked by risk evaluation: " + String.join("; ", riskReasons);
        }
        if (failedFrom == TransactionStatus.PROCESSING) {
            return "Approved but failed while posting funds (insufficient balance at execution time)";
        }
        return "Transaction failed";
    }

    /**
     * A risk-blocked failure's severity comes from the risk assessment that
     * blocked it (it already judged severity once - reuse that judgment rather
     * than re-deriving a second one). A posting-time failure has no risk
     * signal to lean on (it was already approved), so severity there is a
     * simple, fixed amount-based tier - explainable, not a model.
     */
    private ExceptionPriority derivePriority(UUID transactionId, TransactionStatus failedFrom, BigDecimal amount) {
        if (failedFrom == TransactionStatus.SUBMITTED) {
            String riskLevel = riskAssessmentPort.getAssessment(transactionId)
                    .map(RiskAssessmentSummary::riskLevel)
                    .orElse("MEDIUM");
            return switch (riskLevel) {
                case "CRITICAL" -> ExceptionPriority.CRITICAL;
                case "HIGH" -> ExceptionPriority.HIGH;
                case "LOW" -> ExceptionPriority.LOW;
                default -> ExceptionPriority.MEDIUM;
            };
        }
        // Posting-time failure: an already-approved customer transfer that didn't
        // go through is operationally urgent regardless of amount, but larger
        // amounts still get the highest tier.
        if (amount.compareTo(new BigDecimal("10000")) >= 0) {
            return ExceptionPriority.CRITICAL;
        }
        if (amount.compareTo(new BigDecimal("1000")) >= 0) {
            return ExceptionPriority.HIGH;
        }
        return ExceptionPriority.MEDIUM;
    }

    @Transactional(readOnly = true)
    public TransactionException getOrThrow(UUID exceptionId) {
        return transactionExceptionRepository.findById(exceptionId)
                .orElseThrow(() -> new ResourceNotFoundException("Exception not found"));
    }

    @Transactional(readOnly = true)
    public List<TransactionException> list(List<ExceptionStatus> statusFilter) {
        if (statusFilter == null || statusFilter.isEmpty()) {
            return transactionExceptionRepository.findByStatusInOrderByCreatedAtAsc(
                    List.of(ExceptionStatus.values()));
        }
        return transactionExceptionRepository.findByStatusInOrderByCreatedAtAsc(statusFilter);
    }

    @Transactional(readOnly = true)
    public List<TransactionException> listMine(UUID employeeUserId) {
        return transactionExceptionRepository.findByAssignedToUserIdAndStatusNotInOrderByCreatedAtAsc(
                employeeUserId, List.of(ExceptionStatus.CLOSED));
    }

    @Transactional(readOnly = true)
    public List<ExceptionNote> getNotes(UUID exceptionId) {
        return exceptionNoteRepository.findByExceptionIdOrderByCreatedAtAsc(exceptionId);
    }

    @Transactional(readOnly = true)
    public Optional<TransactionException> findLatestByTransaction(UUID transactionId) {
        return transactionExceptionRepository.findFirstByTransactionIdOrderByCreatedAtDesc(transactionId);
    }

    @Transactional
    public TransactionException assign(UUID exceptionId, UUID assigneeUserId, UUID actorUserId) {
        TransactionException exception = getOrThrow(exceptionId);
        if (!ASSIGNABLE_FROM.contains(exception.getStatus())) {
            throw new ConflictException("Cannot assign an exception in status " + exception.getStatus());
        }
        boolean wasUnassigned = exception.getStatus() == ExceptionStatus.OPEN;
        exception.setAssignedToUserId(assigneeUserId);
        if (wasUnassigned) {
            exception.setStatus(ExceptionStatus.ASSIGNED);
        }
        TransactionException saved = transactionExceptionRepository.save(exception);
        addNote(exceptionId, actorUserId, ExceptionNoteType.ASSIGNMENT, "Assigned to operator " + assigneeUserId);
        return saved;
    }

    @Transactional
    public TransactionException startInvestigating(UUID exceptionId, UUID actorUserId) {
        TransactionException exception = getOrThrow(exceptionId);
        if (exception.getStatus() != ExceptionStatus.ASSIGNED) {
            throw new ConflictException("Can only start investigating an ASSIGNED exception (currently " + exception.getStatus() + ")");
        }
        exception.setStatus(ExceptionStatus.INVESTIGATING);
        TransactionException saved = transactionExceptionRepository.save(exception);
        addNote(exceptionId, actorUserId, ExceptionNoteType.STATUS_CHANGE, "Investigation started");
        return saved;
    }

    @Transactional
    public TransactionException requestInformation(UUID exceptionId, UUID actorUserId, String note) {
        TransactionException exception = getOrThrow(exceptionId);
        if (exception.getStatus() != ExceptionStatus.INVESTIGATING) {
            throw new ConflictException("Can only request information from an INVESTIGATING exception (currently " + exception.getStatus() + ")");
        }
        exception.setStatus(ExceptionStatus.ACTION_REQUIRED);
        TransactionException saved = transactionExceptionRepository.save(exception);
        addNote(exceptionId, actorUserId, ExceptionNoteType.STATUS_CHANGE, "Action required: " + note);
        return saved;
    }

    @Transactional
    public TransactionException resolve(UUID exceptionId, UUID actorUserId, String resolutionNote) {
        TransactionException exception = getOrThrow(exceptionId);
        if (!RESOLVABLE_FROM.contains(exception.getStatus())) {
            throw new ConflictException("Cannot resolve an exception in status " + exception.getStatus());
        }
        exception.setStatus(ExceptionStatus.RESOLVED);
        exception.setResolvedAt(Instant.now());
        TransactionException saved = transactionExceptionRepository.save(exception);
        addNote(exceptionId, actorUserId, ExceptionNoteType.STATUS_CHANGE, "Resolved: " + resolutionNote);
        return saved;
    }

    @Transactional
    public TransactionException close(UUID exceptionId, UUID actorUserId, String note) {
        TransactionException exception = getOrThrow(exceptionId);
        if (exception.getStatus() != ExceptionStatus.RESOLVED) {
            throw new ConflictException("Can only close a RESOLVED exception (currently " + exception.getStatus() + ")");
        }
        exception.setStatus(ExceptionStatus.CLOSED);
        TransactionException saved = transactionExceptionRepository.save(exception);
        addNote(exceptionId, actorUserId, ExceptionNoteType.STATUS_CHANGE,
                note == null || note.isBlank() ? "Closed" : "Closed: " + note);
        return saved;
    }

    /**
     * Raises priority (and therefore recomputes the SLA clock from now) without
     * changing status - escalation is "this needs more urgency," not itself an
     * investigation-stage transition.
     */
    @Transactional
    public TransactionException escalate(UUID exceptionId, UUID actorUserId, String note) {
        TransactionException exception = getOrThrow(exceptionId);
        if (TERMINAL.contains(exception.getStatus())) {
            throw new ConflictException("Cannot escalate a " + exception.getStatus() + " exception");
        }
        ExceptionPriority nextPriority = switch (exception.getPriority()) {
            case LOW -> ExceptionPriority.MEDIUM;
            case MEDIUM -> ExceptionPriority.HIGH;
            case HIGH, CRITICAL -> ExceptionPriority.CRITICAL;
        };
        exception.setPriority(nextPriority);
        exception.setSlaDueAt(Instant.now().plus(nextPriority.getSlaWindow()));
        TransactionException saved = transactionExceptionRepository.save(exception);
        addNote(exceptionId, actorUserId, ExceptionNoteType.ACTION_TAKEN,
                "Escalated to " + nextPriority + (note == null || note.isBlank() ? "" : ": " + note));
        return saved;
    }

    /**
     * Runs the original customer's transfer through the full normal pipeline
     * again as a brand-new transaction - never touches the failed one. Status is
     * deliberately left unchanged: the investigator confirms the retry actually
     * succeeded (or opens a fresh investigation if it fails again) before
     * resolving this case themselves.
     */
    @Transactional
    public TransactionException retry(UUID exceptionId, UUID actorUserId) {
        TransactionException exception = getOrThrow(exceptionId);
        if (TERMINAL.contains(exception.getStatus())) {
            throw new ConflictException("Cannot retry a " + exception.getStatus() + " exception");
        }
        UUID newTransactionId = transactionRetryPort.retryAsNewTransfer(exception.getTransactionId(), actorUserId);
        addNote(exceptionId, actorUserId, ExceptionNoteType.ACTION_TAKEN,
                "Retried as new transaction " + newTransactionId);
        return exception;
    }

    @Transactional
    public ExceptionNote addNote(UUID exceptionId, UUID actorUserId, String content) {
        TransactionException exception = getOrThrow(exceptionId);
        if (exception.getStatus() == ExceptionStatus.CLOSED) {
            throw new ConflictException("Cannot add notes to a CLOSED exception");
        }
        return addNote(exceptionId, actorUserId, ExceptionNoteType.NOTE, content);
    }

    private ExceptionNote addNote(UUID exceptionId, UUID actorUserId, ExceptionNoteType type, String content) {
        ExceptionNote note = new ExceptionNote();
        note.setExceptionId(exceptionId);
        note.setAuthorUserId(actorUserId);
        note.setNoteType(type);
        note.setContent(content);
        return exceptionNoteRepository.save(note);
    }
}
