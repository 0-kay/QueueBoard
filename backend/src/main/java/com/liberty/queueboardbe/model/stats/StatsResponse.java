package com.liberty.queueboardbe.model.stats;

import com.liberty.queueboardbe.model.job.JobStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Map;

@Schema(name = "Stats", description = "Dashboard metrics; soft-deleted jobs are excluded")
public record StatsResponse(
        Map<JobStatus, Long> byStatus,
        @Schema(description = "Average ms from job created to first attempt start; 0 when no data")
        long avgQueueWaitMs,
        @Schema(description = "Average ms from attempt start to finish, completed attempts only; 0 when no data")
        long avgRunTimeMs,
        FailureRate failureRate,
        @Schema(description = "Jobs that failed at least once and later completed, over jobs that failed at least once")
        double retryRecoveryRate) {

    @Schema(description = "FAILED jobs over finished jobs, split by the kind of the latest error")
    public record FailureRate(double total, double temporary, double permanent) {
    }
}
