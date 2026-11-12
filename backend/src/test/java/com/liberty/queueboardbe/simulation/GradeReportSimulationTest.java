package com.liberty.queueboardbe.simulation;

import com.liberty.queueboardbe.DatabaseTest;
import com.liberty.queueboardbe.exception.JobProcessingException;
import com.liberty.queueboardbe.model.job.JobErrorCode;
import com.liberty.queueboardbe.model.job.JobType;
import com.liberty.queueboardbe.model.payload.GradeReportPayload;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/** Rollback test runs first, while CSCI-110 is still ungraded, so the failure undoes real writes. */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class GradeReportSimulationTest extends DatabaseTest {

    private static final String COURSE = "CSCI-110";
    private static final String ENROLLMENTS = """
            SELECT e.* FROM enrollment e JOIN course c ON c.id = e.course_id WHERE c.code = ? ORDER BY e.id""";

    @Autowired
    private GradeReportSimulation simulation;

    private UUID newJob(String course) {
        return insertJob(JobType.GRADE_REPORT, "{\"courseCode\":\"" + course + "\"}");
    }

    @Test
    @Order(1)
    void failurePartwayLeavesZeroDomainChanges() {
        List<Map<String, Object>> enrollmentsBefore = rows(ENROLLMENTS, COURSE);
        List<Map<String, Object>> awardsBefore = rows("SELECT * FROM award ORDER BY id");
        // The first award really is inserted, then the second insert blows up.
        doCallRealMethod().doThrow(new IllegalStateException("award store down"))
                .when(lms).addAwardIfMissing(anyLong(), anyString(), anyString(), any(), any());

        UUID jobId = newJob(COURSE);
        assertThatThrownBy(() -> simulation.run(jobId, new GradeReportPayload(COURSE, null)))
                .hasMessage("award store down");

        verify(lms, atLeastOnce()).saveGrade(anyLong(), anyString(), any());
        verify(lms, times(2)).addAwardIfMissing(anyLong(), anyString(), anyString(), any(), any());
        assertThat(rows(ENROLLMENTS, COURSE)).isEqualTo(enrollmentsBefore);
        assertThat(rows("SELECT * FROM award ORDER BY id")).isEqualTo(awardsBefore);
    }

    @Test
    @Order(2)
    void successGradesScoredStudentsAndIssuesAwardsOnce() {
        List<Map<String, Object>> before = rows(ENROLLMENTS, COURSE);
        long scored = before.stream().filter(r -> r.get("SCORE") != null).count();
        long expectedAwards = before.stream()
                .filter(r -> r.get("SCORE") != null && ((BigDecimal) r.get("SCORE")).compareTo(new BigDecimal("90")) >= 0)
                .count();
        assertThat(expectedAwards).isPositive();

        UUID jobId = newJob(COURSE);
        String summary = simulation.run(jobId, new GradeReportPayload(COURSE, null));

        assertThat(summary).isEqualTo(scored + " students graded, " + expectedAwards + " awards");
        for (Map<String, Object> row : rows(ENROLLMENTS, COURSE)) {
            BigDecimal score = (BigDecimal) row.get("SCORE");
            if (score == null) {
                assertThat(row.get("LETTER_GRADE")).isNull();
                assertThat(row.get("COMPLETED")).isEqualTo(false);
            } else {
                assertThat(row.get("LETTER_GRADE")).isEqualTo(GradeReportSimulation.letterFor(score));
                assertThat(row.get("COMPLETED")).isEqualTo(true);
                assertThat(row.get("LAST_GRADED_AT")).isNotNull();
            }
        }
        assertThat(count("SELECT COUNT(*) FROM award WHERE job_id = ?", jobId)).isEqualTo((int) expectedAwards);

        // A second report regrades but finds every award already issued.
        assertThat(simulation.run(newJob(COURSE), new GradeReportPayload(COURSE, null)))
                .isEqualTo(scored + " students graded, 0 awards");
        verify(lms, atLeastOnce()).addAwardIfMissing(anyLong(), anyString(), anyString(), any(), any());
    }

    @Test
    void closedCourseFailsPermanentlyWithoutChanges() {
        List<Map<String, Object>> before = rows(ENROLLMENTS, "PHYS-099");

        assertThatThrownBy(() -> simulation.run(newJob("PHYS-099"), new GradeReportPayload("PHYS-099", null)))
                .isInstanceOfSatisfying(JobProcessingException.class,
                        e -> assertThat(e.getCode()).isEqualTo(JobErrorCode.COURSE_CLOSED));
        assertThat(rows(ENROLLMENTS, "PHYS-099")).isEqualTo(before);
    }

    @Test
    void letterGradesAndAwardThresholds() {
        assertThat(GradeReportSimulation.letterFor(new BigDecimal("90.00"))).isEqualTo("A");
        assertThat(GradeReportSimulation.letterFor(new BigDecimal("89.99"))).isEqualTo("B");
        assertThat(GradeReportSimulation.letterFor(new BigDecimal("59.99"))).isEqualTo("F");
        assertThat(GradeReportSimulation.awardFor(new BigDecimal("95"))).get()
                .extracting(GradeReportSimulation.Award::type).isEqualTo("CERTIFICATE");
        assertThat(GradeReportSimulation.awardFor(new BigDecimal("90"))).get()
                .extracting(GradeReportSimulation.Award::type).isEqualTo("BADGE");
        assertThat(GradeReportSimulation.awardFor(new BigDecimal("89.99"))).isEmpty();
    }
}
