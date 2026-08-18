package com.platform.risk.service;

import com.platform.risk.domain.RiskLevel;

import java.util.List;

public record RiskAssessmentResult(
        RiskLevel riskLevel,
        boolean blocked,
        List<String> reasons
) {
    public static RiskAssessmentResult clean() {
        return new RiskAssessmentResult(RiskLevel.LOW, false, List.of());
    }
}
