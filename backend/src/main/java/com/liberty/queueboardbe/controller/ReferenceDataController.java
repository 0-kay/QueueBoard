package com.liberty.queueboardbe.controller;

import com.liberty.queueboardbe.model.common.ApiResult;
import com.liberty.queueboardbe.model.reference.CourseResponse;
import com.liberty.queueboardbe.model.reference.JobTypeResponse;
import com.liberty.queueboardbe.model.reference.NotificationTemplateResponse;
import com.liberty.queueboardbe.model.reference.TermResponse;
import com.liberty.queueboardbe.service.reference.ReferenceDataService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping(path = "/api/v1", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Reference data", description = "Dropdown values for the submit form")
public class ReferenceDataController {

    private final ReferenceDataService referenceData;

    public ReferenceDataController(ReferenceDataService referenceData) {
        this.referenceData = referenceData;
    }

    @Operation(summary = "Job types", description = "Every type with a label and description.")
    @ApiResponse(responseCode = "200", description = "Job types",
            content = @Content(examples = @ExampleObject(value = ApiExamples.JOB_TYPES)))
    @GetMapping("/job-types")
    public ResponseEntity<ApiResult<List<JobTypeResponse>>> jobTypes() {
        return Responses.of(ApiResult.ok(referenceData.jobTypes()));
    }

    @Operation(summary = "Courses", description = "Includes CLOSED courses, which fail at processing time.")
    @ApiResponse(responseCode = "200", description = "Courses",
            content = @Content(examples = @ExampleObject(value = ApiExamples.COURSES)))
    @GetMapping("/courses")
    public ResponseEntity<ApiResult<List<CourseResponse>>> courses() {
        return Responses.of(ApiResult.ok(referenceData.courses()));
    }

    @Operation(summary = "Terms", description = "Includes CLOSED terms, which fail at processing time.")
    @ApiResponse(responseCode = "200", description = "Terms",
            content = @Content(examples = @ExampleObject(value = ApiExamples.TERMS)))
    @GetMapping("/terms")
    public ResponseEntity<ApiResult<List<TermResponse>>> terms() {
        return Responses.of(ApiResult.ok(referenceData.terms()));
    }

    @Operation(summary = "Notification templates", description = "Includes inactive templates, which fail at processing time.")
    @ApiResponse(responseCode = "200", description = "Templates",
            content = @Content(examples = @ExampleObject(value = ApiExamples.TEMPLATES)))
    @GetMapping("/notification-templates")
    public ResponseEntity<ApiResult<List<NotificationTemplateResponse>>> notificationTemplates() {
        return Responses.of(ApiResult.ok(referenceData.notificationTemplates()));
    }
}
