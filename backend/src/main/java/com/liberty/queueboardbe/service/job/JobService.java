package com.liberty.queueboardbe.service.job;

import com.liberty.queueboardbe.model.common.PageResponse;
import com.liberty.queueboardbe.model.job.JobDetail;
import com.liberty.queueboardbe.model.job.JobStatus;
import com.liberty.queueboardbe.model.job.JobSummary;
import com.liberty.queueboardbe.model.job.JobType;
import com.liberty.queueboardbe.model.job.SubmitJobRequest;

import java.util.UUID;

/** Submit, list, inspect and soft-delete jobs. */
public interface JobService {

    /** Validates the payload and stores the job as QUEUED; the worker picks it up from the table. */
    JobDetail submit(SubmitJobRequest request);

    /** Newest first; null filters are ignored; {@code search} matches the name, case-insensitively. */
    PageResponse<JobSummary> list(JobStatus status, JobType type, String search, int page, int size);

    /** @throws com.liberty.queueboardbe.exception.JobNotFoundException missing or soft-deleted */
    JobDetail get(UUID id);

    /** Soft delete, COMPLETED or FAILED only. */
    void delete(UUID id);
}
