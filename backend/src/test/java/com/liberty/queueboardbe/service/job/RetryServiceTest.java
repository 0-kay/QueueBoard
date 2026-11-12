package com.liberty.queueboardbe.service.job;

import com.liberty.queueboardbe.TestJobs.FailedJob;
import com.liberty.queueboardbe.TestJobs;
import com.liberty.queueboardbe.exception.JobNotFoundException;
import com.liberty.queueboardbe.exception.RetryNotAllowedException;
import com.liberty.queueboardbe.model.job.Job;
import com.liberty.queueboardbe.model.job.JobDetail;
import com.liberty.queueboardbe.model.job.JobErrorCode;
import com.liberty.queueboardbe.model.job.JobStatus;
import com.liberty.queueboardbe.repository.job.JobAttemptRepository;
import com.liberty.queueboardbe.repository.job.JobRepository;
import com.liberty.queueboardbe.service.job.impl.RetryServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RetryServiceTest {

    @Mock
    private JobRepository jobs;
    @Mock
    private JobAttemptRepository attempts;

    private final RetryPolicy policy = new RetryPolicy();
    private RetryService retryService;

    @BeforeEach
    void setUp() {
        JobMapper mapper = new JobMapper(policy, JsonMapper.builder().build());
        Clock clock = Clock.fixed(TestJobs.NOW.plusSeconds(60), ZoneOffset.UTC);
        retryService = new RetryServiceImpl(jobs, attempts, policy, mapper, clock);
    }

    private void stub(FailedJob failed) {
        Job job = failed.job();
        when(jobs.findByIdAndDeletedAtIsNull(job.getId())).thenReturn(Optional.of(job));
        when(attempts.findByJobIdOrderByAttemptNumberAsc(job.getId())).thenReturn(failed.attempts());
    }

    @ParameterizedTest
    @EnumSource(value = JobErrorCode.class, names = {"SIMULATED_TEMPORARY", "UNEXPECTED_ERROR"})
    void temporaryFailureUnderTheCapIsRetryableAndRequeues(JobErrorCode temporary) {
        FailedJob failed = TestJobs.failedWith(temporary);
        stub(failed);
        assertThat(policy.isRetryable(failed.job(), failed.latest())).isTrue();

        JobDetail detail = retryService.retry(failed.job().getId());

        assertThat(detail.status()).isEqualTo(JobStatus.QUEUED);
        assertThat(detail.id()).isEqualTo(failed.job().getId());
        assertThat(detail.attempts()).isEqualTo(1);
        assertThat(detail.retryable()).isFalse();
        verify(jobs).saveAndFlush(failed.job());
    }

    @Test
    void secondTemporaryFailureIsStillRetryable() {
        FailedJob failed = TestJobs.failedWith(JobErrorCode.UNEXPECTED_ERROR, JobErrorCode.SIMULATED_TEMPORARY);
        stub(failed);

        assertThat(policy.isRetryable(failed.job(), failed.latest())).isTrue();
        assertThat(retryService.retry(failed.job().getId()).status()).isEqualTo(JobStatus.QUEUED);
    }

    @ParameterizedTest
    @EnumSource(value = JobErrorCode.class,
            names = {"COURSE_CLOSED", "TEMPLATE_INACTIVE", "TERM_CLOSED", "SIMULATED_PERMANENT"})
    void permanentFailureIsNotRetryable(JobErrorCode permanent) {
        FailedJob failed = TestJobs.failedWith(permanent);
        stub(failed);
        assertThat(policy.isRetryable(failed.job(), failed.latest())).isFalse();

        assertThatThrownBy(() -> retryService.retry(failed.job().getId()))
                .isInstanceOf(RetryNotAllowedException.class)
                .hasMessage("Retry not allowed")
                .extracting(e -> ((RetryNotAllowedException) e).getDescription())
                .isEqualTo("This job failed with a permanent error and can't be retried.");
        verify(jobs, never()).saveAndFlush(any());
    }

    @Test
    void permanentErrorAfterEarlierTemporaryIsNotRetryable() {
        FailedJob failed = TestJobs.failedWith(JobErrorCode.UNEXPECTED_ERROR, JobErrorCode.COURSE_CLOSED);
        stub(failed);

        assertThat(policy.isRetryable(failed.job(), failed.latest())).isFalse();
        assertThatThrownBy(() -> retryService.retry(failed.job().getId())).isInstanceOf(RetryNotAllowedException.class);
    }

    @Test
    void exhaustedAttemptsAreNotRetryableEvenWhenTemporary() {
        FailedJob failed = TestJobs.failedWith(JobErrorCode.UNEXPECTED_ERROR, JobErrorCode.UNEXPECTED_ERROR,
                JobErrorCode.SIMULATED_TEMPORARY);
        stub(failed);
        assertThat(failed.job().getAttempts()).isEqualTo(3);
        assertThat(policy.isRetryable(failed.job(), failed.latest())).isFalse();

        assertThatThrownBy(() -> retryService.retry(failed.job().getId()))
                .isInstanceOf(RetryNotAllowedException.class)
                .hasMessageContaining("exhausted");
        verify(jobs, never()).saveAndFlush(any());
    }

    @ParameterizedTest
    @EnumSource(value = JobStatus.class, names = {"QUEUED", "PROCESSING", "COMPLETED"})
    void onlyFailedJobsCanBeRetried(JobStatus status) {
        Job job = TestJobs.inStatus(status);
        when(jobs.findByIdAndDeletedAtIsNull(job.getId())).thenReturn(Optional.of(job));
        when(attempts.findByJobIdOrderByAttemptNumberAsc(job.getId())).thenReturn(List.of());

        assertThat(policy.isRetryable(job, null)).isFalse();
        assertThatThrownBy(() -> retryService.retry(job.getId()))
                .isInstanceOf(RetryNotAllowedException.class)
                .hasMessageContaining(status.name());
        verify(jobs, never()).saveAndFlush(any());
    }

    @Test
    void missingOrDeletedJobIsNotFound() {
        UUID id = UUID.randomUUID();
        when(jobs.findByIdAndDeletedAtIsNull(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> retryService.retry(id)).isInstanceOf(JobNotFoundException.class);
    }
}
