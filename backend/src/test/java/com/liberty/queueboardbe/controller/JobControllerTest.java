package com.liberty.queueboardbe.controller;

import com.liberty.queueboardbe.TestJobs;
import com.liberty.queueboardbe.exception.IllegalJobTransitionException;
import com.liberty.queueboardbe.exception.JobNotFoundException;
import com.liberty.queueboardbe.exception.JobValidationException;
import com.liberty.queueboardbe.exception.RetryNotAllowedException;
import com.liberty.queueboardbe.model.common.FieldError;
import com.liberty.queueboardbe.model.common.PageResponse;
import com.liberty.queueboardbe.model.job.Job;
import com.liberty.queueboardbe.model.job.JobDetail;
import com.liberty.queueboardbe.model.job.JobErrorCode;
import com.liberty.queueboardbe.model.job.JobStatus;
import com.liberty.queueboardbe.model.job.JobType;
import com.liberty.queueboardbe.model.stats.StatsResponse;
import com.liberty.queueboardbe.service.job.JobMapper;
import com.liberty.queueboardbe.service.job.JobService;
import com.liberty.queueboardbe.service.job.RetryPolicy;
import com.liberty.queueboardbe.service.job.RetryService;
import com.liberty.queueboardbe.service.reference.ReferenceDataService;
import com.liberty.queueboardbe.service.stats.StatsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.ResultMatcher;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** API contract: status codes, the ApiResult envelope on every response, and the retryable flag. */
@WebMvcTest(controllers = {JobController.class, StatsController.class, ReferenceDataController.class})
@Import({JobMapper.class, RetryPolicy.class})
class JobControllerTest {

    private static final String VALID_SUBMIT = """
            { "name": "Midterm grades CSCI-110", "type": "GRADE_REPORT", "payload": { "courseCode": "CSCI-110" } }""";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JobMapper mapper;

    @MockitoBean
    private JobService jobService;
    @MockitoBean
    private RetryService retryService;
    @MockitoBean
    private StatsService statsService;
    @MockitoBean
    private ReferenceDataService referenceDataService;

    /** Every response, success or error, has exactly the ApiResult shape with a matching status. */
    private static ResultMatcher apiResult(int status) {
        return result -> {
            status().is(status).match(result);
            jsonPath("$.status", is(status)).match(result);
            jsonPath("$.message", notNullValue()).match(result);
            jsonPath("$.description", notNullValue()).match(result);
            jsonPath("$.errors", notNullValue()).match(result);
            jsonPath("$.data").hasJsonPath().match(result);
        };
    }

    private static ResultMatcher errorResult(int status) {
        return result -> {
            apiResult(status).match(result);
            jsonPath("$.data", nullValue()).match(result);
        };
    }

    private JobDetail queuedDetail() {
        return mapper.toDetail(TestJobs.queued(), List.of());
    }

    // ---------- submit ----------

    @Test
    void submitReturns201WithLocationAndJobDetail() throws Exception {
        JobDetail detail = queuedDetail();
        when(jobService.submit(any())).thenReturn(detail);

        mvc.perform(post("/api/v1/jobs").contentType(MediaType.APPLICATION_JSON).content(VALID_SUBMIT))
                .andExpect(apiResult(201))
                .andExpect(header().string("Location", "/api/v1/jobs/" + detail.id()))
                .andExpect(jsonPath("$.data.id", is(detail.id().toString())))
                .andExpect(jsonPath("$.data.status", is("QUEUED")))
                .andExpect(jsonPath("$.data.payload.courseCode", is("CSCI-110")))
                .andExpect(jsonPath("$.data.attemptHistory", hasSize(0)))
                .andExpect(jsonPath("$.data.retryable", is(false)))
                .andExpect(jsonPath("$.errors", hasSize(0)));
    }

