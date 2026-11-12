import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Router } from '@angular/router';
import { forkJoin, map } from 'rxjs';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { JobApiService } from '../../api/job-api.service';
import { JOB_STATUSES, JobStatus } from '../../api/models';
import { MetricCard } from '../../components/metric-card/metric-card';
import { RecentJobs } from '../../components/recent-jobs/recent-jobs';
import { StatusCard } from '../../components/status-card/status-card';
import { ClockService } from '../../services/clock.service';
import { JobPollingService } from '../../services/job-polling.service';
import { JobSubmitService } from '../../services/job-submit.service';
import { QueueStateService } from '../../services/queue-state.service';
import { ReferenceDataService } from '../../services/reference-data.service';
import { SessionJobsService } from '../../services/session-jobs.service';
import { formatPercent, formatSeconds, isActiveStatus } from '../../shared/format';

const STATUS_TIPS: Record<JobStatus, string> = {
  QUEUED: 'Jobs waiting for a worker to pick them up.',
  PROCESSING: 'Jobs a worker is running right now.',
  COMPLETED: 'Jobs whose last attempt finished successfully.',
  FAILED: 'Jobs whose last attempt failed. Temporary failures can be retried.',
};

interface HealthItem { label: string; value: string; sub: string | null; tooltip: string }

@Component({
  selector: 'app-dashboard-page',
  imports: [MatButtonModule, MatIconModule, StatusCard, MetricCard, RecentJobs],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <main class="page">
      <div class="title-row">
        <h1>Dashboard</h1>
        <button mat-flat-button (click)="create()"><mat-icon>add</mat-icon>Create new job</button>
      </div>

      @if (state.stats.error() && !state.stats.value()) {
        <div class="load-error" role="alert">
          <mat-icon class="err">error</mat-icon>
          <div class="load-error-text"><strong>Couldn’t load job counts.</strong> {{ state.stats.error()!.description }}</div>
          <button mat-stroked-button (click)="refresh()"><mat-icon>refresh</mat-icon>Try again</button>
        </div>
      } @else {
        <div class="cards">
          @for (s of statuses; track s) {
            <app-status-card [status]="s" [count]="counts()[s]" [tooltip]="tips[s]" [loading]="state.stats.loading()"
              (open)="openStatus($event)" />
          }
        </div>
      }

      <div class="split">
        <app-recent-jobs class="recent" [jobs]="state.recent.value()" [loading]="state.recent.loading()"
          [error]="state.recent.error()" [now]="clock.now()" [types]="reference.jobTypes()"
          (open)="openJob($event)" (viewAll)="viewAll()" (create)="create()" (retry)="refresh()" />

        <section class="health" aria-labelledby="qb-health">
          <h2 id="qb-health">Health</h2>
          @for (h of health(); track h.label) {
            <app-metric-card [label]="h.label" [value]="h.value" [sub]="h.sub" [tooltip]="h.tooltip"
              [loading]="state.stats.loading()" />
          }
        </section>
      </div>
    </main>
  `,
  styles: `
    :host { display: block; }
    .page { padding: 24px 32px 32px; display: flex; flex-direction: column; gap: 24px; max-width: 1376px; box-sizing: border-box; }
    .title-row { display: flex; align-items: center; justify-content: space-between; gap: 16px; }
    h1 { margin: 0; font: 400 28px/36px Roboto, sans-serif; }
    .cards { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 16px; }
    .split { display: grid; grid-template-columns: minmax(0, 2fr) minmax(0, 1fr); gap: 24px; align-items: start; }
    .health { display: flex; flex-direction: column; gap: 12px; }
    .health h2 { margin: 0; padding: 4px 0 0; font: 500 16px/24px Roboto, sans-serif; }
    .load-error { display: flex; align-items: center; gap: 12px; padding: 16px 20px; background: #fff; border: 1px solid #e0e0e0; border-radius: 12px; }
    .load-error-text { flex: 1; font: 400 14px/20px Roboto, sans-serif; color: #49454f; }
    .load-error-text strong { color: #1d1b20; font-weight: 500; }
    .err { color: #b3261e; }
    @media (max-width: 1023px) {
      .cards { grid-template-columns: repeat(2, minmax(0, 1fr)); }
      .split { grid-template-columns: minmax(0, 1fr); }
    }
    @media (max-width: 899px) { .page { padding: 16px; } }
  `,
})
export class DashboardPage {
  protected readonly state = inject(QueueStateService);
  protected readonly clock = inject(ClockService);
  protected readonly reference = inject(ReferenceDataService);
  private readonly api = inject(JobApiService);
  private readonly polling = inject(JobPollingService);
  private readonly session = inject(SessionJobsService);
  private readonly submit = inject(JobSubmitService);
  private readonly router = inject(Router);

  protected readonly statuses = JOB_STATUSES;
  protected readonly tips = STATUS_TIPS;

  protected readonly counts = computed(() => {
    const by = this.state.stats.value()?.byStatus;
    return { QUEUED: by?.QUEUED ?? 0, PROCESSING: by?.PROCESSING ?? 0, COMPLETED: by?.COMPLETED ?? 0, FAILED: by?.FAILED ?? 0 };
  });

  protected readonly health = computed<HealthItem[]>(() => {
    const s = this.state.stats.value();
    const c = this.counts();
    const finished = c.COMPLETED + c.FAILED;
    const noData = !s || finished === 0;
    const dash = (v: string) => (noData ? '—' : v);
    const sub = (v: string) => (noData ? 'Not enough data yet' : v);
    return [
      { label: 'Avg queue wait', value: dash(formatSeconds(s?.avgQueueWaitMs)), sub: noData ? sub('') : null,
        tooltip: 'Average time from submission until a worker starts the first attempt.' },
      { label: 'Avg run time', value: dash(formatSeconds(s?.avgRunTimeMs)), sub: noData ? sub('') : null,
        tooltip: 'Average duration of completed attempts.' },
      { label: 'Failure rate', value: dash(formatPercent(s?.failureRate.total)),
        sub: sub(`${formatPercent(s?.failureRate.temporary)} temporary, ${formatPercent(s?.failureRate.permanent)} permanent`),
        tooltip: 'Share of finished jobs that ended FAILED, split by error kind.' },
      { label: 'Retry recovery', value: dash(formatPercent(s?.retryRecoveryRate)), sub: sub('of retried jobs later completed'),
        tooltip: 'Of jobs that failed at least once, the share that later completed.' },
    ];
  });

  constructor() {
    this.polling
      .poll(
        () => forkJoin({
          stats: this.api.stats(),
          recent: this.api.list({ page: 0, size: 5 }).pipe(map((p) => p.items)),
        }),
        (d) => d.stats.byStatus.QUEUED + d.stats.byStatus.PROCESSING > 0 || d.recent.some((j) => isActiveStatus(j.status)),
      )
      .pipe(takeUntilDestroyed())
      .subscribe((ev) => {
        this.state.stats.apply(ev.data?.stats, ev.error);
        this.state.recent.apply(ev.data?.recent, ev.error);
        if (ev.data) this.session.check(ev.data.recent);
      });
  }

  protected create(): void { this.submit.open(); }
  protected refresh(): void { this.polling.refreshNow(); }
  protected viewAll(): void { this.router.navigate(['/jobs']); }
  protected openJob(id: string): void { this.router.navigate(['/jobs', id]); }
  protected openStatus(status: JobStatus): void { this.router.navigate(['/jobs'], { queryParams: { status } }); }
}
