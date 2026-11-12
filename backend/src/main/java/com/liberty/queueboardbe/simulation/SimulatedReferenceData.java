package com.liberty.queueboardbe.simulation;

import com.liberty.queueboardbe.model.job.JobType;
import com.liberty.queueboardbe.model.reference.CourseResponse;
import com.liberty.queueboardbe.model.reference.JobTypeResponse;
import com.liberty.queueboardbe.model.reference.NotificationTemplateResponse;
import com.liberty.queueboardbe.model.reference.TermResponse;
import com.liberty.queueboardbe.service.reference.ReferenceDataService;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

/** Dropdown data for the submit form, read from the simulated LMS tables. */
@Service
public class SimulatedReferenceData implements ReferenceDataService {

    private final LmsRepository lms;

    public SimulatedReferenceData(LmsRepository lms) {
        this.lms = lms;
    }

    @Override
    public List<JobTypeResponse> jobTypes() {
        return Arrays.stream(JobType.values()).map(t -> new JobTypeResponse(t, t.label(), t.description())).toList();
    }

    @Override
    public List<CourseResponse> courses() {
        return lms.courses().stream().map(c -> new CourseResponse(c.code(), c.title(), c.status())).toList();
    }

    @Override
    public List<TermResponse> terms() {
        return lms.terms().stream().map(t -> new TermResponse(t.code(), t.name(), t.status())).toList();
    }

    @Override
    public List<NotificationTemplateResponse> notificationTemplates() {
        return lms.templates().stream()
                .map(t -> new NotificationTemplateResponse(t.code(), t.kind(), t.subject(), t.active()))
                .toList();
    }
}
