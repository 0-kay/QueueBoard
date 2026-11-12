package com.liberty.queueboardbe.model.job;

import com.liberty.queueboardbe.exception.IllegalJobTransitionException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * A submitted background job. The {@code job} table is the queue.
 *
 * <p>All status changes go through the transition methods below; an illegal transition throws
 * {@link IllegalJobTransitionException}, which the API maps to 409. The worker claims jobs with an
 * atomic conditional UPDATE (see {@code JobRepository#claim}) that applies the same rule as
 * {@link #start(Instant)} at the database level, so two threads can never both claim one job.
 */
@Entity
@Table(name = "job")
public class Job {

    @Id
    private UUID id;

    @Column(nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 32)
    private JobType type;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 16)
    private JobStatus status;

    /** Validated JSON, stored as submitted. */
    @Column(nullable = false, length = 4000)
    private String payload;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "max_attempts", nullable = false)
    private int maxAttempts;

    @Column(name = "result_summary", length = 500)
    private String resultSummary;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Version
    private Long version;

    protected Job() {
        // for JPA
    }

    private Job(UUID id, String name, JobType type, String payload, int maxAttempts, Instant now) {
        this.id = id;
        this.name = name;
        this.type = type;
        this.payload = payload;
        this.maxAttempts = maxAttempts;
        this.status = JobStatus.QUEUED;
        this.attempts = 0;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public static Job submit(String name, JobType type, String payload, int maxAttempts, Instant now) {
        return new Job(UUID.randomUUID(), name, type, payload, maxAttempts, now);
    }

    /** QUEUED to PROCESSING, counting a new attempt. */
    public void start(Instant now) {
        requireStatus(JobStatus.QUEUED, JobStatus.PROCESSING);
        this.status = JobStatus.PROCESSING;
        this.attempts++;
        this.updatedAt = now;
    }

    /** PROCESSING to COMPLETED. */
    public void complete(String resultSummary, Instant now) {
        requireStatus(JobStatus.PROCESSING, JobStatus.COMPLETED);
        this.status = JobStatus.COMPLETED;
        this.resultSummary = resultSummary;
        this.updatedAt = now;
    }

    /** PROCESSING to FAILED. The error itself lives on the attempt. */
    public void fail(Instant now) {
        requireStatus(JobStatus.PROCESSING, JobStatus.FAILED);
        this.status = JobStatus.FAILED;
        this.updatedAt = now;
    }

    /** FAILED to QUEUED (manual retry). Whether a retry is allowed is decided by RetryPolicy. */
    public void requeue(Instant now) {
        requireStatus(JobStatus.FAILED, JobStatus.QUEUED);
        this.status = JobStatus.QUEUED;
        this.updatedAt = now;
    }

    /** Soft delete. Only finished jobs can be deleted. */
    public void markDeleted(Instant now) {
        if (status != JobStatus.COMPLETED && status != JobStatus.FAILED) {
            throw new IllegalJobTransitionException(id, status, "delete");
        }
        this.deletedAt = now;
        this.updatedAt = now;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    private void requireStatus(JobStatus expected, JobStatus target) {
        if (status != expected) {
            throw new IllegalJobTransitionException(id, status, target);
        }
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public JobType getType() {
        return type;
    }

    public JobStatus getStatus() {
        return status;
    }

    public String getPayload() {
        return payload;
    }

    public int getAttempts() {
        return attempts;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public String getResultSummary() {
        return resultSummary;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public Long getVersion() {
        return version;
    }
}
