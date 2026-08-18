package com.platform.customer360.dto;

import java.util.List;
import java.util.Map;

/**
 * Deliberately not a single invented "customer risk score" - this system only
 * ever computes risk per-transaction (see {@code RuleBasedRiskStrategy}), so a
 * customer-level view is honestly built by aggregating those real assessments:
 * their most recent posture, the spread of levels seen across their history,
 * and the specific reasons that have actually fired for them. No ML, no
 * fabricated number - see the product brief's "do not create an unnecessary ML
 * system" note.
 */
public record CustomerRiskProfileResponse(
        /** The risk level of this customer's most recent assessed transaction - null if they have none. */
        String mostRecentRiskLevel,
        /** How many of this customer's assessed transactions fell into each level, e.g. {"LOW": 8, "HIGH": 1}. */
        Map<String, Long> assessmentCountByLevel,
        /** Every distinct reason that has ever fired for this customer's transactions. */
        List<String> distinctRiskReasons
) {
}
