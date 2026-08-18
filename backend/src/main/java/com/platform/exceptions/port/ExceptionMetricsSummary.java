package com.platform.exceptions.port;

public record ExceptionMetricsSummary(
        long openCount,
        long slaWithinCount,
        long slaAtRiskCount,
        long slaBreachedCount,
        /** Average minutes from creation to resolution, among RESOLVED/CLOSED cases. Null if none resolved yet. */
        Double averageResolutionMinutes
) {
}
