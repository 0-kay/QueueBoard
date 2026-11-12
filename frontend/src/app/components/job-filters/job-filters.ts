import { ChangeDetectionStrategy, Component, computed, effect, input, output, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { JOB_STATUSES, JOB_TYPES, JobStatus, JobType, JobTypeInfo } from '../../api/models';
import { STATUS_LABELS, typeLabel } from '../../shared/format';

@Component({
  selector: 'app-job-filters',
  imports: [MatFormFieldModule, MatInputModule, MatSelectModule, MatIconModule, MatButtonModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <mat-form-field class="search" subscriptSizing="dynamic">
      <mat-label>Search by name</mat-label>
      <mat-icon matPrefix>search</mat-icon>
      <input matInput type="search" maxlength="100" [value]="draft()" (input)="onInput($any($event.target).value)"
        (keydown.enter)="flush()" data-testid="search" />
      @if (draft()) {
        <button matSuffix mat-icon-button aria-label="Clear search" (click)="onInput(''); flush()"><mat-icon>close</mat-icon></button>
      }
    </mat-form-field>
    <mat-form-field class="status" subscriptSizing="dynamic">
      <mat-label>Status</mat-label>
      <mat-select [value]="status() ?? ''" (selectionChange)="statusChange.emit($event.value || null)" data-testid="status-filter">
        <mat-option value="">All statuses</mat-option>
        @for (s of statuses; track s) { <mat-option [value]="s">{{ statusLabels[s] }}</mat-option> }
      </mat-select>
    </mat-form-field>
    <mat-form-field class="type" subscriptSizing="dynamic">
      <mat-label>Type</mat-label>
      <mat-select [value]="type() ?? ''" (selectionChange)="typeChange.emit($event.value || null)" data-testid="type-filter">
        <mat-option value="">All types</mat-option>
        @for (t of typeOptions(); track t.value) { <mat-option [value]="t.value">{{ t.label }}</mat-option> }
      </mat-select>
    </mat-form-field>
    @if (hasFilters()) {
      <button mat-button (click)="clear.emit()">Clear filters</button>
    }
  `,
  styles: `
    :host { display: flex; flex-wrap: wrap; align-items: center; gap: 12px; }
    .search { flex: 1 1 280px; max-width: 360px; }
    .status { width: 200px; }
    .type { width: 220px; }
    mat-form-field { --mat-form-field-outlined-container-shape: 4px; }
    .search mat-icon[matPrefix] { padding: 0 4px 0 12px; color: #49454f; }
  `,
})
export class JobFilters {
  readonly search = input<string>('');
  readonly status = input<JobStatus | null>(null);
  readonly type = input<JobType | null>(null);
  readonly types = input<JobTypeInfo[] | null>(null);
  readonly searchChange = output<string>();
  readonly statusChange = output<JobStatus | null>();
  readonly typeChange = output<JobType | null>();
  readonly clear = output<void>();

  protected readonly statuses = JOB_STATUSES;
  protected readonly statusLabels = STATUS_LABELS;
  protected readonly typeOptions = computed(() => JOB_TYPES.map((t) => ({ value: t, label: typeLabel(t, this.types()) })));
  protected readonly hasFilters = computed(() => !!(this.search() || this.status() || this.type()));

  /** Local text while typing; emitted after a short pause. */
  protected readonly draft = signal('');
  private timer: ReturnType<typeof setTimeout> | null = null;

  constructor() {
    effect(() => this.draft.set(this.search()));
  }

  protected onInput(value: string): void {
    this.draft.set(value);
    if (this.timer) clearTimeout(this.timer);
    this.timer = setTimeout(() => this.flush(), 300);
  }

  protected flush(): void {
    if (this.timer) clearTimeout(this.timer);
    this.timer = null;
    if (this.draft() !== this.search()) this.searchChange.emit(this.draft());
  }
}
