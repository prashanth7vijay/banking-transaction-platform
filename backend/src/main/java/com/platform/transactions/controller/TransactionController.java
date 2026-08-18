package com.platform.transactions.controller;

import com.platform.accounts.port.AccountLookupPort;
import com.platform.accounts.port.AccountSummary;
import com.platform.auth.security.AuthenticatedUser;
import com.platform.auth.security.CurrentUserProvider;
import com.platform.risk.port.RiskAssessmentPort;
import com.platform.shared.exception.UnauthorizedException;
import com.platform.transactions.domain.Transaction;
import com.platform.transactions.domain.TransactionStatus;
import com.platform.transactions.dto.ApprovalSlaSummaryResponse;
import com.platform.transactions.dto.ApprovalWorkbenchItemResponse;
import com.platform.transactions.dto.CreateTransferRequest;
import com.platform.transactions.dto.RejectTransactionRequest;
import com.platform.transactions.dto.Transaction360Response;
import com.platform.transactions.dto.TransactionResponse;
import com.platform.transactions.dto.TransactionTimelineEntryResponse;
import com.platform.transactions.mapper.TransactionMapper;
import com.platform.transactions.service.ApprovalService;
import com.platform.transactions.service.ApprovalWorkbenchService;
import com.platform.transactions.service.Transaction360Service;
import com.platform.transactions.service.TransferService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/transactions")
@RequiredArgsConstructor
public class TransactionController {

    private final TransferService transferService;
    private final ApprovalService approvalService;
    private final Transaction360Service transaction360Service;
    private final ApprovalWorkbenchService approvalWorkbenchService;
    private final TransactionMapper transactionMapper;
    private final CurrentUserProvider currentUserProvider;
    private final AccountLookupPort accountLookupPort;
    private final RiskAssessmentPort riskAssessmentPort;

    @PostMapping("/transfer")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<TransactionResponse> createTransfer(@Valid @RequestBody CreateTransferRequest request) {
        UUID userId = currentUserProvider.get().userId();
        Transaction transaction = transferService.createTransfer(
                userId, request.sourceAccountId(), request.destinationAccountNumber(),
                request.amount(), request.idempotencyKey(), request.note());
        return ResponseEntity.status(HttpStatus.CREATED).body(transactionMapper.toResponse(transaction));
    }

    @GetMapping("/mine")
    @PreAuthorize("hasRole('CUSTOMER')")
    public List<TransactionResponse> mine() {
        UUID userId = currentUserProvider.get().userId();
        List<UUID> ownedAccountIds = accountLookupPort.findByOwnerUserId(userId).stream()
                .map(AccountSummary::id)
                .toList();
        return transferService.listMine(userId, ownedAccountIds).stream()
                .map(transactionMapper::toResponse)
                .toList();
    }

    /**
     * The one list endpoint enriched with risk level - this is where an employee
     * actually needs it to make an approve/reject decision. Kept off `mine` and
     * the write endpoints deliberately, to keep this phase's surface small.
     */
    @GetMapping("/pending")
    @PreAuthorize("hasRole('EMPLOYEE')")
    public List<TransactionResponse> pending() {
        return transferService.listPending().stream()
                .map(tx -> transactionMapper.toResponse(tx, riskAssessmentPort.getRiskLevel(tx.getId()).orElse(null)))
                .toList();
    }

    /**
     * The Approval Workbench: the same queue as {@link #pending}, enriched with
     * why each item is here, how it compares to the customer's usual activity,
     * and its SLA - everything an approver needs without navigating away.
     * {@link #pending} stays untouched for anything else that depends on its
     * plain shape.
     */
    @GetMapping("/pending/workbench")
    @PreAuthorize("hasRole('EMPLOYEE')")
    public List<ApprovalWorkbenchItemResponse> pendingWorkbench() {
        return approvalWorkbenchService.listQueue();
    }

    /** Team-level SLA compliance across the current queue, for a manager's view. */
    @GetMapping("/pending/sla-summary")
    @PreAuthorize("hasRole('EMPLOYEE')")
    public ApprovalSlaSummaryResponse pendingSlaSummary() {
        return approvalWorkbenchService.slaSummary();
    }

    /**
     * A generic status-filtered view for employees - what the Operations Command
     * Center's metric cards link out to (e.g. clicking "12 Failed Transactions"
     * lands here with {@code ?status=FAILED}).
     */
    @GetMapping("/by-status")
    @PreAuthorize("hasRole('EMPLOYEE')")
    public List<TransactionResponse> byStatus(@RequestParam TransactionStatus status) {
        return transferService.listByStatus(status).stream()
                .map(tx -> transactionMapper.toResponse(tx, riskAssessmentPort.getRiskLevel(tx.getId()).orElse(null)))
                .toList();
    }

    @GetMapping("/{id}")
    public TransactionResponse getOne(@PathVariable UUID id) {
        var current = currentUserProvider.get();
        Transaction transaction = transferService.getById(id);
        assertAccessible(current, transaction);
        return transactionMapper.toResponse(transaction, riskAssessmentPort.getRiskLevel(id).orElse(null));
    }

    /**
     * The full state-transition history for one transaction (Phase 4). Same
     * ownership check as {@link #getOne}: an employee/admin can view any
     * transaction's timeline, a customer only their own. Superseded for UI
     * purposes by {@link #summary360} below, which includes this same data
     * alongside everything else - kept as its own endpoint since it's a small,
     * independently useful read.
     */
    @GetMapping("/{id}/timeline")
    public List<TransactionTimelineEntryResponse> timeline(@PathVariable UUID id) {
        var current = currentUserProvider.get();
        Transaction transaction = transferService.getById(id);
        assertAccessible(current, transaction);

        return transferService.getTimeline(id).stream()
                .map(transactionMapper::toTimelineEntry)
                .toList();
    }

    /**
     * Transaction 360: the single-screen operational view - summary, lifecycle,
     * risk explainability, approval decision, ledger postings, and full timeline,
     * composed from `transactions`, `risk`, `ledger`, `accounts`, and `users` in
     * one read. Same ownership check as {@link #getOne}/{@link #timeline}.
     */
    @GetMapping("/{id}/360")
    public Transaction360Response summary360(@PathVariable UUID id) {
        var current = currentUserProvider.get();
        Transaction transaction = transferService.getById(id);
        assertAccessible(current, transaction);

        return transaction360Service.getSummary(id);
    }

    private void assertAccessible(AuthenticatedUser current, Transaction transaction) {
        boolean isEmployeeOrAdmin = current.roles().contains("EMPLOYEE") || current.roles().contains("ADMIN");
        if (!isEmployeeOrAdmin) {
            var source = accountLookupPort.getById(transaction.getSourceAccountId());
            if (!source.ownerUserId().equals(current.userId())) {
                throw new UnauthorizedException("You do not have access to this transaction");
            }
        }
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasRole('EMPLOYEE')")
    public TransactionResponse approve(@PathVariable UUID id) {
        UUID employeeId = currentUserProvider.get().userId();
        return transactionMapper.toResponse(approvalService.approve(employeeId, id));
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasRole('EMPLOYEE')")
    public TransactionResponse reject(@PathVariable UUID id, @Valid @RequestBody(required = false) RejectTransactionRequest request) {
        UUID employeeId = currentUserProvider.get().userId();
        String reason = request != null ? request.reason() : null;
        return transactionMapper.toResponse(approvalService.reject(employeeId, id, reason));
    }
}
