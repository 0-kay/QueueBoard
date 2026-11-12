package com.liberty.queueboardbe.simulation;

import com.liberty.queueboardbe.DatabaseTest;
import com.liberty.queueboardbe.exception.JobProcessingException;
import com.liberty.queueboardbe.model.job.JobErrorCode;
import com.liberty.queueboardbe.model.job.JobType;
import com.liberty.queueboardbe.model.payload.Channel;
import com.liberty.queueboardbe.model.payload.NotificationBatchPayload;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class NotificationBatchSimulationTest extends DatabaseTest {

    private static final int FAIL_AFTER = 10;

    @Autowired
    private NotificationBatchSimulation simulation;

    private UUID newJob(NotificationBatchPayload payload) {
        return insertJob(JobType.NOTIFICATION_BATCH, "{\"templateCode\":\"" + payload.templateCode()
                + "\",\"courseCode\":\"" + payload.courseCode() + "\",\"channel\":\"" + payload.channel() + "\"}");
    }

    private List<Long> sentStudents(UUID jobId) {
        return jdbc.queryForList("SELECT student_id FROM notification_delivery WHERE job_id = ? AND status = 'SENT'",
                Long.class, jobId);
    }

    @Test
    void failurePartwayKeepsSentRowsAndRetrySendsOnlyTheRest() {
        var payload = new NotificationBatchPayload("ANNOUNCE_MIDTERM", "CSCI-110", Channel.EMAIL, null);
        int recipients = count("""
                SELECT COUNT(*) FROM enrollment e JOIN course c ON c.id = e.course_id WHERE c.code = 'CSCI-110'""");
        UUID jobId = newJob(payload);

        // Test-only fault: the gateway dies after FAIL_AFTER messages.
        AtomicInteger calls = new AtomicInteger();
        doAnswer(invocation -> {
            if (calls.incrementAndGet() > FAIL_AFTER) {
                throw new IllegalStateException("gateway down");
            }
            return invocation.callRealMethod();
        }).when(sender).send(any());

        assertThatThrownBy(() -> simulation.run(jobId, payload)).hasMessage("gateway down");
        List<Long> sentFirstRun = sentStudents(jobId);
        assertThat(sentFirstRun).hasSize(FAIL_AFTER);
        assertThat(count("SELECT COUNT(*) FROM notification_delivery WHERE job_id = ? AND status = 'PENDING'", jobId))
                .isEqualTo(recipients - FAIL_AFTER);

        // Retry with the same job id and a healthy gateway.
        doCallRealMethod().when(sender).send(any());
        clearInvocations(sender);
        String summary = simulation.run(jobId, payload);

        ArgumentCaptor<NotificationSender.Message> sent = ArgumentCaptor.forClass(NotificationSender.Message.class);
        verify(sender, times(recipients - FAIL_AFTER)).send(sent.capture());
        assertThat(sent.getAllValues()).extracting(NotificationSender.Message::studentId)
                .doesNotContainAnyElementsOf(sentFirstRun)
                .doesNotHaveDuplicates();
        assertThat(summary).isEqualTo(recipients + " of " + recipients + " sent");
        assertThat(sentStudents(jobId)).hasSize(recipients).doesNotHaveDuplicates();
    }

    @Test
    void inactiveTemplateFailsPermanentlyBeforeSending() {
        var payload = new NotificationBatchPayload("LEGACY_NEWSLETTER", "CSCI-110", Channel.EMAIL, null);
        UUID jobId = newJob(payload);

        assertThatThrownBy(() -> simulation.run(jobId, payload))
                .isInstanceOfSatisfying(JobProcessingException.class,
                        e -> assertThat(e.getCode()).isEqualTo(JobErrorCode.TEMPLATE_INACTIVE));
        verify(sender, never()).send(any());
        assertThat(count("SELECT COUNT(*) FROM notification_delivery WHERE job_id = ?", jobId)).isZero();
    }

    @Test
    void closedCourseFailsPermanentlyBeforeSending() {
        var payload = new NotificationBatchPayload("ANNOUNCE_MIDTERM", "PHYS-099", Channel.PUSH, null);

        assertThatThrownBy(() -> simulation.run(newJob(payload), payload))
                .isInstanceOfSatisfying(JobProcessingException.class,
                        e -> assertThat(e.getCode()).isEqualTo(JobErrorCode.COURSE_CLOSED));
        verify(sender, never()).send(any());
    }
}
