package com.platform.accounts.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "beneficiaries",
        schema = "accounts",
        uniqueConstraints = @UniqueConstraint(columnNames = {"owner_user_id", "beneficiary_account_number"})
)
@Getter
@Setter
@NoArgsConstructor
public class Beneficiary {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "owner_user_id", nullable = false)
    private UUID ownerUserId;

    @Column(name = "beneficiary_account_number", nullable = false, length = 20)
    private String beneficiaryAccountNumber;

    @Column(nullable = false, length = 100)
    private String nickname;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
