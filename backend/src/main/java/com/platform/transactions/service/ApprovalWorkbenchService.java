package com.platform.transactions.service;

import com.platform.risk.port.RiskAssessmentPort;
import com.platform.risk.port.RiskAssessmentSummary;
import com.platform.transactions.domain.Transaction;
import com.platform.transactions.domain.TransactionStatus;
import com.platform.transactions.dto.ApprovalSlaSummaryResponse;
import com.platform.transactions.dto.ApprovalWorkbenchItemResponse;
import com.platform.transactions.dto.TransactionPartyResponse;
import com.platform.transactions.repository.TransactionRepository;
import com.platform.users.port.UserLookupPort;
import com.platform.users.port.UserSummary;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Turns the plain pending queue ({@code TransferService.listPending()}) into
 * what an approver actually needs: why each item is here, how it compares to
 * the customer's usual activity, and its SLA. Pure aggregator, same spirit as
 * {@code Transaction360Service} - no new business rule about *whether* a
 * transfer needs approval (every transfer does, unconditionally, per this
 * system's maker-checker design), only about *explaining* and *prioritizing*
 * what's already in the queue.
 */
@Service
@RequiredArgsConstructor
public class ApprovalWorkbenchService {

    private static final double NOTABLE_AMOUNT_RATIO = 2.0;
    private static final double AT_RISK_REMAINING_FRACTION = 0.2;

    private final TransferService transferService;
    private final TransactionRepository transactionRepository;
    private final RiskAssessmentPort riskAssessmentPort;
    private final UserLookupPort userLookupPort;

    @Transactional(readOnly = true)
    public List<ApprovalWorkbenchItemResponse> listQueue() {
        return transferService.listPending().stream().map(this::toWorkbenchItem).toList();
    }

    @Transactional(readOnly = true)
    public ApprovalSlaSummaryResponse slaSummary() {
        List<Transaction> pending = transferService.listPending();
        long within = 0;
        long atRisk = 0;
        long breached = 0;
        for (Transaction transaction : pending) {
            switch (computeSlaStatus(transaction)) {
                case "AT_RISK" -> atRisk++;
                case "BREACHED" -> breached++;
                default -> within++;
            }
        }
        return new ApprovalSlaSummaryResponse(within, atRisk, breached, pending.size());
    }

    private ApprovalWorkbenchItemResponse toWorkbenchItem(Transaction transaction) {
        Optional<RiskAssessmentSummary> risk = riskAssessmentPort.getAssessment(transaction.getId());
        BigDecimal customerAverage = transactionRepository
                .findAverageAmountByStatus(transaction.getSourceAccountId(), TransactionStatus.COMPLETED, transaction.getId())
                .orElse(null);
        Double ratio = (customerAverage != null && customerAverage.compareTo(BigDecimal.ZERO) > 0)
                ? transaction.getAmount().doubleValue() / customerAverage.doubleValue()
                : null;

        return new ApprovalWorkbenchItemResponse(
                transaction.getId(),
                transaction.getAmount(),
                transaction.getCurrency(),
                transaction.getDestinationAccountNumber(),
                toParty(transaction.getInitiatedByUserId()),
                risk.map(RiskAssessmentSummary::riskLevel).orElse(null),
                risk.map(RiskAssessmentSummary::reasons).orElse(List.of()),
                buildWhyApprovalRequired(risk.orElse(null), ratio),
                customerAverage,
                ratio,
                transaction.getCreatedAt(),
                transaction.getSlaDueAt(),
                computeSlaStatus(transaction),
                transaction.getNote()
        );
    }

    private List<String> buildWhyApprovalRequired(RiskAssessmentSummary risk, Double amountToAverageRatio) {
        List<String> reasons = new ArrayList<>();
        reasons.add("All customer transfers require employee approval before funds move (maker-checker control).");
        if (risk != null) {
            reasons.addAll(risk.reasons());
        }
        if (amountToAverageRatio != null && amountToAverageRatio >= NOTABLE_AMOUNT_RATIO) {
            reasons.add(String.format("This transfer is %.1f\u00d7 this customer's average completed transfer amount.", amountToAverageRatio));
        }
        return reasons;
    }

    /**
     * The total SLA window isn't stored separately - it's reconstructed from
     * createdAt -> slaDueAt (set together, moments apart, at creation), which is
     * accurate enough for a multi-hour-to-multi-day window without needing a
     * second stored field.
     */
    private String computeSlaStatus(Transaction transaction) {
        if (transaction.getSlaDueAt() == null) {
            return "WITHIN";
        }
        Instant now = Instant.now();
        if (now.isAfter(transaction.getSlaDueAt())) {
            return "BREACHED";
        }
        Duration totalWindow = Duration.between(transaction.getCreatedAt(), transaction.getSlaDueAt());
        Duration remaining = Duration.between(now, transaction.getSlaDueAt());
        if (totalWindow.isZero() || totalWindow.isNegative()) {
            return "WITHIN";
        }
        double remainingFraction = (double) remaining.toMillis() / (double) totalWindow.toMillis();
        return remainingFraction <= AT_RISK_REMAINING_FRACTION ? "AT_RISK" : "WITHIN";
    }

    private TransactionPartyResponse toParty(UUID userId) {
        if (userId == null) {
            return null;
        }
        UserSummary user = userLookupPort.getById(userId);
        return new TransactionPartyResponse(user.id(), user.firstName(), user.email());
    }
}
