package com.liberty.queueboardbe.exception;

import com.liberty.queueboardbe.model.job.JobStatus;

import java.util.UUID;

/** Thrown by the Job entity on any transition the lifecycle doesn't allow. Maps to 409. */
public class IllegalJobTransitionException extends RuntimeException {

    private final JobStatus currentStatus;
    private final String action;

    public IllegalJobTransitionException(UUID jobId, JobStatus current, JobStatus target) {
        this(jobId, current, "move to " + target);
    }

    public IllegalJobTransitionException(UUID jobId, JobStatus current, String action) {
        super("Illegal transition for job " + jobId + ": cannot " + action + " from " + current);
        this.currentStatus = current;
        this.action = action;
    }

    public JobStatus getCurrentStatus() {
        return currentStatus;
    }

    public String getAction() {
        return action;
    }
}
