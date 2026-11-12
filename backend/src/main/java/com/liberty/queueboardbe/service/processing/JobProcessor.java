package com.liberty.queueboardbe.service.processing;

import com.liberty.queueboardbe.model.common.FieldError;
import com.liberty.queueboardbe.model.payload.JobPayload;

import java.util.List;
import java.util.UUID;

/**
 * The only thing the core knows about what a job actually does. The job lifecycle, worker, retry rules
 * and API never look past this interface; the {@code simulation} package implements it.
 */
public interface JobProcessor {

    /**
     * Submit-time checks that need the processor's data, e.g. "course not found". Add a FieldError per
     * problem. Closed courses, closed terms and inactive templates are valid here on purpose; they fail
     * later, in {@link #process}, with a permanent error.
     */
    void validate(JobPayload payload, List<FieldError> errors);

    /**
     * Does the work and returns the result summary shown on the job. Throw
     * {@link com.liberty.queueboardbe.exception.JobProcessingException} for known failures; any other
     * exception is recorded as UNEXPECTED_ERROR. The implementation owns its transactions.
     */
    String process(UUID jobId, JobPayload payload);
}
