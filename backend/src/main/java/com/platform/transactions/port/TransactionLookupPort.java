package com.platform.transactions.port;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * The `transactions` module's public read contract for other modules - `exceptions`
 * needs transaction context (amount, accounts, customer, status) for its
 * investigation workspace, and `customer360` needs a customer's transaction
 * history and activity, both without depending on TransactionRepository or the
 * Transaction entity directly. Same pattern as AccountLookupPort/UserLookupPort.
 */
public interface TransactionLookupPort {
    TransactionSummary getById(UUID transactionId);

    /**
     * A customer's most recent transfers since a given point in time, limited
     * to {@code limit} rows and ordered most-recent-first - DB-side limited,
     * for Customer 360's recent-transactions and timeline sections, which only
     * ever display a small, fixed number of rows regardless of how much
     * history the customer has.
     */
    List<TransactionSummary> findRecentForCustomer(UUID customerUserId, Instant since, int limit);

    /**
     * Id-only view of a customer's transactions since a given point in time,
     * most recent first. Used to scope batched risk-assessment/exception
     * lookups to "this customer's transactions in this window" without
     * loading full transaction rows just to read their ids.
     */
    List<UUID> findTransactionIdsForCustomerSince(UUID customerUserId, Instant since);

    /**
     * Count/sum/30-day-sub-total/status-count numbers for a customer's
     * transaction window, computed by the database rather than by loading the
     * window's rows into the JVM. See {@link TransactionActivityAggregate}.
     */
    TransactionActivityAggregate getActivityAggregate(UUID customerUserId, Instant since90, Instant since30);
}
