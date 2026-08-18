package com.platform.notifications.listener;

import com.platform.notifications.service.NotificationService;
import com.platform.transactions.domain.TransactionStatus;
import com.platform.transactions.event.TransactionStatusChangedEvent;
import com.platform.users.event.UserActionEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.text.NumberFormat;
import java.util.Locale;

@Component
@RequiredArgsConstructor
public class NotificationEventListener {

    private final NotificationService notificationService;

    @EventListener
    public void onUserAction(UserActionEvent event) {
        if (UserActionEvent.PASSWORD_CHANGED.equals(event.action())) {
            notificationService.notify(
                    event.userId(),
                    event.action(),
                    "Your password was changed",
                    "Your Transaction Platform password was just changed. If this wasn't you, contact support immediately."
            );
        }
    }

    @EventListener
    public void onTransactionStatusChanged(TransactionStatusChangedEvent event) {
        // Only the outcomes worth notifying the customer about - not the initial
        // submission passing risk and landing in the approval queue, which the
        // customer already knows they just did. (This old event only ever carries
        // the *final* status TransferService/ApprovalService settle on, never the
        // transient SUBMITTED/APPROVED/PROCESSING states in between - see
        // TransactionStateChangedEvent for the richer per-edge picture.)
        if (event.status() == TransactionStatus.PENDING_APPROVAL) {
            return;
        }

        String amount = NumberFormat.getCurrencyInstance(Locale.US).format(event.amount());
        String title;
        String message;

        switch (event.status()) {
            case COMPLETED -> {
                title = "Transfer completed";
                message = "Your transfer of " + amount + " has been approved and completed.";
            }
            case REJECTED -> {
                title = "Transfer rejected";
                message = "Your transfer of " + amount + " was rejected by an employee.";
            }
            case FAILED -> {
                title = "Transfer failed";
                message = "Your transfer of " + amount + " could not be completed due to insufficient funds.";
            }
            default -> {
                return;
            }
        }

        notificationService.notify(event.initiatedByUserId(), "TRANSACTION_" + event.status(), title, message);
    }
}
