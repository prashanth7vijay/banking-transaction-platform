package com.platform.exceptions.port;

/**
 * The `exceptions` module's public read contract for aggregate metrics - so far
 * only the new `dashboard` module (Operations Command Center) consumes this.
 * Everything here is a count or an average over existing rows, never a new
 * business rule.
 */
public interface ExceptionMetricsPort {
    ExceptionMetricsSummary getMetrics();
}
