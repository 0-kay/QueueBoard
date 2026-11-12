package com.liberty.queueboardbe.controller;

import com.liberty.queueboardbe.model.common.ApiResult;
import com.liberty.queueboardbe.model.stats.StatsResponse;
import com.liberty.queueboardbe.service.stats.StatsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Stats", description = "Dashboard metrics")
public class StatsController {

    private final StatsService statsService;

    public StatsController(StatsService statsService) {
        this.statsService = statsService;
    }

    @Operation(summary = "Dashboard metrics",
            description = """
                    Counts by status, average queue wait (created to first start), average run time (completed \
                    attempts), failure rate (FAILED over finished, split by error kind) and retry recovery rate \
                    (failed at least once and later COMPLETED, over failed at least once). Soft-deleted jobs are excluded.""")
    @ApiResponse(responseCode = "200", description = "Current metrics",
            content = @Content(examples = @ExampleObject(value = ApiExamples.STATS)))
    @GetMapping(path = "/api/v1/jobs/stats", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResult<StatsResponse>> stats() {
        return Responses.of(ApiResult.ok(statsService.stats()));
    }
}
