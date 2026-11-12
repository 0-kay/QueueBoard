package com.liberty.queueboardbe.exception;

import com.liberty.queueboardbe.model.job.JobErrorCode;

/** Raised by handlers during processing. Never reaches the API; the worker records it on the attempt. */
public class JobProcessingException extends RuntimeException {

    private final JobErrorCode code;

    public JobProcessingException(JobErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    public JobErrorCode getCode() {
        return code;
    }
}
