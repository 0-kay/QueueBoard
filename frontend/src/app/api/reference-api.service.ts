import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { forkJoin, Observable } from 'rxjs';
import { CourseRef, JobTypeInfo, ReferenceData, TemplateRef, TermRef } from './models';

@Injectable({ providedIn: 'root' })
export class ReferenceApiService {
  private readonly http = inject(HttpClient);

  jobTypes(): Observable<JobTypeInfo[]> {
    return this.http.get<JobTypeInfo[]>('/api/v1/job-types');
  }

  courses(): Observable<CourseRef[]> {
    return this.http.get<CourseRef[]>('/api/v1/courses');
  }

  terms(): Observable<TermRef[]> {
    return this.http.get<TermRef[]>('/api/v1/terms');
  }

  templates(): Observable<TemplateRef[]> {
    return this.http.get<TemplateRef[]>('/api/v1/notification-templates');
  }

  all(): Observable<ReferenceData> {
    return forkJoin({
      jobTypes: this.jobTypes(),
      courses: this.courses(),
      terms: this.terms(),
      templates: this.templates(),
    });
  }
}
