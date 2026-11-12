import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';
import { ClipboardModule } from '@angular/cdk/clipboard';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTooltipModule } from '@angular/material/tooltip';
import { AppError } from '../../api/app-error';
import { JobAttempt, JobDetail, JobTypeInfo } from '../../api/models';
import { formatLong, formatTime, typeLabel } from '../../shared/format';
import { StatusBadge } from '../status-badge/status-badge';

@Component({
  selector: 'app-detail-panel',
  imports: [MatButtonModule, MatIconModule, MatTooltipModule, MatProgressSpinnerModule, ClipboardModule, StatusBadge],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <aside class="panel" aria-labelledby="qb-detail-title">
      <header class="head">
        <div class="head-main">
          @if (job(); as j) {
            <h2 id="qb-detail-title">{{ j.name }}</h2>
            <div><app-status-badge [status]="j.status" /></div>
          } @else if (loading()) {
            <h2 id="qb-detail-title" class="sr-only">Loading job</h2>
            <span class="qb-skel" style="height:24px;width:70%"></span>
            <span class="qb-skel" style="height:24px;width:96px;border-radius:12px"></span>
          } @else {
            <h2 id="qb-detail-title">Job details</h2>
          }
        </div>
        <button mat-icon-button aria-label="Close details" matTooltip="Close" (click)="close.emit()"><mat-icon>close</mat-icon></button>
      </header>

      <div class="body">
        @if (job(); as j) {
          <dl class="facts">
            <dt>Type</dt><dd>{{ label(j.type) }}</dd>
            <dt>Attempts</dt><dd data-testid="attempts">{{ j.attempts }} of {{ j.maxAttempts }}</dd>
            <dt>Created</dt><dd>{{ long(j.createdAt) }}</dd>
            <dt>Updated</dt><dd>{{ long(j.updatedAt) }}</dd>
            <dt class="center">Job ID</dt>
            <dd class="id-row">
              <span class="mono id">{{ j.id }}</span>
              <button mat-icon-button class="small" aria-label="Copy job ID" matTooltip="Copy job ID"
                [cdkCopyToClipboard]="j.id" (cdkCopyToClipboardCopied)="copied.emit('Job ID')"><mat-icon>content_copy</mat-icon></button>
            </dd>
          </dl>

          <section class="section">
            <h3>Payload</h3>
            <div class="payload">
              <pre data-testid="payload">{{ payloadJson() }}</pre>
              <button mat-icon-button class="copy-payload" aria-label="Copy payload" matTooltip="Copy payload"
                [cdkCopyToClipboard]="payloadJson()" (cdkCopyToClipboardCopied)="copied.emit('Payload')"><mat-icon>content_copy</mat-icon></button>
            </div>
          </section>

          @if (j.status === 'COMPLETED' && j.resultSummary) {
            <section class="section" data-testid="result">
              <h3>Result</h3>
              <div class="result"><mat-icon class="ok">check_circle</mat-icon>{{ j.resultSummary }}</div>
            </section>
          }

          <section class="section">
            <div class="history-head"><h3>Attempt history</h3><span class="remaining">{{ remaining() }}</span></div>
            <div class="history">
              @for (a of j.attemptHistory; track a.attemptNumber) {
                <div class="attempt" data-testid="attempt">
                  <div class="attempt-row">
                    <span class="num">#{{ a.attemptNumber }}</span>
                    <span class="times">{{ times(a) }}</span>
                    @switch (a.outcome) {
                      @case ('COMPLETED') { <span class="outcome ok-text"><mat-icon>check</mat-icon>Succeeded</span> }
                      @case ('FAILED') { <span class="outcome fail-text"><mat-icon>close</mat-icon>Failed</span> }
                      @default { <span class="outcome running">Running</span> }
                    }
                  </div>
                  @if (a.outcome === 'FAILED' || a.errorCode || a.errorMessage) {
                    <div class="attempt-error">
                      <div class="code-row">
                        @if (a.errorCode) { <span class="mono code">{{ a.errorCode }}</span> }
                        @if (a.errorKind === 'TEMPORARY') { <span class="kind temporary">TEMPORARY</span> }
                        @if (a.errorKind === 'PERMANENT') { <span class="kind permanent">PERMANENT</span> }
                      </div>
                      @if (a.errorMessage) { <div class="msg">{{ a.errorMessage }}</div> }
                    </div>
                  }
                </div>
              } @empty {
                <div class="attempt empty">No attempts yet. The job is waiting for a worker.</div>
              }
            </div>
          </section>
        } @else if (error()) {
          <div class="state" role="alert">
            <mat-icon class="state-icon">error</mat-icon>
            <div class="state-title">Couldn’t load this job</div>
            <div class="state-sub">{{ error()!.description }}</div>
            <button mat-stroked-button (click)="reload.emit()"><mat-icon>refresh</mat-icon>Try again</button>
          </div>
        } @else {
          <div class="skel-body" aria-busy="true">
            @for (i of [1,2,3,4,5]; track i) { <span class="qb-skel" style="height:14px;width:80%"></span> }
            <span class="qb-skel" style="height:96px;width:100%;border-radius:8px"></span>
          </div>
        }
      </div>

      @if (job(); as j) {
        <footer class="foot">
          @if (j.retryable) {
            <button mat-flat-button (click)="retry.emit(j)" [disabled]="!!busy()" data-testid="retry">
              @if (busy() === 'retry') { <mat-progress-spinner diameter="16" mode="indeterminate" /> } @else { <mat-icon>replay</mat-icon> }
              Retry
            </button>
          }
          <button mat-stroked-button (click)="duplicate.emit(j)" [disabled]="!!busy()" data-testid="duplicate"><mat-icon>content_copy</mat-icon>Duplicate</button>
          <span class="spacer"></span>
          @if (canDelete()) {
            <button mat-button class="delete" (click)="delete.emit(j)" [disabled]="!!busy()" data-testid="delete"><mat-icon>delete</mat-icon>Delete</button>
          }
        </footer>
      }
    </aside>
  `,
  styles: `
    :host { display: block; height: 100%; }
    .panel { height: 100%; background: #fff; display: flex; flex-direction: column; color: #1d1b20; }
    .head { display: flex; align-items: flex-start; gap: 12px; padding: 20px 12px 16px 24px; border-bottom: 1px solid #e0e0e0; }
    .head-main { flex: 1; display: flex; flex-direction: column; gap: 8px; min-width: 0; }
    h2 { margin: 0; font: 400 22px/28px Roboto, sans-serif; overflow-wrap: anywhere; }
    .sr-only { position: absolute; width: 1px; height: 1px; overflow: hidden; clip: rect(0 0 0 0); }
    .body { flex: 1; overflow: auto; padding: 20px 24px 24px; display: flex; flex-direction: column; gap: 24px; }
    .facts { margin: 0; display: grid; grid-template-columns: 96px minmax(0,1fr); row-gap: 10px; column-gap: 16px; font: 400 14px/20px Roboto, sans-serif; }
    dt { color: #49454f; } dd { margin: 0; }
    dt.center { align-self: center; }
    .id-row { display: flex; align-items: center; gap: 4px; min-width: 0; }
    .id { font-size: 13px; overflow-wrap: anywhere; }
    .small { --mat-icon-button-state-layer-size: 32px; width: 32px; height: 32px; padding: 4px; }
    .small mat-icon, .copy-payload mat-icon { font-size: 18px; width: 18px; height: 18px; }
    .section { display: flex; flex-direction: column; gap: 8px; }
    h3 { margin: 0; font: 500 14px/20px Roboto, sans-serif; }
    .payload { position: relative; background: #f5f5f5; border: 1px solid #e0e0e0; border-radius: 8px; }
    pre { margin: 0; padding: 12px 48px 12px 16px; font: 400 13px/20px 'Roboto Mono', monospace; white-space: pre-wrap; overflow-wrap: anywhere; }
    .copy-payload { position: absolute; top: 4px; right: 4px; --mat-icon-button-state-layer-size: 36px; width: 36px; height: 36px; padding: 6px; }
    .result { display: flex; align-items: center; gap: 10px; font: 400 14px/20px Roboto, sans-serif; }
    .ok { color: #2e7d32; font-size: 20px; width: 20px; height: 20px; flex: none; }
    .history-head { display: flex; align-items: baseline; justify-content: space-between; }
    .remaining { font: 400 12px Roboto, sans-serif; color: #49454f; }
    .history { border: 1px solid #e0e0e0; border-radius: 8px; overflow: hidden; }
    .attempt { display: flex; flex-direction: column; gap: 6px; padding: 12px 16px; border-bottom: 1px solid #eeeeee; }
    .attempt:last-child { border-bottom: 0; }
    .attempt.empty { color: #49454f; font-size: 14px; }
    .attempt-row { display: flex; align-items: center; gap: 12px; font: 400 14px/20px Roboto, sans-serif; }
    .num { font-weight: 500; width: 24px; }
    .times { flex: 1; color: #49454f; }
    .outcome { display: flex; align-items: center; gap: 4px; font-weight: 500; }
    .outcome mat-icon { font-size: 18px; width: 18px; height: 18px; }
    .ok-text { color: #1b5e20; } .fail-text { color: #b3261e; } .running { color: #0d3a74; }
    .attempt-error { display: flex; flex-direction: column; gap: 6px; padding-left: 36px; }
    .code-row { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
    .code { font-size: 12px; font-weight: 500; }
    .kind { display: inline-flex; align-items: center; height: 22px; padding: 0 8px; border-radius: 6px; font: 500 11px/1 Roboto, sans-serif; letter-spacing: .6px; box-sizing: border-box; }
    .kind.temporary { background: #fff8e1; color: #6d4c00; border: 1px solid #ffca28; }
    .kind.permanent { background: #fff; color: #b3261e; border: 1px solid #b3261e; }
    .msg { font: 400 13px/18px Roboto, sans-serif; color: #49454f; }
    .foot { display: flex; align-items: center; gap: 8px; padding: 12px 24px; border-top: 1px solid #e0e0e0; }
    .spacer { flex: 1; }
    .delete.mat-mdc-button { --mat-button-text-label-text-color: #b3261e; --mat-button-text-state-layer-color: #b3261e; }
    .state { display: flex; flex-direction: column; align-items: center; gap: 8px; padding: 48px 8px; text-align: center; }
    .state-icon { font-size: 40px; width: 40px; height: 40px; color: #b3261e; }
    .state-title { font: 500 16px/24px Roboto, sans-serif; }
    .state-sub { font: 400 14px/20px Roboto, sans-serif; color: #49454f; margin-bottom: 8px; }
    .skel-body { display: flex; flex-direction: column; gap: 14px; }
    mat-progress-spinner { --mat-progress-spinner-active-indicator-color: #fff; margin-right: 8px; display: inline-block; vertical-align: middle; }
  `,
})
export class DetailPanel {
  readonly job = input<JobDetail | null>(null);
  readonly loading = input(false);
  readonly error = input<AppError | null>(null);
  readonly types = input<JobTypeInfo[] | null>(null);
  /** Which action is in flight, if any. */
  readonly busy = input<'retry' | 'delete' | null>(null);

  readonly close = output<void>();
  readonly reload = output<void>();
  readonly retry = output<JobDetail>();
  readonly duplicate = output<JobDetail>();
  readonly delete = output<JobDetail>();
  readonly copied = output<string>();

  protected readonly payloadJson = computed(() => JSON.stringify(this.job()?.payload ?? {}, null, 2));
  /** Delete is offered only for finished jobs. */
  protected readonly canDelete = computed(() => {
    const s = this.job()?.status;
    return s === 'COMPLETED' || s === 'FAILED';
  });
  protected readonly remaining = computed(() => {
    const j = this.job();
    if (!j || !j.retryable) return '';
    const left = j.maxAttempts - j.attempts;
    return left > 0 ? `${left} attempt${left === 1 ? '' : 's'} left` : '';
  });

  protected label(type: string) { return typeLabel(type, this.types()); }
  protected long(iso: string) { return formatLong(iso); }
  protected times(a: JobAttempt): string {
    const start = formatTime(a.startedAt);
    if (!a.finishedAt) return `${start} → …`;
    const secs = (Date.parse(a.finishedAt) - Date.parse(a.startedAt)) / 1000;
    const dur = Number.isFinite(secs) ? ` · ${secs.toFixed(1)} s` : '';
    return `${start} → ${formatTime(a.finishedAt)}${dur}`;
  }
}
