package com.liberty.queueboardbe.model.job;

/** Processing failure codes. The kind is defined here and never stored. */
public enum JobErrorCode {
    COURSE_CLOSED(ErrorKind.PERMANENT),
    TEMPLATE_INACTIVE(ErrorKind.PERMANENT),
    TERM_CLOSED(ErrorKind.PERMANENT),
    SIMULATED_PERMANENT(ErrorKind.PERMANENT),
    SIMULATED_TEMPORARY(ErrorKind.TEMPORARY),
    UNEXPECTED_ERROR(ErrorKind.TEMPORARY);

    private final ErrorKind kind;

    JobErrorCode(ErrorKind kind) {
        this.kind = kind;
    }

    public ErrorKind kind() {
        return kind;
    }
}
