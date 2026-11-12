package com.liberty.queueboardbe;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;

import static org.assertj.core.api.Assertions.assertThat;

/** The context starting at all proves Hibernate validated the entities against the Flyway schema. */
class FlywayMigrationTest extends DatabaseTest {

    @Autowired
    private Environment environment;

    @Test
    void migrationsApplyAndHibernateOnlyValidates() {
        assertThat(environment.getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("validate");
        assertThat(rows("SELECT \"version\", \"success\" FROM \"flyway_schema_history\" WHERE \"version\" IS NOT NULL ORDER BY \"installed_rank\""))
                .extracting(r -> r.get("version"), r -> r.get("success"))
                .containsExactly(org.assertj.core.groups.Tuple.tuple("1", true),
                        org.assertj.core.groups.Tuple.tuple("2", true),
                        org.assertj.core.groups.Tuple.tuple("3", true));
    }

    @Test
    void seedDataIsLoaded() {
        assertThat(count("SELECT COUNT(*) FROM course")).isEqualTo(4);
        assertThat(count("SELECT COUNT(*) FROM course WHERE status = 'CLOSED'")).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM student")).isEqualTo(40);
        assertThat(count("SELECT COUNT(*) FROM student_account a JOIN term t ON t.id = a.term_id WHERE t.code = 'FA26'"))
                .isEqualTo(40);
        assertThat(count("SELECT COUNT(*) FROM term WHERE status = 'CLOSED'")).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM notification_template WHERE active = FALSE")).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM enrollment e JOIN course c ON c.id = e.course_id WHERE c.status = 'OPEN'"))
                .isBetween(70, 80);
    }

    @Test
    void seedJobsCoverRecoveredAndPermanentFailures() {
        assertThat(count("SELECT COUNT(*) FROM job WHERE status IN ('COMPLETED', 'FAILED')")).isGreaterThanOrEqualTo(12);
        assertThat(count("""
                SELECT COUNT(*) FROM job j WHERE j.status = 'COMPLETED'
                  AND EXISTS (SELECT 1 FROM job_attempt a WHERE a.job_id = j.id AND a.outcome = 'FAILED')"""))
                .isGreaterThanOrEqualTo(1);
        assertThat(count("""
                SELECT COUNT(*) FROM job j JOIN job_attempt a ON a.job_id = j.id AND a.attempt_number = j.attempts
                WHERE j.status = 'FAILED' AND a.error_code IN ('COURSE_CLOSED', 'TERM_CLOSED', 'TEMPLATE_INACTIVE')"""))
                .isGreaterThanOrEqualTo(1);
    }
}
