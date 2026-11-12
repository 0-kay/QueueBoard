package com.liberty.queueboardbe.model.job;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Map;

/**
 * Submit request. {@code type} is a string (not the enum) so an unknown type comes back as a field
 * error instead of an unreadable-body error. {@code payload} is a JSON object, validated against the
 * type's payload record in JobPayloadValidator.
 */
@Schema(description = "Submit a job")
public record SubmitJobRequest(
        @Schema(example = "Midterm grades CSCI-110", maxLength = 100)
        @NotBlank(message = "Name is required")
        @Size(max = 100, message = "Name must be at most 100 characters")
        String name,

        @Schema(example = "GRADE_REPORT", allowableValues = {"GRADE_REPORT", "NOTIFICATION_BATCH", "BILLING_ASSESSMENT"})
        @NotBlank(message = "Type is required")
        String type,

        @Schema(description = "JSON object matching the type's payload", example = "{\"courseCode\":\"CSCI-110\"}")
        @NotNull(message = "Payload is required")
        Map<String, Object> payload) {
}
