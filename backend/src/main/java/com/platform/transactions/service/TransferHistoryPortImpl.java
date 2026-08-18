package com.platform.transactions.service;

import com.platform.transactions.domain.TransactionStatus;
import com.platform.transactions.port.TransferHistoryPort;
import com.platform.transactions.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TransferHistoryPortImpl implements TransferHistoryPort {

    private final TransactionRepository transactionRepository;

    /**
     * "In flight or completed" - every non-terminal-failed status plus COMPLETED,
     * so a customer can't dodge a daily cap by submitting many transfers before
     * any of them clear the approval queue. Widened from the original two-value
     * list (PENDING, COMPLETED) now that the lifecycle has more in-flight states
     * (Phase 4) - REJECTED/FAILED/CANCELLED are deliberately excluded, since money
     * that never moved (or moved back) shouldn't count against the cap.
     */
    private static final List<TransactionStatus> COUNTS_TOWARD_DAILY_CAP = List.of(
            TransactionStatus.SUBMITTED,
            TransactionStatus.PENDING_APPROVAL,
            TransactionStatus.APPROVED,
            TransactionStatus.PROCESSING,
            TransactionStatus.COMPLETED
    );

    @Override
    @Transactional(readOnly = true)
    public BigDecimal sumTodaysAmountFromAccount(UUID accountId) {
        Instant startOfDay = LocalDate.now().atStartOfDay(ZoneOffset.UTC).toInstant();
        return transactionRepository.sumAmountFromAccountSince(accountId, startOfDay, COUNTS_TOWARD_DAILY_CAP);
    }

    @Override
    @Transactional(readOnly = true)
    public long countRecentTransfersFromAccount(UUID accountId, int windowMinutes) {
        Instant since = Instant.now().minusSeconds(windowMinutes * 60L);
        return transactionRepository.countBySourceAccountIdAndCreatedAtAfter(accountId, since);
    }
}
