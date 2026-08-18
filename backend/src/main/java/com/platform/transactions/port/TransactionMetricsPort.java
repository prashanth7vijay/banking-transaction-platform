package com.platform.transactions.port;

/**
 * The `transactions` module's public read contract for aggregate operational
 * metrics - consumed by the new `dashboard` module (Operations Command
 * Center). Every field is a count/average/sum over existing rows; nothing
 * here decides anything, it only reports.
 */
public interface TransactionMetricsPort {
    TransactionMetricsSummary getMetrics();
}
