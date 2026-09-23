package com.platform.risk.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.risk.domain.LimitPolicy;
import com.platform.risk.domain.LimitScope;
import com.platform.risk.domain.LimitType;
import com.platform.risk.domain.RiskAssessment;
import com.platform.risk.port.RiskAssessmentPort;
import com.platform.risk.port.RiskAssessmentSummary;
import com.platform.risk.repository.LimitPolicyRepository;
import com.platform.risk.repository.RiskAssessmentRepository;
import com.platform.transactions.port.TransferHistoryPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class RiskAssessmentService implements RiskAssessmentPort {

    /** Used when no active VELOCITY_COUNT policy specifies its own window. */
    private static final int DEFAULT_VELOCITY_WINDOW_MINUTES = 10;

    private final RiskStrategy riskStrategy;
    private final RiskAssessmentRepository riskAssessmentRepository;
    private final LimitPolicyRepository limitPolicyRepository;
    private final TransferHistoryPort transferHistoryPort;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public RiskAssessmentResult assessAndRecord(UUID transactionId, UUID sourceAccountId, UUID customerUserId,
                                                 BigDecimal amount, String currency) {
        int velocityWindow = resolveVelocityWindowMinutes();

        TransferContext context = new TransferContext(
                sourceAccountId,
                customerUserId,
                amount,
                currency,
                transferHistoryPort.sumTodaysAmountFromAccount(sourceAccountId),
                transferHistoryPort.countRecentTransfersFromAccount(sourceAccountId, velocityWindow)
        );

        RiskAssessmentResult result = riskStrategy.assess(context);

        RiskAssessment assessment = new RiskAssessment();
        assessment.setTransactionId(transactionId);
        assessment.setRiskLevel(result.riskLevel());
        assessment.setBlocked(result.blocked());
        assessment.setReasons(serializeReasons(result.reasons()));
        riskAssessmentRepository.save(assessment);

        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<String> getRiskLevel(UUID transactionId) {
        return riskAssessmentRepository.findByTransactionId(transactionId).map(a -> a.getRiskLevel().name());
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<RiskAssessmentSummary> getAssessment(UUID transactionId) {
        return riskAssessmentRepository.findByTransactionId(transactionId)
                .map(this::toSummary);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, RiskAssessmentSummary> getAssessments(Collection<UUID> transactionIds) {
        if (transactionIds.isEmpty()) {
            return Map.of();
        }
        return riskAssessmentRepository.findByTransactionIdIn(transactionIds).stream()
                .collect(Collectors.toMap(RiskAssessment::getTransactionId, this::toSummary));
    }

    private RiskAssessmentSummary toSummary(RiskAssessment a) {
        return new RiskAssessmentSummary(
                a.getRiskLevel().name(),
                deserializeReasons(a.getReasons()),
                a.isBlocked(),
                a.getAssessedAt());
    }

    private List<String> deserializeReasons(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            log.warn("Failed to deserialize risk assessment reasons, returning empty list", e);
            return List.of();
        }
    }

    private int resolveVelocityWindowMinutes() {
        return limitPolicyRepository.findByScopeAndActiveTrue(LimitScope.GLOBAL).stream()
                .filter(p -> p.getLimitType() == LimitType.VELOCITY_COUNT && p.getWindowMinutes() != null)
                .map(LimitPolicy::getWindowMinutes)
                .findFirst()
                .orElse(DEFAULT_VELOCITY_WINDOW_MINUTES);
    }

    private String serializeReasons(List<String> reasons) {
        if (reasons.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(reasons);
        } catch (Exception e) {
            log.warn("Failed to serialize risk assessment reasons, storing null", e);
            return null;
        }
    }
}
