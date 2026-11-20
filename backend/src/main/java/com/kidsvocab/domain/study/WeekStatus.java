package com.kidsvocab.domain.study;

public enum WeekStatus {
    /** Being prepared by the parent; editable. */
    DRAFT,
    /** Launched for next Monday; frozen. */
    SCHEDULED,
    /** Running this calendar week. */
    ACTIVE,
    /** First pass finished. */
    COMPLETED,
    /** The week ended before the first pass was finished. */
    EXPIRED;

    public boolean isPending() {
        return this == DRAFT || this == SCHEDULED;
    }

    public boolean isClosed() {
        return this == COMPLETED || this == EXPIRED;
    }
}
