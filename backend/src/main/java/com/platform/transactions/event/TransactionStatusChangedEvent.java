package com.platform.transactions.event;

import com.platform.transactions.domain.TransactionStatus;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Published via Spring's in-process ApplicationEventPublisher - not a message
 * broker. This keeps `transactions` decoupled from `audit`/`notifications` (it
 * doesn't know or care who's listening) while staying broker-agnostic: swapping in
 * Kafka/RabbitMQ later only changes how this event is published/consumed, not this
 * class or the modules that raise/handle it.
 */
public record TransactionStatusChangedEvent(
        UUID transactionId,
        TransactionStatus status,
        UUID actorUserId,
        UUID initiatedByUserId,
        BigDecimal amount
) {
}
