package com.platform.dashboard.service;

import com.platform.dashboard.dto.CommandCenterSummaryResponse;
import com.platform.exceptions.port.ExceptionMetricsPort;
import com.platform.transactions.port.TransactionMetricsPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * `dashboard` is a new, small, schema-less module: it exists only to compose
 * what `transactions` and `exceptions` already expose through their metrics
 * ports. The dependency runs one way, same as `exceptions` -&gt; `transactions` -
 * neither of those modules has any idea `dashboard` exists.
 */
@Service
@RequiredArgsConstructor
public class CommandCenterService {

    private final TransactionMetricsPort transactionMetricsPort;
    private final ExceptionMetricsPort exceptionMetricsPort;

    public CommandCenterSummaryResponse getSummary() {
        return new CommandCenterSummaryResponse(
                transactionMetricsPort.getMetrics(),
                exceptionMetricsPort.getMetrics()
        );
    }
}
