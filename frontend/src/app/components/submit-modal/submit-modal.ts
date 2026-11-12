import { ChangeDetectionStrategy, Component, computed, effect, input, output, signal, untracked } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatDialogModule } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatRadioModule } from '@angular/material/radio';
import { MatSelectModule } from '@angular/material/select';
import { inject } from '@angular/core';
import { AppError } from '../../api/app-error';
import { FieldError, JOB_TYPES, JobType, ReferenceData, SubmitJobRequest } from '../../api/models';
import { typeLabel } from '../../shared/format';

export interface SubmitPrefill {
  name: string;
  type: JobType | string;
  payload: Record<string, unknown>;
}

type FieldName = 'name' | 'type' | 'courseCode' | 'templateCode' | 'channel' | 'termCode' | 'applyLateFees';
const PAYLOAD_KEYS = ['courseCode', 'templateCode', 'channel', 'termCode', 'applyLateFees'] as const;
const FIELDS_BY_TYPE: Record<JobType, FieldName[]> = {
  GRADE_REPORT: ['courseCode'],
  NOTIFICATION_BATCH: ['templateCode', 'courseCode', 'channel'],
  BILLING_ASSESSMENT: ['termCode', 'applyLateFees'],
};
const REQUIRED_BY_TYPE: Record<JobType, FieldName[]> = {
  GRADE_REPORT: ['courseCode'],
  NOTIFICATION_BATCH: ['templateCode', 'courseCode', 'channel'],
  BILLING_ASSESSMENT: ['termCode'],
};

/**
 * Submit / duplicate form. Presentational: reference data, the in-flight flag and the
 * server error come in as inputs; the request goes out through `submitJob`.
 */
