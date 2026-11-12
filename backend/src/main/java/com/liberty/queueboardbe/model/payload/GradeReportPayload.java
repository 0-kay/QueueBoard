package com.liberty.queueboardbe.model.payload;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Payload for GRADE_REPORT")
public record GradeReportPayload(
        @NotBlank(message = "Course code is required") String courseCode,
        SimulateFailure simulateFailure) implements JobPayload {
}
