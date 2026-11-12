import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable, toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, NavigationEnd, Router, RouterOutlet } from '@angular/router';
import { catchError, distinctUntilChanged, filter, forkJoin, map, of, startWith, switchMap } from 'rxjs';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatSidenavModule } from '@angular/material/sidenav';
import { AppError, toAppError } from '../../api/app-error';
import { JobApiService } from '../../api/job-api.service';
import { JOB_STATUSES, JOB_TYPES, JobDetail, JobQuery, JobStatus, JobType } from '../../api/models';
import { ConfirmDialog, ConfirmDialogData } from '../../components/confirm-dialog/confirm-dialog';
import { DetailPanel } from '../../components/detail-panel/detail-panel';
import { JobFilters } from '../../components/job-filters/job-filters';
import { JobSort, JobSortField, JobTable } from '../../components/job-table/job-table';
import { JobPollingService } from '../../services/job-polling.service';
import { JobSubmitService } from '../../services/job-submit.service';
import { NotificationService } from '../../services/notification.service';
import { QueueStateService } from '../../services/queue-state.service';
import { ReferenceDataService } from '../../services/reference-data.service';
import { SessionJobsService } from '../../services/session-jobs.service';
import { isActiveStatus, STATUS_LABELS, typeLabel } from '../../shared/format';

const DEFAULT_SIZE = 20;

interface ViewQuery extends Required<Pick<JobQuery, 'page' | 'size'>> {
  status: JobStatus | null;
  type: JobType | null;
  search: string;
  sort: JobSort;
}

type JobPart = { job: JobDetail } | { error: AppError } | null;

