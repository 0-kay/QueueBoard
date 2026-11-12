package com.liberty.queueboardbe.simulation;

import com.liberty.queueboardbe.model.common.FieldError;
import com.liberty.queueboardbe.model.payload.BillingAssessmentPayload;
import com.liberty.queueboardbe.model.payload.Channel;
import com.liberty.queueboardbe.model.payload.GradeReportPayload;
import com.liberty.queueboardbe.model.payload.JobPayload;
import com.liberty.queueboardbe.model.payload.NotificationBatchPayload;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Submit-time reference checks: codes must exist; closed or inactive ones are accepted (they fail later). */
class SimulatedJobProcessorTest {

    private final LmsRepository lms = mock(LmsRepository.class);
    private final SimulatedJobProcessor processor = new SimulatedJobProcessor(
            new GradeReportSimulation(lms, null, null),
            new NotificationBatchSimulation(lms, null, null, null),
            new BillingAssessmentSimulation(lms, null, null));

    @BeforeEach
    void setUp() {
        when(lms.course("CSCI-110")).thenReturn(Optional.of(new LmsRepository.Course(1, "CSCI-110", "Intro", "OPEN")));
        when(lms.course("PHYS-099")).thenReturn(Optional.of(new LmsRepository.Course(4, "PHYS-099", "Old", "CLOSED")));
        when(lms.term("FA26")).thenReturn(Optional.of(new LmsRepository.Term(1, "FA26", "Fall", "OPEN", LocalDate.now())));
        when(lms.template("LEGACY_NEWSLETTER"))
                .thenReturn(Optional.of(new LmsRepository.Template(4, "LEGACY_NEWSLETTER", "ANNOUNCEMENT", "s", false)));
    }

    private List<FieldError> errorsFor(JobPayload payload) {
        List<FieldError> errors = new ArrayList<>();
        processor.validate(payload, errors);
        return errors;
    }

    @Test
    void knownCodesPass() {
        assertThat(errorsFor(new GradeReportPayload("CSCI-110", null))).isEmpty();
        assertThat(errorsFor(new BillingAssessmentPayload("FA26", true, null))).isEmpty();
    }

    @Test
    void closedCourseAndInactiveTemplateAreValidAtSubmit() {
        assertThat(errorsFor(new GradeReportPayload("PHYS-099", null))).isEmpty();
        assertThat(errorsFor(new NotificationBatchPayload("LEGACY_NEWSLETTER", "PHYS-099", Channel.EMAIL, null)))
                .isEmpty();
    }

    @Test
    void unknownCourse() {
        assertThat(errorsFor(new GradeReportPayload("NOPE-101", null)))
                .containsExactly(new FieldError("payload.courseCode", "Course not found"));
    }

    @Test
    void unknownTemplateAndCourseAreBothReported() {
        assertThat(errorsFor(new NotificationBatchPayload("NOPE", "NOPE", Channel.EMAIL, null)))
                .containsExactly(new FieldError("payload.templateCode", "Template not found"),
                        new FieldError("payload.courseCode", "Course not found"));
    }

    @Test
    void unknownTerm() {
        assertThat(errorsFor(new BillingAssessmentPayload("WI99", false, null)))
                .containsExactly(new FieldError("payload.termCode", "Term not found"));
    }
}
