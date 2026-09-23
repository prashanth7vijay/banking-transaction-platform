package com.platform.customer360.service;

import com.platform.accounts.port.AccountLookupPort;
import com.platform.accounts.port.AccountSummary;
import com.platform.customer360.dto.*;
import com.platform.exceptions.port.ExceptionLookupPort;
import com.platform.exceptions.port.ExceptionSummary;
import com.platform.risk.port.RiskAssessmentPort;
import com.platform.risk.port.RiskAssessmentSummary;
import com.platform.transactions.port.TransactionActivityAggregate;
import com.platform.transactions.port.TransactionLookupPort;
import com.platform.transactions.port.TransactionSummary;
import com.platform.users.port.UserLookupPort;
import com.platform.users.port.UserSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Customer 360: the same "pure aggregator, no new source of truth" pattern as
 * Transaction360Service, just for a customer instead of a transaction. Lives
 * in its own new module rather than inside `users` deliberately -
 * `transactions`/`accounts`/`exceptions` all already depend on `users` (for
 * name resolution), so hosting this aggregation *inside* `users` would create
 * exactly the cycle Transaction 360 had to work around for its exception link.
 * A separate, dependency-only module (mirroring `dashboard`) avoids that
 * entirely: `customer360` depends on `users`, `accounts`, `transactions`,
 * `risk`, and `exceptions`, one-directionally, and nothing depends back on it -
 * so unlike Transaction 360, this view *can* safely embed exception data
 * directly, no frontend-composition workaround needed.
 *
 * <p><b>Performance history:</b> the original implementation loaded a
 * customer's entire 90-day transaction history into the JVM, then issued one
 * risk-assessment query per transaction and one user query per open
 * exception's assignee - an O(transactions + exceptions) query count per
 * request. This version issues a fixed number of database round trips (7)
 * regardless of how much history the customer has: one row-limited query for
 * the transactions actually displayed, one aggregate query for the activity
 * statistics, one id-projection query to scope the batched lookups, and one
 * batched (IN-clause) query each for risk assessments, exceptions, and
 * exception-assignee names. See {@code docs/customer360-performance.md} for
 * the full before/after query trace and the benchmark that measures it.
 */
@Service
@RequiredArgsConstructor
public class Customer360Service {

    private static final int ACTIVITY_WINDOW_DAYS = 90;
    private static final int RECENT_TRANSACTIONS_LIMIT = 15;
    private static final int TIMELINE_LIMIT = 20;

    /**
     * How many transactions to pull for the timeline/recent-activity sections.
     * Must be at least TIMELINE_LIMIT: the timeline merges transactions with
     * exceptions and keeps only the most recent TIMELINE_LIMIT entries overall,
     * and adding exceptions to the merge can only push a transaction further
     * down the ranking, never up. So the true top-TIMELINE_LIMIT timeline can
     * never need to look past the top TIMELINE_LIMIT transactions by recency -
     * fetching exactly that many up front is provably equivalent to fetching
     * every transaction in the window and sorting in Java, at a fraction of the
     * data transferred.
     */
    private static final int TRANSACTION_FETCH_LIMIT = Math.max(RECENT_TRANSACTIONS_LIMIT, TIMELINE_LIMIT);

    private final UserLookupPort userLookupPort;
    private final AccountLookupPort accountLookupPort;
    private final TransactionLookupPort transactionLookupPort;
    private final RiskAssessmentPort riskAssessmentPort;
    private final ExceptionLookupPort exceptionLookupPort;
    private final MeterRegistry meterRegistry;

