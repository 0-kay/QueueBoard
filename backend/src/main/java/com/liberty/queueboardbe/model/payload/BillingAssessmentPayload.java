package com.liberty.queueboardbe.model.payload;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Payload for BILLING_ASSESSMENT")
public record BillingAssessmentPayload(
        @NotBlank(message = "Term code is required") String termCode,
        @NotNull(message = "applyLateFees is required") Boolean applyLateFees,
        SimulateFailure simulateFailure) implements JobPayload {
}
