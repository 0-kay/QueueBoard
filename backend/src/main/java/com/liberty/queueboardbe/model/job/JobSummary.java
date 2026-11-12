package com.liberty.queueboardbe.model.job;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "Job as shown in lists")
public record JobSummary(
        UUID id,
        String name,
        JobType type,
        JobStatus status,
        int attempts,
        int maxAttempts,
        @Schema(description = "Error code of the latest attempt, if it failed") JobErrorCode lastErrorCode,
        @Schema(description = "Computed by the server: FAILED, latest error TEMPORARY, attempts < maxAttempts")
        boolean retryable,
        Instant createdAt,
        Instant updatedAt) {
}
