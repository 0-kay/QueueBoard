package com.liberty.queueboardbe.model.job;

/** Whether a failure can go away on its own (TEMPORARY, retryable) or never will (PERMANENT). */
public enum ErrorKind {
    TEMPORARY,
    PERMANENT
}
