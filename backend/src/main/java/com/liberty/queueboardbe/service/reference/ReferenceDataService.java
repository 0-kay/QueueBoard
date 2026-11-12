package com.liberty.queueboardbe.service.reference;

import com.liberty.queueboardbe.model.reference.CourseResponse;
import com.liberty.queueboardbe.model.reference.JobTypeResponse;
import com.liberty.queueboardbe.model.reference.NotificationTemplateResponse;
import com.liberty.queueboardbe.model.reference.TermResponse;

import java.util.List;

/** Dropdown data. Closed courses and terms and inactive templates are included on purpose. */
public interface ReferenceDataService {

    List<JobTypeResponse> jobTypes();

    List<CourseResponse> courses();

    List<TermResponse> terms();

    List<NotificationTemplateResponse> notificationTemplates();
}
