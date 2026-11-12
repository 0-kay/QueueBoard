package com.liberty.queueboardbe.controller;

/** JSON examples shown in Swagger UI. */
final class ApiExamples {

    private ApiExamples() {
    }

    static final String SUBMIT_GRADE = """
            { "name": "Midterm grades CSCI-110", "type": "GRADE_REPORT", "payload": { "courseCode": "CSCI-110" } }""";

    static final String SUBMIT_NOTIFICATION = """
            { "name": "Midterm announcement CSCI-110", "type": "NOTIFICATION_BATCH",
              "payload": { "templateCode": "ANNOUNCE_MIDTERM", "courseCode": "CSCI-110", "channel": "EMAIL" } }""";

    static final String SUBMIT_BILLING = """
            { "name": "FA26 late fees", "type": "BILLING_ASSESSMENT",
              "payload": { "termCode": "FA26", "applyLateFees": true, "simulateFailure": "TEMPORARY" } }""";

    static final String JOB_QUEUED = """
            { "status": 201, "message": "Created", "description": "Job submitted.",
              "data": { "id": "3f1c2b8e-6c1d-4c47-9a53-2a7c1f0e9b11", "name": "Midterm grades CSCI-110",
                "type": "GRADE_REPORT", "status": "QUEUED", "attempts": 0, "maxAttempts": 3,
                "lastErrorCode": null, "retryable": false,
                "createdAt": "2026-11-11T18:30:00Z", "updatedAt": "2026-11-11T18:30:00Z",
                "payload": { "courseCode": "CSCI-110" }, "resultSummary": null, "attemptHistory": [] },
              "errors": [] }""";

    static final String JOB_FAILED_DETAIL = """
            { "status": 200, "message": "OK", "description": "Request completed.",
              "data": { "id": "3f1c2b8e-6c1d-4c47-9a53-2a7c1f0e9b11", "name": "FA26 late fees",
                "type": "BILLING_ASSESSMENT", "status": "FAILED", "attempts": 1, "maxAttempts": 3,
                "lastErrorCode": "SIMULATED_TEMPORARY", "retryable": true,
                "createdAt": "2026-11-11T18:30:00Z", "updatedAt": "2026-11-11T18:30:02Z",
                "payload": { "termCode": "FA26", "applyLateFees": true, "simulateFailure": "TEMPORARY" },
                "resultSummary": null,
                "attemptHistory": [ { "attemptNumber": 1, "startedAt": "2026-11-11T18:30:01Z",
                  "finishedAt": "2026-11-11T18:30:02Z", "outcome": "FAILED", "errorCode": "SIMULATED_TEMPORARY",
                  "errorKind": "TEMPORARY",
                  "errorMessage": "Simulated temporary failure on the first attempt; a retry will succeed" } ] },
              "errors": [] }""";

    static final String JOB_REQUEUED = """
            { "status": 202, "message": "Accepted", "description": "Job queued for another attempt.",
              "data": { "id": "3f1c2b8e-6c1d-4c47-9a53-2a7c1f0e9b11", "name": "FA26 late fees",
                "type": "BILLING_ASSESSMENT", "status": "QUEUED", "attempts": 1, "maxAttempts": 3,
                "lastErrorCode": "SIMULATED_TEMPORARY", "retryable": false,
                "createdAt": "2026-11-11T18:30:00Z", "updatedAt": "2026-11-11T18:31:10Z",
                "payload": { "termCode": "FA26", "applyLateFees": true, "simulateFailure": "TEMPORARY" },
                "resultSummary": null, "attemptHistory": [ { "attemptNumber": 1, "startedAt": "2026-11-11T18:30:01Z",
                  "finishedAt": "2026-11-11T18:30:02Z", "outcome": "FAILED", "errorCode": "SIMULATED_TEMPORARY",
                  "errorKind": "TEMPORARY", "errorMessage": "Simulated temporary failure" } ] },
              "errors": [] }""";

