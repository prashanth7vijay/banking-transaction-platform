package com.platform.risk.port;

import java.time.Instant;
import java.util.List;

/**
 * The full explainability picture for one transaction's risk assessment - level,
 * the specific reasons that were triggered, whether it was a hard block, and
 * when it ran. Sits alongside {@link RiskAssessmentPort#getRiskLevel} (which
 * stays as-is - it's the lightweight version existing callers already use for
 * list-view badges) rather than replacing it; this richer summary exists for
 * Transaction 360, where "why is this risky" is the whole point of the section.
 */
public record RiskAssessmentSummary(
        String riskLevel,
        List<String> reasons,
        boolean blocked,
        Instant assessedAt
) {
}
