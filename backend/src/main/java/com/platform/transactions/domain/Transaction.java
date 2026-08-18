package com.platform.transactions.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "transactions", schema = "transactions")
@Getter
@Setter
@NoArgsConstructor
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "source_account_id", nullable = false)
    private UUID sourceAccountId;

    @Column(name = "destination_account_number", nullable = false, length = 20)
    private String destinationAccountNumber;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency = "USD";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private TransactionStatus status = TransactionStatus.SUBMITTED;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private TransactionType type = TransactionType.TRANSFER;

    @Column(name = "idempotency_key", nullable = false, unique = true)
    private String idempotencyKey;

    @Column(name = "initiated_by_user_id", nullable = false)
    private UUID initiatedByUserId;

    @Column(name = "approved_by_user_id")
    private UUID approvedByUserId;

    @Column(name = "approved_at")
    private Instant approvedAt;

    /**
     * Set once, at the moment this transaction enters PENDING_APPROVAL
     * (TransferService.createTransfer) - a fixed deadline from a risk-level-based
     * window, not recomputed on every read. Null for a transaction that never
     * reached PENDING_APPROVAL.
     */
    @Column(name = "sla_due_at")
    private Instant slaDueAt;

    @Column(length = 255)
    private String note;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
