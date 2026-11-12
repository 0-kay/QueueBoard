package com.liberty.queueboardbe.model.common;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/** Custom page shape (not Spring's Page), so the JSON contract stays stable. */
@Schema(name = "Page", description = "One page of results")
public record PageResponse<T>(
        List<T> items,
        @Schema(example = "0") int page,
        @Schema(example = "20") int size,
        @Schema(example = "54") long totalItems) {
}
