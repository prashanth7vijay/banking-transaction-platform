package com.platform.transactions.service;

import com.platform.accounts.domain.AccountStatus;
import com.platform.accounts.exception.InsufficientFundsException;
import com.platform.accounts.port.AccountLookupPort;
import com.platform.accounts.port.AccountSummary;
import com.platform.ledger.port.LedgerPostingPort;
import com.platform.shared.exception.ApiException;
import com.platform.shared.exception.ConflictException;
import com.platform.transactions.domain.Transaction;
import com.platform.transactions.domain.TransactionStatus;
import com.platform.transactions.repository.TransactionRepository;
import com.platform.transactions.repository.TransactionStateHistoryRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ApprovalServiceTest {

    @Mock
    private TransactionRepository transactionRepository;
    @Mock
    private TransactionStateHistoryRepository transactionStateHistoryRepository;
    @Mock
    private AccountLookupPort accountLookupPort;
    @Mock
    private LedgerPostingPort ledgerPostingPort;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    private ApprovalService approvalService;

    private final UUID customerId = UUID.randomUUID();
    private final UUID employeeId = UUID.randomUUID();
    private final UUID sourceAccountId = UUID.randomUUID();
    private final UUID destinationAccountId = UUID.randomUUID();
    private final UUID transactionId = UUID.randomUUID();

    private Transaction pendingTransaction;

    @BeforeEach
    void setUp() {
        // Every real transition() call in ApprovalService goes through this CAS -
        // stub it to always "win" (as if nothing else is racing it) so the tests
        // stay focused on approval logic, not the concurrency guard itself (that
        // guard has its own coverage in TransactionStateMachineTest).
        lenient().when(transactionRepository.compareAndSetStatus(any(), any(), any())).thenReturn(1);

        TransactionStateMachine transactionStateMachine =
                new TransactionStateMachine(transactionRepository, transactionStateHistoryRepository, eventPublisher);

        approvalService = new ApprovalService(
                transactionRepository, accountLookupPort, ledgerPostingPort, transactionStateMachine,
                eventPublisher, new SimpleMeterRegistry());

        pendingTransaction = new Transaction();
        pendingTransaction.setId(transactionId);
        pendingTransaction.setSourceAccountId(sourceAccountId);
        pendingTransaction.setDestinationAccountNumber("1000000099");
        pendingTransaction.setAmount(new BigDecimal("100.00"));
        pendingTransaction.setInitiatedByUserId(customerId);
        pendingTransaction.setStatus(TransactionStatus.PENDING_APPROVAL);

        when(transactionRepository.save(any(Transaction.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void approveCompletesTransactionWhenFundsAreSufficient() {
        when(transactionRepository.findById(transactionId)).thenReturn(Optional.of(pendingTransaction));
        when(accountLookupPort.findByAccountNumber("1000000099"))
                .thenReturn(Optional.of(new AccountSummary(destinationAccountId, UUID.randomUUID(), "1000000099", BigDecimal.ZERO, AccountStatus.ACTIVE)));

        Transaction result = approvalService.approve(employeeId, transactionId);

        assertThat(result.getStatus()).isEqualTo(TransactionStatus.COMPLETED);
        assertThat(result.getApprovedByUserId()).isEqualTo(employeeId);
        verify(accountLookupPort).debit(sourceAccountId, new BigDecimal("100.00"));
        verify(accountLookupPort).credit(destinationAccountId, new BigDecimal("100.00"));
        verify(ledgerPostingPort).postTransfer(
                eq(transactionId), eq(sourceAccountId), eq(destinationAccountId), eq(new BigDecimal("100.00")), any(), any());
    }

    @Test
    void approveMarksTransactionFailedWhenFundsAreInsufficient() {
        when(transactionRepository.findById(transactionId)).thenReturn(Optional.of(pendingTransaction));
        doThrow(new InsufficientFundsException("Insufficient funds"))
                .when(accountLookupPort).debit(eq(sourceAccountId), any());

        Transaction result = approvalService.approve(employeeId, transactionId);

        assertThat(result.getStatus()).isEqualTo(TransactionStatus.FAILED);
        verify(accountLookupPort, never()).credit(any(), any());
        verify(ledgerPostingPort, never()).postTransfer(any(), any(), any(), any(), any(), any());
    }

    @Test
    void approveRejectsSelfApproval() {
        pendingTransaction.setInitiatedByUserId(employeeId);
        when(transactionRepository.findById(transactionId)).thenReturn(Optional.of(pendingTransaction));

        assertThatThrownBy(() -> approvalService.approve(employeeId, transactionId))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getStatus()).isEqualTo(HttpStatus.FORBIDDEN));

        verify(accountLookupPort, never()).debit(any(), any());
    }

    @Test
    void rejectRejectsSelfApproval() {
        pendingTransaction.setInitiatedByUserId(employeeId);
        when(transactionRepository.findById(transactionId)).thenReturn(Optional.of(pendingTransaction));

        assertThatThrownBy(() -> approvalService.reject(employeeId, transactionId, "no reason"))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void approveRejectsAlreadyDecidedTransaction() {
        pendingTransaction.setStatus(TransactionStatus.COMPLETED);
        when(transactionRepository.findById(transactionId)).thenReturn(Optional.of(pendingTransaction));

        assertThatThrownBy(() -> approvalService.approve(employeeId, transactionId))
                .isInstanceOf(ConflictException.class);
    }
}
