package com.liberty.queueboardbe.model.common;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "One field validation problem")
public record FieldError(
        @Schema(example = "payload.courseCode") String field,
        @Schema(example = "Course not found") String message) {
}
