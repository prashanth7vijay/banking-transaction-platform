package com.platform.transactions.port;

import java.math.BigDecimal;

/**
 * The numeric summary Customer 360 needs for a customer's transaction
 * activity window, computed by PostgreSQL (COUNT/SUM/CASE) over the whole
 * window instead of by streaming every row of that window into the JVM.
 * <p>
 * {@code totalCount}/{@code totalAmount} cover the full query window (90 days
 * in Customer 360 today); {@code last30*} fields are the same window's
 * transactions further restricted to the last 30 days - two sub-aggregates in
 * one query rather than two separate scans. Average amount is deliberately
 * NOT included here: it must preserve the original null-when-empty and
 * BigDecimal(scale=2, HALF_UP) behavior exactly, which is safer to compute in
 * Java from {@code totalCount}/{@code totalAmount} than to trust to JPQL's
 * {@code AVG()} (whose result type/rounding isn't guaranteed to match).
 */
public record TransactionActivityAggregate(
        long totalCount,
        BigDecimal totalAmount,
        long last30DayCount,
        BigDecimal last30DayAmount,
        long pendingCount,
        long failedCount
) {
}
