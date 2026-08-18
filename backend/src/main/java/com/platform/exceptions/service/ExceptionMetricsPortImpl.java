package com.platform.exceptions.service;

import com.platform.exceptions.domain.ExceptionStatus;
import com.platform.exceptions.domain.TransactionException;
import com.platform.exceptions.mapper.ExceptionMapper;
import com.platform.exceptions.port.ExceptionMetricsPort;
import com.platform.exceptions.port.ExceptionMetricsSummary;
import com.platform.exceptions.repository.TransactionExceptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.List;
import java.util.OptionalDouble;

/**
 * All computed in Java over the (demo-scale) result sets rather than pushed
 * into SQL aggregates - deliberately simple, since correctness and
 * readability matter more than query efficiency at this data volume. A real
 * production version would move the averages server-side once case volume
 * justified it.
 */
@Service
@RequiredArgsConstructor
public class ExceptionMetricsPortImpl implements ExceptionMetricsPort {

    private static final List<ExceptionStatus> OPEN_STATUSES =
            List.of(ExceptionStatus.OPEN, ExceptionStatus.ASSIGNED, ExceptionStatus.INVESTIGATING, ExceptionStatus.ACTION_REQUIRED);
    private static final List<ExceptionStatus> RESOLVED_STATUSES =
            List.of(ExceptionStatus.RESOLVED, ExceptionStatus.CLOSED);

    private final TransactionExceptionRepository transactionExceptionRepository;

    @Override
    @Transactional(readOnly = true)
    public ExceptionMetricsSummary getMetrics() {
        List<TransactionException> open = transactionExceptionRepository.findByStatusInOrderByCreatedAtAsc(OPEN_STATUSES);

        long within = 0;
        long atRisk = 0;
        long breached = 0;
        for (TransactionException exception : open) {
            switch (ExceptionMapper.computeSlaStatus(exception)) {
                case "AT_RISK" -> atRisk++;
                case "BREACHED" -> breached++;
                default -> within++;
            }
        }

        List<TransactionException> resolved = transactionExceptionRepository.findByStatusInOrderByCreatedAtAsc(RESOLVED_STATUSES);
        OptionalDouble avgResolution = resolved.stream()
                .filter(e -> e.getResolvedAt() != null)
                .mapToLong(e -> Duration.between(e.getCreatedAt(), e.getResolvedAt()).toMinutes())
                .average();
        Double averageResolutionMinutes = avgResolution.isPresent() ? avgResolution.getAsDouble() : null;

        return new ExceptionMetricsSummary(open.size(), within, atRisk, breached, averageResolutionMinutes);
    }
}
