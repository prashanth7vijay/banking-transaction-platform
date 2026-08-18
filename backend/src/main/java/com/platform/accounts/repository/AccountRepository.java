package com.platform.accounts.repository;

import com.platform.accounts.domain.Account;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AccountRepository extends JpaRepository<Account, UUID> {
    List<Account> findByUserId(UUID userId);
    Optional<Account> findByAccountNumber(String accountNumber);
    boolean existsByAccountNumber(String accountNumber);

    @Query("SELECT COALESCE(SUM(a.balance), 0) FROM Account a")
    BigDecimal sumAllBalances();
}
