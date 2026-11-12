package com.liberty.queueboardbe.repository.job;

import com.liberty.queueboardbe.model.job.JobErrorCode;
import com.liberty.queueboardbe.model.job.JobStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.EnumMap;
import java.util.Map;

/** Aggregate queries for the dashboard. Soft-deleted jobs are excluded everywhere. */
@Repository
public class JobStatsRepository {

    private final JdbcTemplate jdbc;

    public JobStatsRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Map<JobStatus, Long> countByStatus() {
        Map<JobStatus, Long> counts = new EnumMap<>(JobStatus.class);
        jdbc.query("SELECT status, COUNT(*) AS n FROM job WHERE deleted_at IS NULL GROUP BY status",
                rs -> {
                    counts.put(JobStatus.valueOf(rs.getString("status")), rs.getLong("n"));
                });
        return counts;
    }

    /** Job created to first attempt start. */
    public Double averageQueueWaitMs() {
        return jdbc.queryForObject("""
                SELECT AVG(CAST(DATEDIFF('MILLISECOND', j.created_at, a.started_at) AS DOUBLE PRECISION))
                FROM job j JOIN job_attempt a ON a.job_id = j.id AND a.attempt_number = 1
                WHERE j.deleted_at IS NULL""", Double.class);
    }

    /** Attempt start to finish, completed attempts only. */
    public Double averageRunTimeMs() {
        return jdbc.queryForObject("""
                SELECT AVG(CAST(DATEDIFF('MILLISECOND', a.started_at, a.finished_at) AS DOUBLE PRECISION))
                FROM job j JOIN job_attempt a ON a.job_id = j.id
                WHERE j.deleted_at IS NULL AND a.outcome = 'COMPLETED'""", Double.class);
    }

    /** For FAILED jobs, how many ended on each error code (latest attempt). */
    public Map<JobErrorCode, Long> failedJobsByLatestErrorCode() {
        Map<JobErrorCode, Long> counts = new EnumMap<>(JobErrorCode.class);
        jdbc.query("""
                        SELECT a.error_code, COUNT(*) AS n
                        FROM job j JOIN job_attempt a ON a.job_id = j.id AND a.attempt_number = j.attempts
                        WHERE j.status = 'FAILED' AND j.deleted_at IS NULL AND a.error_code IS NOT NULL
                        GROUP BY a.error_code""",
                rs -> {
                    counts.put(JobErrorCode.valueOf(rs.getString("error_code")), rs.getLong("n"));
                });
        return counts;
    }

    /** [jobs that failed at least once, of those how many are now COMPLETED]. */
    public long[] retryRecoveryCounts() {
        return jdbc.queryForObject("""
                        SELECT COUNT(*) AS failed_once,
                               COALESCE(SUM(CASE WHEN j.status = 'COMPLETED' THEN 1 ELSE 0 END), 0) AS recovered
                        FROM job j
                        WHERE j.deleted_at IS NULL
                          AND EXISTS (SELECT 1 FROM job_attempt a WHERE a.job_id = j.id AND a.outcome = 'FAILED')""",
                (rs, i) -> new long[]{rs.getLong("failed_once"), rs.getLong("recovered")});
    }
}
