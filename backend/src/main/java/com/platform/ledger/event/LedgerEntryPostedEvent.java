package com.platform.ledger.event;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Not published anywhere yet - LedgerService.post() doesn't fire this in Phase 1.
 * Defined now so the shape is settled; wired up when a real consumer (reconciliation,
 * a future risk-analytics feed) needs it, rather than speculatively publishing an
 * event nothing listens to.
 */
public record LedgerEntryPostedEvent(
        UUID journalEntryId,
        UUID transactionReference,
        BigDecimal netAmount,
        String currency
) {
}
