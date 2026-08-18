package com.platform.transactions.service;

import com.platform.accounts.port.AccountLookupPort;
import com.platform.accounts.port.AccountSummary;
import com.platform.ledger.port.JournalLineSummary;
import com.platform.ledger.port.LedgerPostingPort;
import com.platform.risk.port.RiskAssessmentPort;
import com.platform.risk.port.RiskAssessmentSummary;
import com.platform.transactions.domain.Transaction;
import com.platform.transactions.domain.TransactionStatus;
import com.platform.transactions.dto.*;
import com.platform.transactions.mapper.TransactionMapper;
import com.platform.users.port.UserLookupPort;
import com.platform.users.port.UserSummary;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Transaction 360: the "central operational object" view. Deliberately just an
 * aggregator over existing modules' ports - transactions, risk, ledger, accounts,
 * users - not a new source of truth. Every field it returns is something one of
 * those modules already computed and persisted; this service's only job is
 * composing the reads (and translating a couple of raw IDs into display-friendly
 * names) into one response so an employee doesn't have to open five screens.
 * <p>
 * Read-only, no state changes - a request for this view can never itself cause a
 * side effect, unlike TransferService/ApprovalService.
 */
@Service
@RequiredArgsConstructor
public class Transaction360Service {

    private final TransferService transferService;
    private final AccountLookupPort accountLookupPort;
    private final UserLookupPort userLookupPort;
    private final RiskAssessmentPort riskAssessmentPort;
    private final LedgerPostingPort ledgerPostingPort;
    private final TransactionMapper transactionMapper;

    @Transactional(readOnly = true)
    public Transaction360Response getSummary(UUID transactionId) {
        Transaction transaction = transferService.getById(transactionId);

        AccountSummary sourceAccount = accountLookupPort.getById(transaction.getSourceAccountId());
        Optional<AccountSummary> destinationAccount = accountLookupPort.findByAccountNumber(transaction.getDestinationAccountNumber());

        String riskLevel = riskAssessmentPort.getRiskLevel(transactionId).orElse(null);

        return new Transaction360Response(
                transactionMapper.toResponse(transaction, riskLevel),
                toParty(transaction.getInitiatedByUserId()),
                toAccountResponse(sourceAccount),
                destinationAccount.map(this::toAccountResponse).orElse(null),
                toRiskResponse(riskAssessmentPort.getAssessment(transactionId)),
                toApprovalResponse(transaction),
                toLedgerLines(transactionId),
                transferService.getTimeline(transactionId).stream()
                        .map(transactionMapper::toTimelineEntry)
                        .toList()
        );
    }

    private TransactionPartyResponse toParty(UUID userId) {
        if (userId == null) {
            return null;
        }
        UserSummary user = userLookupPort.getById(userId);
        return new TransactionPartyResponse(user.id(), user.firstName(), user.email());
    }

    private TransactionAccountResponse toAccountResponse(AccountSummary account) {
        return new TransactionAccountResponse(
                account.id(),
                account.accountNumber(),
                account.balance(),
                account.status().name(),
                toParty(account.ownerUserId())
        );
    }

    private TransactionRiskResponse toRiskResponse(Optional<RiskAssessmentSummary> assessment) {
        return assessment
                .map(a -> new TransactionRiskResponse(a.riskLevel(), a.reasons(), a.blocked(), a.assessedAt()))
                .orElse(null);
    }

    /**
     * Not stored anywhere as its own field - derived from the same status the
     * transaction already settled on. REJECTED is unambiguous; any other
     * post-decision status (APPROVED, PROCESSING, COMPLETED, or a posting-time
     * FAILED) means the approval step itself was an approval, not a rejection.
     */
    private static final Set<TransactionStatus> APPROVED_OUTCOME_STATUSES = Set.of(
            TransactionStatus.APPROVED, TransactionStatus.PROCESSING, TransactionStatus.COMPLETED, TransactionStatus.FAILED);

    private TransactionApprovalResponse toApprovalResponse(Transaction transaction) {
        if (transaction.getApprovedByUserId() == null) {
            return null;
        }
        String decision = transaction.getStatus() == TransactionStatus.REJECTED ? "REJECTED"
                : APPROVED_OUTCOME_STATUSES.contains(transaction.getStatus()) ? "APPROVED"
                : null;
        return new TransactionApprovalResponse(
                toParty(transaction.getApprovedByUserId()),
                transaction.getApprovedAt(),
                decision
        );
    }

    private List<TransactionLedgerLineResponse> toLedgerLines(UUID transactionId) {
        return ledgerPostingPort.getJournalLinesForTransaction(transactionId).stream()
                .map(this::toLedgerLineResponse)
                .toList();
    }

    private TransactionLedgerLineResponse toLedgerLineResponse(JournalLineSummary line) {
        String accountNumber = null;
        if (line.accountId() != null) {
            try {
                accountNumber = accountLookupPort.getById(line.accountId()).accountNumber();
            } catch (RuntimeException ignored) {
                // Account lookup failing here would mean a data-integrity problem
                // in the ledger itself (a ledger line pointing at a bank account
                // that no longer resolves) - display-degrades to a null account
                // number rather than breaking the whole 360 view over it.
            }
        }
        return new TransactionLedgerLineResponse(
                line.accountId(), accountNumber, line.direction(), line.amount(), line.currency(), line.postedAt());
    }
}
