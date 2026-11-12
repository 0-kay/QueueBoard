import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatSortModule, Sort, SortDirection } from '@angular/material/sort';
import { MatTableModule } from '@angular/material/table';
import { AppError } from '../../api/app-error';
import { JobSummary, JobTypeInfo, Page } from '../../api/models';
import { formatShort, typeLabel } from '../../shared/format';
import { StatusBadge } from '../status-badge/status-badge';

export type JobSortField = 'createdAt' | 'updatedAt';
export interface JobSort { active: JobSortField; direction: SortDirection }

@Component({
  selector: 'app-job-table',
  imports: [MatCardModule, MatTableModule, MatSortModule, MatPaginatorModule, MatButtonModule, MatIconModule, StatusBadge],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <mat-card appearance="outlined" class="card">
      @if (error() && !page()) {
        <div class="state" role="alert" data-testid="table-error">
          <mat-icon class="state-icon err">error</mat-icon>
          <div class="state-title">Couldn’t load jobs</div>
          <div class="mono-box">{{ error()!.description }}</div>
          <button mat-stroked-button (click)="retry.emit()"><mat-icon>refresh</mat-icon>Try again</button>
        </div>
      } @else if (loading()) {
        <div class="grid header-row"><span>Name</span><span>Type</span><span>Status</span><span>Attempts</span><span>Created</span><span>Updated</span></div>
        @for (i of skeleton; track i) {
          <div class="grid row" aria-busy="true">
            <span class="qb-skel" style="height:12px;width:70%"></span><span class="qb-skel" style="height:12px;width:60%"></span>
            <span class="qb-skel" style="height:20px;width:72px;border-radius:10px"></span><span class="qb-skel" style="height:12px;width:40%"></span>
            <span class="qb-skel" style="height:12px;width:70%"></span><span class="qb-skel" style="height:12px;width:70%"></span>
          </div>
        }
      } @else if (!rows().length) {
        <div class="grid header-row"><span>Name</span><span>Type</span><span>Status</span><span>Attempts</span><span>Created</span><span>Updated</span></div>
        @if (filtered()) {
          <div class="state" data-testid="filtered-empty">
            <mat-icon class="state-icon">filter_alt_off</mat-icon>
            <div class="state-title">No jobs match these filters</div>
            @if (filterSummary()) { <div class="state-sub">{{ filterSummary() }}</div> }
            <button mat-stroked-button (click)="clearFilters.emit()">Clear filters</button>
          </div>
        } @else {
          <div class="state" data-testid="empty">
            <mat-icon class="state-icon">inbox</mat-icon>
            <div class="state-title">No jobs yet</div>
            <div class="state-sub">Jobs you submit will show up here.</div>
            <button mat-flat-button (click)="create.emit()"><mat-icon>add</mat-icon>Create new job</button>
          </div>
        }
      } @else {
        <div class="scroll">
          <table mat-table [dataSource]="rows()" matSort [matSortActive]="sort().active" [matSortDirection]="sort().direction"
            matSortDisableClear (matSortChange)="onSort($event)" class="table" aria-label="Jobs">
            <ng-container matColumnDef="name">
              <th mat-header-cell *matHeaderCellDef>Name</th>
              <td mat-cell *matCellDef="let j" class="name">{{ j.name }}</td>
            </ng-container>
            <ng-container matColumnDef="type">
              <th mat-header-cell *matHeaderCellDef>Type</th>
              <td mat-cell *matCellDef="let j" class="muted">{{ label(j.type) }}</td>
            </ng-container>
            <ng-container matColumnDef="status">
              <th mat-header-cell *matHeaderCellDef>Status</th>
              <td mat-cell *matCellDef="let j"><app-status-badge [status]="j.status" /></td>
            </ng-container>
            <ng-container matColumnDef="attempts">
              <th mat-header-cell *matHeaderCellDef>Attempts</th>
              <td mat-cell *matCellDef="let j" class="muted">{{ j.attempts }} of {{ j.maxAttempts }}</td>
            </ng-container>
            <ng-container matColumnDef="createdAt">
              <th mat-header-cell *matHeaderCellDef mat-sort-header start="desc" sortActionDescription="Sort by created">Created</th>
              <td mat-cell *matCellDef="let j" class="muted">{{ short(j.createdAt) }}</td>
            </ng-container>
            <ng-container matColumnDef="updatedAt">
              <th mat-header-cell *matHeaderCellDef mat-sort-header start="desc" sortActionDescription="Sort by updated">Updated</th>
              <td mat-cell *matCellDef="let j" class="muted">{{ short(j.updatedAt) }}</td>
            </ng-container>
            <tr mat-header-row *matHeaderRowDef="columns"></tr>
            <tr mat-row *matRowDef="let j; columns: columns" class="clickable" tabindex="0" role="link"
              [class.selected]="j.id === selectedId()" [attr.aria-label]="'Open ' + j.name"
              (click)="open.emit(j.id)" (keydown.enter)="open.emit(j.id)"></tr>
          </table>
        </div>
      }
      <mat-paginator [length]="page()?.totalItems ?? 0" [pageIndex]="page()?.page ?? 0" [pageSize]="pageSize()"
        [pageSizeOptions]="[20, 50, 100]" (page)="onPage($event)" aria-label="Jobs pages"></mat-paginator>
    </mat-card>
  `,
  styles: `
    :host { display: block; }
    .card { overflow: hidden; }
    .scroll { overflow-x: auto; }
    .table { width: 100%; min-width: 760px; table-layout: fixed; }
    .table th:nth-child(1) { width: 25%; } .table th:nth-child(2) { width: 16%; } .table th:nth-child(3) { width: 13%; }
    .table th:nth-child(4) { width: 9%; } .table th:nth-child(5), .table th:nth-child(6) { width: 13%; }
    .table th { border-bottom-color: #e0e0e0; }
    .grid { display: grid; grid-template-columns: minmax(0,2.2fr) minmax(0,1.4fr) minmax(0,1.1fr) minmax(0,.8fr) minmax(0,1.1fr) minmax(0,1.1fr); gap: 16px; padding: 0 20px; align-items: center; }
    .header-row { height: 56px; border-bottom: 1px solid #e0e0e0; font: 500 12px Roboto, sans-serif; color: #49454f; }
    .row { height: 52px; border-bottom: 1px solid #eeeeee; }
    .name { font-weight: 500; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
    .muted { color: #49454f; }
    .clickable { cursor: pointer; }
    .clickable:hover, .clickable:focus-visible { background: #f5f5f5; outline: none; }
    .clickable.selected { background: #e3eef9; }
    .state { display: flex; flex-direction: column; align-items: center; gap: 8px; padding: 64px 24px 72px; text-align: center; }
    .state-icon { font-size: 40px; width: 40px; height: 40px; color: #79747e; }
    .state-icon.err { color: #b3261e; }
    .state-title { font: 500 16px/24px Roboto, sans-serif; }
    .state-sub { font: 400 14px/20px Roboto, sans-serif; color: #49454f; margin-bottom: 8px; }
    .mono-box { font: 400 13px/20px 'Roboto Mono', monospace; color: #49454f; background: #f5f5f5; border-radius: 4px; padding: 4px 8px; margin-bottom: 8px; }
    mat-paginator { border-top: 1px solid #e0e0e0; }
  `,
})
export class JobTable {
  readonly page = input<Page<JobSummary> | null>(null);
  readonly loading = input(false);
  readonly error = input<AppError | null>(null);
  readonly filtered = input(false);
  readonly filterSummary = input<string>('');
  readonly sort = input<JobSort>({ active: 'createdAt', direction: 'desc' });
  readonly pageSize = input(20);
  readonly selectedId = input<string | null>(null);
  readonly types = input<JobTypeInfo[] | null>(null);

  readonly open = output<string>();
  readonly sortChange = output<JobSort>();
  readonly pageChange = output<{ page: number; size: number }>();
  readonly clearFilters = output<void>();
  readonly create = output<void>();
  readonly retry = output<void>();

  protected readonly columns = ['name', 'type', 'status', 'attempts', 'createdAt', 'updatedAt'];
  protected readonly skeleton = Array.from({ length: 8 }, (_, i) => i);

  /** The API returns newest first; sorting reorders the rows of the current page. */
  protected readonly rows = computed(() => {
    const items = [...(this.page()?.items ?? [])];
    const { active, direction } = this.sort();
    if (!direction) return items;
    const sign = direction === 'asc' ? 1 : -1;
    return items.sort((a, b) => sign * (Date.parse(a[active]) - Date.parse(b[active])));
  });

  protected label(type: string) { return typeLabel(type, this.types()); }
  protected short(iso: string) { return formatShort(iso); }

  protected onSort(s: Sort): void {
    this.sortChange.emit({ active: (s.active as JobSortField) || 'createdAt', direction: s.direction || 'desc' });
  }

  protected onPage(e: PageEvent): void {
    this.pageChange.emit({ page: e.pageIndex, size: e.pageSize });
  }
}
