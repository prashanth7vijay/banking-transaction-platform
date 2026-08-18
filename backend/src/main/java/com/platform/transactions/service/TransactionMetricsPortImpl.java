package com.platform.transactions.service;

import com.platform.transactions.domain.Transaction;
import com.platform.transactions.domain.TransactionStatus;
import com.platform.transactions.dto.ApprovalWorkbenchItemResponse;
import com.platform.transactions.port.TransactionMetricsPort;
import com.platform.transactions.port.TransactionMetricsSummary;
import com.platform.transactions.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.OptionalDouble;

/**
 * All computed in Java over the (demo-scale) result sets rather than pushed
 * into SQL aggregates - same deliberate simplicity as
 * {@code com.platform.exceptions.service.ExceptionMetricsPortImpl}. A real
 * production version would move the averages server-side once transaction
 * volume justified it.
 */
@Service
@RequiredArgsConstructor
public class TransactionMetricsPortImpl implements TransactionMetricsPort {

    private final TransactionRepository transactionRepository;
    private final ApprovalWorkbenchService approvalWorkbenchService;

    @Override
    @Transactional(readOnly = true)
    public TransactionMetricsSummary getMetrics() {
        Instant startOfDay = LocalDate.now().atStartOfDay(ZoneOffset.UTC).toInstant();

        long totalToday = List.of(TransactionStatus.values()).stream()
                .mapToLong(status -> transactionRepository.countByStatusAndCreatedAtAfter(status, startOfDay))
                .sum();
        BigDecimal totalValueToday = transactionRepository.sumAmountCreatedSince(startOfDay);
        long completedToday = transactionRepository.countByStatusAndCreatedAtAfter(TransactionStatus.COMPLETED, startOfDay);
        long failedToday = transactionRepository.countByStatusAndCreatedAtAfter(TransactionStatus.FAILED, startOfDay);
        long rejectedToday = transactionRepository.countByStatusAndCreatedAtAfter(TransactionStatus.REJECTED, startOfDay);
        long decidedToday = completedToday + failedToday + rejectedToday;
        Double successRateToday = decidedToday == 0 ? null : (double) completedToday / decidedToday;

        long pendingApprovalCount = transactionRepository.countByStatus(TransactionStatus.PENDING_APPROVAL);

        List<ApprovalWorkbenchItemResponse> queue = approvalWorkbenchService.listQueue();
        long highRiskPendingCount = queue.stream()
                .filter(item -> "HIGH".equals(item.riskLevel()) || "CRITICAL".equals(item.riskLevel()))
                .count();
        long slaWithin = queue.stream().filter(item -> "WITHIN".equals(item.slaStatus())).count();
        long slaAtRisk = queue.stream().filter(item -> "AT_RISK".equals(item.slaStatus())).count();
        long slaBreached = queue.stream().filter(item -> "BREACHED".equals(item.slaStatus())).count();

        List<Transaction> decided = transactionRepository.findByApprovedByUserIdIsNotNull();
        OptionalDouble avgApproval = decided.stream()
                .mapToLong(t -> Duration.between(t.getCreatedAt(), t.getApprovedAt()).toMinutes())
                .average();
        Double averageApprovalMinutes = avgApproval.isPresent() ? avgApproval.getAsDouble() : null;

        long decidedWithSla = decided.stream().filter(t -> t.getSlaDueAt() != null).count();
        long decidedWithinSla = decided.stream()
                .filter(t -> t.getSlaDueAt() != null && !t.getApprovedAt().isAfter(t.getSlaDueAt()))
                .count();
        Double slaComplianceRate = decidedWithSla == 0 ? null : (double) decidedWithinSla / decidedWithSla;

        return new TransactionMetricsSummary(
                totalToday, totalValueToday, completedToday, failedToday,
                pendingApprovalCount, highRiskPendingCount, successRateToday,
                averageApprovalMinutes, slaComplianceRate, slaWithin, slaAtRisk, slaBreached
        );
    }
}
