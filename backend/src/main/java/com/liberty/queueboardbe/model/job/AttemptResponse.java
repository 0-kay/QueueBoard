package com.liberty.queueboardbe.model.job;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(name = "Attempt", description = "One run of a job")
public record AttemptResponse(
        int attemptNumber,
        Instant startedAt,
        @Schema(description = "Null while running") Instant finishedAt,
        @Schema(description = "Null while running") AttemptOutcome outcome,
        JobErrorCode errorCode,
        ErrorKind errorKind,
        String errorMessage) {
}
