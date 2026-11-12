package com.liberty.queueboardbe.service.worker;

import com.liberty.queueboardbe.config.QueueBoardProperties;
import com.liberty.queueboardbe.config.WorkerConfig;
import com.liberty.queueboardbe.exception.JobProcessingException;
import com.liberty.queueboardbe.model.job.JobErrorCode;
import com.liberty.queueboardbe.model.payload.JobPayload;
import com.liberty.queueboardbe.model.payload.SimulateFailure;
import com.liberty.queueboardbe.repository.job.JobRepository;
import com.liberty.queueboardbe.service.job.JobPayloadValidator;
import com.liberty.queueboardbe.service.processing.JobProcessor;
import com.liberty.queueboardbe.service.worker.JobTransitions.Claim;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Semaphore;

/**
 * The worker. The {@code job} table is the queue: every poll it claims as many QUEUED jobs as there
 * are free threads (oldest first) and runs each one on the pool. It never claims more than it can run,
 * so nothing waits in memory and a job's status is always the truth.
 *
 * <p>Running a job never throws: every exception is mapped to a JobErrorCode (UNEXPECTED_ERROR if
 * unknown) and recorded, so no job stays PROCESSING because of an exception.
 */
@Component
@ConditionalOnProperty(prefix = "queueboard.worker", name = "enabled", havingValue = "true", matchIfMissing = true)
public class JobWorker {

    private static final Logger log = LoggerFactory.getLogger(JobWorker.class);
    /** MDC key printed by {@code logging.pattern.level}, so every line a job writes carries its id. */
    private static final String JOB_ID = "jobId";

    private final JobRepository jobs;
    private final JobTransitions transitions;
    private final JobProcessor processor;
    private final JobPayloadValidator payloads;
    private final ThreadPoolTaskExecutor executor;
    private final Semaphore freeThreads;

    public JobWorker(JobRepository jobs, JobTransitions transitions, JobProcessor processor,
                     JobPayloadValidator payloads, @Qualifier(WorkerConfig.JOB_EXECUTOR) ThreadPoolTaskExecutor executor,
                     QueueBoardProperties properties) {
        this.jobs = jobs;
        this.transitions = transitions;
        this.processor = processor;
        this.payloads = payloads;
        this.executor = executor;
        this.freeThreads = new Semaphore(properties.worker().threads());
        log.info("Worker started: {} threads, polling every {} ms", properties.worker().threads(),
                properties.worker().pollIntervalMs());
    }

    @Scheduled(fixedDelayString = "${queueboard.worker.poll-interval-ms:500}")
    public void poll() {
        int free = freeThreads.availablePermits();
        if (free == 0) {
            return;
        }
        for (UUID id : jobs.findQueuedIds(PageRequest.of(0, free))) {
            if (!freeThreads.tryAcquire()) {
                return;
            }
            Optional<Claim> claim;
            try {
                claim = transitions.claim(id);
            } catch (RuntimeException e) {
                freeThreads.release();
                log.warn("Claim of job {} failed; it stays QUEUED for the next poll", id, e);
                continue;
            }
            if (claim.isEmpty()) {
                freeThreads.release(); // another thread got it, or it was deleted
                continue;
            }
            dispatch(claim.get());
        }
    }

    private void dispatch(Claim claim) {
        try {
            executor.execute(() -> {
                try {
                    run(claim);
                } finally {
                    freeThreads.release();
                }
            });
        } catch (TaskRejectedException e) {
            freeThreads.release();
            log.error("Worker pool rejected job {}", claim.jobId(), e);
            transitions.fail(claim, JobErrorCode.UNEXPECTED_ERROR, "Worker pool rejected the job");
        }
    }

    private void run(Claim claim) {
        try (MDC.MDCCloseable ignored = MDC.putCloseable(JOB_ID, claim.jobId().toString())) {
            long started = System.nanoTime();
            log.info("Started {} attempt {}", claim.type(), claim.attemptNumber());
            try {
                JobPayload payload = payloads.parseStored(claim.type(), claim.payload());
                simulateFailureIfRequested(payload, claim.attemptNumber());
                String summary = processor.process(claim.jobId(), payload);
                transitions.complete(claim, summary);
                log.info("Completed in {} ms: {}", elapsedMs(started), summary);
            } catch (JobProcessingException e) {
                log.info("Failed in {} ms: {} ({}) {}", elapsedMs(started), e.getCode(), e.getCode().kind(),
                        e.getMessage());
                recordFailure(claim, e.getCode(), e.getMessage());
            } catch (Exception e) {
                log.warn("Failed in {} ms with an unexpected error", elapsedMs(started), e);
                recordFailure(claim, JobErrorCode.UNEXPECTED_ERROR, describe(e));
            }
        }
    }

    /**
     * Demo switch, applied before any work begins. PERMANENT fails every attempt. TEMPORARY fails only
     * the first, so a manual retry shows a temporary error going away.
     */
    private static void simulateFailureIfRequested(JobPayload payload, int attemptNumber) {
        if (payload.simulateFailure() == SimulateFailure.PERMANENT) {
            throw new JobProcessingException(JobErrorCode.SIMULATED_PERMANENT, "Simulated permanent failure");
        }
        if (payload.simulateFailure() == SimulateFailure.TEMPORARY && attemptNumber == 1) {
            throw new JobProcessingException(JobErrorCode.SIMULATED_TEMPORARY,
                    "Simulated temporary failure on the first attempt; a retry will succeed");
        }
    }

    private void recordFailure(Claim claim, JobErrorCode code, String message) {
        try {
            transitions.fail(claim, code, message);
        } catch (Exception e) {
            // Only reachable if the database itself is failing. The job stays PROCESSING; a stuck-job
            // reaper is out of scope (see README, known limitations).
            log.error("Could not record the failure; the job stays PROCESSING", e);
        }
    }

    private static long elapsedMs(long startedNanos) {
        return (System.nanoTime() - startedNanos) / 1_000_000;
    }

    /** Root cause class and message, e.g. "SQLTimeoutException: lock wait timeout". */
    private static String describe(Throwable e) {
        Throwable root = e;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        return root.getClass().getSimpleName() + (root.getMessage() == null ? "" : ": " + root.getMessage());
    }
}
