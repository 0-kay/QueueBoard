package com.liberty.queueboardbe.model.job;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Schema(description = "JobSummary plus payload, result and attempt history")
public record JobDetail(
        UUID id,
        String name,
        JobType type,
        JobStatus status,
        int attempts,
        int maxAttempts,
        JobErrorCode lastErrorCode,
        boolean retryable,
        Instant createdAt,
        Instant updatedAt,
        Map<String, Object> payload,
        String resultSummary,
        List<AttemptResponse> attemptHistory) {
}
