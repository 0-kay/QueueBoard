import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { badgeKind } from '../../shared/format';

const LABELS = { queued: 'Queued', processing: 'Processing', completed: 'Completed', failed: 'Failed', unknown: 'Unknown' } as const;

/** Status badge: always text plus color; unknown values render a grey "Unknown". */
@Component({
  selector: 'app-status-badge',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <span class="badge" [class]="'badge ' + kind()" [attr.data-status]="kind()" [attr.title]="title()">
      @switch (kind()) {
        @case ('failed') { <span class="material-symbols-outlined filled icon" aria-hidden="true">error</span> }
        @case ('processing') { <span class="ring" aria-hidden="true"></span> }
        @case ('unknown') { <span class="hollow" aria-hidden="true"></span> }
        @default { <span class="dot" aria-hidden="true"></span> }
      }
      <span class="label">{{ label() }}</span>
    </span>
  `,
  styles: `
    :host { display: inline-flex; }
    .badge { display: inline-flex; align-items: center; gap: 6px; height: 24px; padding: 0 10px; border-radius: 12px;
      font: 500 12px/1 Roboto, sans-serif; letter-spacing: .2px; white-space: nowrap; box-sizing: border-box; }
    .dot { width: 8px; height: 8px; border-radius: 50%; }
    .queued { background: #eceff1; color: #37474f; }
    .queued .dot { background: #78909c; }
    .processing { background: #e3eef9; color: #0d3a74; }
    .ring { width: 6px; height: 6px; border-radius: 50%; border: 2px solid #2f6db5; border-top-color: transparent;
      animation: spin 1s linear infinite; }
    .completed { background: #e8f5e9; color: #1b5e20; }
    .completed .dot { background: #2e7d32; }
    .failed { background: #fce8e6; color: #b3261e; gap: 4px; padding-left: 7px; }
    .icon { font-size: 15px; width: 15px; height: 15px; line-height: 1; color: #b3261e; overflow: hidden; }
    .unknown { background: #f5f5f5; color: #424242; border: 1px dashed #9e9e9e; }
    .hollow { width: 6px; height: 6px; border-radius: 50%; border: 1px solid #757575; }
    @keyframes spin { to { transform: rotate(360deg); } }
    @media (prefers-reduced-motion: reduce) { .ring { animation: none; } }
  `,
})
export class StatusBadge {
  readonly status = input<string | null | undefined>(null);
  protected readonly kind = computed(() => badgeKind(this.status()));
  protected readonly label = computed(() => LABELS[this.kind()]);
  protected readonly title = computed(() =>
    this.kind() === 'unknown' && this.status() ? `Status reported as “${this.status()}”` : null,
  );
}
