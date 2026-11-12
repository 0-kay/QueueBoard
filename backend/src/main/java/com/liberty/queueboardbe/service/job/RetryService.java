package com.liberty.queueboardbe.service.job;

import com.liberty.queueboardbe.model.job.JobDetail;

import java.util.UUID;

/** Manual retry: FAILED back to QUEUED with the same id, when {@link RetryPolicy} allows it. */
public interface RetryService {

    /**
     * @throws com.liberty.queueboardbe.exception.JobNotFoundException     missing or soft-deleted (404)
     * @throws com.liberty.queueboardbe.exception.RetryNotAllowedException the retry rule rejects it (409)
     * @throws org.springframework.dao.OptimisticLockingFailureException   concurrent change (409)
     */
    JobDetail retry(UUID id);
}
