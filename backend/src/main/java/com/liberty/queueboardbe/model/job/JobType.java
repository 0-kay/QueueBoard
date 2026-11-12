package com.liberty.queueboardbe.model.job;

import com.liberty.queueboardbe.model.payload.BillingAssessmentPayload;
import com.liberty.queueboardbe.model.payload.GradeReportPayload;
import com.liberty.queueboardbe.model.payload.JobPayload;
import com.liberty.queueboardbe.model.payload.NotificationBatchPayload;

/** Each type names its payload record, which is what submitted JSON is validated against. */
public enum JobType {
    GRADE_REPORT(GradeReportPayload.class, "Grade report",
            "Recalculates letter grades from scores, marks completion, and issues badges and certificates."),
    NOTIFICATION_BATCH(NotificationBatchPayload.class, "Notification batch",
            "Sends a notification template to every student enrolled in a course, by email or push."),
    BILLING_ASSESSMENT(BillingAssessmentPayload.class, "Billing assessment",
            "Recalculates term balances, applies late fees to past-due accounts, and updates aid status.");

    private final Class<? extends JobPayload> payloadType;
    private final String label;
    private final String description;

    JobType(Class<? extends JobPayload> payloadType, String label, String description) {
        this.payloadType = payloadType;
        this.label = label;
        this.description = description;
    }

    public Class<? extends JobPayload> payloadType() {
        return payloadType;
    }

    public String label() {
        return label;
    }

    public String description() {
        return description;
    }
}
