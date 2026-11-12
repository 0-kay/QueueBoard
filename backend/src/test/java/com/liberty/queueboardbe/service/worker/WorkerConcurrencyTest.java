package com.liberty.queueboardbe.service.worker;

import com.liberty.queueboardbe.WorkerTest;
import com.liberty.queueboardbe.model.job.JobStatus;
import com.liberty.queueboardbe.model.job.SubmitJobRequest;
import com.liberty.queueboardbe.service.job.JobService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/** Many jobs through the real poller and its five threads: none lost, none run twice. */
class WorkerConcurrencyTest extends WorkerTest {

    private static final int JOBS = 40;
    private static final List<String> COURSES = List.of("CSCI-110", "MATH-201", "HIST-150");

    @Autowired
    private JobService jobService;
    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void everyJobIsClaimedOnceAndCompletes() {
        List<UUID> ids = IntStream.range(0, JOBS).mapToObj(i -> jobService.submit(new SubmitJobRequest(
                "Batch " + i, "NOTIFICATION_BATCH", Map.of("templateCode", "FORUM_DIGEST",
                        "courseCode", COURSES.get(i % COURSES.size()), "channel", i % 2 == 0 ? "EMAIL" : "PUSH"))).id())
                .toList();

        await().atMost(Duration.ofSeconds(60)).pollInterval(Duration.ofMillis(100)).untilAsserted(() ->
                assertThat(ids).allSatisfy(id -> assertThat(jobService.get(id).status()).isEqualTo(JobStatus.COMPLETED)));

        for (UUID id : ids) {
            Map<String, Object> job = jdbc.queryForMap("SELECT attempts, result_summary FROM job WHERE id = ?", id);
            assertThat(job.get("ATTEMPTS")).as("attempts of %s", id).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM job_attempt WHERE job_id = ? AND outcome = 'COMPLETED'",
                    Integer.class, id)).isEqualTo(1);

            int enrolled = jdbc.queryForObject("""
                    SELECT COUNT(*) FROM enrollment e JOIN course c ON c.id = e.course_id
                    WHERE c.code = ?""", Integer.class, COURSES.get(ids.indexOf(id) % COURSES.size()));
            assertThat(jdbc.queryForObject("""
                    SELECT COUNT(DISTINCT student_id) FROM notification_delivery WHERE job_id = ? AND status = 'SENT'""",
                    Integer.class, id)).isEqualTo(enrolled);
            assertThat(job.get("RESULT_SUMMARY")).isEqualTo(enrolled + " of " + enrolled + " sent");
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM job WHERE status IN ('QUEUED', 'PROCESSING')",
                Integer.class)).isZero();
    }
}
