package com.platform.exceptions.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * One entry in an exception's investigation trail. Append-only - no service
 * method updates or deletes a row here, same convention as
 * {@code TransactionStateHistory} and {@code audit_logs}. {@code exceptionId}
 * is a real FK this time (unlike {@code transactionId} on
 * {@link TransactionException}), since both tables live in the same
 * {@code exceptions} schema - within-module FKs are fine, only cross-module
 * ones are avoided.
 */
@Entity
@Table(name = "exception_notes", schema = "exceptions")
@Getter
@Setter
@NoArgsConstructor
public class ExceptionNote {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "exception_id", nullable = false)
    private UUID exceptionId;

    @Column(name = "author_user_id", nullable = false)
    private UUID authorUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "note_type", nullable = false, length = 20)
    private ExceptionNoteType noteType;

    @Column(nullable = false, length = 2000)
    private String content;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
