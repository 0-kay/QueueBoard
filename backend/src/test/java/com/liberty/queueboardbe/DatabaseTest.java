package com.liberty.queueboardbe;

import com.liberty.queueboardbe.model.job.Job;
import com.liberty.queueboardbe.model.job.JobType;
import com.liberty.queueboardbe.repository.job.JobRepository;
import com.liberty.queueboardbe.simulation.LmsRepository;
import com.liberty.queueboardbe.simulation.NotificationSender;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Real H2 with the poller off, so tests drive handlers and claims directly. The spies call real
 * methods unless a test stubs them to inject a failure; they reset after each test.
 */
@SpringBootTest(properties = "queueboard.worker.enabled=false")
@ActiveProfiles("test")
public abstract class DatabaseTest {

    @Autowired
    protected JdbcTemplate jdbc;
    @Autowired
    protected JobRepository jobRepository;

    @MockitoSpyBean
    protected LmsRepository lms;
    @MockitoSpyBean
    protected NotificationSender sender;

    /** Domain rows reference job ids, so handler tests need a real job row. */
    protected UUID insertJob(JobType type, String payload) {
        Job job = Job.submit("test " + type, type, payload, 3, Instant.now());
        jobRepository.save(job);
        return job.getId();
    }

    protected List<Map<String, Object>> rows(String sql, Object... args) {
        return jdbc.queryForList(sql, args);
    }

    protected int count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Integer.class, args);
    }
}