@Component({
  selector: 'app-submit-modal',
  imports: [
    ReactiveFormsModule, MatDialogModule, MatFormFieldModule, MatInputModule, MatSelectModule, MatRadioModule,
    MatCheckboxModule, MatButtonModule, MatIconModule, MatProgressSpinnerModule,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h2 mat-dialog-title id="qb-create-title">{{ prefill() ? 'Duplicate job' : 'Create new job' }}</h2>
    <form [formGroup]="form" (ngSubmit)="onSubmit()" novalidate>
      <mat-dialog-content class="content">
        @if (serverError(); as err) {
          <div class="alert" role="alert" data-testid="form-error">
            <mat-icon class="alert-icon">error</mat-icon>
            <div>
              <strong>Couldn’t submit this job.</strong> {{ err.description }}
              @if (unmatched().length) {
                <ul>@for (e of unmatched(); track $index) { <li>{{ e.field ? e.field + ': ' : '' }}{{ e.message }}</li> }</ul>
              }
            </div>
          </div>
        }

        <mat-form-field>
          <mat-label>Name</mat-label>
          <input matInput formControlName="name" maxlength="100" cdkFocusInitial data-testid="name" />
          <mat-hint>Shown in the jobs list, e.g. “Midterm grades CSCI-110”</mat-hint>
          <mat-error data-testid="error-name">{{ errorText('name') }}</mat-error>
        </mat-form-field>

        <mat-form-field>
          <mat-label>Type</mat-label>
          <mat-select formControlName="type" data-testid="type">
            @for (t of typeOptions(); track t.value) { <mat-option [value]="t.value">{{ t.label }}</mat-option> }
          </mat-select>
          @if (!type()) { <mat-hint>Pick a type to see its options</mat-hint> }
          @else if (typeDescription()) { <mat-hint>{{ typeDescription() }}</mat-hint> }
          <mat-error data-testid="error-type">{{ errorText('type') }}</mat-error>
        </mat-form-field>

        @if (type()) { <div class="divider"></div> }

        @if (shows('templateCode')) {
          <mat-form-field>
            <mat-label>Template</mat-label>
            <mat-select formControlName="templateCode" data-testid="templateCode">
              @for (t of reference()?.templates ?? []; track t.code) {
                <mat-option [value]="t.code">{{ t.code }} · {{ t.subject }}@if (!t.active) {<span class="qb-muted-suffix">(inactive)</span>}</mat-option>
              }
            </mat-select>
            @if (!reference()) { <mat-hint>Loading templates…</mat-hint> }
            <mat-error data-testid="error-templateCode">{{ errorText('templateCode') }}</mat-error>
          </mat-form-field>
        }

        @if (shows('courseCode')) {
          <mat-form-field>
            <mat-label>Course</mat-label>
            <mat-select formControlName="courseCode" data-testid="courseCode">
              @for (c of reference()?.courses ?? []; track c.code) {
                <mat-option [value]="c.code">{{ c.code }} · {{ c.title }}@if (c.status === 'CLOSED') {<span class="qb-muted-suffix">(closed)</span>}</mat-option>
              }
            </mat-select>
            @if (!reference()) { <mat-hint>Loading courses…</mat-hint> }
            <mat-error data-testid="error-courseCode">{{ errorText('courseCode') }}</mat-error>
          </mat-form-field>
        }

        @if (shows('channel')) {
          <div class="radio-row">
            <span class="radio-label" id="qb-channel-label">Channel</span>
            <mat-radio-group formControlName="channel" aria-labelledby="qb-channel-label" data-testid="channel">
              <mat-radio-button value="EMAIL">Email</mat-radio-button>
              <mat-radio-button value="PUSH">Push</mat-radio-button>
            </mat-radio-group>
          </div>
          @if (errorText('channel')) { <div class="field-error" role="alert" data-testid="error-channel">{{ errorText('channel') }}</div> }
        }

        @if (shows('termCode')) {
          <mat-form-field>
            <mat-label>Term</mat-label>
            <mat-select formControlName="termCode" data-testid="termCode">
              @for (t of reference()?.terms ?? []; track t.code) {
                <mat-option [value]="t.code">{{ t.code }} · {{ t.name }}@if (t.status === 'CLOSED') {<span class="qb-muted-suffix">(closed)</span>}</mat-option>
              }
            </mat-select>
            @if (!reference()) { <mat-hint>Loading terms…</mat-hint> }
            <mat-error data-testid="error-termCode">{{ errorText('termCode') }}</mat-error>
          </mat-form-field>
        }

        @if (shows('applyLateFees')) {
          <mat-checkbox formControlName="applyLateFees" data-testid="applyLateFees">Apply late fees</mat-checkbox>
          @if (errorText('applyLateFees')) { <div class="field-error" role="alert">{{ errorText('applyLateFees') }}</div> }
        }
      </mat-dialog-content>

      <mat-dialog-actions align="end">
        <button mat-button type="button" (click)="cancel.emit()" [disabled]="sending()">Cancel</button>
        <button mat-flat-button type="submit" [disabled]="!canSubmit()" [attr.aria-busy]="sending()" data-testid="submit">
          @if (sending()) { <mat-progress-spinner diameter="16" mode="indeterminate" class="spin" /> Submitting… } @else { Submit }
        </button>
      </mat-dialog-actions>
    </form>
  `,
  styles: `
    .content { display: flex; flex-direction: column; gap: 12px; padding-top: 8px !important; }
    mat-form-field { width: 100%; }
    .divider { height: 1px; background: #e0e0e0; margin: 4px 0 8px; }
    .alert { display: flex; gap: 12px; align-items: flex-start; padding: 12px 16px; border-radius: 8px; background: #f9dedc;
      color: #410e0b; font: 400 14px/20px Roboto, sans-serif; margin-bottom: 8px; }
    .alert strong { font-weight: 500; }
    .alert ul { margin: 4px 0 0; padding-left: 18px; }
    .alert-icon { font-size: 20px; width: 20px; height: 20px; color: #b3261e; margin-top: 1px; flex: none; }
    .radio-row { display: flex; align-items: center; gap: 16px; padding: 0 4px; }
    .radio-label { font: 400 14px Roboto, sans-serif; color: #49454f; width: 64px; }
    .field-error { color: #b3261e; font: 400 12px/16px Roboto, sans-serif; padding: 0 16px; }
    .spin { --mat-progress-spinner-active-indicator-color: #fff; display: inline-block; vertical-align: middle; margin-right: 8px; }
  `,
})
export class SubmitModal {
  readonly reference = input<ReferenceData | null>(null);
  readonly prefill = input<SubmitPrefill | null>(null);
  readonly sending = input(false);
  readonly serverError = input<AppError | null>(null);
  readonly submitJob = output<SubmitJobRequest>();
  readonly cancel = output<void>();

  private readonly fb = inject(FormBuilder);
  protected readonly form = this.fb.group({
    name: this.fb.nonNullable.control('', [Validators.required, Validators.maxLength(100)]),
    type: this.fb.control<JobType | null>(null, Validators.required),
    courseCode: this.fb.nonNullable.control(''),
    templateCode: this.fb.nonNullable.control(''),
    channel: this.fb.nonNullable.control<'EMAIL' | 'PUSH'>('EMAIL'),
    termCode: this.fb.nonNullable.control(''),
    applyLateFees: this.fb.nonNullable.control(false),
  });

  private readonly formTick = toSignal(this.form.events, { initialValue: null });
  protected readonly type = toSignal(this.form.controls.type.valueChanges, { initialValue: null as JobType | null });
  protected readonly unmatched = signal<FieldError[]>([]);
  /** Payload keys without form fields (for example simulateFailure) are kept on duplicate. */
  private extraPayload: Record<string, unknown> = {};

  protected readonly typeOptions = computed(() =>
    JOB_TYPES.map((t) => ({ value: t, label: typeLabel(t, this.reference()?.jobTypes) })),
  );
  protected readonly typeDescription = computed(
    () => this.reference()?.jobTypes.find((t) => t.type === this.type())?.description ?? '',
  );

  /** Submit is disabled while sending or while required fields are empty (server errors don't block a resend). */
  protected readonly canSubmit = computed(() => {
    this.formTick();
    if (this.sending()) return false;
    const type = this.type();
    if (!type) return false;
    const c = this.form.controls;
    if (!c.name.value.trim() || c.name.value.length > 100) return false;
    return REQUIRED_BY_TYPE[type].every((f) => {
      const v = this.form.get(f)?.value;
      return v !== '' && v != null;
    });
  });

  constructor() {
    effect(() => {
      const p = this.prefill();
      if (!p) return;
      untracked(() => this.applyPrefill(p));
    });
    effect(() => {
      const sending = this.sending();
      untracked(() => (sending ? this.form.disable({ emitEvent: false }) : this.form.enable({ emitEvent: false })));
    });
    effect(() => {
      const err = this.serverError();
      untracked(() => this.applyServerErrors(err));
    });
  }

  protected shows(field: FieldName): boolean {
    const t = this.type();
    return !!t && FIELDS_BY_TYPE[t].includes(field);
  }

  protected errorText(field: FieldName): string {
    this.formTick();
    const c = this.form.get(field);
    if (!c?.errors) return '';
    if (c.errors['server']) return c.errors['server'] as string;
    if (c.errors['required']) return field === 'name' ? 'Enter a name.' : 'Choose an option.';
    if (c.errors['maxlength']) return 'Use 100 characters or fewer.';
    return '';
  }

  protected onSubmit(): void {
    if (!this.canSubmit()) return;
    const v = this.form.getRawValue();
    const type = v.type as JobType;
    const payload: Record<string, unknown> = { ...this.extraPayload };
    for (const f of FIELDS_BY_TYPE[type]) payload[f] = v[f];
    this.submitJob.emit({ name: v.name.trim(), type, payload });
  }

  private applyPrefill(p: SubmitPrefill): void {
    const type = (JOB_TYPES as readonly string[]).includes(p.type) ? (p.type as JobType) : null;
    const pl = p.payload ?? {};
    this.extraPayload = Object.fromEntries(
      Object.entries(pl).filter(([k]) => !(PAYLOAD_KEYS as readonly string[]).includes(k)),
    );
    this.form.patchValue({
      name: p.name ?? '',
      type,
      courseCode: typeof pl['courseCode'] === 'string' ? pl['courseCode'] : '',
      templateCode: typeof pl['templateCode'] === 'string' ? pl['templateCode'] : '',
      channel: pl['channel'] === 'PUSH' ? 'PUSH' : 'EMAIL',
      termCode: typeof pl['termCode'] === 'string' ? pl['termCode'] : '',
      applyLateFees: pl['applyLateFees'] === true,
    });
  }

  private applyServerErrors(err: AppError | null): void {
    const leftover: FieldError[] = [];
    for (const e of err?.errors ?? []) {
      const key = (e.field ?? '').replace(/^payload\./, '') as FieldName;
      const control = key ? this.form.get(key) : null;
      if (control && (key === 'name' || key === 'type' || this.shows(key))) {
        control.setErrors({ ...(control.errors ?? {}), server: e.message });
        control.markAsTouched();
      } else {
        leftover.push(e);
      }
    }
    this.unmatched.set(leftover);
  }
}
