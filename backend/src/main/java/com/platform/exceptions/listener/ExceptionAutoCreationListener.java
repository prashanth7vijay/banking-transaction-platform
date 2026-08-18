package com.platform.exceptions.listener;

import com.platform.exceptions.service.ExceptionService;
import com.platform.transactions.domain.TransactionStatus;
import com.platform.transactions.event.TransactionStateChangedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * The one thing that connects `transactions` to `exceptions`, and it's a
 * one-directional listener, not a call `transactions` makes - `transactions`
 * has no idea this module exists. Subscribes to {@code TransactionStateChangedEvent}
 * (Phase 4's per-edge event, published on every transition but unused until
 * now) rather than the older {@code TransactionStatusChangedEvent}, since only
 * the richer event carries {@code fromStatus} - which path a transaction
 * failed from (blocked by risk vs. failed at posting) is exactly what
 * {@code ExceptionService} needs to write an accurate reason and priority.
 * <p>
 * Plain synchronous {@code @EventListener}, same convention as
 * AuditEventListener/NotificationEventListener - runs in the same DB
 * transaction as the transition that triggered it. If exception creation
 * failed, that would roll back the transition too; this is an accepted
 * trade-off already made everywhere else events are used in this codebase, not
 * a new one introduced here.
 */
@Component
@RequiredArgsConstructor
public class ExceptionAutoCreationListener {

    private final ExceptionService exceptionService;

    @EventListener
    public void onTransactionStateChanged(TransactionStateChangedEvent event) {
        if (event.toStatus() != TransactionStatus.FAILED) {
            return;
        }
        exceptionService.createFromFailedTransaction(event.transactionId(), event.fromStatus(), event.actorUserId());
    }
}
