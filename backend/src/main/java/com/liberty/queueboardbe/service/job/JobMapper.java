package com.liberty.queueboardbe.service.job;

import com.liberty.queueboardbe.model.job.AttemptResponse;
import com.liberty.queueboardbe.model.job.Job;
import com.liberty.queueboardbe.model.job.JobAttempt;
import com.liberty.queueboardbe.model.job.JobDetail;
import com.liberty.queueboardbe.model.job.JobErrorCode;
import com.liberty.queueboardbe.model.job.JobSummary;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;

/** Entity to DTO. Entities never leave the service layer. */
@Component
public class JobMapper {

    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {
    };

    private final RetryPolicy retryPolicy;
    private final JsonMapper jsonMapper;

    public JobMapper(RetryPolicy retryPolicy, JsonMapper jsonMapper) {
        this.retryPolicy = retryPolicy;
        this.jsonMapper = jsonMapper;
    }

    public JobSummary toSummary(Job job, JobAttempt latestAttempt) {
        return new JobSummary(job.getId(), job.getName(), job.getType(), job.getStatus(), job.getAttempts(),
                job.getMaxAttempts(), lastErrorCode(latestAttempt), retryPolicy.isRetryable(job, latestAttempt),
                job.getCreatedAt(), job.getUpdatedAt());
    }

    /** @param attempts all attempts of the job, ordered by attempt number */
    public JobDetail toDetail(Job job, List<JobAttempt> attempts) {
        JobAttempt latest = attempts.isEmpty() ? null : attempts.getLast();
        return new JobDetail(job.getId(), job.getName(), job.getType(), job.getStatus(), job.getAttempts(),
                job.getMaxAttempts(), lastErrorCode(latest), retryPolicy.isRetryable(job, latest),
                job.getCreatedAt(), job.getUpdatedAt(), jsonMapper.readValue(job.getPayload(), MAP),
                job.getResultSummary(), attempts.stream().map(JobMapper::toAttempt).toList());
    }

    private static AttemptResponse toAttempt(JobAttempt a) {
        return new AttemptResponse(a.getAttemptNumber(), a.getStartedAt(), a.getFinishedAt(), a.getOutcome(),
                a.getErrorCode(), a.getErrorKind(), a.getErrorMessage());
    }

    private static JobErrorCode lastErrorCode(JobAttempt latest) {
        return latest == null ? null : latest.getErrorCode();
    }
}
