import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { JobDetail, JobQuery, JobSummary, Page, Stats, SubmitJobRequest } from './models';

const BASE = '/api/v1/jobs';

@Injectable({ providedIn: 'root' })
export class JobApiService {
  private readonly http = inject(HttpClient);

  list(query: JobQuery = {}): Observable<Page<JobSummary>> {
    let params = new HttpParams();
    if (query.status) params = params.set('status', query.status);
    if (query.type) params = params.set('type', query.type);
    if (query.search?.trim()) params = params.set('search', query.search.trim());
    if (query.page != null) params = params.set('page', query.page);
    if (query.size != null) params = params.set('size', query.size);
    return this.http.get<Page<JobSummary>>(BASE, { params });
  }

  get(id: string): Observable<JobDetail> {
    return this.http.get<JobDetail>(`${BASE}/${encodeURIComponent(id)}`);
  }

  submit(request: SubmitJobRequest): Observable<JobDetail> {
    return this.http.post<JobDetail>(BASE, request);
  }

  retry(id: string): Observable<JobDetail> {
    return this.http.post<JobDetail>(`${BASE}/${encodeURIComponent(id)}/retry`, null);
  }

  delete(id: string): Observable<null> {
    return this.http.delete<null>(`${BASE}/${encodeURIComponent(id)}`);
  }

  stats(): Observable<Stats> {
    return this.http.get<Stats>(`${BASE}/stats`);
  }
}
