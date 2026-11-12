import { inject, Injectable } from '@angular/core';
import { catchError, forkJoin, map, Observable, of } from 'rxjs';
import { AppError, toAppError } from '../api/app-error';
import { JobApiService } from '../api/job-api.service';
import { JobDetail, ReferenceData, SubmitJobRequest } from '../api/models';
import { DemoAction } from '../components/demo-menu/demo-menu';
import { JobPollingService } from './job-polling.service';
import { NotificationService } from './notification.service';
import { ReferenceDataService } from './reference-data.service';
import { SessionJobsService } from './session-jobs.service';

type Outcome = { job: JobDetail } | { error: AppError };

/** Demo menu actions. Each one is just real POST /api/v1/jobs calls. */
@Injectable({ providedIn: 'root' })
export class DemoService {
  private readonly api = inject(JobApiService);
  private readonly reference = inject(ReferenceDataService);
  private readonly notify = inject(NotificationService);
  private readonly session = inject(SessionJobsService);
  private readonly polling = inject(JobPollingService);

  run(action: DemoAction): void {
    this.reference.load().subscribe({
      next: (ref) => {
        const requests = buildDemoRequests(action, ref);
        if (typeof requests === 'string') {
          this.notify.show(requests);
          return;
        }
        this.submitAll(requests).subscribe((outcomes) => this.report(outcomes));
      },
      error: (err: unknown) => this.notify.show(toAppError(err).description),
    });
  }

  private submitAll(requests: SubmitJobRequest[]): Observable<Outcome[]> {
    return forkJoin(
      requests.map((r) =>
        this.api.submit(r).pipe(
          map((job): Outcome => ({ job })),
          catchError((err: unknown) => of<Outcome>({ error: toAppError(err) })),
        ),
      ),
    );
  }

  private report(outcomes: Outcome[]): void {
    const ok = outcomes.filter((o): o is { job: JobDetail } => 'job' in o);
    const firstError = outcomes.find((o): o is { error: AppError } => 'error' in o)?.error;
    for (const { job } of ok) this.session.track(job.id, job.name);
    const n = outcomes.length;
    if (ok.length === n) {
      this.notify.show(`Submitted ${n} demo job${n === 1 ? '' : 's'}.`);
    } else if (ok.length === 0) {
      this.notify.show(firstError?.description ?? 'The demo jobs were not submitted.');
    } else {
      this.notify.show(`Submitted ${ok.length} of ${n} demo jobs. ${firstError?.description ?? ''}`.trim());
    }
    if (ok.length) this.polling.refreshNow();
  }
}

/** Builds the requests for a demo action, or returns a message when the reference data can't support it. */
export function buildDemoRequests(action: DemoAction, ref: ReferenceData): SubmitJobRequest[] | string {
  const openCourses = ref.courses.filter((c) => c.status === 'OPEN');
  const openTerms = ref.terms.filter((t) => t.status === 'OPEN');
  const activeTemplates = ref.templates.filter((t) => t.active);
  const courses = openCourses.length ? openCourses : ref.courses;
  const terms = openTerms.length ? openTerms : ref.terms;
  const templates = activeTemplates.length ? activeTemplates : ref.templates;

  const grade = (i: number): SubmitJobRequest | null => {
    const c = courses[i % Math.max(courses.length, 1)];
    return c ? { name: `Demo grades ${c.code}`, type: 'GRADE_REPORT', payload: { courseCode: c.code } } : null;
  };
  const notice = (i: number): SubmitJobRequest | null => {
    const c = courses[i % Math.max(courses.length, 1)];
    const t = templates[i % Math.max(templates.length, 1)];
    return c && t
      ? { name: `Demo notice ${c.code}`, type: 'NOTIFICATION_BATCH', payload: { templateCode: t.code, courseCode: c.code, channel: i % 2 ? 'PUSH' : 'EMAIL' } }
      : null;
  };
  const billing = (i: number): SubmitJobRequest | null => {
    const t = terms[i % Math.max(terms.length, 1)];
    return t ? { name: `Demo billing ${t.code}`, type: 'BILLING_ASSESSMENT', payload: { termCode: t.code, applyLateFees: i % 2 === 0 } } : null;
  };
  const missing = 'Reference data is missing, so the demo can’t build its jobs.';

  switch (action) {
    case 'one-of-each': {
      const list = [grade(0), notice(0), billing(0)];
      return list.every(Boolean) ? (list as SubmitJobRequest[]) : missing;
    }
    case 'temporary-failure': {
      const g = grade(0);
      if (!g) return missing;
      return [{ ...g, name: `Demo temporary failure ${g.payload['courseCode']}`, payload: { ...g.payload, simulateFailure: 'TEMPORARY' } }];
    }
    case 'permanent-failure': {
      const closed = ref.courses.find((c) => c.status === 'CLOSED');
      if (!closed) return 'No closed course is available for a permanent failure.';
      return [{ name: `Demo grades ${closed.code} (closed)`, type: 'GRADE_REPORT', payload: { courseCode: closed.code } }];
    }
    case 'burst': {
      const makers = [grade, notice, billing];
      const list = Array.from({ length: 15 }, (_, i) => {
        const r = makers[i % 3](Math.floor(i / 3));
        return r ? { ...r, name: `${r.name} #${i + 1}` } : null;
      });
      return list.every(Boolean) ? (list as SubmitJobRequest[]) : missing;
    }
  }
}
