package com.liberty.queueboardbe.service.job;

import com.liberty.queueboardbe.config.QueueBoardProperties;
import com.liberty.queueboardbe.exception.JobValidationException;
import com.liberty.queueboardbe.model.common.FieldError;
import com.liberty.queueboardbe.model.job.JobType;
import com.liberty.queueboardbe.model.payload.BillingAssessmentPayload;
import com.liberty.queueboardbe.model.payload.Channel;
import com.liberty.queueboardbe.model.payload.GradeReportPayload;
import com.liberty.queueboardbe.model.payload.NotificationBatchPayload;
import com.liberty.queueboardbe.model.payload.SimulateFailure;
import com.liberty.queueboardbe.service.processing.JobProcessor;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.json.JsonMapper;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class JobPayloadValidatorTest {

    private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final JobProcessor processor = mock(JobProcessor.class);

    private JobPayloadValidator validator;

    @BeforeEach
    void setUp() {
        validator = validator(true);
    }

    private JobPayloadValidator validator(boolean demoEnabled) {
        QueueBoardProperties properties = new QueueBoardProperties(
                new QueueBoardProperties.Worker(true, 5, 500), new QueueBoardProperties.Jobs(3),
                new QueueBoardProperties.Demo(demoEnabled), new QueueBoardProperties.Simulation(0),
                new QueueBoardProperties.Cors(List.of()));
        return new JobPayloadValidator(processor, VALIDATOR, JSON, properties);
    }

    private static Map<String, Object> payload(Object... keyValues) {
        Map<String, Object> map = new HashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            map.put((String) keyValues[i], keyValues[i + 1]);
        }
        return map;
    }

    private List<FieldError> errorsFor(String type, Map<String, Object> payload) {
        try {
            validator.validate(type, payload);
        } catch (JobValidationException e) {
            return e.getErrors();
        }
        throw new AssertionError("Expected validation to fail for " + type + " " + payload);
    }

    @ParameterizedTest
    @ValueSource(strings = {"REPORT", "grade_report", ""})
    void unknownTypeIsRejected(String type) {
        assertThat(errorsFor(type, payload("courseCode", "CSCI-110")))
                .singleElement().extracting(FieldError::field).isEqualTo("type");
    }

    @Test
    void nullTypeAndPayloadAreBothReported() {
        assertThat(errorsFor(null, null)).extracting(FieldError::field).containsExactly("type", "payload");
    }

    @Test
    void payloadOverFourKilobytesIsRejected() {
        assertThat(errorsFor("GRADE_REPORT", payload("courseCode", "x".repeat(4100))))
                .singleElement()
                .satisfies(e -> {
                    assertThat(e.field()).isEqualTo("payload");
                    assertThat(e.message()).contains("4000 bytes");
                });
    }

    @Nested
    class GradeReport {

        @Test
        void validPayloadParsesToRecord() {
            var result = validator.validate("GRADE_REPORT", payload("courseCode", "CSCI-110"));

            assertThat(result.type()).isEqualTo(JobType.GRADE_REPORT);
            assertThat(result.payload()).isEqualTo(new GradeReportPayload("CSCI-110", null));
            assertThat(result.json()).isEqualTo("{\"courseCode\":\"CSCI-110\"}");
        }


        @Test
        void missingCourseCode() {
            assertThat(errorsFor("GRADE_REPORT", payload()))
                    .containsExactly(new FieldError("payload.courseCode", "Course code is required"));
        }

        @Test
        void blankCourseCode() {
            assertThat(errorsFor("GRADE_REPORT", payload("courseCode", "  ")))
                    .extracting(FieldError::field).containsExactly("payload.courseCode");
        }

        @Test
        void unknownField() {
            assertThat(errorsFor("GRADE_REPORT", payload("courseCode", "CSCI-110", "templateCode", "X")))
                    .containsExactly(new FieldError("payload.templateCode", "Unknown field for GRADE_REPORT"));
        }

        @Test
        void wrongFieldType() {
            assertThat(errorsFor("GRADE_REPORT", payload("courseCode", Map.of("code", "CSCI-110"))))
                    .extracting(FieldError::field).containsExactly("payload.courseCode");
        }

    }

    @Nested
    class NotificationBatch {

        @Test
        void validPayloadParsesToRecord() {
            var result = validator.validate("NOTIFICATION_BATCH",
                    payload("templateCode", "ANNOUNCE_MIDTERM", "courseCode", "CSCI-110", "channel", "PUSH"));

            assertThat(result.payload())
                    .isEqualTo(new NotificationBatchPayload("ANNOUNCE_MIDTERM", "CSCI-110", Channel.PUSH, null));
        }

        @Test
        void missingFieldsAreAllReported() {
            assertThat(errorsFor("NOTIFICATION_BATCH", payload()))
                    .extracting(FieldError::field)
                    .containsExactly("payload.channel", "payload.courseCode", "payload.templateCode");
        }

        @Test
        void unknownField() {
            assertThat(errorsFor("NOTIFICATION_BATCH", payload("templateCode", "ANNOUNCE_MIDTERM",
                    "courseCode", "CSCI-110", "channel", "EMAIL", "priority", "high")))
                    .extracting(FieldError::field).containsExactly("payload.priority");
        }

        @Test
        void wrongChannelValue() {
            assertThat(errorsFor("NOTIFICATION_BATCH",
                    payload("templateCode", "ANNOUNCE_MIDTERM", "courseCode", "CSCI-110", "channel", "SMS")))
                    .containsExactly(new FieldError("payload.channel", "Must be one of [EMAIL, PUSH]"));
        }

    }

    @Nested
    class BillingAssessment {

        @Test
        void validPayloadParsesToRecord() {
            var result = validator.validate("BILLING_ASSESSMENT",
                    payload("termCode", "FA26", "applyLateFees", true, "simulateFailure", "TEMPORARY"));

            assertThat(result.payload())
                    .isEqualTo(new BillingAssessmentPayload("FA26", true, SimulateFailure.TEMPORARY));
        }

        @Test
        void missingFields() {
            assertThat(errorsFor("BILLING_ASSESSMENT", payload()))
                    .extracting(FieldError::field).containsExactly("payload.applyLateFees", "payload.termCode");
        }

        @Test
        void unknownField() {
            assertThat(errorsFor("BILLING_ASSESSMENT", payload("termCode", "FA26", "applyLateFees", true,
                    "courseCode", "CSCI-110")))
                    .extracting(FieldError::field).containsExactly("payload.courseCode");
        }

        @Test
        void wrongFieldType() {
            assertThat(errorsFor("BILLING_ASSESSMENT", payload("termCode", "FA26", "applyLateFees", "yes")))
                    .extracting(FieldError::field).containsExactly("payload.applyLateFees");
        }


        @Test
        void unknownSimulateFailureValue() {
            assertThat(errorsFor("BILLING_ASSESSMENT",
                    payload("termCode", "FA26", "applyLateFees", false, "simulateFailure", "SOMETIMES")))
                    .extracting(FieldError::field).containsExactly("payload.simulateFailure");
        }
    }

    @Nested
    class DemoFlag {

        @ParameterizedTest
        @ValueSource(strings = {"TEMPORARY", "PERMANENT"})
        void simulateFailureIsRejectedWhenDemoIsOff(String mode) {
            validator = validator(false);

            assertThat(errorsFor("GRADE_REPORT", payload("courseCode", "CSCI-110", "simulateFailure", mode)))
                    .containsExactly(new FieldError("payload.simulateFailure",
                            "Simulated failures are disabled on this server"));
        }

        @Test
        void normalPayloadIsFineWhenDemoIsOff() {
            validator = validator(false);

            assertThat(validator.validate("GRADE_REPORT", payload("courseCode", "CSCI-110")).payload())
                    .isEqualTo(new GradeReportPayload("CSCI-110", null));
        }

        @Test
        void simulateFailureIsAcceptedWhenDemoIsOn() {
            assertThat(validator.validate("GRADE_REPORT",
                    payload("courseCode", "CSCI-110", "simulateFailure", "PERMANENT")).payload())
                    .isEqualTo(new GradeReportPayload("CSCI-110", SimulateFailure.PERMANENT));
        }
    }

    @Test
    void processorErrorsAreReturnedWithTheRest() {
        doAnswer(inv -> {
            List<FieldError> errors = inv.getArgument(1);
            errors.add(new FieldError("payload.courseCode", "Course not found"));
            return null;
        }).when(processor).validate(any(), any());

        assertThat(errorsFor("GRADE_REPORT", payload("courseCode", "NOPE-101")))
                .containsExactly(new FieldError("payload.courseCode", "Course not found"));
    }

    @Test
    void processorIsAskedOnlyAfterTheShapeIsValid() {
        errorsFor("GRADE_REPORT", payload());
        verify(processor, never()).validate(any(), any());

        validator.validate("GRADE_REPORT", payload("courseCode", "CSCI-110"));
        verify(processor).validate(eq(new GradeReportPayload("CSCI-110", null)), any());
    }

    @Test
    void validationErrorIsAnException() {
        assertThatThrownBy(() -> validator.validate("GRADE_REPORT", payload()))
                .isInstanceOf(JobValidationException.class);
    }
}
