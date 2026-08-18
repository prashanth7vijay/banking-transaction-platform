package com.platform.risk.port;

import com.platform.risk.service.RiskAssessmentResult;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

/**
 * The `risk` module's public contract for `transactions` - the mirror image of
 * `transactions`' own TransferHistoryPort, which `risk` depends on in the other
 * direction. Two peer modules each needing something narrow from the other is
 * expressed as two separate, one-directional port interfaces - never a direct
 * service-to-service reach across the boundary.
 */
public interface RiskAssessmentPort {
    RiskAssessmentResult assessAndRecord(UUID transactionId, UUID sourceAccountId, UUID customerUserId,
                                          BigDecimal amount, String currency);

    /** Used only to enrich transaction responses for display - never for a decision. */
    Optional<String> getRiskLevel(UUID transactionId);

    /**
     * The full explainability picture - level, reasons, blocked flag, timestamp -
     * for Transaction 360's risk section. {@link #getRiskLevel} stays as the
     * lightweight version for existing list-view badges; this is additive, not a
     * replacement.
     */
    Optional<RiskAssessmentSummary> getAssessment(UUID transactionId);
}
