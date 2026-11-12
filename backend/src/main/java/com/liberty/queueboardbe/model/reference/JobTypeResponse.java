package com.liberty.queueboardbe.model.reference;

import com.liberty.queueboardbe.model.job.JobType;

public record JobTypeResponse(JobType type, String label, String description) {
}
