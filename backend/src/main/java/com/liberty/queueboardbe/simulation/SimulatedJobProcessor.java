package com.liberty.queueboardbe.simulation;

import com.liberty.queueboardbe.model.common.FieldError;
import com.liberty.queueboardbe.model.payload.BillingAssessmentPayload;
import com.liberty.queueboardbe.model.payload.GradeReportPayload;
import com.liberty.queueboardbe.model.payload.JobPayload;
import com.liberty.queueboardbe.model.payload.NotificationBatchPayload;
import com.liberty.queueboardbe.service.processing.JobProcessor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/** The dispatcher: routes each payload to its simulation. The switch is exhaustive because JobPayload is sealed. */
@Component
public class SimulatedJobProcessor implements JobProcessor {

    private final GradeReportSimulation grades;
    private final NotificationBatchSimulation notifications;
    private final BillingAssessmentSimulation billing;

    public SimulatedJobProcessor(GradeReportSimulation grades, NotificationBatchSimulation notifications,
                                 BillingAssessmentSimulation billing) {
        this.grades = grades;
        this.notifications = notifications;
        this.billing = billing;
    }

    @Override
    public void validate(JobPayload payload, List<FieldError> errors) {
        switch (payload) {
            case GradeReportPayload p -> grades.validate(p, errors);
            case NotificationBatchPayload p -> notifications.validate(p, errors);
            case BillingAssessmentPayload p -> billing.validate(p, errors);
        }
    }

    @Override
    public String process(UUID jobId, JobPayload payload) {
        return switch (payload) {
            case GradeReportPayload p -> grades.run(jobId, p);
            case NotificationBatchPayload p -> notifications.run(jobId, p);
            case BillingAssessmentPayload p -> billing.run(jobId, p);
        };
    }
}
