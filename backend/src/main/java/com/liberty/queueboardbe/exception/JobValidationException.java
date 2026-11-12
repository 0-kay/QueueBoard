package com.liberty.queueboardbe.exception;

import com.liberty.queueboardbe.model.common.FieldError;

import java.util.List;

/** Submit-time validation failed; the job is never created. Maps to 400 with field errors. */
public class JobValidationException extends RuntimeException {

    private final List<FieldError> errors;

    public JobValidationException(List<FieldError> errors) {
        super("Job validation failed: " + errors);
        this.errors = List.copyOf(errors);
    }

    public List<FieldError> getErrors() {
        return errors;
    }
}
