package com.platform.risk.service;

import com.platform.risk.domain.LimitPolicy;
import com.platform.risk.domain.LimitScope;
import com.platform.risk.domain.RiskLevel;
import com.platform.risk.repository.LimitPolicyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;


@Service
@RequiredArgsConstructor
public class RuleBasedRiskStrategy implements RiskStrategy {

    private final LimitPolicyRepository limitPolicyRepository;

    @Override
    public RiskAssessmentResult assess(TransferContext context) {
        List<LimitPolicy> policies = limitPolicyRepository.findByScopeAndActiveTrue(LimitScope.GLOBAL);

        List<String> reasons = new ArrayList<>();
        RiskLevel highest = RiskLevel.LOW;
        boolean blocked = false;

        for (LimitPolicy policy : policies) {
            EvaluationOutcome outcome = evaluate(policy, context);
            if (outcome == null) {
                continue;
            }
            reasons.add(outcome.reason());
            if (outcome.level().ordinal() > highest.ordinal()) {
                highest = outcome.level();
            }
            if (outcome.hardBlock()) {
                blocked = true;
            }
        }

        return new RiskAssessmentResult(highest, blocked, reasons);
    }

    private EvaluationOutcome evaluate(LimitPolicy policy, TransferContext context) {
        return switch (policy.getLimitType()) {
            case PER_TRANSACTION -> {
                if (policy.getMaxAmount() != null && context.amount().compareTo(policy.getMaxAmount()) > 0) {
                    yield new EvaluationOutcome(RiskLevel.HIGH, true,
                            "Amount " + context.amount() + " exceeds per-transaction limit " + policy.getMaxAmount()
                                    + " (policy: " + policy.getName() + ")");
                }
                yield null;
            }
            case DAILY_CUMULATIVE -> {
                if (policy.getMaxAmount() != null) {
                    BigDecimal projected = context.todaysAmountSoFar().add(context.amount());
                    if (projected.compareTo(policy.getMaxAmount()) > 0) {
                        yield new EvaluationOutcome(RiskLevel.MEDIUM, false,
                                "Projected daily total " + projected + " exceeds limit " + policy.getMaxAmount()
                                        + " (policy: " + policy.getName() + ")");
                    }
                }
                yield null;
            }
            case VELOCITY_COUNT -> {
                if (policy.getMaxCount() != null && context.recentTransferCount() >= policy.getMaxCount()) {
                    yield new EvaluationOutcome(RiskLevel.MEDIUM, false,
                            "Transfer velocity (" + context.recentTransferCount() + " in "
                                    + policy.getWindowMinutes() + " min) reached limit " + policy.getMaxCount()
                                    + " (policy: " + policy.getName() + ")");
                }
                yield null;
            }
        };
    }

    private record EvaluationOutcome(RiskLevel level, boolean hardBlock, String reason) {
    }
}
