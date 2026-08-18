package com.platform.customer360.dto;

import java.time.Instant;

public record CustomerTimelineEntry(
        Instant occurredAt,
        String description
) {
}
