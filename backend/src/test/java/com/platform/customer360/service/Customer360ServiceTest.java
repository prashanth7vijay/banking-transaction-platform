package com.platform.customer360.service;

import com.platform.accounts.domain.AccountStatus;
import com.platform.accounts.port.AccountLookupPort;
import com.platform.accounts.port.AccountSummary;
import com.platform.customer360.dto.Customer360Response;
import com.platform.exceptions.port.ExceptionLookupPort;
import com.platform.exceptions.port.ExceptionSummary;
import com.platform.risk.port.RiskAssessmentPort;
import com.platform.risk.port.RiskAssessmentSummary;
import com.platform.transactions.domain.TransactionStatus;
import com.platform.transactions.port.TransactionActivityAggregate;
import com.platform.transactions.port.TransactionLookupPort;
import com.platform.transactions.port.TransactionSummary;
import com.platform.users.port.UserLookupPort;
import com.platform.users.port.UserSummary;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Covers the correctness cases called out in the Customer 360 performance
 * work: the optimized service must produce the same response shape/values as
 * the original N+1 implementation for every case below, while only ever
 * issuing the fixed, batched set of port calls (never one call per
 * transaction/exception). Ports are mocked, so this suite exercises the
 * service's aggregation and wiring logic; it does not (and cannot, without a
 * real Postgres instance) verify the SQL behind {@code TransactionRepository}
 * /{@code RiskAssessmentRepository} - that's covered separately by
 * {@code Customer360QueryCountIT} and the EXPLAIN ANALYZE notes in
 * docs/customer360-performance.md.
 */
@ExtendWith(MockitoExtension.class)
class Customer360ServiceTest {

    @Mock private UserLookupPort userLookupPort;
    @Mock private AccountLookupPort accountLookupPort;
    @Mock private TransactionLookupPort transactionLookupPort;
    @Mock private RiskAssessmentPort riskAssessmentPort;
    @Mock private ExceptionLookupPort exceptionLookupPort;

    private Customer360Service service;
    private final UUID customerId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new Customer360Service(
                userLookupPort, accountLookupPort, transactionLookupPort,
                riskAssessmentPort, exceptionLookupPort, new SimpleMeterRegistry());

