package com.platform.exceptions.domain;

import java.time.Duration;

/**
 * Operational priority - distinct from {@code RiskLevel} (which describes the
 * transaction itself); this describes how urgently *this case* needs an
 * operator's attention. Each level carries a fixed SLA window, set at exception
 * creation time. These durations are a deliberately simple, explainable
 * business rule (not a model or heuristic) - easy to justify to an auditor:
 * "critical cases get one hour," full stop.
 */
public enum ExceptionPriority {
    CRITICAL(Duration.ofHours(1)),
    HIGH(Duration.ofHours(4)),
    MEDIUM(Duration.ofHours(24)),
    LOW(Duration.ofHours(72));

    private final Duration slaWindow;

    ExceptionPriority(Duration slaWindow) {
        this.slaWindow = slaWindow;
    }

    public Duration getSlaWindow() {
        return slaWindow;
    }
}
