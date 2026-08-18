package com.platform.accounts.port;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The `accounts` module's public contract for other modules. `transactions` depends
 * on this interface only - never on `AccountRepository` or the `Account` entity
 * directly. If `accounts` were later extracted into its own service, this interface
 * becomes an HTTP/gRPC client with the same method signatures, and nothing in
 * `transactions` needs to change.
 */
public interface AccountLookupPort {

    AccountSummary getById(UUID accountId);

    Optional<AccountSummary> findByAccountNumber(String accountNumber);

    List<AccountSummary> findByOwnerUserId(UUID ownerUserId);

    /**
     * Every account in the system. A deliberately narrow addition, used only by
     * ledger's one-time/idempotent backfill (ensuring every pre-existing account has
     * ledger coverage) - not a general-purpose listing endpoint. Ordinary account
     * lookups still go through getById/findByOwnerUserId, never this.
     */
    List<AccountSummary> findAll();

    /**
     * Debits the account by the given amount within the caller's transaction.
     * Throws {@link com.platform.accounts.exception.InsufficientFundsException} if
     * the current balance is insufficient, or if the account is not ACTIVE.
     */
    void debit(UUID accountId, BigDecimal amount);

    void credit(UUID accountId, BigDecimal amount);
}
