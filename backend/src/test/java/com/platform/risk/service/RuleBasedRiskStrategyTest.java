package com.platform.risk.service;

import com.platform.risk.domain.LimitPolicy;
import com.platform.risk.domain.LimitScope;
import com.platform.risk.domain.LimitType;
import com.platform.risk.domain.RiskLevel;
import com.platform.risk.repository.LimitPolicyRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RuleBasedRiskStrategyTest {

    @Mock
    private LimitPolicyRepository limitPolicyRepository;

    private RuleBasedRiskStrategy strategy;

    private final UUID accountId = UUID.randomUUID();
    private final UUID customerId = UUID.randomUUID();

    private void init() {
        strategy = new RuleBasedRiskStrategy(limitPolicyRepository);
    }

    @Test
    void noActivePoliciesMeansCleanAssessment() {
        init();
        when(limitPolicyRepository.findByScopeAndActiveTrue(LimitScope.GLOBAL)).thenReturn(List.of());

        RiskAssessmentResult result = strategy.assess(context(new BigDecimal("100.00"), BigDecimal.ZERO, 0));

        assertThat(result.riskLevel()).isEqualTo(RiskLevel.LOW);
        assertThat(result.blocked()).isFalse();
        assertThat(result.reasons()).isEmpty();
    }

    @Test
    void perTransactionLimitExceededBlocksAndIsHighRisk() {
        init();
        when(limitPolicyRepository.findByScopeAndActiveTrue(LimitScope.GLOBAL))
                .thenReturn(List.of(policy(LimitType.PER_TRANSACTION, new BigDecimal("10000.00"), null, null)));

        RiskAssessmentResult result = strategy.assess(context(new BigDecimal("15000.00"), BigDecimal.ZERO, 0));

        assertThat(result.blocked()).isTrue();
        assertThat(result.riskLevel()).isEqualTo(RiskLevel.HIGH);
        assertThat(result.reasons()).hasSize(1);
    }

    @Test
    void perTransactionLimitNotExceededStaysClean() {
        init();
        when(limitPolicyRepository.findByScopeAndActiveTrue(LimitScope.GLOBAL))
                .thenReturn(List.of(policy(LimitType.PER_TRANSACTION, new BigDecimal("10000.00"), null, null)));

        RiskAssessmentResult result = strategy.assess(context(new BigDecimal("500.00"), BigDecimal.ZERO, 0));

        assertThat(result.blocked()).isFalse();
        assertThat(result.riskLevel()).isEqualTo(RiskLevel.LOW);
    }

    @Test
    void dailyCumulativeLimitExceededFlagsMediumRiskButDoesNotBlock() {
        init();
        when(limitPolicyRepository.findByScopeAndActiveTrue(LimitScope.GLOBAL))
                .thenReturn(List.of(policy(LimitType.DAILY_CUMULATIVE, new BigDecimal("1000.00"), null, null)));

        RiskAssessmentResult result = strategy.assess(context(new BigDecimal("200.00"), new BigDecimal("900.00"), 0));

        assertThat(result.blocked()).isFalse();
        assertThat(result.riskLevel()).isEqualTo(RiskLevel.MEDIUM);
    }

    @Test
    void velocityLimitReachedFlagsMediumRisk() {
        init();
        when(limitPolicyRepository.findByScopeAndActiveTrue(LimitScope.GLOBAL))
                .thenReturn(List.of(policy(LimitType.VELOCITY_COUNT, null, 5, 10)));

        RiskAssessmentResult result = strategy.assess(context(new BigDecimal("50.00"), BigDecimal.ZERO, 5));

        assertThat(result.blocked()).isFalse();
        assertThat(result.riskLevel()).isEqualTo(RiskLevel.MEDIUM);
    }

    @Test
    void combinedRulesReportTheHighestSeverityAmongTriggeredRules() {
        init();
        when(limitPolicyRepository.findByScopeAndActiveTrue(LimitScope.GLOBAL)).thenReturn(List.of(
                policy(LimitType.VELOCITY_COUNT, null, 3, 10),
                policy(LimitType.PER_TRANSACTION, new BigDecimal("100.00"), null, null)
        ));

        RiskAssessmentResult result = strategy.assess(context(new BigDecimal("500.00"), BigDecimal.ZERO, 3));

        assertThat(result.blocked()).isTrue();
        assertThat(result.riskLevel()).isEqualTo(RiskLevel.HIGH);
        assertThat(result.reasons()).hasSize(2);
    }

    private TransferContext context(BigDecimal amount, BigDecimal todaysAmountSoFar, long recentTransferCount) {
        return new TransferContext(accountId, customerId, amount, "USD", todaysAmountSoFar, recentTransferCount);
    }

    private LimitPolicy policy(LimitType type, BigDecimal maxAmount, Integer maxCount, Integer windowMinutes) {
        LimitPolicy policy = new LimitPolicy();
        policy.setName("Test policy - " + type);
        policy.setScope(LimitScope.GLOBAL);
        policy.setLimitType(type);
        policy.setMaxAmount(maxAmount);
        policy.setMaxCount(maxCount);
        policy.setWindowMinutes(windowMinutes);
        policy.setActive(true);
        return policy;
    }
}
