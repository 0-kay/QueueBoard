package com.liberty.queueboardbe.exception;

import java.util.UUID;

/** No such job, or it was soft-deleted. Maps to 404. */
public class JobNotFoundException extends RuntimeException {

    public JobNotFoundException(UUID id) {
        super("Job " + id + " not found");
    }
}
