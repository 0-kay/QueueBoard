package com.liberty.queueboardbe.service.job.impl;

import com.liberty.queueboardbe.config.QueueBoardProperties;
import com.liberty.queueboardbe.exception.JobNotFoundException;
import com.liberty.queueboardbe.model.common.PageResponse;
import com.liberty.queueboardbe.model.job.Job;
import com.liberty.queueboardbe.model.job.JobAttempt;
import com.liberty.queueboardbe.model.job.JobDetail;
import com.liberty.queueboardbe.model.job.JobStatus;
import com.liberty.queueboardbe.model.job.JobSummary;
import com.liberty.queueboardbe.model.job.JobType;
import com.liberty.queueboardbe.model.job.SubmitJobRequest;
import com.liberty.queueboardbe.repository.job.JobAttemptRepository;
import com.liberty.queueboardbe.repository.job.JobRepository;
import com.liberty.queueboardbe.service.job.JobMapper;
import com.liberty.queueboardbe.service.job.JobPayloadValidator;
import com.liberty.queueboardbe.service.job.JobService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Submitting only inserts a QUEUED row; there is no in-memory queue, so status and queue never disagree. */
@Service
public class JobServiceImpl implements JobService {

    private static final Logger log = LoggerFactory.getLogger(JobServiceImpl.class);

    private final JobRepository jobs;
    private final JobAttemptRepository attempts;
    private final JobPayloadValidator payloadValidator;
    private final JobMapper mapper;
    private final QueueBoardProperties properties;
    private final Clock clock;

    public JobServiceImpl(JobRepository jobs, JobAttemptRepository attempts, JobPayloadValidator payloadValidator,
                      JobMapper mapper, QueueBoardProperties properties, Clock clock) {
        this.jobs = jobs;
        this.attempts = attempts;
        this.payloadValidator = payloadValidator;
        this.mapper = mapper;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    @Transactional
    public JobDetail submit(SubmitJobRequest request) {
        JobPayloadValidator.ValidatedPayload valid = payloadValidator.validate(request.type(), request.payload());
        Job job = Job.submit(request.name().trim(), valid.type(), valid.json(), properties.jobs().maxAttempts(),
                clock.instant());
        jobs.save(job);
        log.info("Job {} submitted: type={}, name='{}'", job.getId(), job.getType(), job.getName());
        return mapper.toDetail(job, List.of());
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<JobSummary> list(JobStatus status, JobType type, String search, int page, int size) {
        Page<Job> result = jobs.search(status, type, likePattern(search), PageRequest.of(page, size));
        List<UUID> ids = result.getContent().stream().map(Job::getId).toList();
        Map<UUID, JobAttempt> latest = ids.isEmpty() ? Map.of()
                : attempts.findLatestForJobs(ids).stream()
                .collect(Collectors.toMap(JobAttempt::getJobId, Function.identity()));
        List<JobSummary> items = result.getContent().stream()
                .map(job -> mapper.toSummary(job, latest.get(job.getId())))
                .toList();
        return new PageResponse<>(items, page, size, result.getTotalElements());
    }

    @Override
    @Transactional(readOnly = true)
    public JobDetail get(UUID id) {
        Job job = findActive(id);
        return mapper.toDetail(job, attempts.findByJobIdOrderByAttemptNumberAsc(id));
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        Job job = findActive(id);
        job.markDeleted(clock.instant());
        jobs.saveAndFlush(job);
        log.info("Job {} deleted (was {})", id, job.getStatus());
    }

    private Job findActive(UUID id) {
        return jobs.findByIdAndDeletedAtIsNull(id).orElseThrow(() -> new JobNotFoundException(id));
    }

    /** Case-insensitive "contains", with LIKE wildcards in the input escaped. */
    private static String likePattern(String search) {
        if (search == null || search.isBlank()) {
            return null;
        }
        String escaped = search.trim().toLowerCase(Locale.ROOT)
                .replace("!", "!!").replace("%", "!%").replace("_", "!_");
        return "%" + escaped + "%";
    }
}
