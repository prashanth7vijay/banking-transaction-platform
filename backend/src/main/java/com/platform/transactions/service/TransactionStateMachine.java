package com.platform.transactions.service;

import com.platform.shared.exception.ConflictException;
import com.platform.transactions.domain.Transaction;
import com.platform.transactions.domain.TransactionStateHistory;
import com.platform.transactions.domain.TransactionStatus;
import com.platform.transactions.event.TransactionStateChangedEvent;
import com.platform.transactions.repository.TransactionRepository;
import com.platform.transactions.repository.TransactionStateHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static com.platform.transactions.domain.TransactionStatus.*;

/**
 * Owns the single question "is this a legal thing to do right now" for every
 * {@link Transaction} state change (architecture doc §6) - the one gap the earlier,
 * flat-status design had no single place to enforce (ApprovalService used to
 * re-derive it ad hoc from a single {@code status == PENDING} check).
 * <p>
 * Deliberately not a third-party state-machine framework: a {@code sealed}-adjacent
 * enum plus a {@code Map<TransactionStatus, Set<TransactionStatus>>} built once at
 * class-load time is the entire "engine," which is all a transition table this size
 * needs (Spring State Machine or similar was evaluated in the design doc and
 * rejected as unnecessary weight).
 * <p>
 * {@link #isLegal(TransactionStatus, TransactionStatus)} is a pure, static,
 * dependency-free check - directly unit-testable without a Spring context (see
 * {@code TransactionStateMachineTest}). {@link #transition} is the actual service
 * method services call: it re-checks legality, performs the DB compare-and-swap
 * (the concurrency guard from §16 - see {@link TransactionRepository#compareAndSetStatus}),
 * records the transition in {@code transaction_state_history}, and publishes the
 * new {@link TransactionStateChangedEvent}. All of this runs inside the caller's
 * existing transaction (default REQUIRED propagation) - a transition is never its
 * own, separately-committed unit of work.
 */
@Service
@RequiredArgsConstructor
public class TransactionStateMachine {

    private final TransactionRepository transactionRepository;
    private final TransactionStateHistoryRepository transactionStateHistoryRepository;
    private final ApplicationEventPublisher eventPublisher;

    private static final Map<TransactionStatus, Set<TransactionStatus>> LEGAL_TRANSITIONS = buildTransitionTable();

    /**
     * Validates and executes {@code from -> to} on an already-persisted transaction,
     * then records it and publishes it. Mutates {@code transaction} in place so the
     * caller's in-memory copy stays consistent for the rest of its own transaction.
     *
     * @throws ConflictException if the transition isn't in the legal table, if
     *                            {@code transaction} isn't currently in {@code from}
     *                            (a programming error at the call site), or if the
     *                            compare-and-swap affected zero rows (a concurrent
     *                            writer moved it out of {@code from} first).
     */
    @Transactional
    public void transition(Transaction transaction, TransactionStatus from, TransactionStatus to, UUID actorUserId) {
        if (!isLegal(from, to)) {
            throw new ConflictException("Illegal transaction state transition: " + from + " -> " + to);
        }
        if (transaction.getStatus() != from) {
            throw new ConflictException(
                    "Transaction " + transaction.getId() + " is not in state " + from
                            + " (currently " + transaction.getStatus() + ")");
        }

        int updated = transactionRepository.compareAndSetStatus(transaction.getId(), from, to);
        if (updated == 0) {
            throw new ConflictException(
                    "Transaction " + transaction.getId() + " was concurrently moved out of state " + from);
        }
        transaction.setStatus(to);

        recordHistory(transaction.getId(), from, to, actorUserId);
        eventPublisher.publishEvent(new TransactionStateChangedEvent(
                transaction.getId(), from, to, actorUserId, Instant.now()));
    }

    /**
     * Records the very first history row for a freshly-created transaction - not a
     * state-machine-validated edge (there is no prior persisted state to CAS
     * against), just an audit-trail entry so the Transaction 360 timeline has a
     * complete start rather than beginning mid-story. Does not publish an event:
     * the existing {@code TransactionStatusChangedEvent} already covers creation
     * for today's listeners, and adding a second event for the same moment would
     * just be noise.
     */
    @Transactional
    public void recordInitialState(Transaction transaction, UUID actorUserId) {
        recordHistory(transaction.getId(), null, transaction.getStatus(), actorUserId);
    }

    private void recordHistory(UUID transactionId, TransactionStatus from, TransactionStatus to, UUID actorUserId) {
        TransactionStateHistory history = new TransactionStateHistory();
        history.setTransactionId(transactionId);
        history.setFromStatus(from);
        history.setToStatus(to);
        history.setActorUserId(actorUserId);
        transactionStateHistoryRepository.save(history);
    }

    public static boolean isLegal(TransactionStatus from, TransactionStatus to) {
        return LEGAL_TRANSITIONS.getOrDefault(from, Set.of()).contains(to);
    }

    private static Map<TransactionStatus, Set<TransactionStatus>> buildTransitionTable() {
        Map<TransactionStatus, Set<TransactionStatus>> table = new EnumMap<>(TransactionStatus.class);
        table.put(DRAFT, Set.of(SUBMITTED, CANCELLED));
        table.put(SUBMITTED, Set.of(PENDING_APPROVAL, FAILED, CANCELLED));
        table.put(PENDING_APPROVAL, Set.of(APPROVED, REJECTED, CANCELLED));
        table.put(APPROVED, Set.of(PROCESSING));
        table.put(PROCESSING, Set.of(COMPLETED, FAILED));
        // COMPLETED, REJECTED, FAILED, CANCELLED are terminal - no entry, so
        // isLegal(...) falls through to Set.of() for any "from" that isn't a key.
        return Map.copyOf(table);
    }
}
