package com.platform.ledger.exception;

/**
 * Thrown when a journal entry's lines don't sum to zero (debits minus credits)
 * per currency. This is what makes DEBITS = CREDITS enforced by construction:
 * LedgerService.post() refuses to commit an unbalanced entry, full stop - there
 * is no code path that can accidentally post one.
 */
public class UnbalancedJournalEntryException extends RuntimeException {
    public UnbalancedJournalEntryException(String message) {
        super(message);
    }
}
