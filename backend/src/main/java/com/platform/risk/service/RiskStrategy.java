package com.platform.risk.service;

/**
 * Rule-based today (RuleBasedRiskStrategy). A future ML-based strategy would
 * implement this same interface and could run alongside the rule-based one (the
 * higher assessed risk level winning) rather than replacing it - this is the
 * seam a future ML strategy needs, kept minimal because it's the only place in
 * this design where that flexibility is actually required.
 */
public interface RiskStrategy {
    RiskAssessmentResult assess(TransferContext context);
}
