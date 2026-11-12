package com.liberty.queueboardbe.service.job;

import com.liberty.queueboardbe.model.job.ErrorKind;
import com.liberty.queueboardbe.model.job.Job;
import com.liberty.queueboardbe.model.job.JobAttempt;
import com.liberty.queueboardbe.model.job.JobStatus;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * The retry rule: a job is retryable only when it is FAILED, its latest attempt's error is TEMPORARY,
 * and it has attempts left. Used for the {@code retryable} flag on every response and by RetryService.
 */
@Component
public class RetryPolicy {

    public record Decision(boolean allowed, String message, String description) {

        static final Decision ALLOWED = new Decision(true, "Retry allowed", "This job can be retried.");

        static Decision denied(String message, String description) {
            return new Decision(false, message, description);
        }
    }

    public boolean isRetryable(Job job, JobAttempt latestAttempt) {
        return decide(job, latestAttempt).allowed();
    }

    public Decision decide(Job job, JobAttempt latestAttempt) {
        if (job.getStatus() != JobStatus.FAILED) {
            return Decision.denied("Retry not allowed: job is " + job.getStatus(),
                    "Only failed jobs can be retried. This job is " + job.getStatus().name().toLowerCase(Locale.ROOT) + ".");
        }
        if (latestAttempt == null || latestAttempt.getErrorKind() == null) {
            return Decision.denied("Retry not allowed: no failed attempt recorded",
                    "This job has no recorded failure, so it can't be retried.");
        }
        if (latestAttempt.getErrorKind() == ErrorKind.PERMANENT) {
            return Decision.denied("Retry not allowed",
                    "This job failed with a permanent error and can't be retried.");
        }
        if (job.getAttempts() >= job.getMaxAttempts()) {
            return Decision.denied("Retry not allowed: attempts exhausted",
                    "This job has used all " + job.getMaxAttempts() + " attempts and can't be retried.");
        }
        return Decision.ALLOWED;
    }
}
