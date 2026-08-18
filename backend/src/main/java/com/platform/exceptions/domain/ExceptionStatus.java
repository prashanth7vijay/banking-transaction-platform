package com.platform.exceptions.domain;

/**
 * The investigation lifecycle (product spec's suggested flow, unchanged):
 * OPEN -&gt; ASSIGNED -&gt; INVESTIGATING -&gt; ACTION_REQUIRED -&gt; RESOLVED -&gt; CLOSED.
 * <p>
 * Deliberately not a full {@code TransactionStateMachine}-style class with its
 * own transition table - this lifecycle is lower-stakes than money movement
 * (it never touches a balance), so straightforward precondition checks inline
 * in {@link com.platform.exceptions.service.ExceptionService} are proportional.
 * RESOLVED and CLOSED are terminal.
 */
public enum ExceptionStatus {
    OPEN,
    ASSIGNED,
    INVESTIGATING,
    ACTION_REQUIRED,
    RESOLVED,
    CLOSED
}
