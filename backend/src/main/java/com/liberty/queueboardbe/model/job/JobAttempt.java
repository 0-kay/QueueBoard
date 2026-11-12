package com.liberty.queueboardbe.model.job;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/** One run of a job. Errors and timings live only here. */
@Entity
@Table(name = "job_attempt")
public class JobAttempt {

    static final int MAX_ERROR_MESSAGE = 1000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "job_id", nullable = false)
    private UUID jobId;

    @Column(name = "attempt_number", nullable = false)
    private int attemptNumber;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(length = 16)
    private AttemptOutcome outcome;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "error_code", length = 40)
    private JobErrorCode errorCode;

    @Column(name = "error_message", length = MAX_ERROR_MESSAGE)
    private String errorMessage;

    protected JobAttempt() {
        // for JPA
    }

    public JobAttempt(UUID jobId, int attemptNumber, Instant startedAt) {
        this.jobId = jobId;
        this.attemptNumber = attemptNumber;
        this.startedAt = startedAt;
    }

    public void succeed(Instant now) {
        this.outcome = AttemptOutcome.COMPLETED;
        this.finishedAt = now;
    }

    public void failWith(JobErrorCode code, String message, Instant now) {
        this.outcome = AttemptOutcome.FAILED;
        this.errorCode = code;
        this.errorMessage = truncate(message);
        this.finishedAt = now;
    }

    /** Derived from the code, never stored. */
    public ErrorKind getErrorKind() {
        return errorCode == null ? null : errorCode.kind();
    }

    private static String truncate(String message) {
        if (message == null || message.length() <= MAX_ERROR_MESSAGE) {
            return message;
        }
        return message.substring(0, MAX_ERROR_MESSAGE - 3) + "...";
    }

    public Long getId() {
        return id;
    }

    public UUID getJobId() {
        return jobId;
    }

    public int getAttemptNumber() {
        return attemptNumber;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    public AttemptOutcome getOutcome() {
        return outcome;
    }

    public JobErrorCode getErrorCode() {
        return errorCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }
}
