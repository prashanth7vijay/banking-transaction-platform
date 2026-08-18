package com.platform.accounts.exception;

/**
 * Deliberately not an ApiException/HTTP-mapped exception - this is caught internally
 * by whichever service orchestrates the debit (e.g. transactions' ApprovalService)
 * and translated into a domain outcome (a FAILED transaction), not surfaced directly
 * as a generic HTTP error to the caller.
 */
public class InsufficientFundsException extends RuntimeException {
    public InsufficientFundsException(String message) {
        super(message);
    }
}
