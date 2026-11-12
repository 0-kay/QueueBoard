package com.liberty.queueboardbe.model.job;

import com.liberty.queueboardbe.TestJobs;
import com.liberty.queueboardbe.exception.IllegalJobTransitionException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.Instant;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static com.liberty.queueboardbe.TestJobs.NOW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JobLifecycleTest {

    private static final Instant LATER = NOW.plusSeconds(5);

    /** Each action and the only statuses it is legal from. */
    private static final Map<String, Set<JobStatus>> LEGAL_FROM = Map.of(
            "start", Set.of(JobStatus.QUEUED),
            "complete", Set.of(JobStatus.PROCESSING),
            "fail", Set.of(JobStatus.PROCESSING),
            "requeue", Set.of(JobStatus.FAILED),
            "delete", Set.of(JobStatus.COMPLETED, JobStatus.FAILED));

    private static final Map<String, Consumer<Job>> ACTIONS = Map.of(
            "start", job -> job.start(LATER),
            "complete", job -> job.complete("summary", LATER),
            "fail", job -> job.fail(LATER),
            "requeue", job -> job.requeue(LATER),
            "delete", job -> job.markDeleted(LATER));

    @Test
    void newJobIsQueuedWithNoAttempts() {
        Job job = TestJobs.queued();

        assertThat(job.getStatus()).isEqualTo(JobStatus.QUEUED);
        assertThat(job.getAttempts()).isZero();
        assertThat(job.getMaxAttempts()).isEqualTo(3);
        assertThat(job.getCreatedAt()).isEqualTo(NOW);
        assertThat(job.isDeleted()).isFalse();
    }

    @Test
    void happyPathQueuedProcessingCompleted() {
        Job job = TestJobs.queued();

        job.start(LATER);
        assertThat(job.getStatus()).isEqualTo(JobStatus.PROCESSING);
        assertThat(job.getAttempts()).isEqualTo(1);

        job.complete("25 students graded, 4 awards", LATER);
        assertThat(job.getStatus()).isEqualTo(JobStatus.COMPLETED);
        assertThat(job.getResultSummary()).isEqualTo("25 students graded, 4 awards");
        assertThat(job.getUpdatedAt()).isEqualTo(LATER);
    }

    @Test
    void failThenRequeueThenSucceedCountsBothAttempts() {
        Job job = TestJobs.queued();

        job.start(LATER);
        job.fail(LATER);
        assertThat(job.getStatus()).isEqualTo(JobStatus.FAILED);

        job.requeue(LATER);
        assertThat(job.getStatus()).isEqualTo(JobStatus.QUEUED);
        assertThat(job.getAttempts()).isEqualTo(1);

        job.start(LATER);
        job.complete("ok", LATER);
        assertThat(job.getAttempts()).isEqualTo(2);
        assertThat(job.getStatus()).isEqualTo(JobStatus.COMPLETED);
    }

    @Test
    void deleteSetsDeletedAtOnFinishedJobs() {
        Job completed = TestJobs.inStatus(JobStatus.COMPLETED);
        completed.markDeleted(LATER);
        assertThat(completed.isDeleted()).isTrue();
        assertThat(completed.getDeletedAt()).isEqualTo(LATER);

        Job failed = TestJobs.inStatus(JobStatus.FAILED);
        failed.markDeleted(LATER);
        assertThat(failed.isDeleted()).isTrue();
    }

    static Stream<Arguments> everyActionFromEveryStatus() {
        return ACTIONS.keySet().stream().sorted().flatMap(action -> Arrays.stream(JobStatus.values())
                .map(status -> Arguments.of(action, status, LEGAL_FROM.get(action).contains(status))));
    }

    @ParameterizedTest(name = "{0} from {1}: legal={2}")
    @MethodSource("everyActionFromEveryStatus")
    void onlyLegalTransitionsAreAllowed(String action, JobStatus from, boolean legal) {
        Job job = TestJobs.inStatus(from);
        int attemptsBefore = job.getAttempts();

        if (legal) {
            ACTIONS.get(action).accept(job);
            return;
        }
        assertThatThrownBy(() -> ACTIONS.get(action).accept(job))
                .isInstanceOf(IllegalJobTransitionException.class)
                .hasMessageContaining(from.name())
                .satisfies(e -> assertThat(((IllegalJobTransitionException) e).getCurrentStatus()).isEqualTo(from));
        assertThat(job.getStatus()).as("status unchanged").isEqualTo(from);
        assertThat(job.getAttempts()).as("attempts unchanged").isEqualTo(attemptsBefore);
        assertThat(job.isDeleted()).isFalse();
    }
}
