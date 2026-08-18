package com.platform.risk.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "risk_assessments", schema = "risk")
@Getter
@Setter
@NoArgsConstructor
public class RiskAssessment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "transaction_id", nullable = false, unique = true)
    private UUID transactionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "risk_level", nullable = false, length = 10)
    private RiskLevel riskLevel;

    /** Human-readable reasons, one per triggered rule - for explainability. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String reasons;

    @Column(nullable = false)
    private boolean blocked = false;

    @CreationTimestamp
    @Column(name = "assessed_at", nullable = false, updatable = false)
    private Instant assessedAt;
}
