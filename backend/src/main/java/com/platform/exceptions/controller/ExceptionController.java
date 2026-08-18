package com.platform.exceptions.controller;

import com.platform.auth.security.CurrentUserProvider;
import com.platform.exceptions.domain.ExceptionNote;
import com.platform.exceptions.domain.ExceptionStatus;
import com.platform.exceptions.domain.TransactionException;
import com.platform.exceptions.dto.*;
import com.platform.exceptions.mapper.ExceptionMapper;
import com.platform.exceptions.service.ExceptionService;
import com.platform.transactions.port.TransactionLookupPort;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Every endpoint here requires the EMPLOYEE role - this is an internal
 * operations tool, unlike {@code TransactionController} which customers also
 * use. Matches the existing codebase convention (roles are flat, not
 * hierarchical - see EmployeeAccountController/EmployeeCustomerController)
 * rather than also granting ADMIN, which doesn't automatically inherit
 * EMPLOYEE's permissions here.
 * <p>
 * {@link #byTransaction} is how Transaction 360 (in the `transactions` module)
 * links forward to an exception without `transactions` depending on
 * `exceptions` - the dependency only ever runs one way (`exceptions` depends on
 * `transactions`, via {@link TransactionLookupPort}/TransactionRetryPort), so
 * the frontend composes the two independent reads at the page level instead of
 * the backend introducing a cycle to embed one inside the other.
 */
@RestController
@RequestMapping("/api/v1/exceptions")
@RequiredArgsConstructor
@PreAuthorize("hasRole('EMPLOYEE')")
public class ExceptionController {

    private final ExceptionService exceptionService;
    private final ExceptionMapper exceptionMapper;
    private final TransactionLookupPort transactionLookupPort;
    private final CurrentUserProvider currentUserProvider;

    @GetMapping
    public List<TransactionExceptionResponse> list(@RequestParam(required = false) List<ExceptionStatus> status) {
        return exceptionService.list(status).stream().map(this::toResponseWithTransaction).toList();
    }

    @GetMapping("/mine")
    public List<TransactionExceptionResponse> mine() {
        UUID currentUserId = currentUserProvider.get().userId();
        return exceptionService.listMine(currentUserId).stream().map(this::toResponseWithTransaction).toList();
    }

    @GetMapping("/by-transaction/{transactionId}")
    public TransactionExceptionResponse byTransaction(@PathVariable UUID transactionId) {
        return exceptionService.findLatestByTransaction(transactionId)
                .map(this::toResponseWithTransaction)
                .orElse(null);
    }

    @GetMapping("/{id}")
    public ExceptionDetailResponse getOne(@PathVariable UUID id) {
        TransactionException exception = exceptionService.getOrThrow(id);
        List<ExceptionNote> notes = exceptionService.getNotes(id);
        var transaction = transactionLookupPort.getById(exception.getTransactionId());
        return new ExceptionDetailResponse(
                exceptionMapper.toResponse(exception, transaction),
                notes.stream().map(exceptionMapper::toNoteResponse).toList(),
                exceptionMapper.toLinkedTransaction(transaction)
        );
    }

    private TransactionExceptionResponse toResponseWithTransaction(TransactionException exception) {
        return exceptionMapper.toResponse(exception, transactionLookupPort.getById(exception.getTransactionId()));
    }

    @PostMapping("/{id}/assign")
    public TransactionExceptionResponse assign(@PathVariable UUID id, @Valid @RequestBody AssignExceptionRequest request) {
        UUID actorUserId = currentUserProvider.get().userId();
        return toResponseWithTransaction(exceptionService.assign(id, request.assigneeUserId(), actorUserId));
    }

    @PostMapping("/{id}/start-investigating")
    public TransactionExceptionResponse startInvestigating(@PathVariable UUID id) {
        UUID actorUserId = currentUserProvider.get().userId();
        return toResponseWithTransaction(exceptionService.startInvestigating(id, actorUserId));
    }

    @PostMapping("/{id}/request-information")
    public TransactionExceptionResponse requestInformation(@PathVariable UUID id, @Valid @RequestBody ExceptionNoteRequest request) {
        UUID actorUserId = currentUserProvider.get().userId();
        return toResponseWithTransaction(exceptionService.requestInformation(id, actorUserId, request.content()));
    }

    @PostMapping("/{id}/resolve")
    public TransactionExceptionResponse resolve(@PathVariable UUID id, @Valid @RequestBody ExceptionNoteRequest request) {
        UUID actorUserId = currentUserProvider.get().userId();
        return toResponseWithTransaction(exceptionService.resolve(id, actorUserId, request.content()));
    }

    @PostMapping("/{id}/close")
    public TransactionExceptionResponse close(@PathVariable UUID id, @Valid @RequestBody(required = false) OptionalNoteRequest request) {
        UUID actorUserId = currentUserProvider.get().userId();
        return toResponseWithTransaction(exceptionService.close(id, actorUserId, request == null ? null : request.content()));
    }

    @PostMapping("/{id}/escalate")
    public TransactionExceptionResponse escalate(@PathVariable UUID id, @Valid @RequestBody(required = false) OptionalNoteRequest request) {
        UUID actorUserId = currentUserProvider.get().userId();
        return toResponseWithTransaction(exceptionService.escalate(id, actorUserId, request == null ? null : request.content()));
    }

    @PostMapping("/{id}/retry")
    public TransactionExceptionResponse retry(@PathVariable UUID id) {
        UUID actorUserId = currentUserProvider.get().userId();
        return toResponseWithTransaction(exceptionService.retry(id, actorUserId));
    }

    @PostMapping("/{id}/notes")
    public ExceptionNoteResponse addNote(@PathVariable UUID id, @Valid @RequestBody ExceptionNoteRequest request) {
        UUID actorUserId = currentUserProvider.get().userId();
        return exceptionMapper.toNoteResponse(exceptionService.addNote(id, actorUserId, request.content()));
    }
}
