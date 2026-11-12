package com.liberty.queueboardbe.simulation;

import com.liberty.queueboardbe.exception.JobProcessingException;
import com.liberty.queueboardbe.model.common.FieldError;
import com.liberty.queueboardbe.model.job.JobErrorCode;
import com.liberty.queueboardbe.model.payload.NotificationBatchPayload;
import com.liberty.queueboardbe.simulation.LmsRepository.Course;
import com.liberty.queueboardbe.simulation.LmsRepository.Delivery;
import com.liberty.queueboardbe.simulation.LmsRepository.Template;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

/**
 * NOTIFICATION_BATCH: one delivery row per enrolled student, then send each and mark it SENT.
 *
 * <p>Deliberately no transaction: every statement auto-commits, so each SENT flag is saved the moment
 * its message goes out. A failure partway keeps what was sent, and a retry (same job id) sends only the
 * rows still PENDING. Effectively once, not exactly once: a crash between {@code send} and
 * {@code markSent} would resend that one message.
 */
@Component
public class NotificationBatchSimulation {

    private static final Logger log = LoggerFactory.getLogger(NotificationBatchSimulation.class);

    private final LmsRepository lms;
    private final NotificationSender sender;
    private final SimulatedWork work;
    private final Clock clock;

    public NotificationBatchSimulation(LmsRepository lms, NotificationSender sender, SimulatedWork work, Clock clock) {
        this.lms = lms;
        this.sender = sender;
        this.work = work;
        this.clock = clock;
    }

    public void validate(NotificationBatchPayload payload, List<FieldError> errors) {
        if (lms.template(payload.templateCode()).isEmpty()) {
            errors.add(new FieldError("payload.templateCode", "Template not found"));
        }
        if (lms.course(payload.courseCode()).isEmpty()) {
            errors.add(new FieldError("payload.courseCode", "Course not found"));
        }
    }

    public String run(UUID jobId, NotificationBatchPayload payload) {
        Template template = lms.template(payload.templateCode()).orElseThrow();
        if (!template.active()) {
            throw new JobProcessingException(JobErrorCode.TEMPLATE_INACTIVE,
                    "Template " + template.code() + " is inactive");
        }
        Course course = lms.course(payload.courseCode()).orElseThrow();
        if (course.closed()) {
            throw new JobProcessingException(JobErrorCode.COURSE_CLOSED, "Course " + course.code() + " is closed");
        }

        lms.createPendingDeliveries(jobId, course.id(), payload.channel());
        int total = lms.countDeliveries(jobId);
        List<Delivery> pending = lms.pendingDeliveries(jobId);
        log.debug("Sending {} to {} of {} students in {}", template.code(), pending.size(), total, course.code());

        long perMessageMs = total == 0 ? 0 : SimulatedWork.between(1000, 3000, 80, total) / total;
        for (Delivery delivery : pending) {
            work.pause(perMessageMs);
            sender.send(new NotificationSender.Message(jobId, delivery.studentId(), payload.channel(),
                    template.subject()));
            lms.markSent(delivery.id(), clock.instant());
        }
        return lms.countSent(jobId) + " of " + total + " sent";
    }
}
