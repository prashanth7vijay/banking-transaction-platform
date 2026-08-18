package com.platform.ledger.repository;

import com.platform.ledger.domain.LedgerAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface LedgerAccountRepository extends JpaRepository<LedgerAccount, UUID> {
    Optional<LedgerAccount> findByAccountId(UUID accountId);
    boolean existsByAccountId(UUID accountId);
}
