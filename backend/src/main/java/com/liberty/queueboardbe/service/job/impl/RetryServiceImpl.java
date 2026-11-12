package com.liberty.queueboardbe.service.job.impl;

import com.liberty.queueboardbe.exception.JobNotFoundException;
import com.liberty.queueboardbe.exception.RetryNotAllowedException;
import com.liberty.queueboardbe.model.job.Job;
import com.liberty.queueboardbe.model.job.JobAttempt;
import com.liberty.queueboardbe.model.job.JobDetail;
import com.liberty.queueboardbe.repository.job.JobAttemptRepository;
import com.liberty.queueboardbe.repository.job.JobRepository;
import com.liberty.queueboardbe.service.job.JobMapper;
import com.liberty.queueboardbe.service.job.RetryPolicy;
import com.liberty.queueboardbe.service.job.RetryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

@Service
public class RetryServiceImpl implements RetryService {

    private static final Logger log = LoggerFactory.getLogger(RetryServiceImpl.class);

    private final JobRepository jobs;
    private final JobAttemptRepository attempts;
    private final RetryPolicy policy;
    private final JobMapper mapper;
    private final Clock clock;

    public RetryServiceImpl(JobRepository jobs, JobAttemptRepository attempts, RetryPolicy policy, JobMapper mapper,
                        Clock clock) {
        this.jobs = jobs;
        this.attempts = attempts;
        this.policy = policy;
        this.mapper = mapper;
        this.clock = clock;
    }

    @Override
    @Transactional
    public JobDetail retry(UUID id) {
        Job job = jobs.findByIdAndDeletedAtIsNull(id).orElseThrow(() -> new JobNotFoundException(id));
        List<JobAttempt> history = attempts.findByJobIdOrderByAttemptNumberAsc(id);
        JobAttempt latest = history.isEmpty() ? null : history.getLast();

        RetryPolicy.Decision decision = policy.decide(job, latest);
        if (!decision.allowed()) {
            log.info("Retry of job {} rejected: {}", id, decision.message());
            throw new RetryNotAllowedException(decision.message(), decision.description());
        }
        job.requeue(clock.instant());
        jobs.saveAndFlush(job);
        log.info("Job {} re-queued for attempt {} of {}", id, job.getAttempts() + 1, job.getMaxAttempts());
        return mapper.toDetail(job, history);
    }
}
