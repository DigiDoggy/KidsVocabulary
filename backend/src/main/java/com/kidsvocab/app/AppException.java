package com.kidsvocab.app;

/** A business error with a stable code; the web layer maps the code to an HTTP status. */
public class AppException extends RuntimeException {

    public enum Code {
        VALIDATION,
        NOT_FOUND,
        CONFLICT,
        INVALID_CREDENTIALS,
        INVALID_REFRESH_TOKEN,
        INVALID_PIN,
        PIN_LOCKED,
        FORBIDDEN,
        /** The training session is already completed or closed (e.g. the week ended). */
        SESSION_CLOSED
    }

    private final Code code;

    public AppException(Code code, String message) {
        super(message);
        this.code = code;
    }

    public Code code() {
        return code;
    }

    public static AppException notFound(String what) {
        return new AppException(Code.NOT_FOUND, what + " not found");
    }
}
