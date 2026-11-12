package com.liberty.queueboardbe.model.payload;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Payload for NOTIFICATION_BATCH")
public record NotificationBatchPayload(
        @NotBlank(message = "Template code is required") String templateCode,
        @NotBlank(message = "Course code is required") String courseCode,
        @NotNull(message = "Channel is required") Channel channel,
        SimulateFailure simulateFailure) implements JobPayload {
}
