package com.liberty.queueboardbe.model.common;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/** Envelope for every response, success and error. {@code status} always equals the HTTP status. */
@Schema(description = "Envelope for every response")
public record ApiResult<T>(
        @Schema(description = "HTTP status, mirrored in the body", example = "200") int status,
        @Schema(description = "For developers and logs", example = "OK") String message,
        @Schema(description = "Safe to show to the user", example = "Request completed.") String description,
        @Schema(description = "Payload; null on errors") T data,
        @Schema(description = "Field validation errors on 400, otherwise empty") List<FieldError> errors) {

    public ApiResult {
        errors = errors == null ? List.of() : List.copyOf(errors);
    }

    public static <T> ApiResult<T> ok(T data) {
        return ok(data, "Request completed.");
    }

    public static <T> ApiResult<T> ok(T data, String description) {
        return new ApiResult<>(200, "OK", description, data, List.of());
    }

    public static <T> ApiResult<T> created(T data) {
        return new ApiResult<>(201, "Created", "Job submitted.", data, List.of());
    }

    public static <T> ApiResult<T> accepted(T data) {
        return new ApiResult<>(202, "Accepted", "Job queued for another attempt.", data, List.of());
    }

    public static <T> ApiResult<T> error(int status, String message, String description, List<FieldError> errors) {
        return new ApiResult<>(status, message, description, null, errors);
    }
}