    static final String JOB_PAGE = """
            { "status": 200, "message": "OK", "description": "Request completed.",
              "data": { "items": [ { "id": "3f1c2b8e-6c1d-4c47-9a53-2a7c1f0e9b11", "name": "FA26 late fees",
                "type": "BILLING_ASSESSMENT", "status": "FAILED", "attempts": 1, "maxAttempts": 3,
                "lastErrorCode": "SIMULATED_TEMPORARY", "retryable": true,
                "createdAt": "2026-11-11T18:30:00Z", "updatedAt": "2026-11-11T18:30:02Z" } ],
                "page": 0, "size": 20, "totalItems": 1 },
              "errors": [] }""";

    static final String DELETED = """
            { "status": 200, "message": "OK", "description": "Job deleted.", "data": null, "errors": [] }""";

    static final String VALIDATION_ERROR = """
            { "status": 400, "message": "Validation failed", "description": "Some fields need attention.",
              "data": null, "errors": [ { "field": "payload.courseCode", "message": "Course not found" } ] }""";

    static final String BAD_FILTER = """
            { "status": 400, "message": "Invalid value 'DONE' for parameter 'status'",
              "description": "Some fields need attention.", "data": null,
              "errors": [ { "field": "status", "message": "Must be one of [QUEUED, PROCESSING, COMPLETED, FAILED]" } ] }""";

    static final String NOT_FOUND = """
            { "status": 404, "message": "Job 3f1c2b8e-6c1d-4c47-9a53-2a7c1f0e9b11 not found",
              "description": "We couldn't find that job. It may have been deleted.", "data": null, "errors": [] }""";

    static final String RETRY_CONFLICT = """
            { "status": 409, "message": "Retry not allowed",
              "description": "This job failed with a permanent error and can't be retried.",
              "data": null, "errors": [] }""";

    static final String DELETE_CONFLICT = """
            { "status": 409, "message": "Illegal transition for job 3f1c2b8e-6c1d-4c47-9a53-2a7c1f0e9b11: cannot delete from PROCESSING",
              "description": "This action isn't allowed while the job is processing.",
              "data": null, "errors": [] }""";

    static final String STATS = """
            { "status": 200, "message": "OK", "description": "Request completed.",
              "data": { "byStatus": { "QUEUED": 2, "PROCESSING": 5, "COMPLETED": 41, "FAILED": 6 },
                "avgQueueWaitMs": 1840, "avgRunTimeMs": 4210,
                "failureRate": { "total": 0.1277, "temporary": 0.0851, "permanent": 0.0426 },
                "retryRecoveryRate": 0.6667 },
              "errors": [] }""";

    static final String JOB_TYPES = """
            { "status": 200, "message": "OK", "description": "Request completed.",
              "data": [ { "type": "GRADE_REPORT", "label": "Grade report",
                "description": "Recalculates letter grades from scores, marks completion, and issues badges and certificates." } ],
              "errors": [] }""";

    static final String COURSES = """
            { "status": 200, "message": "OK", "description": "Request completed.",
              "data": [ { "code": "CSCI-110", "title": "Introduction to Programming", "status": "OPEN" },
                        { "code": "PHYS-099", "title": "Legacy Physics Lab (retired)", "status": "CLOSED" } ],
              "errors": [] }""";

    static final String TERMS = """
            { "status": 200, "message": "OK", "description": "Request completed.",
              "data": [ { "code": "FA26", "name": "Fall 2026", "status": "OPEN" },
                        { "code": "SP26", "name": "Spring 2026", "status": "CLOSED" } ],
              "errors": [] }""";

    static final String TEMPLATES = """
            { "status": 200, "message": "OK", "description": "Request completed.",
              "data": [ { "code": "ANNOUNCE_MIDTERM", "kind": "ANNOUNCEMENT", "subject": "Midterm exam schedule", "active": true },
                        { "code": "LEGACY_NEWSLETTER", "kind": "ANNOUNCEMENT", "subject": "Department newsletter (retired)", "active": false } ],
              "errors": [] }""";
}
