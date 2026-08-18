package com.platform.risk.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * A row here, not an `if (amount > 10000)` in code, because an operations/risk
 * team - not an engineer - needs to be able to tighten a limit without a
 * deployment. Same reasoning that already justifies users.roles being a table
 * rather than a hardcoded enum.
 * <p>
 * Phase 3 scope note: only GLOBAL-scoped policies are actually evaluated by
 * RuleBasedRiskStrategy right now (see its class Javadoc) - the scope column and
 * ACCOUNT_TYPE/CUSTOMER/ACCOUNT values exist so a later phase can add
 * most-specific-wins resolution without a schema change, not because that
 * resolution is implemented yet.
 */
@Entity
@Table(name = "limit_policies", schema = "risk")
@Getter
@Setter
@NoArgsConstructor
public class LimitPolicy {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private LimitScope scope;

    @Column(name = "scope_reference")
    private UUID scopeReference;

    @Enumerated(EnumType.STRING)
    @Column(name = "limit_type", nullable = false, length = 20)
    private LimitType limitType;

    @Column(name = "max_amount", precision = 19, scale = 4)
    private BigDecimal maxAmount;

    @Column(name = "max_count")
    private Integer maxCount;

    @Column(name = "window_minutes")
    private Integer windowMinutes;

    @Column(nullable = false)
    private boolean active = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
