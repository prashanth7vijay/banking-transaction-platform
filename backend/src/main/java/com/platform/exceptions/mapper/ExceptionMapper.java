package com.platform.exceptions.mapper;

import com.platform.exceptions.domain.ExceptionNote;
import com.platform.exceptions.domain.ExceptionStatus;
import com.platform.exceptions.domain.TransactionException;
import com.platform.exceptions.dto.ExceptionNoteResponse;
import com.platform.exceptions.dto.LinkedTransactionResponse;
import com.platform.exceptions.dto.PartyResponse;
import com.platform.exceptions.dto.TransactionExceptionResponse;
import com.platform.transactions.port.TransactionSummary;
import com.platform.users.port.UserLookupPort;
import com.platform.users.port.UserSummary;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class ExceptionMapper {

    private final UserLookupPort userLookupPort;

    public TransactionExceptionResponse toResponse(TransactionException exception, TransactionSummary transaction) {
        return new TransactionExceptionResponse(
                exception.getId(),
                exception.getTransactionId(),
                transaction.amount(),
                transaction.currency(),
                exception.getStatus().name(),
                exception.getPriority().name(),
                exception.getReason(),
                toParty(exception.getAssignedToUserId()),
                exception.getCreatedAt(),
                exception.getUpdatedAt(),
                exception.getResolvedAt(),
                exception.getSlaDueAt(),
                computeSlaStatus(exception)
        );
    }

    public ExceptionNoteResponse toNoteResponse(ExceptionNote note) {
        return new ExceptionNoteResponse(
                note.getId(),
                toParty(note.getAuthorUserId()),
                note.getNoteType().name(),
                note.getContent(),
                note.getCreatedAt()
        );
    }

    public LinkedTransactionResponse toLinkedTransaction(TransactionSummary transaction) {
        return new LinkedTransactionResponse(
                transaction.id(),
                transaction.amount(),
                transaction.currency(),
                transaction.status().name(),
                transaction.destinationAccountNumber(),
                transaction.createdAt()
        );
    }

    private PartyResponse toParty(UUID userId) {
        if (userId == null) {
            return null;
        }
        UserSummary user = userLookupPort.getById(userId);
        return new PartyResponse(user.id(), user.firstName(), user.email());
    }

    /**
     * For an already-resolved/closed case: a historical fact (did the team make
     * the deadline). For an open case: where it stands right now, with an
     * "at risk" band in the last 20% of the SLA window - the same shape of
     * signal the product spec's Approval Workbench SLA section describes, kept
     * consistent here.
     * <p>
     * Public static and dependency-free on purpose: {@code ExceptionMetricsPortImpl}
     * (Feature 4, Operations Command Center) needs this exact same computation
     * over entities it fetches directly from {@code TransactionExceptionRepository}
     * within the same module - reused here rather than re-derived.
     */
    public static String computeSlaStatus(TransactionException exception) {
        if (exception.getStatus() == ExceptionStatus.RESOLVED || exception.getStatus() == ExceptionStatus.CLOSED) {
            Instant completedAt = exception.getResolvedAt() != null ? exception.getResolvedAt() : exception.getUpdatedAt();
            return completedAt.isAfter(exception.getSlaDueAt()) ? "BREACHED" : "MET";
        }

        Instant now = Instant.now();
        if (now.isAfter(exception.getSlaDueAt())) {
            return "BREACHED";
        }

        Duration remaining = Duration.between(now, exception.getSlaDueAt());
        Duration window = exception.getPriority().getSlaWindow();
        double remainingFraction = (double) remaining.toMillis() / (double) window.toMillis();
        return remainingFraction <= 0.2 ? "AT_RISK" : "WITHIN";
    }
}
