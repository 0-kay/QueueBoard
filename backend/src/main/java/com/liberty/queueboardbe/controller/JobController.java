package com.liberty.queueboardbe.controller;

import com.liberty.queueboardbe.model.common.ApiResult;
import com.liberty.queueboardbe.model.common.PageResponse;
import com.liberty.queueboardbe.model.job.JobDetail;
import com.liberty.queueboardbe.model.job.JobStatus;
import com.liberty.queueboardbe.model.job.JobSummary;
import com.liberty.queueboardbe.model.job.JobType;
import com.liberty.queueboardbe.model.job.SubmitJobRequest;
import com.liberty.queueboardbe.service.job.JobService;
import com.liberty.queueboardbe.service.job.RetryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.UUID;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

@RestController
@RequestMapping(path = "/api/v1/jobs", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Jobs", description = "Submit, list, inspect, retry and delete background jobs")
public class JobController {

    private final JobService jobService;
    private final RetryService retryService;

    public JobController(JobService jobService, RetryService retryService) {
        this.jobService = jobService;
        this.retryService = retryService;
    }

    @Operation(summary = "Submit a job",
            description = """
                    Validates the payload against the type's record and stores the job as QUEUED; the worker picks \
                    it up within one poll interval. Closed courses, closed terms and inactive templates are accepted \
                    here and fail during processing with a permanent error. `simulateFailure` (TEMPORARY or PERMANENT) \
                    is accepted only when demo mode is on; TEMPORARY fails the first attempt only.""",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true,
                    content = @Content(mediaType = APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = SubmitJobRequest.class),
                            examples = {
                                    @ExampleObject(name = "Grade report", value = ApiExamples.SUBMIT_GRADE),
                                    @ExampleObject(name = "Notification batch", value = ApiExamples.SUBMIT_NOTIFICATION),
                                    @ExampleObject(name = "Billing assessment (simulated failure)",
                                            value = ApiExamples.SUBMIT_BILLING)})))
    @ApiResponse(responseCode = "201", description = "Job created; Location header points to it",
            content = @Content(examples = @ExampleObject(value = ApiExamples.JOB_QUEUED)))
    @ApiResponse(responseCode = "400", description = "Invalid name, type or payload; nothing was created",
            content = @Content(examples = @ExampleObject(value = ApiExamples.VALIDATION_ERROR)))
    @PostMapping(consumes = APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResult<JobDetail>> submit(@Valid @RequestBody SubmitJobRequest request) {
        JobDetail job = jobService.submit(request);
        return ResponseEntity.created(URI.create("/api/v1/jobs/" + job.id())).body(ApiResult.created(job));
    }

    @Operation(summary = "List jobs", description = "Newest first. Soft-deleted jobs are never listed.")
    @ApiResponse(responseCode = "200", description = "One page of jobs",
            content = @Content(examples = @ExampleObject(value = ApiExamples.JOB_PAGE)))
    @ApiResponse(responseCode = "400", description = "Unknown status or type, or page/size out of range",
            content = @Content(examples = @ExampleObject(value = ApiExamples.BAD_FILTER)))
    @GetMapping
    public ResponseEntity<ApiResult<PageResponse<JobSummary>>> list(
            @Parameter(description = "Filter by status") @RequestParam(required = false) JobStatus status,
            @Parameter(description = "Filter by type") @RequestParam(required = false) JobType type,
            @Parameter(description = "Case-insensitive match on name")
            @RequestParam(required = false) @Size(max = 100, message = "Search must be at most 100 characters") String search,
            @Parameter(description = "Zero-based page") @RequestParam(defaultValue = "0")
            @Min(value = 0, message = "Page must be 0 or more") int page,
            @Parameter(description = "Page size, 1 to 100") @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "Size must be between 1 and 100")
            @Max(value = 100, message = "Size must be between 1 and 100") int size) {
        return Responses.of(ApiResult.ok(jobService.list(status, type, search, page, size)));
    }

    @Operation(summary = "Inspect a job", description = "Includes payload, result summary and every attempt.")
    @ApiResponse(responseCode = "200", description = "The job",
            content = @Content(examples = @ExampleObject(value = ApiExamples.JOB_FAILED_DETAIL)))
    @ApiResponse(responseCode = "404", description = "No such job, or it was deleted",
            content = @Content(examples = @ExampleObject(value = ApiExamples.NOT_FOUND)))
    @GetMapping("/{id}")
    public ResponseEntity<ApiResult<JobDetail>> get(@Parameter(description = "Job id") @PathVariable("id") UUID id) {
        return Responses.of(ApiResult.ok(jobService.get(id)));
    }

    @Operation(summary = "Retry a failed job",
            description = """
                    Moves a FAILED job back to QUEUED with the same id. Allowed only when the latest attempt failed \
                    with a TEMPORARY error and attempts < maxAttempts (the `retryable` flag on every job response).""")
    @ApiResponse(responseCode = "202", description = "Job re-queued",
            content = @Content(examples = @ExampleObject(value = ApiExamples.JOB_REQUEUED)))
    @ApiResponse(responseCode = "404", description = "No such job, or it was deleted",
            content = @Content(examples = @ExampleObject(value = ApiExamples.NOT_FOUND)))
    @ApiResponse(responseCode = "409", description = "Not retryable (permanent error, attempts used up, not FAILED) or a concurrent change",
            content = @Content(examples = @ExampleObject(value = ApiExamples.RETRY_CONFLICT)))
    @PostMapping("/{id}/retry")
    public ResponseEntity<ApiResult<JobDetail>> retry(@Parameter(description = "Job id") @PathVariable("id") UUID id) {
        return Responses.of(ApiResult.accepted(retryService.retry(id)));
    }

    @Operation(summary = "Delete a job", description = "Soft delete. Only COMPLETED or FAILED jobs can be deleted.")
    @ApiResponse(responseCode = "200", description = "Deleted; data is null",
            content = @Content(examples = @ExampleObject(value = ApiExamples.DELETED)))
    @ApiResponse(responseCode = "404", description = "No such job, or already deleted",
            content = @Content(examples = @ExampleObject(value = ApiExamples.NOT_FOUND)))
    @ApiResponse(responseCode = "409", description = "Job is QUEUED or PROCESSING",
            content = @Content(examples = @ExampleObject(value = ApiExamples.DELETE_CONFLICT)))
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResult<Void>> delete(@Parameter(description = "Job id") @PathVariable("id") UUID id) {
        jobService.delete(id);
        return Responses.of(ApiResult.ok(null, "Job deleted."));
    }
}