    @Transactional(readOnly = true)
    public Customer360Response getSummary(UUID customerId) {
        Timer.Sample sample = Timer.start(meterRegistry);
        int dbOperations = 0;
        try {
            UserSummary customer = userLookupPort.getById(customerId);
            dbOperations++;

            List<AccountSummary> accounts = accountLookupPort.findByOwnerUserId(customerId);
            dbOperations++;

            Instant since90 = Instant.now().minus(Duration.ofDays(ACTIVITY_WINDOW_DAYS));
            Instant since30 = Instant.now().minus(Duration.ofDays(30));

            // DB-side LIMIT: only the rows the response can actually display,
            // never the whole 90-day history (see TRANSACTION_FETCH_LIMIT javadoc).
            List<TransactionSummary> recentTransactions =
                    transactionLookupPort.findRecentForCustomer(customerId, since90, TRANSACTION_FETCH_LIMIT);
            dbOperations++;

            // DB-side aggregation: counts/sums/sub-totals computed by Postgres
            // over the full 90-day window, not reduced in Java after loading it.
            TransactionActivityAggregate activityAggregate =
                    transactionLookupPort.getActivityAggregate(customerId, since90, since30);
            dbOperations++;

            // Id-only projection of the *full* window, used only to scope the
            // batched risk/exception lookups below - never materialized as
            // full Transaction rows.
            List<UUID> windowTransactionIds = transactionLookupPort.findTransactionIdsForCustomerSince(customerId, since90);
            dbOperations++;

            // One batched (IN-clause) risk lookup for the whole window, replacing
            // the previous one-query-per-transaction loop.
            Map<UUID, RiskAssessmentSummary> riskByTransaction = riskAssessmentPort.getAssessments(windowTransactionIds);
            dbOperations++;

            List<ExceptionSummary> exceptions = exceptionLookupPort.listForTransactionIds(windowTransactionIds);
            dbOperations++;

            List<ExceptionSummary> openExceptions = exceptions.stream()
                    .filter(e -> !"RESOLVED".equals(e.status()) && !"CLOSED".equals(e.status()))
                    .toList();

            // One batched name lookup for every assignee referenced by an open
            // exception, replacing the previous one-query-per-exception loop.
            Set<UUID> assigneeIds = openExceptions.stream()
                    .map(ExceptionSummary::assignedToUserId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
            Map<UUID, String> assigneeFirstNames = userLookupPort.getFirstNamesByIds(assigneeIds);
            if (!assigneeIds.isEmpty()) {
                dbOperations++;
            }

            Customer360Response response = new Customer360Response(
                    toProfile(customer),
                    accounts.stream().map(this::toAccountResponse).toList(),
                    buildActivity(recentTransactions, activityAggregate, riskByTransaction, windowTransactionIds),
                    buildRiskProfile(windowTransactionIds, riskByTransaction),
                    openExceptions.stream().map(e -> toExceptionItem(e, assigneeFirstNames)).toList(),
                    buildTimeline(recentTransactions, exceptions)
            );

            meterRegistry.summary("platform.customer360.db.operations").record(dbOperations);
            meterRegistry.summary("platform.customer360.transactions.returned")
                    .record(response.transactionActivity().recentTransactions().size());
            meterRegistry.summary("platform.customer360.transactions.scanned").record(windowTransactionIds.size());
            return response;
        } catch (RuntimeException ex) {
            meterRegistry.counter("platform.customer360.errors", "exception", ex.getClass().getSimpleName()).increment();
            throw ex;
        } finally {
            sample.stop(meterRegistry.timer("platform.customer360.request.duration"));
        }
    }

    private CustomerProfileResponse toProfile(UserSummary user) {
        return new CustomerProfileResponse(user.id(), user.email(), user.firstName(), user.lastName(), user.status(), user.createdAt());
    }

    private CustomerAccountResponse toAccountResponse(AccountSummary account) {
        return new CustomerAccountResponse(account.id(), account.accountNumber(), account.balance(), account.status().name());
    }

    private CustomerTransactionItem toTransactionItem(TransactionSummary t) {
        return new CustomerTransactionItem(t.id(), t.amount(), t.currency(), t.status().name(), t.destinationAccountNumber(), t.createdAt());
    }

    private CustomerExceptionItem toExceptionItem(ExceptionSummary e, Map<UUID, String> assigneeFirstNames) {
        String assignedToName = e.assignedToUserId() == null ? null : assigneeFirstNames.get(e.assignedToUserId());
        return new CustomerExceptionItem(e.id(), e.transactionId(), e.status(), e.priority(), e.reason(), assignedToName, e.createdAt());
    }

    /**
     * {@code recentTransactions} is already DB-limited to
     * {@link #TRANSACTION_FETCH_LIMIT} rows (used here only for the displayed
     * "recent transactions" list, truncated further to
     * {@link #RECENT_TRANSACTIONS_LIMIT}); every count/sum below comes from
     * {@code aggregate}, computed by Postgres over the true 90-day window, not
     * from these rows. High-risk count still needs the full window's risk
     * assessments (reasons/levels aren't aggregable in SQL without decoding the
     * JSONB reasons column - see {@link #buildRiskProfile}), so it's derived
     * from the same {@code riskByTransaction} map built there, scoped by
     * {@code windowTransactionIds}.
     */
    private CustomerTransactionActivityResponse buildActivity(List<TransactionSummary> recentTransactions,
                                                                TransactionActivityAggregate aggregate,
                                                                Map<UUID, RiskAssessmentSummary> riskByTransaction,
                                                                List<UUID> windowTransactionIds) {
        BigDecimal averageAmount = aggregate.totalCount() == 0 ? null
                : aggregate.totalAmount().divide(BigDecimal.valueOf(aggregate.totalCount()), 2, RoundingMode.HALF_UP);

        long highRiskCount = windowTransactionIds.stream()
                .map(riskByTransaction::get)
                .filter(Objects::nonNull)
                .filter(r -> "HIGH".equals(r.riskLevel()) || "CRITICAL".equals(r.riskLevel()))
                .count();

        List<CustomerTransactionItem> recent = recentTransactions.stream()
                .limit(RECENT_TRANSACTIONS_LIMIT)
                .map(this::toTransactionItem)
                .toList();

        return new CustomerTransactionActivityResponse(
                recent, aggregate.last30DayCount(), aggregate.last30DayAmount(), averageAmount,
                aggregate.pendingCount(), aggregate.failedCount(), highRiskCount);
    }

    /**
     * No invented customer-level score - see this DTO's own javadoc.
     * {@code windowTransactionIds} is ordered most-recent-first (see
     * {@code TransactionRepository.findTransactionIdsForCustomerSince}), so
     * walking it against the batched {@code riskByTransaction} map and taking
     * the first hit reproduces the original "first transaction (in
     * most-recent-first order) that has an assessment" logic exactly - just
     * without re-fetching full transaction rows to get that order.
     */
    private CustomerRiskProfileResponse buildRiskProfile(List<UUID> windowTransactionIds,
                                                           Map<UUID, RiskAssessmentSummary> riskByTransaction) {
        String mostRecentLevel = windowTransactionIds.stream()
                .map(riskByTransaction::get)
                .filter(Objects::nonNull)
                .map(RiskAssessmentSummary::riskLevel)
                .findFirst()
                .orElse(null);

        Map<String, Long> countByLevel = riskByTransaction.values().stream()
                .collect(Collectors.groupingBy(RiskAssessmentSummary::riskLevel, Collectors.counting()));

        List<String> distinctReasons = riskByTransaction.values().stream()
                .flatMap(r -> r.reasons().stream())
                .distinct()
                .toList();

        return new CustomerRiskProfileResponse(mostRecentLevel, countByLevel, distinctReasons);
    }

    /**
     * Built from transaction submissions and exception openings only - not also
     * account-opening events, since {@code AccountSummary} doesn't carry a
     * creation timestamp and extending it would ripple through every existing
     * caller for a timeline nicety. A documented simplification, not an
     * oversight.
     *
     * <p>{@code transactions} is DB-limited to {@link #TRANSACTION_FETCH_LIMIT}
     * rows rather than the whole window - see that constant's javadoc for why
     * this is provably equivalent to using every transaction in the window.
     */
    private List<CustomerTimelineEntry> buildTimeline(List<TransactionSummary> transactions, List<ExceptionSummary> exceptions) {
        List<CustomerTimelineEntry> entries = new ArrayList<>();
        for (TransactionSummary t : transactions) {
            entries.add(new CustomerTimelineEntry(t.createdAt(),
                    String.format("Transfer of %s %s submitted (%s)", t.currency(), t.amount(), t.status())));
        }
        for (ExceptionSummary e : exceptions) {
            entries.add(new CustomerTimelineEntry(e.createdAt(), "Exception opened: " + e.reason()));
        }
        return entries.stream()
                .sorted(Comparator.comparing(CustomerTimelineEntry::occurredAt).reversed())
                .limit(TIMELINE_LIMIT)
                .toList();
    }
}
