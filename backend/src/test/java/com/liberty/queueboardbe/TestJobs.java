package com.liberty.queueboardbe;

import com.liberty.queueboardbe.model.job.Job;
import com.liberty.queueboardbe.model.job.JobAttempt;
import com.liberty.queueboardbe.model.job.JobErrorCode;
import com.liberty.queueboardbe.model.job.JobStatus;
import com.liberty.queueboardbe.model.job.JobType;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Builds jobs in a given state through the real transition methods (no reflection). */
public final class TestJobs {

    public static final Instant NOW = Instant.parse("2026-11-11T12:00:00Z");

    private TestJobs() {
    }

    public static Job queued() {
        return Job.submit("Test job", JobType.GRADE_REPORT, "{\"courseCode\":\"CSCI-110\"}", 3, NOW);
    }

    public static Job inStatus(JobStatus status) {
        Job job = queued();
        switch (status) {
            case QUEUED -> {
            }
            case PROCESSING -> job.start(NOW);
            case COMPLETED -> {
                job.start(NOW);
                job.complete("done", NOW);
            }
            case FAILED -> {
                job.start(NOW);
                job.fail(NOW);
            }
        }
        return job;
    }

    /** A FAILED job and its attempts, where every attempt failed with the given codes in order. */
    public record FailedJob(Job job, List<JobAttempt> attempts) {
        public JobAttempt latest() {
            return attempts.getLast();
        }
    }

    public static FailedJob failedWith(JobErrorCode... codes) {
        Job job = queued();
        List<JobAttempt> attempts = new ArrayList<>();
        for (int i = 0; i < codes.length; i++) {
            if (i > 0) {
                job.requeue(NOW);
            }
            job.start(NOW);
            JobAttempt attempt = new JobAttempt(job.getId(), job.getAttempts(), NOW);
            attempt.failWith(codes[i], "failure " + (i + 1), NOW);
            job.fail(NOW);
            attempts.add(attempt);
        }
        return new FailedJob(job, attempts);
    }
}
