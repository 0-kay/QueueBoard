package com.liberty.queueboardbe;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Through HTTP, with the real worker: submit, run, fail, retry, delete, and the stats that result. */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class EndToEndTest extends WorkerTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JsonMapper json;
    @Autowired
    private JdbcTemplate jdbc;

    private String submit(String body) throws Exception {
        String location = mvc.perform(post("/api/v1/jobs").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getHeader("Location");
        return location.substring(location.lastIndexOf('/') + 1);
    }

    private JsonNode job(String id) throws Exception {
        return data(mvc.perform(get("/api/v1/jobs/{id}", id)).andExpect(status().isOk()));
    }

    private JsonNode data(org.springframework.test.web.servlet.ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsString()).get("data");
    }

    private JsonNode awaitStatus(String id, String status) {
        await().atMost(Duration.ofSeconds(20)).pollInterval(Duration.ofMillis(50))
                .until(() -> job(id).get("status").asString(), status::equals);
        try {
            return job(id);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    @Order(1)
    void submittedJobRunsToCompleted() throws Exception {
        String id = submit("""
                { "name": "Quiz grades MATH-201", "type": "GRADE_REPORT", "payload": { "courseCode": "MATH-201" } }""");

        JsonNode job = awaitStatus(id, "COMPLETED");

        assertThat(job.get("attempts").asInt()).isEqualTo(1);
        assertThat(job.get("retryable").asBoolean()).isFalse();
        assertThat(job.get("resultSummary").asString()).matches("\\d+ students graded, \\d+ awards");
        assertThat(job.get("attemptHistory")).hasSize(1);
        assertThat(job.get("attemptHistory").get(0).get("outcome").asString()).isEqualTo("COMPLETED");
    }

    @Test
    @Order(2)
    void temporaryFailureThenRetryCompletes() throws Exception {
        String id = submit("""
                { "name": "FA26 late fees", "type": "BILLING_ASSESSMENT",
                  "payload": { "termCode": "FA26", "applyLateFees": true, "simulateFailure": "TEMPORARY" } }""");

        JsonNode failed = awaitStatus(id, "FAILED");
        assertThat(failed.get("lastErrorCode").asString()).isEqualTo("SIMULATED_TEMPORARY");
        assertThat(failed.get("retryable").asBoolean()).isTrue();

        JsonNode requeued = data(mvc.perform(post("/api/v1/jobs/{id}/retry", id)).andExpect(status().isAccepted()));
        assertThat(requeued.get("id").asString()).isEqualTo(id);

        JsonNode completed = awaitStatus(id, "COMPLETED");
        assertThat(completed.get("attempts").asInt()).isEqualTo(2);
        assertThat(completed.get("retryable").asBoolean()).isFalse();
        assertThat(completed.get("resultSummary").asString()).startsWith("40 accounts assessed");
        JsonNode history = completed.get("attemptHistory");
        assertThat(history).hasSize(2);
        assertThat(history.get(0).get("outcome").asString()).isEqualTo("FAILED");
        assertThat(history.get(0).get("errorKind").asString()).isEqualTo("TEMPORARY");
        assertThat(history.get(1).get("outcome").asString()).isEqualTo("COMPLETED");
    }

    @Test
    @Order(3)
    void permanentFailureCannotBeRetriedAndCanBeDeleted() throws Exception {
        String id = submit("""
                { "name": "Old lab grades", "type": "GRADE_REPORT", "payload": { "courseCode": "PHYS-099" } }""");

        JsonNode failed = awaitStatus(id, "FAILED");
        assertThat(failed.get("lastErrorCode").asString()).isEqualTo("COURSE_CLOSED");
        assertThat(failed.get("retryable").asBoolean()).isFalse();

        mvc.perform(post("/api/v1/jobs/{id}/retry", id)).andExpect(status().isConflict());
        mvc.perform(delete("/api/v1/jobs/{id}", id)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/jobs/{id}", id)).andExpect(status().isNotFound());
        mvc.perform(delete("/api/v1/jobs/{id}", id)).andExpect(status().isNotFound());
    }

    /** Recomputes every metric from raw rows in Java, independently of the SQL in JobStatsRepository. */
    @Test
    @Order(4)
    void statsMatchTheRows() throws Exception {
        List<Map<String, Object>> jobs = jdbc.queryForList(
                "SELECT id, status, attempts, created_at FROM job WHERE deleted_at IS NULL");
        Set<UUID> live = jobs.stream().map(j -> (UUID) j.get("ID")).collect(Collectors.toSet());
        Map<UUID, Map<String, Object>> jobById = jobs.stream()
                .collect(Collectors.toMap(j -> (UUID) j.get("ID"), j -> j));
        List<Map<String, Object>> attempts = jdbc.queryForList("SELECT * FROM job_attempt").stream()
                .filter(a -> live.contains((UUID) a.get("JOB_ID"))).toList();

        Map<String, Long> byStatus = new HashMap<>(Map.of("QUEUED", 0L, "PROCESSING", 0L, "COMPLETED", 0L, "FAILED", 0L));
        jobs.forEach(j -> byStatus.merge((String) j.get("STATUS"), 1L, Long::sum));
        long finished = byStatus.get("COMPLETED") + byStatus.get("FAILED");

        double queueWait = attempts.stream().filter(a -> (int) a.get("ATTEMPT_NUMBER") == 1)
                .mapToLong(a -> millis(jobById.get((UUID) a.get("JOB_ID")).get("CREATED_AT"), a.get("STARTED_AT")))
                .average().orElse(0);
        double runTime = attempts.stream().filter(a -> "COMPLETED".equals(a.get("OUTCOME")))
                .mapToLong(a -> millis(a.get("STARTED_AT"), a.get("FINISHED_AT"))).average().orElse(0);

        Set<String> permanentCodes = Set.of("COURSE_CLOSED", "TEMPLATE_INACTIVE", "TERM_CLOSED", "SIMULATED_PERMANENT");
        long permanent = attempts.stream()
                .filter(a -> "FAILED".equals(jobById.get((UUID) a.get("JOB_ID")).get("STATUS")))
                .filter(a -> (int) a.get("ATTEMPT_NUMBER") == (int) jobById.get((UUID) a.get("JOB_ID")).get("ATTEMPTS"))
                .filter(a -> permanentCodes.contains((String) a.get("ERROR_CODE")))
                .count();
        Set<UUID> failedOnce = attempts.stream().filter(a -> "FAILED".equals(a.get("OUTCOME")))
                .map(a -> (UUID) a.get("JOB_ID")).collect(Collectors.toSet());
        long recovered = failedOnce.stream().filter(id -> "COMPLETED".equals(jobById.get(id).get("STATUS"))).count();

        JsonNode stats = data(mvc.perform(get("/api/v1/jobs/stats")).andExpect(status().isOk()));

        byStatus.forEach((status, n) -> assertThat(stats.get("byStatus").get(status).asLong()).as(status).isEqualTo(n));
        assertThat(stats.get("avgQueueWaitMs").asDouble()).isCloseTo(queueWait, within(1.5));
        assertThat(stats.get("avgRunTimeMs").asDouble()).isCloseTo(runTime, within(1.5));
        JsonNode failure = stats.get("failureRate");
        assertThat(failure.get("total").asDouble()).isCloseTo((double) byStatus.get("FAILED") / finished, within(1e-4));
        assertThat(failure.get("permanent").asDouble()).isCloseTo((double) permanent / finished, within(1e-4));
        assertThat(failure.get("temporary").asDouble())
                .isCloseTo((double) (byStatus.get("FAILED") - permanent) / finished, within(1e-4));
        assertThat(stats.get("retryRecoveryRate").asDouble())
                .isCloseTo((double) recovered / failedOnce.size(), within(1e-4));
        assertThat(recovered).as("the retried job counts as recovered").isGreaterThanOrEqualTo(3);
    }

    private static long millis(Object from, Object to) {
        return Duration.between(((OffsetDateTime) from).toInstant(), ((OffsetDateTime) to).toInstant()).toMillis();
    }
}
