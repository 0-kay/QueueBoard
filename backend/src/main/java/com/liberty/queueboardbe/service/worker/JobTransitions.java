package com.liberty.queueboardbe.service.worker;

import com.liberty.queueboardbe.model.job.Job;
import com.liberty.queueboardbe.model.job.JobAttempt;
import com.liberty.queueboardbe.model.job.JobErrorCode;
import com.liberty.queueboardbe.model.job.JobType;
import com.liberty.queueboardbe.repository.job.JobAttemptRepository;
import com.liberty.queueboardbe.repository.job.JobRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * The worker's state changes, each in a transaction of its own (REQUIRES_NEW). They live in a
 * separate bean from {@link JobWorker} because {@code @Transactional} does not apply to calls inside
 * the same class. Recording the outcome separately means a failed job's work can roll back while the
 * record that it failed is still saved.
 */
@Service
public class JobTransitions {

    private static final int MAX_SUMMARY = 500;

    /** What the worker needs to run a job it has claimed. */
    public record Claim(UUID jobId, long attemptId, int attemptNumber, JobType type, String payload) {
    }

    private final JobRepository jobs;
    private final JobAttemptRepository attempts;
    private final Clock clock;

    public JobTransitions(JobRepository jobs, JobAttemptRepository attempts, Clock clock) {
        this.jobs = jobs;
        this.attempts = attempts;
        this.clock = clock;
    }

    /**
     * QUEUED to PROCESSING via one conditional UPDATE, then opens the attempt.
     *
     * @return the claim, or empty if another thread claimed the job first or it was deleted
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<Claim> claim(UUID jobId) {
        Instant now = clock.instant();
        if (jobs.claim(jobId, now) != 1) {
            return Optional.empty();
        }
        Job job = jobs.findById(jobId).orElseThrow();
        JobAttempt attempt = attempts.save(new JobAttempt(jobId, job.getAttempts(), now));
        return Optional.of(new Claim(jobId, attempt.getId(), attempt.getAttemptNumber(), job.getType(),
                job.getPayload()));
    }

    /** PROCESSING to COMPLETED. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void complete(Claim claim, String resultSummary) {
        Instant now = clock.instant();
        String summary = resultSummary != null && resultSummary.length() > MAX_SUMMARY
                ? resultSummary.substring(0, MAX_SUMMARY - 3) + "..." : resultSummary;
        jobs.findById(claim.jobId()).orElseThrow().complete(summary, now);
        attempts.findById(claim.attemptId()).orElseThrow().succeed(now);
    }

    /** PROCESSING to FAILED, with the error recorded on the attempt. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void fail(Claim claim, JobErrorCode code, String message) {
        Instant now = clock.instant();
        jobs.findById(claim.jobId()).orElseThrow().fail(now);
        attempts.findById(claim.attemptId()).orElseThrow().failWith(code, message, now);
    }
}
