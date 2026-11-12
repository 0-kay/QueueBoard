package com.liberty.queueboardbe.simulation;

import com.liberty.queueboardbe.model.payload.Channel;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The simulated LMS data the job handlers read and write: courses, enrollments and awards; terms,
 * accounts and charges; notification templates and deliveries. Plain SQL through JdbcTemplate.
 * Callers own the transaction.
 */
@Repository
public class LmsRepository {

    public record Course(long id, String code, String title, String status) {
        public boolean closed() {
            return "CLOSED".equals(status);
        }
    }

    public record Term(long id, String code, String name, String status, LocalDate feeDueDate) {
        public boolean closed() {
            return "CLOSED".equals(status);
        }
    }

    public record Template(long id, String code, String kind, String subject, boolean active) {
    }

    /** score is null when the student has no score yet. */
    public record Enrollment(long id, BigDecimal score) {
    }

    /** A student's account for one term, with the total of charges already on it. */
    public record Account(long id, BigDecimal tuition, BigDecimal aid, BigDecimal paid, String aidStatus,
                          BigDecimal charges) {
        /** What the student owes before charges. */
        public BigDecimal owed() {
            return tuition.subtract(aid).subtract(paid);
        }
    }

    public record Delivery(long id, long studentId) {
    }

    private static final RowMapper<Course> COURSE = (rs, i) ->
            new Course(rs.getLong("id"), rs.getString("code"), rs.getString("title"), rs.getString("status"));
    private static final RowMapper<Term> TERM = (rs, i) -> new Term(rs.getLong("id"), rs.getString("code"),
            rs.getString("name"), rs.getString("status"), rs.getObject("fee_due_date", LocalDate.class));
    private static final RowMapper<Template> TEMPLATE = (rs, i) -> new Template(rs.getLong("id"),
            rs.getString("code"), rs.getString("kind"), rs.getString("subject"), rs.getBoolean("active"));

    private final JdbcTemplate jdbc;

    public LmsRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ---------- reference data ----------

    public List<Course> courses() {
        return jdbc.query("SELECT * FROM course ORDER BY code", COURSE);
    }

    public Optional<Course> course(String code) {
        return jdbc.query("SELECT * FROM course WHERE code = ?", COURSE, code).stream().findFirst();
    }

    public List<Term> terms() {
        return jdbc.query("SELECT * FROM term ORDER BY fee_due_date DESC", TERM);
    }

    public Optional<Term> term(String code) {
        return jdbc.query("SELECT * FROM term WHERE code = ?", TERM, code).stream().findFirst();
    }

    public List<Template> templates() {
        return jdbc.query("SELECT * FROM notification_template ORDER BY code", TEMPLATE);
    }

    public Optional<Template> template(String code) {
        return jdbc.query("SELECT * FROM notification_template WHERE code = ?", TEMPLATE, code).stream().findFirst();
    }

    // ---------- grade reports ----------

    public List<Enrollment> enrollments(long courseId) {
        return jdbc.query("SELECT id, score FROM enrollment WHERE course_id = ? ORDER BY id",
                (rs, i) -> new Enrollment(rs.getLong("id"), rs.getBigDecimal("score")), courseId);
    }

    public void saveGrade(long enrollmentId, String letterGrade, Instant gradedAt) {
        jdbc.update("UPDATE enrollment SET letter_grade = ?, completed = TRUE, last_graded_at = ? WHERE id = ?",
                letterGrade, utc(gradedAt), enrollmentId);
    }

    /** @return 1 if the award was added, 0 if the enrollment already had it */
    public int addAwardIfMissing(long enrollmentId, String type, String name, Instant at, UUID jobId) {
        return jdbc.update("""
                        INSERT INTO award (enrollment_id, award_type, name, awarded_at, job_id)
                        SELECT CAST(? AS BIGINT), CAST(? AS VARCHAR(16)), CAST(? AS VARCHAR(100)),
                               CAST(? AS TIMESTAMP WITH TIME ZONE), CAST(? AS UUID)
                        WHERE NOT EXISTS (SELECT 1 FROM award WHERE enrollment_id = ? AND award_type = ? AND name = ?)""",
                enrollmentId, type, name, utc(at), jobId, enrollmentId, type, name);
    }

    // ---------- billing assessments ----------

    public List<Account> accounts(long termId) {
        return jdbc.query("""
                        SELECT a.id, a.tuition_amount, a.aid_amount, a.amount_paid, a.aid_status,
                               COALESCE((SELECT SUM(c.amount) FROM account_charge c WHERE c.account_id = a.id), 0) AS charges
                        FROM student_account a WHERE a.term_id = ? ORDER BY a.id""",
                (rs, i) -> new Account(rs.getLong("id"), rs.getBigDecimal("tuition_amount"),
                        rs.getBigDecimal("aid_amount"), rs.getBigDecimal("amount_paid"), rs.getString("aid_status"),
                        rs.getBigDecimal("charges")),
                termId);
    }

    /** @return 1 if the late fee was added, 0 if the account already had one */
    public int addLateFeeIfMissing(long accountId, BigDecimal amount, Instant at, UUID jobId) {
        return jdbc.update("""
                        INSERT INTO account_charge (account_id, charge_type, amount, created_at, job_id)
                        SELECT CAST(? AS BIGINT), 'LATE_FEE', CAST(? AS DECIMAL(10,2)),
                               CAST(? AS TIMESTAMP WITH TIME ZONE), CAST(? AS UUID)
                        WHERE NOT EXISTS (SELECT 1 FROM account_charge WHERE account_id = ? AND charge_type = 'LATE_FEE')""",
                accountId, amount, utc(at), jobId, accountId);
    }

    public void saveAssessment(long accountId, BigDecimal balance, String aidStatus, Instant at) {
        jdbc.update("UPDATE student_account SET balance = ?, aid_status = ?, last_assessed_at = ? WHERE id = ?",
                balance, aidStatus, utc(at), accountId);
    }

    // ---------- notification batches ----------

    /** One PENDING delivery per enrolled student, skipping students this job already has a row for. */
    public void createPendingDeliveries(UUID jobId, long courseId, Channel channel) {
        jdbc.update("""
                        INSERT INTO notification_delivery (job_id, student_id, channel, status)
                        SELECT CAST(? AS UUID), e.student_id, CAST(? AS VARCHAR(8)), 'PENDING'
                        FROM enrollment e
                        WHERE e.course_id = ?
                          AND NOT EXISTS (SELECT 1 FROM notification_delivery d
                                          WHERE d.job_id = ? AND d.student_id = e.student_id)""",
                jobId, channel.name(), courseId, jobId);
    }

    public List<Delivery> pendingDeliveries(UUID jobId) {
        return jdbc.query("SELECT id, student_id FROM notification_delivery WHERE job_id = ? AND status = 'PENDING' ORDER BY id",
                (rs, i) -> new Delivery(rs.getLong("id"), rs.getLong("student_id")), jobId);
    }

    public void markSent(long deliveryId, Instant at) {
        jdbc.update("UPDATE notification_delivery SET status = 'SENT', sent_at = ? WHERE id = ? AND status = 'PENDING'",
                utc(at), deliveryId);
    }

    public int countDeliveries(UUID jobId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM notification_delivery WHERE job_id = ?", Integer.class, jobId);
    }

    public int countSent(UUID jobId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM notification_delivery WHERE job_id = ? AND status = 'SENT'",
                Integer.class, jobId);
    }

    /** TIMESTAMP WITH TIME ZONE columns are bound as UTC. */
    private static OffsetDateTime utc(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
