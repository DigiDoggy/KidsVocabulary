package com.kidsvocab.ai;

/** A failed call to a word suggestion provider. */
public class ProviderException extends RuntimeException {

    public enum Kind {
        /** Network error, timeout, server error or rejected key. */
        UNAVAILABLE,
        /** HTTP 429: quota exhausted for now. */
        RATE_LIMITED,
        /** The API answered, but not with the expected JSON. */
        INVALID_RESPONSE
    }

    private final Kind kind;

    public ProviderException(Kind kind, String message, Throwable cause) {
        super(message, cause);
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }
}
