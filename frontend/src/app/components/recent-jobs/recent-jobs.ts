import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatTableModule } from '@angular/material/table';
import { AppError } from '../../api/app-error';
import { JobSummary, JobTypeInfo } from '../../api/models';
import { formatAgo, typeLabel } from '../../shared/format';
import { StatusBadge } from '../status-badge/status-badge';

@Component({
  selector: 'app-recent-jobs',
  imports: [MatCardModule, MatTableModule, MatButtonModule, MatIconModule, StatusBadge],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <mat-card appearance="outlined" class="card">
      <div class="head">
        <h2>Recent jobs</h2>
        @if (jobs()?.length) {
          <button mat-button (click)="viewAll.emit()">View all jobs<mat-icon iconPositionEnd>arrow_forward</mat-icon></button>
        }
      </div>

      @if (error() && !jobs()) {
        <div class="state" role="alert">
          <mat-icon class="state-icon err">error</mat-icon>
          <div class="state-title">Couldn’t load recent jobs</div>
          <div class="mono-box">{{ error()!.description }}</div>
          <button mat-stroked-button (click)="retry.emit()"><mat-icon>refresh</mat-icon>Try again</button>
        </div>
      } @else if (loading()) {
        <div class="grid header-row"><span>Name</span><span>Type</span><span>Status</span><span>Created</span></div>
        @for (i of skeleton; track i) {
          <div class="grid row skel-row" aria-busy="true">
            <span class="qb-skel" style="height:12px;width:70%"></span><span class="qb-skel" style="height:12px;width:60%"></span>
            <span class="qb-skel" style="height:20px;width:72px;border-radius:10px"></span><span class="qb-skel" style="height:12px;width:50%"></span>
          </div>
        }
      } @else if (!jobs()?.length) {
        <div class="grid header-row"><span>Name</span><span>Type</span><span>Status</span><span>Created</span></div>
        <div class="state">
          <mat-icon class="state-icon">inbox</mat-icon>
          <div class="state-title">No jobs yet</div>
          <div class="state-sub">Jobs you submit will show up here.</div>
          <button mat-flat-button (click)="create.emit()"><mat-icon>add</mat-icon>Create new job</button>
        </div>
      } @else {
        <table mat-table [dataSource]="jobs()!" class="table" aria-label="Recent jobs">
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
          <ng-container matColumnDef="created">
            <th mat-header-cell *matHeaderCellDef>Created</th>
            <td mat-cell *matCellDef="let j" class="muted">{{ ago(j.createdAt) }}</td>
          </ng-container>
          <tr mat-header-row *matHeaderRowDef="columns"></tr>
          <tr mat-row *matRowDef="let j; columns: columns" class="clickable" tabindex="0" role="link"
            [attr.aria-label]="'Open ' + j.name" (click)="open.emit(j.id)" (keydown.enter)="open.emit(j.id)"></tr>
        </table>
      }
    </mat-card>
  `,
  styles: `
    :host { display: block; }
    .card { overflow: hidden; }
    .head { display: flex; align-items: center; justify-content: space-between; padding: 12px 12px 12px 20px; min-height: 64px; box-sizing: border-box; border-bottom: 1px solid #e0e0e0; }
    h2 { margin: 0; font: 500 16px/24px Roboto, sans-serif; }
    .grid { display: grid; grid-template-columns: minmax(0,2.2fr) minmax(0,1.5fr) minmax(0,1.1fr) minmax(0,1fr); gap: 16px; padding: 0 20px; align-items: center; }
    .header-row { height: 44px; border-bottom: 1px solid #e0e0e0; font: 500 12px Roboto, sans-serif; color: #49454f; }
    .row { height: 52px; border-bottom: 1px solid #eeeeee; }
    .table { width: 100%; --mat-table-header-container-height: 44px; table-layout: fixed; }
    .table th { border-bottom-color: #e0e0e0; }
    .table th:nth-child(1), .table td:nth-child(1) { width: 38%; }
    .table th:nth-child(2), .table td:nth-child(2) { width: 25%; }
    .table th:nth-child(3), .table td:nth-child(3) { width: 20%; }
    .name { font-weight: 500; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
    .muted { color: #49454f; }
    .clickable { cursor: pointer; }
    .clickable:hover, .clickable:focus-visible { background: #f5f5f5; outline: none; }
    .state { display: flex; flex-direction: column; align-items: center; gap: 8px; padding: 48px 24px 56px; text-align: center; }
    .state-icon { font-size: 40px; width: 40px; height: 40px; color: #79747e; }
    .state-icon.err { color: #b3261e; }
    .state-title { font: 500 16px/24px Roboto, sans-serif; }
    .state-sub { font: 400 14px/20px Roboto, sans-serif; color: #49454f; margin-bottom: 8px; }
    .mono-box { font: 400 13px/20px 'Roboto Mono', monospace; color: #49454f; background: #f5f5f5; border-radius: 4px; padding: 4px 8px; margin-bottom: 8px; }
  `,
})
export class RecentJobs {
  readonly jobs = input<JobSummary[] | null>(null);
  readonly loading = input(false);
  readonly error = input<AppError | null>(null);
  readonly now = input<number>(Date.now());
  readonly types = input<JobTypeInfo[] | null>(null);
  readonly open = output<string>();
  readonly viewAll = output<void>();
  readonly create = output<void>();
  readonly retry = output<void>();

  protected readonly columns = ['name', 'type', 'status', 'created'];
  protected readonly skeleton = [1, 2, 3, 4, 5];
  protected label(type: string) { return typeLabel(type, this.types()); }
  protected ago(iso: string) { return formatAgo(iso, this.now()); }
}