    @Test
    void submitWithBlankNameAndNoTypeReturns400WithFieldErrors() throws Exception {
        mvc.perform(post("/api/v1/jobs").contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"name\": \" \", \"payload\": {} }"))
                .andExpect(errorResult(400))
                .andExpect(jsonPath("$.errors[?(@.field == 'name')].message").value("Name is required"))
                .andExpect(jsonPath("$.errors[?(@.field == 'type')].message").value("Type is required"));
        verifyNoInteractions(jobService);
    }

    @Test
    void submitWithTooLongNameReturns400() throws Exception {
        String body = VALID_SUBMIT.replace("Midterm grades CSCI-110", "x".repeat(101));

        mvc.perform(post("/api/v1/jobs").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(errorResult(400))
                .andExpect(jsonPath("$.errors[0].field", is("name")));
    }

    @Test
    void submitWithPayloadErrorsFromServiceReturns400() throws Exception {
        when(jobService.submit(any())).thenThrow(new JobValidationException(List.of(
                new FieldError("payload.courseCode", "Course not found"))));

        mvc.perform(post("/api/v1/jobs").contentType(MediaType.APPLICATION_JSON).content(VALID_SUBMIT))
                .andExpect(errorResult(400))
                .andExpect(jsonPath("$.message", is("Validation failed")))
                .andExpect(jsonPath("$.errors", hasSize(1)))
                .andExpect(jsonPath("$.errors[0].field", is("payload.courseCode")))
                .andExpect(jsonPath("$.errors[0].message", is("Course not found")));
    }

    @Test
    void malformedJsonReturns400() throws Exception {
        mvc.perform(post("/api/v1/jobs").contentType(MediaType.APPLICATION_JSON).content("{ not json"))
                .andExpect(errorResult(400));
    }

    // ---------- list ----------

    @Test
    void listPassesFiltersAndReturnsPage() throws Exception {
        Job failed = TestJobs.failedWith(JobErrorCode.SIMULATED_TEMPORARY).job();
        var summary = mapper.toSummary(failed, TestJobs.failedWith(JobErrorCode.SIMULATED_TEMPORARY).latest());
        when(jobService.list(eq(JobStatus.FAILED), eq(JobType.GRADE_REPORT), eq("mid"), eq(0), eq(20)))
                .thenReturn(new PageResponse<>(List.of(summary), 0, 20, 1));

        mvc.perform(get("/api/v1/jobs").param("status", "FAILED").param("type", "GRADE_REPORT").param("search", "mid"))
                .andExpect(apiResult(200))
                .andExpect(jsonPath("$.data.items", hasSize(1)))
                .andExpect(jsonPath("$.data.items[0].lastErrorCode", is("SIMULATED_TEMPORARY")))
                .andExpect(jsonPath("$.data.page", is(0)))
                .andExpect(jsonPath("$.data.size", is(20)))
                .andExpect(jsonPath("$.data.totalItems", is(1)));
    }

    @Test
    void listWithUnknownStatusReturns400() throws Exception {
        mvc.perform(get("/api/v1/jobs").param("status", "DONE"))
                .andExpect(errorResult(400))
                .andExpect(jsonPath("$.errors[0].field", is("status")));
        verifyNoInteractions(jobService);
    }

    @Test
    void listWithOutOfRangeSizeReturns400() throws Exception {
        mvc.perform(get("/api/v1/jobs").param("size", "500"))
                .andExpect(errorResult(400))
                .andExpect(jsonPath("$.errors[0].field", is("size")));
    }

    // ---------- get ----------

    @Test
    void getUnknownJobReturns404() throws Exception {
        UUID id = UUID.randomUUID();
        when(jobService.get(id)).thenThrow(new JobNotFoundException(id));

        mvc.perform(get("/api/v1/jobs/{id}", id))
                .andExpect(errorResult(404))
                .andExpect(jsonPath("$.description", is("We couldn't find that job. It may have been deleted.")));
    }

    @Test
    void getWithInvalidIdReturns400() throws Exception {
        mvc.perform(get("/api/v1/jobs/not-a-uuid"))
                .andExpect(errorResult(400))
                .andExpect(jsonPath("$.errors[0].field", is("id")));
    }

    @Test
    void retryableFlagReflectsTheRetryRule() throws Exception {
        var temporary = TestJobs.failedWith(JobErrorCode.SIMULATED_TEMPORARY);
        var permanent = TestJobs.failedWith(JobErrorCode.COURSE_CLOSED);
        var exhausted = TestJobs.failedWith(JobErrorCode.UNEXPECTED_ERROR, JobErrorCode.UNEXPECTED_ERROR,
                JobErrorCode.UNEXPECTED_ERROR);
        when(jobService.get(temporary.job().getId())).thenReturn(mapper.toDetail(temporary.job(), temporary.attempts()));
        when(jobService.get(permanent.job().getId())).thenReturn(mapper.toDetail(permanent.job(), permanent.attempts()));
        when(jobService.get(exhausted.job().getId())).thenReturn(mapper.toDetail(exhausted.job(), exhausted.attempts()));

        mvc.perform(get("/api/v1/jobs/{id}", temporary.job().getId()))
                .andExpect(apiResult(200))
                .andExpect(jsonPath("$.data.retryable", is(true)))
                .andExpect(jsonPath("$.data.attemptHistory[0].errorKind", is("TEMPORARY")));
        mvc.perform(get("/api/v1/jobs/{id}", permanent.job().getId()))
                .andExpect(jsonPath("$.data.retryable", is(false)))
                .andExpect(jsonPath("$.data.lastErrorCode", is("COURSE_CLOSED")))
                .andExpect(jsonPath("$.data.attemptHistory[0].errorKind", is("PERMANENT")));
        mvc.perform(get("/api/v1/jobs/{id}", exhausted.job().getId()))
                .andExpect(jsonPath("$.data.retryable", is(false)))
                .andExpect(jsonPath("$.data.attempts", is(3)))
                .andExpect(jsonPath("$.data.attemptHistory", hasSize(3)));
    }

    // ---------- retry ----------

    @Test
    void retryReturns202() throws Exception {
        JobDetail detail = queuedDetail();
        when(retryService.retry(detail.id())).thenReturn(detail);

        mvc.perform(post("/api/v1/jobs/{id}/retry", detail.id()))
                .andExpect(apiResult(202))
                .andExpect(jsonPath("$.data.status", is("QUEUED")));
    }

    @Test
    void illegalRetryReturns409WithUserDescription() throws Exception {
        UUID id = UUID.randomUUID();
        when(retryService.retry(id)).thenThrow(new RetryNotAllowedException("Retry not allowed",
                "This job failed with a permanent error and can't be retried."));

        mvc.perform(post("/api/v1/jobs/{id}/retry", id))
                .andExpect(errorResult(409))
                .andExpect(jsonPath("$.message", is("Retry not allowed")))
                .andExpect(jsonPath("$.description", is("This job failed with a permanent error and can't be retried.")))
                .andExpect(jsonPath("$.errors", hasSize(0)));
    }

    @Test
    void retryOfMissingJobReturns404() throws Exception {
        UUID id = UUID.randomUUID();
        when(retryService.retry(id)).thenThrow(new JobNotFoundException(id));

        mvc.perform(post("/api/v1/jobs/{id}/retry", id)).andExpect(errorResult(404));
    }

    @Test
    void versionConflictReturns409() throws Exception {
        UUID id = UUID.randomUUID();
        when(retryService.retry(id)).thenThrow(new ObjectOptimisticLockingFailureException(Job.class, id));

        mvc.perform(post("/api/v1/jobs/{id}/retry", id))
                .andExpect(errorResult(409))
                .andExpect(jsonPath("$.description", is(
                        "This job was changed at the same time by something else. Refresh and try again.")));
    }

    // ---------- delete ----------

    @Test
    void deleteReturns200WithNullData() throws Exception {
        UUID id = UUID.randomUUID();

        mvc.perform(delete("/api/v1/jobs/{id}", id))
                .andExpect(apiResult(200))
                .andExpect(jsonPath("$.data", nullValue()))
                .andExpect(jsonPath("$.description", is("Job deleted.")));
    }

    @Test
    void deleteOfRunningJobReturns409() throws Exception {
        UUID id = UUID.randomUUID();
        doThrow(new IllegalJobTransitionException(id, JobStatus.PROCESSING, "delete")).when(jobService).delete(id);

        mvc.perform(delete("/api/v1/jobs/{id}", id))
                .andExpect(errorResult(409))
                .andExpect(jsonPath("$.description", is("This action isn't allowed while the job is processing.")));
    }

    @Test
    void deleteOfMissingJobReturns404() throws Exception {
        UUID id = UUID.randomUUID();
        doThrow(new JobNotFoundException(id)).when(jobService).delete(id);

        mvc.perform(delete("/api/v1/jobs/{id}", id)).andExpect(errorResult(404));
    }

    // ---------- other endpoints and framework errors ----------

    @Test
    void statsAreWrapped() throws Exception {
        when(statsService.stats()).thenReturn(new StatsResponse(Map.of(JobStatus.COMPLETED, 3L), 10, 20,
                new StatsResponse.FailureRate(0.25, 0.25, 0.0), 0.5));

        mvc.perform(get("/api/v1/jobs/stats"))
                .andExpect(apiResult(200))
                .andExpect(jsonPath("$.data.byStatus.COMPLETED", is(3)))
                .andExpect(jsonPath("$.data.failureRate.total", is(0.25)))
                .andExpect(jsonPath("$.data.retryRecoveryRate", is(0.5)));
    }

    @Test
    void referenceDataIsWrapped() throws Exception {
        when(referenceDataService.jobTypes()).thenReturn(List.of());

        mvc.perform(get("/api/v1/job-types")).andExpect(apiResult(200)).andExpect(jsonPath("$.data", hasSize(0)));
        mvc.perform(get("/api/v1/courses")).andExpect(apiResult(200));
        mvc.perform(get("/api/v1/terms")).andExpect(apiResult(200));
        mvc.perform(get("/api/v1/notification-templates")).andExpect(apiResult(200));
    }

    @Test
    void unexpectedExceptionReturns500InApiResult() throws Exception {
        when(jobService.list(isNull(), isNull(), isNull(), anyInt(), anyInt()))
                .thenThrow(new IllegalStateException("database exploded"));

        ResultActions result = mvc.perform(get("/api/v1/jobs"));

        result.andExpect(errorResult(500))
                .andExpect(jsonPath("$.description", is("Something went wrong on our side. Please try again.")));
    }

    @Test
    void unsupportedMethodAndUnknownRouteAreWrapped() throws Exception {
        mvc.perform(put("/api/v1/jobs/stats")).andExpect(errorResult(405));
        mvc.perform(get("/api/v1/nothing-here")).andExpect(errorResult(404));
    }
}
