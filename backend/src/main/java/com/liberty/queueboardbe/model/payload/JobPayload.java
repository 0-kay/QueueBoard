package com.liberty.queueboardbe.model.payload;

/** Common shape of every job payload record. Sealed, so a switch over payloads is checked for completeness. */
public sealed interface JobPayload permits GradeReportPayload, NotificationBatchPayload, BillingAssessmentPayload {

    /** Demo switch: TEMPORARY or PERMANENT makes the job fail before any work begins. Null = run normally. */
    SimulateFailure simulateFailure();
}