        when(userLookupPort.getById(customerId)).thenReturn(
                new UserSummary(customerId, "a@b.com", "Ann", "Lee", "ACTIVE", Instant.now(), Set.of("CUSTOMER")));
        when(accountLookupPort.findByOwnerUserId(customerId)).thenReturn(List.of(
                new AccountSummary(UUID.randomUUID(), customerId, "ACC-1", BigDecimal.TEN, AccountStatus.ACTIVE)));
    }

    private TransactionSummary tx(UUID id, BigDecimal amount, TransactionStatus status, Instant createdAt) {
        return new TransactionSummary(id, UUID.randomUUID(), "DEST-1", amount, "USD", status, customerId, createdAt);
    }

    @Test
    void customerWithNoTransactionsGetsEmptyActivityAndNullAverage() {
        when(transactionLookupPort.findRecentForCustomer(eq(customerId), any(), anyInt())).thenReturn(List.of());
        when(transactionLookupPort.findTransactionIdsForCustomerSince(eq(customerId), any())).thenReturn(List.of());
        when(transactionLookupPort.getActivityAggregate(eq(customerId), any(), any()))
                .thenReturn(new TransactionActivityAggregate(0, BigDecimal.ZERO, 0, BigDecimal.ZERO, 0, 0));
        when(riskAssessmentPort.getAssessments(List.of())).thenReturn(Map.of());
        when(exceptionLookupPort.listForTransactionIds(List.of())).thenReturn(List.of());

        Customer360Response response = service.getSummary(customerId);

        assertThat(response.transactionActivity().recentTransactions()).isEmpty();
        assertThat(response.transactionActivity().averageTransactionAmount()).isNull();
        assertThat(response.transactionActivity().highRiskCount()).isZero();
        assertThat(response.risk().mostRecentRiskLevel()).isNull();
        assertThat(response.openExceptions()).isEmpty();
        assertThat(response.timeline()).isEmpty();
        // No exceptions -> no assignee names to resolve -> that batch call must be skipped entirely.
        verify(userLookupPort, never()).getFirstNamesByIds(argThat(ids -> !ids.isEmpty()));
    }

    @Test
    void recentTransactionsAreTruncatedToDisplayLimitEvenWhenMoreAreFetched() {
        // The service fetches TRANSACTION_FETCH_LIMIT (20) rows to safely build
        // the timeline, but must only ever *display* 15 as "recent transactions".
        List<TransactionSummary> twenty = java.util.stream.IntStream.range(0, 20)
                .mapToObj(i -> tx(UUID.randomUUID(), BigDecimal.valueOf(10 + i), TransactionStatus.COMPLETED,
                        Instant.now().minus(i, ChronoUnit.HOURS)))
                .toList();
        when(transactionLookupPort.findRecentForCustomer(eq(customerId), any(), eq(20))).thenReturn(twenty);
        when(transactionLookupPort.findTransactionIdsForCustomerSince(eq(customerId), any()))
                .thenReturn(twenty.stream().map(TransactionSummary::id).toList());
        when(transactionLookupPort.getActivityAggregate(eq(customerId), any(), any()))
                .thenReturn(new TransactionActivityAggregate(20, BigDecimal.valueOf(400), 5, BigDecimal.valueOf(60), 0, 0));
        when(riskAssessmentPort.getAssessments(anyList())).thenReturn(Map.of());
        when(exceptionLookupPort.listForTransactionIds(anyList())).thenReturn(List.of());

        Customer360Response response = service.getSummary(customerId);

        assertThat(response.transactionActivity().recentTransactions()).hasSize(15);
        assertThat(response.timeline()).hasSize(20);
        // Exactly one call to fetch the bounded set - never one per transaction.
        verify(transactionLookupPort, times(1)).findRecentForCustomer(eq(customerId), any(), eq(20));
    }

    @Test
    void averageAmountUsesFullWindowNotJustDisplayedRows() {
        when(transactionLookupPort.findRecentForCustomer(eq(customerId), any(), anyInt()))
                .thenReturn(List.of(tx(UUID.randomUUID(), BigDecimal.TEN, TransactionStatus.COMPLETED, Instant.now())));
        when(transactionLookupPort.findTransactionIdsForCustomerSince(eq(customerId), any()))
                .thenReturn(List.of(UUID.randomUUID()));
        // 100 transactions totalling 1000.00 over the 90-day window -> average 10.00,
        // even though only 1 row was fetched for display.
        when(transactionLookupPort.getActivityAggregate(eq(customerId), any(), any()))
                .thenReturn(new TransactionActivityAggregate(100, new BigDecimal("1000.00"), 3, new BigDecimal("30.00"), 1, 2));
        when(riskAssessmentPort.getAssessments(anyList())).thenReturn(Map.of());
        when(exceptionLookupPort.listForTransactionIds(anyList())).thenReturn(List.of());

        Customer360Response response = service.getSummary(customerId);

        assertThat(response.transactionActivity().averageTransactionAmount()).isEqualByComparingTo("10.00");
        assertThat(response.transactionActivity().pendingCount()).isEqualTo(1);
        assertThat(response.transactionActivity().failedCount()).isEqualTo(2);
        assertThat(response.transactionActivity().last30DayCount()).isEqualTo(3);
        assertThat(response.transactionActivity().last30DayValue()).isEqualByComparingTo("30.00");
    }

    @Test
    void riskAssessmentsAreBatchFetchedOnceRegardlessOfTransactionCount() {
        List<UUID> ids = List.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        when(transactionLookupPort.findRecentForCustomer(eq(customerId), any(), anyInt())).thenReturn(List.of(
                tx(ids.get(0), BigDecimal.TEN, TransactionStatus.COMPLETED, Instant.now()),
                tx(ids.get(1), BigDecimal.ONE, TransactionStatus.COMPLETED, Instant.now().minusSeconds(60)),
                tx(ids.get(2), BigDecimal.TEN, TransactionStatus.COMPLETED, Instant.now().minusSeconds(120))));
        when(transactionLookupPort.findTransactionIdsForCustomerSince(eq(customerId), any())).thenReturn(ids);
        when(transactionLookupPort.getActivityAggregate(eq(customerId), any(), any()))
                .thenReturn(new TransactionActivityAggregate(3, BigDecimal.valueOf(21), 3, BigDecimal.valueOf(21), 0, 0));
        // Only the most recent transaction (ids.get(0)) and the oldest (ids.get(2)) have an assessment.
        when(riskAssessmentPort.getAssessments(ids)).thenReturn(Map.of(
                ids.get(0), new RiskAssessmentSummary("HIGH", List.of("VELOCITY"), false, Instant.now()),
                ids.get(2), new RiskAssessmentSummary("LOW", List.of("NONE"), false, Instant.now())));
        when(exceptionLookupPort.listForTransactionIds(ids)).thenReturn(List.of());

        Customer360Response response = service.getSummary(customerId);

        assertThat(response.transactionActivity().highRiskCount()).isEqualTo(1);
        // Most recent transaction with an assessment is ids.get(0) (HIGH) -
        // even though ids.get(1) is more recent than ids.get(2), it has no
        // assessment at all and must be skipped, not treated as "no risk".
        assertThat(response.risk().mostRecentRiskLevel()).isEqualTo("HIGH");
        assertThat(response.risk().assessmentCountByLevel()).containsEntry("HIGH", 1L).containsEntry("LOW", 1L);
        assertThat(response.risk().distinctRiskReasons()).containsExactlyInAnyOrder("VELOCITY", "NONE");
        // Exactly one batched call, never one per transaction id.
        verify(riskAssessmentPort, times(1)).getAssessments(anyList());
    }

    @Test
    void exceptionAssigneeNamesAreBatchFetchedOnceAndResolvedExceptionsAreExcluded() {
        UUID txId = UUID.randomUUID();
        UUID assignee1 = UUID.randomUUID();
        UUID assignee2 = UUID.randomUUID();
        when(transactionLookupPort.findRecentForCustomer(eq(customerId), any(), anyInt()))
                .thenReturn(List.of(tx(txId, BigDecimal.TEN, TransactionStatus.FAILED, Instant.now())));
        when(transactionLookupPort.findTransactionIdsForCustomerSince(eq(customerId), any())).thenReturn(List.of(txId));
        when(transactionLookupPort.getActivityAggregate(eq(customerId), any(), any()))
                .thenReturn(new TransactionActivityAggregate(1, BigDecimal.TEN, 1, BigDecimal.TEN, 0, 1));
        when(riskAssessmentPort.getAssessments(List.of(txId))).thenReturn(Map.of());

        ExceptionSummary open = new ExceptionSummary(UUID.randomUUID(), txId, "OPEN", "HIGH", "Failed transfer", assignee1, Instant.now());
        ExceptionSummary assigned = new ExceptionSummary(UUID.randomUUID(), txId, "ASSIGNED", "LOW", "Review", assignee2, Instant.now());
        ExceptionSummary resolved = new ExceptionSummary(UUID.randomUUID(), txId, "RESOLVED", "LOW", "Fixed", assignee1, Instant.now());
        ExceptionSummary closedOne = new ExceptionSummary(UUID.randomUUID(), txId, "CLOSED", "LOW", "Done", null, Instant.now());
        when(exceptionLookupPort.listForTransactionIds(List.of(txId)))
                .thenReturn(List.of(open, assigned, resolved, closedOne));
        when(userLookupPort.getFirstNamesByIds(Set.of(assignee1, assignee2)))
                .thenReturn(Map.of(assignee1, "Sam", assignee2, "Robin"));

        Customer360Response response = service.getSummary(customerId);

        assertThat(response.openExceptions()).hasSize(2);
        assertThat(response.openExceptions()).extracting("assignedToName").containsExactlyInAnyOrder("Sam", "Robin");
        // Resolved/closed excluded from openExceptions but still contribute to the timeline.
        assertThat(response.timeline()).hasSize(5); // 1 transaction + 4 exceptions
        verify(userLookupPort, times(1)).getFirstNamesByIds(anySet());
    }

    @Test
    void exceptionWithNoAssigneeGetsNullNameWithoutLookup() {
        UUID txId = UUID.randomUUID();
        when(transactionLookupPort.findRecentForCustomer(eq(customerId), any(), anyInt()))
                .thenReturn(List.of(tx(txId, BigDecimal.TEN, TransactionStatus.FAILED, Instant.now())));
        when(transactionLookupPort.findTransactionIdsForCustomerSince(eq(customerId), any())).thenReturn(List.of(txId));
        when(transactionLookupPort.getActivityAggregate(eq(customerId), any(), any()))
                .thenReturn(new TransactionActivityAggregate(1, BigDecimal.TEN, 1, BigDecimal.TEN, 0, 1));
        when(riskAssessmentPort.getAssessments(List.of(txId))).thenReturn(Map.of());
        ExceptionSummary unassigned = new ExceptionSummary(UUID.randomUUID(), txId, "OPEN", "LOW", "Unassigned case", null, Instant.now());
        when(exceptionLookupPort.listForTransactionIds(List.of(txId))).thenReturn(List.of(unassigned));

        Customer360Response response = service.getSummary(customerId);

        assertThat(response.openExceptions()).hasSize(1);
        assertThat(response.openExceptions().get(0).assignedToName()).isNull();
        verify(userLookupPort, never()).getFirstNamesByIds(any());
    }
}