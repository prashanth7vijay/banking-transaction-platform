package com.platform.audit.listener;

import com.platform.accounts.event.AccountOpenedEvent;
import com.platform.audit.service.AuditService;
import com.platform.auth.event.AuthActionEvent;
import com.platform.transactions.event.TransactionStatusChangedEvent;
import com.platform.users.event.UserActionEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class AuditEventListener {

    private final AuditService auditService;

    @EventListener
    public void onAuthAction(AuthActionEvent event) {
        auditService.record(
                event.userId(),
                event.action(),
                "USER",
                event.userId().toString(),
                Map.of("email", event.email())
        );
    }

    @EventListener
    public void onUserAction(UserActionEvent event) {
        auditService.record(
                event.userId(),
                event.action(),
                "USER",
                event.userId().toString(),
                Map.of()
        );
    }

    @EventListener
    public void onTransactionStatusChanged(TransactionStatusChangedEvent event) {
        auditService.record(
                event.actorUserId(),
                "TRANSACTION_" + event.status(),
                "TRANSACTION",
                event.transactionId().toString(),
                Map.of(
                        "amount", event.amount().toString(),
                        "initiatedByUserId", event.initiatedByUserId().toString()
                )
        );
    }

    @EventListener
    public void onAccountOpened(AccountOpenedEvent event) {
        auditService.record(
                event.openedByUserId(),
                "ACCOUNT_OPENED",
                "ACCOUNT",
                event.accountId().toString(),
                Map.of(
                        "customerUserId", event.customerUserId().toString(),
                        "accountType", event.accountType(),
                        "openingBalance", event.openingBalance().toString()
                )
        );
    }
}
