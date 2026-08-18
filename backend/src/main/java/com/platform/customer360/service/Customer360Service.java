package com.platform.customer360.service;

import com.platform.accounts.port.AccountLookupPort;
import com.platform.accounts.port.AccountSummary;
import com.platform.customer360.dto.*;
import com.platform.exceptions.port.ExceptionLookupPort;
import com.platform.exceptions.port.ExceptionSummary;
import com.platform.risk.port.RiskAssessmentPort;
import com.platform.risk.port.RiskAssessmentSummary;
import com.platform.transactions.domain.TransactionStatus;
import com.platform.transactions.port.TransactionLookupPort;
import com.platform.transactions.port.TransactionSummary;
import com.platform.users.port.UserLookupPort;
import com.platform.users.port.UserSummary;
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
 */
@Service
@RequiredArgsConstructor
public class Customer360Service {

    private static final int ACTIVITY_WINDOW_DAYS = 90;
    private static final int RECENT_TRANSACTIONS_LIMIT = 15;
    private static final int TIMELINE_LIMIT = 20;

    private final UserLookupPort userLookupPort;
    private final AccountLookupPort accountLookupPort;
    private final TransactionLookupPort transactionLookupPort;
    private final RiskAssessmentPort riskAssessmentPort;
    private final ExceptionLookupPort exceptionLookupPort;

    @Transactional(readOnly = true)
    public Customer360Response getSummary(UUID customerId) {
        UserSummary customer = userLookupPort.getById(customerId);
        List<AccountSummary> accounts = accountLookupPort.findByOwnerUserId(customerId);

        Instant since = Instant.now().minus(Duration.ofDays(ACTIVITY_WINDOW_DAYS));
        List<TransactionSummary> transactions = transactionLookupPort.listForCustomerSince(customerId, since);

        // One risk lookup per transaction, reused below by both the activity
        // section (high-risk count) and the risk profile section - never
        // fetched twice for the same transaction.
        Map<UUID, RiskAssessmentSummary> riskByTransaction = transactions.stream()
                .map(TransactionSummary::id)
                .flatMap(id -> riskAssessmentPort.getAssessment(id).stream().map(r -> Map.entry(id, r)))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        List<UUID> transactionIds = transactions.stream().map(TransactionSummary::id).toList();
        List<ExceptionSummary> exceptions = exceptionLookupPort.listForTransactionIds(transactionIds);

        return new Customer360Response(
                toProfile(customer),
                accounts.stream().map(this::toAccountResponse).toList(),
                buildActivity(transactions, riskByTransaction),
                buildRiskProfile(transactions, riskByTransaction),
                exceptions.stream()
                        .filter(e -> !"RESOLVED".equals(e.status()) && !"CLOSED".equals(e.status()))
                        .map(this::toExceptionItem)
                        .toList(),
                buildTimeline(transactions, exceptions)
        );
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

    private CustomerExceptionItem toExceptionItem(ExceptionSummary e) {
        String assignedToName = e.assignedToUserId() == null ? null : userLookupPort.getById(e.assignedToUserId()).firstName();
        return new CustomerExceptionItem(e.id(), e.transactionId(), e.status(), e.priority(), e.reason(), assignedToName, e.createdAt());
    }

    private CustomerTransactionActivityResponse buildActivity(List<TransactionSummary> transactions,
                                                                Map<UUID, RiskAssessmentSummary> riskByTransaction) {
        Instant thirtyDaysAgo = Instant.now().minus(Duration.ofDays(30));
        List<TransactionSummary> last30Days = transactions.stream().filter(t -> t.createdAt().isAfter(thirtyDaysAgo)).toList();
        BigDecimal last30DayValue = last30Days.stream().map(TransactionSummary::amount).reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal averageAmount = transactions.isEmpty() ? null
                : transactions.stream().map(TransactionSummary::amount).reduce(BigDecimal.ZERO, BigDecimal::add)
                        .divide(BigDecimal.valueOf(transactions.size()), 2, RoundingMode.HALF_UP);

        long pendingCount = transactions.stream().filter(t -> t.status() == TransactionStatus.PENDING_APPROVAL).count();
        long failedCount = transactions.stream().filter(t -> t.status() == TransactionStatus.FAILED).count();
        long highRiskCount = transactions.stream()
                .map(t -> riskByTransaction.get(t.id()))
                .filter(Objects::nonNull)
                .filter(r -> "HIGH".equals(r.riskLevel()) || "CRITICAL".equals(r.riskLevel()))
                .count();

        List<CustomerTransactionItem> recent = transactions.stream()
                .limit(RECENT_TRANSACTIONS_LIMIT)
                .map(this::toTransactionItem)
                .toList();

        return new CustomerTransactionActivityResponse(
                recent, last30Days.size(), last30DayValue, averageAmount, pendingCount, failedCount, highRiskCount);
    }

    /**
     * No invented customer-level score - see this DTO's own javadoc. transactions
     * is already ordered most-recent-first (TransactionLookupPort's contract), so
     * the first one with an assessment gives an honest "current posture."
     */
    private CustomerRiskProfileResponse buildRiskProfile(List<TransactionSummary> transactions,
                                                           Map<UUID, RiskAssessmentSummary> riskByTransaction) {
        String mostRecentLevel = transactions.stream()
                .map(t -> riskByTransaction.get(t.id()))
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