@Component({
  selector: 'app-jobs-page',
  imports: [MatSidenavModule, MatButtonModule, MatIconModule, RouterOutlet, JobFilters, JobTable, DetailPanel],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <mat-sidenav-container class="container" [hasBackdrop]="true">
      <mat-sidenav-content>
        <main class="page">
          <div class="title-row">
            <h1>Jobs</h1>
            <button mat-flat-button (click)="create()"><mat-icon>add</mat-icon>Create new job</button>
          </div>
          <app-job-filters [search]="query().search" [status]="query().status" [type]="query().type" [types]="reference.jobTypes()"
            (searchChange)="update({ search: $event || null })" (statusChange)="update({ status: $event })"
            (typeChange)="update({ type: $event })" (clear)="clearFilters()" />
          <app-job-table [page]="state.jobs.value()" [loading]="state.jobs.loading()" [error]="state.jobs.error()"
            [filtered]="filtered()" [filterSummary]="filterSummary()" [sort]="query().sort" [pageSize]="query().size"
            [selectedId]="jobId()" [types]="reference.jobTypes()"
            (open)="openJob($event)" (sortChange)="onSort($event)" (pageChange)="onPage($event)"
            (clearFilters)="clearFilters()" (create)="create()" (retry)="refresh()" />
        </main>
      </mat-sidenav-content>
      <mat-sidenav class="sidenav" position="end" mode="over" [opened]="!!jobId()" (openedChange)="!$event && closePanel()"
        [autoFocus]="false">
        @if (jobId()) {
          <app-detail-panel [job]="state.openJob.value()" [loading]="state.openJob.loading()" [error]="state.openJob.error()"
            [types]="reference.jobTypes()" [busy]="busy()"
            (close)="closePanel()" (reload)="refresh()" (retry)="retry($event)" (duplicate)="duplicate($event)"
            (delete)="confirmDelete($event)" (copied)="notify.show($event + ' copied')" />
        }
      </mat-sidenav>
    </mat-sidenav-container>
    <router-outlet />
  `,
  styles: `
    :host { display: block; height: 100%; }
    .container { height: 100%; background: #fafafa; }
    .page { padding: 24px 32px 32px; display: flex; flex-direction: column; gap: 20px; max-width: 1376px; box-sizing: border-box; }
    .title-row { display: flex; align-items: center; justify-content: space-between; gap: 16px; }
    h1 { margin: 0; font: 400 28px/36px Roboto, sans-serif; }
    .sidenav { width: 480px; max-width: 100vw;
      box-shadow: 0 8px 10px -5px rgba(0,0,0,.2), 0 16px 24px 2px rgba(0,0,0,.14), 0 6px 30px 5px rgba(0,0,0,.12); }
    @media (max-width: 1023px) { .sidenav { width: 100vw; } }
    @media (max-width: 899px) { .page { padding: 16px; } }
  `,
})
export class JobsPage {
  protected readonly state = inject(QueueStateService);
  protected readonly reference = inject(ReferenceDataService);
  protected readonly notify = inject(NotificationService);
  private readonly api = inject(JobApiService);
  private readonly polling = inject(JobPollingService);
  private readonly session = inject(SessionJobsService);
  private readonly submit = inject(JobSubmitService);
  private readonly dialog = inject(MatDialog);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  protected readonly busy = signal<'retry' | 'delete' | null>(null);

  private readonly params = toSignal(this.route.queryParamMap, { requireSync: true });

  /** The open job id from the child route (/jobs/:id). */
  protected readonly jobId = toSignal(
    this.router.events.pipe(
      filter((e) => e instanceof NavigationEnd),
      startWith(null),
      map(() => this.route.snapshot.firstChild?.paramMap.get('id') ?? null),
      distinctUntilChanged(),
    ),
    { initialValue: null },
  );

  protected readonly query = computed<ViewQuery>(() => {
    const p = this.params();
    const status = p.get('status');
    const type = p.get('type');
    const page = Number(p.get('page'));
    const size = Number(p.get('size'));
    const sortField = p.get('sort');
    const dir = p.get('dir');
    return {
      status: (JOB_STATUSES as readonly string[]).includes(status ?? '') ? (status as JobStatus) : null,
      type: (JOB_TYPES as readonly string[]).includes(type ?? '') ? (type as JobType) : null,
      search: p.get('search') ?? '',
      page: Number.isInteger(page) && page > 0 ? page : 0,
      size: [20, 50, 100].includes(size) ? size : DEFAULT_SIZE,
      sort: {
        active: (sortField === 'updatedAt' ? 'updatedAt' : 'createdAt') as JobSortField,
        direction: dir === 'asc' ? 'asc' : 'desc',
      },
    };
  });

  protected readonly filtered = computed(() => {
    const q = this.query();
    return !!(q.status || q.type || q.search);
  });

  protected readonly filterSummary = computed(() => {
    const q = this.query();
    const what = [q.status ? STATUS_LABELS[q.status] : '', q.type ? typeLabel(q.type, this.reference.jobTypes()) : '']
      .filter(Boolean).join(' ');
    const scope = what ? `${what} jobs` : 'all jobs';
    return q.search ? `Searching “${q.search}” in ${scope}.` : `Showing ${scope}.`;
  });

  constructor() {
    this.reference.loadJobTypes();
    const fetchKey = computed(() => {
      const q = this.query();
      return { status: q.status, type: q.type, search: q.search, page: q.page, size: q.size, id: this.jobId() };
    }, { equal: (a, b) => JSON.stringify(a) === JSON.stringify(b) });

    let lastId: string | null = null;
    toObservable(fetchKey)
      .pipe(
        switchMap((key) => {
          if (key.id !== lastId) {
            this.state.openJob.reset();
            lastId = key.id;
          }
          return this.polling.poll(
            () => forkJoin({
              page: this.api.list({ status: key.status, type: key.type, search: key.search, page: key.page, size: key.size }),
              job: key.id
                ? this.api.get(key.id).pipe(
                    map((job): JobPart => ({ job })),
                    catchError((err: unknown) => of<JobPart>({ error: toAppError(err) })),
                  )
                : of<JobPart>(null),
            }),
            (d) => d.page.items.some((j) => isActiveStatus(j.status)) || (!!d.job && 'job' in d.job && isActiveStatus(d.job.job.status)),
          );
        }),
        takeUntilDestroyed(),
      )
      .subscribe((ev) => {
        this.state.jobs.apply(ev.data?.page, ev.error);
        const part = ev.data?.job;
        if (part && 'job' in part) {
          this.state.openJob.apply(part.job, undefined);
        } else if (part && 'error' in part) {
          if (part.error.status === 404) {
            this.state.openJob.value.set(null);
            this.state.openJob.error.set(part.error);
          } else {
            this.state.openJob.apply(undefined, part.error);
          }
        } else if (ev.error && this.jobId()) {
          this.state.openJob.apply(undefined, ev.error);
        }
        if (ev.data) {
          this.session.check(ev.data.page.items);
          if (part && 'job' in part) this.session.check([part.job]);
        }
      });
  }

  protected update(patch: Partial<Record<'status' | 'type' | 'search', string | null>>): void {
    this.navigate({ ...patch, page: null });
  }

  protected clearFilters(): void {
    this.navigate({ status: null, type: null, search: null, page: null });
  }

  protected onSort(sort: JobSort): void {
    const isDefault = sort.active === 'createdAt' && sort.direction === 'desc';
    this.navigate({ sort: isDefault ? null : sort.active, dir: isDefault ? null : sort.direction });
  }

  protected onPage(e: { page: number; size: number }): void {
    const sizeChanged = e.size !== this.query().size;
    this.navigate({ page: sizeChanged || e.page === 0 ? null : e.page, size: e.size === DEFAULT_SIZE ? null : e.size });
  }

  protected openJob(id: string): void {
    this.router.navigate(['/jobs', id], { queryParamsHandling: 'preserve' });
  }

  protected closePanel(): void {
    if (!this.jobId()) return;
    this.router.navigate(['/jobs'], { queryParamsHandling: 'preserve' });
  }

  protected refresh(): void {
    this.polling.refreshNow();
  }

  protected create(): void {
    this.submit.open();
  }

  protected duplicate(job: JobDetail): void {
    this.submit.open({ name: job.name, type: job.type, payload: job.payload });
  }

  /** Retry: show only what the server returns (QUEUED), never success. */
  protected retry(job: JobDetail): void {
    this.busy.set('retry');
    this.api.retry(job.id).subscribe({
      next: (updated) => {
        this.busy.set(null);
        this.state.openJob.value.set(updated);
        this.session.track(updated.id, updated.name);
        this.notify.show(`Retry queued · ${updated.name}`);
        this.polling.refreshNow();
      },
      error: (err: unknown) => {
        this.busy.set(null);
        const e = toAppError(err);
        this.notify.show(e.description);
        if (e.status === 409) this.polling.refreshNow();
      },
    });
  }

  protected confirmDelete(job: JobDetail): void {
    if (job.status !== 'COMPLETED' && job.status !== 'FAILED') return;
    const data: ConfirmDialogData = {
      title: 'Delete this job?',
      message: `“${job.name}” and its attempt history will be removed from QueueBoard. This can’t be undone.`,
      confirmLabel: 'Delete',
    };
    this.dialog
      .open(ConfirmDialog, { data, width: '400px', maxWidth: 'calc(100vw - 32px)', role: 'alertdialog' })
      .afterClosed()
      .pipe(filter((ok) => ok === true))
      .subscribe(() => this.delete(job));
  }

  private delete(job: JobDetail): void {
    this.busy.set('delete');
    this.api.delete(job.id).subscribe({
      next: () => {
        this.busy.set(null);
        this.notify.show('Job deleted');
        this.router.navigate(['/jobs'], { queryParamsHandling: 'preserve' });
        this.polling.refreshNow();
      },
      error: (err: unknown) => {
        this.busy.set(null);
        const e = toAppError(err);
        this.notify.show(e.description);
        if (e.status === 409 || e.status === 404) this.polling.refreshNow();
      },
    });
  }

  private navigate(params: Record<string, string | number | null>): void {
    const id = this.jobId();
    this.router.navigate(id ? ['/jobs', id] : ['/jobs'], { queryParams: params, queryParamsHandling: 'merge' });
  }
}

/** Empty child so /jobs/:id keeps JobsPage mounted. */
@Component({ selector: 'app-jobs-child', template: '' })
export class JobsChild {}
