package com.platform.transactions.port;

import java.util.UUID;

/**
 * The one write capability `transactions` exposes to `exceptions`: retrying a
 * failed transfer as a brand-new transaction. Deliberately narrow (one method,
 * one very specific operation) rather than exposing TransferService wholesale -
 * `exceptions` gets exactly the capability it needs and nothing else.
 * <p>
 * "Retry" here means running the *original customer's* request through the
 * full normal pipeline again from scratch (a new transaction ID, a fresh risk
 * assessment, a fresh approval decision if required) - never resurrecting or
 * mutating the failed transaction itself. This is the concrete implementation
 * of the product spec's "never solve a financial problem by editing the
 * original transaction record" principle.
 */
public interface TransactionRetryPort {

    /**
     * @param originalTransactionId the FAILED transaction being retried
     * @param triggeredByEmployeeUserId the employee who initiated the retry (recorded in the new transaction's note, not as its initiator - the retry is still made on the original customer's behalf)
     * @return the ID of the brand-new transaction created
     */
    UUID retryAsNewTransfer(UUID originalTransactionId, UUID triggeredByEmployeeUserId);
}
