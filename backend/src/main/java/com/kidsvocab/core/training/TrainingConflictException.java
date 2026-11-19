package com.kidsvocab.core.training;

/** The client acted on a word or round that is not the current one (stale screen, second tab). */
public class TrainingConflictException extends RuntimeException {

    public TrainingConflictException(String message) {
        super(message);
    }
}
