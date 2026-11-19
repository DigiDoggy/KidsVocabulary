package com.kidsvocab.core.training;

/** The session is already completed or closed (for example, the week has expired). */
public class SessionNotActiveException extends RuntimeException {

    public SessionNotActiveException(TrainingSession.Status status) {
        super("Training session is " + status);
    }
}
