package com.liberty.queueboardbe.exception;

/** The job exists but the retry rule rejects it. Maps to 409 with a user-facing reason. */
public class RetryNotAllowedException extends RuntimeException {

    private final String description;

    public RetryNotAllowedException(String message, String description) {
        super(message);
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
