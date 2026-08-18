package com.platform.transactions.service;

import com.platform.accounts.domain.AccountStatus;
import com.platform.accounts.port.AccountLookupPort;
import com.platform.accounts.port.AccountSummary;
import com.platform.risk.port.RiskAssessmentPort;
import com.platform.risk.service.RiskAssessmentResult;
import com.platform.shared.exception.ConflictException;
import com.platform.shared.exception.ResourceNotFoundException;
import com.platform.transactions.domain.Transaction;
import com.platform.transactions.domain.TransactionStatus;
import com.platform.transactions.repository.TransactionRepository;
import com.platform.transactions.repository.TransactionStateHistoryRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransferServiceTest {

    @Mock
    private TransactionRepository transactionRepository;
    @Mock
    private TransactionStateHistoryRepository transactionStateHistoryRepository;
    @Mock
    private AccountLookupPort accountLookupPort;
    @Mock
    private RiskAssessmentPort riskAssessmentPort;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    private TransferService transferService;

    private final UUID customerId = UUID.randomUUID();
    private final UUID sourceAccountId = UUID.randomUUID();
    private final UUID destinationAccountId = UUID.randomUUID();

    private void init() {
        // See ApprovalServiceTest for why this CAS stub is here: every real
        // transition() goes through it, and its own concurrency behavior is
        // covered separately by TransactionStateMachineTest.
        lenient().when(transactionRepository.compareAndSetStatus(any(), any(), any())).thenReturn(1);

        TransactionStateMachine transactionStateMachine =
                new TransactionStateMachine(transactionRepository, transactionStateHistoryRepository, eventPublisher);

        transferService = new TransferService(
                transactionRepository, transactionStateHistoryRepository, accountLookupPort, riskAssessmentPort,
                transactionStateMachine, eventPublisher, new SimpleMeterRegistry());
    }

    private void stubCleanRiskAssessment() {
        when(riskAssessmentPort.assessAndRecord(any(), any(), any(), any(), any()))
                .thenReturn(RiskAssessmentResult.clean());
    }

    @Test
    void replayingTheSameIdempotencyKeyReturnsTheExistingTransactionWithoutCreatingAnother() {
        init();
        Transaction existing = new Transaction();
        existing.setId(UUID.randomUUID());
        existing.setStatus(TransactionStatus.PENDING_APPROVAL);
        when(transactionRepository.findByIdempotencyKeyAndInitiatedByUserId("idem-1", customerId))
                .thenReturn(Optional.of(existing));

        Transaction result = transferService.createTransfer(
                customerId, sourceAccountId, "1000000099", new BigDecimal("50.00"), "idem-1", null);

        assertThat(result).isSameAs(existing);
        verify(transactionRepository, never()).save(any());
        verifyNoInteractions(accountLookupPort);
        verifyNoInteractions(riskAssessmentPort);
    }

    @Test
    void createsATransferAwaitingApprovalWhenAccountsAreValidAndRiskIsClean() {
        init();
        stubCleanRiskAssessment();
        when(transactionRepository.findByIdempotencyKeyAndInitiatedByUserId("idem-2", customerId))
                .thenReturn(Optional.empty());
        when(accountLookupPort.getById(sourceAccountId))
                .thenReturn(new AccountSummary(sourceAccountId, customerId, "1000000001", new BigDecimal("500.00"), AccountStatus.ACTIVE));
        when(accountLookupPort.findByAccountNumber("1000000099"))
                .thenReturn(Optional.of(new AccountSummary(destinationAccountId, UUID.randomUUID(), "1000000099", BigDecimal.ZERO, AccountStatus.ACTIVE)));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(inv -> inv.getArgument(0));

        Transaction result = transferService.createTransfer(
                customerId, sourceAccountId, "1000000099", new BigDecimal("50.00"), "idem-2", "rent");

        assertThat(result.getStatus()).isEqualTo(TransactionStatus.PENDING_APPROVAL);
        assertThat(result.getInitiatedByUserId()).isEqualTo(customerId);
    }

    @Test
    void aBlockedRiskAssessmentFailsTheTransferInstead() {
        init();
        when(riskAssessmentPort.assessAndRecord(any(), any(), any(), any(), any()))
                .thenReturn(new RiskAssessmentResult(
                        com.platform.risk.domain.RiskLevel.HIGH, true, List.of("Amount exceeds per-transaction limit")));
        when(transactionRepository.findByIdempotencyKeyAndInitiatedByUserId("idem-5", customerId))
                .thenReturn(Optional.empty());
        when(accountLookupPort.getById(sourceAccountId))
                .thenReturn(new AccountSummary(sourceAccountId, customerId, "1000000001", new BigDecimal("500.00"), AccountStatus.ACTIVE));
        when(accountLookupPort.findByAccountNumber("1000000099"))
                .thenReturn(Optional.of(new AccountSummary(destinationAccountId, UUID.randomUUID(), "1000000099", BigDecimal.ZERO, AccountStatus.ACTIVE)));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(inv -> inv.getArgument(0));

        Transaction result = transferService.createTransfer(
                customerId, sourceAccountId, "1000000099", new BigDecimal("999999.00"), "idem-5", null);

        assertThat(result.getStatus()).isEqualTo(TransactionStatus.FAILED);
        assertThat(result.getNote()).contains("Blocked by risk check");
    }

    @Test
    void rejectsTransferWhereSourceAndDestinationAreTheSameAccount() {
        init();
        when(transactionRepository.findByIdempotencyKeyAndInitiatedByUserId("idem-3", customerId))
                .thenReturn(Optional.empty());
        when(accountLookupPort.getById(sourceAccountId))
                .thenReturn(new AccountSummary(sourceAccountId, customerId, "1000000001", new BigDecimal("500.00"), AccountStatus.ACTIVE));
        when(accountLookupPort.findByAccountNumber("1000000001"))
                .thenReturn(Optional.of(new AccountSummary(sourceAccountId, customerId, "1000000001", new BigDecimal("500.00"), AccountStatus.ACTIVE)));

        assertThatThrownBy(() -> transferService.createTransfer(
                customerId, sourceAccountId, "1000000001", new BigDecimal("50.00"), "idem-3", null))
                .isInstanceOf(ConflictException.class);
        verifyNoInteractions(riskAssessmentPort);
    }

    @Test
    void rejectsTransferWhenDestinationAccountDoesNotExist() {
        init();
        when(transactionRepository.findByIdempotencyKeyAndInitiatedByUserId("idem-4", customerId))
                .thenReturn(Optional.empty());
        when(accountLookupPort.getById(sourceAccountId))
                .thenReturn(new AccountSummary(sourceAccountId, customerId, "1000000001", new BigDecimal("500.00"), AccountStatus.ACTIVE));
        when(accountLookupPort.findByAccountNumber("0000000000")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> transferService.createTransfer(
                customerId, sourceAccountId, "0000000000", new BigDecimal("50.00"), "idem-4", null))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
