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
     * A customer's transfers since a given point in time, most recent first -
     * for Customer 360's transaction activity section (recent transactions plus
     * the raw material to compute 30-day volume/value/averages in Java, same
     * "computed in Java over demo-scale result sets" approach as
     * TransactionMetricsPortImpl/ExceptionMetricsPortImpl).
     */
    List<TransactionSummary> listForCustomerSince(UUID customerUserId, Instant since);
}
