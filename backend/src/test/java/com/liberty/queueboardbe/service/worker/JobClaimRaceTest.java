package com.liberty.queueboardbe.service.worker;

import com.liberty.queueboardbe.DatabaseTest;
import com.liberty.queueboardbe.model.job.JobType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/** Many threads race to claim the same jobs; the conditional UPDATE lets exactly one win each. */
class JobClaimRaceTest extends DatabaseTest {

    private static final int JOBS = 25;
    private static final int THREADS = 8;

    @Autowired
    private JobTransitions transitions;

    @Test
    void everyJobIsClaimedExactlyOnce() throws Exception {
        List<UUID> ids = IntStream.range(0, JOBS)
                .mapToObj(i -> insertJob(JobType.GRADE_REPORT, "{\"courseCode\":\"CSCI-110\"}"))
                .toList();
        Queue<UUID> won = new ConcurrentLinkedQueue<>();
        CountDownLatch start = new CountDownLatch(1);

        try (ExecutorService pool = Executors.newFixedThreadPool(THREADS)) {
            List<Future<?>> racers = IntStream.range(0, THREADS).<Future<?>>mapToObj(t -> pool.submit(() -> {
                start.await();
                for (UUID id : ids) {
                    transitions.claim(id).ifPresent(claim -> won.add(claim.jobId()));
                }
                return null;
            })).toList();
            start.countDown();
            for (Future<?> racer : racers) {
                racer.get();
            }
        }

        assertThat(won).hasSize(JOBS).doesNotHaveDuplicates().containsExactlyInAnyOrderElementsOf(ids);
        for (UUID id : ids) {
            assertThat(count("SELECT attempts FROM job WHERE id = ? AND status = 'PROCESSING'", id)).isEqualTo(1);
            assertThat(count("SELECT COUNT(*) FROM job_attempt WHERE job_id = ?", id)).isEqualTo(1);
        }
    }
}
