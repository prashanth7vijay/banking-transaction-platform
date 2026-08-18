package com.platform.exceptions.domain;

/**
 * What kind of entry this is in an exception's investigation trail - lets the
 * UI render a plain note differently from a system-recorded status change,
 * assignment, or action (retry/escalate), without needing separate tables for
 * each. One unified, append-only, chronological log per exception.
 */
public enum ExceptionNoteType {
    NOTE,
    STATUS_CHANGE,
    ASSIGNMENT,
    ACTION_TAKEN
}
