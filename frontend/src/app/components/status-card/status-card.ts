import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatTooltipModule } from '@angular/material/tooltip';
import { JobStatus } from '../../api/models';
import { STATUS_LABELS } from '../../shared/format';

@Component({
  selector: 'app-status-card',
  imports: [MatCardModule, MatIconModule, MatTooltipModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (loading()) {
      <mat-card appearance="outlined" class="card skeleton" aria-busy="true">
        <span class="qb-skel" style="width:40%;height:14px"></span>
        <span class="qb-skel" style="width:30%;height:36px"></span>
      </mat-card>
    } @else {
      <mat-card appearance="outlined" class="card" role="button" tabindex="0"
        [attr.aria-label]="label() + ': ' + count() + '. Show ' + label().toLowerCase() + ' jobs'"
        (click)="open.emit(status())" (keydown.enter)="open.emit(status())" (keydown.space)="$event.preventDefault(); open.emit(status())">
        <div class="top">
          <div class="title">
            @switch (status()) {
              @case ('FAILED') { <span class="material-symbols-outlined filled err" aria-hidden="true">error</span> }
              @case ('PROCESSING') { <span class="ring" aria-hidden="true"></span> }
              @default { <span class="dot" [class.green]="status() === 'COMPLETED'" aria-hidden="true"></span> }
            }
            <span class="label">{{ label() }}</span>
          </div>
          <mat-icon class="info" [matTooltip]="tooltip()" [attr.aria-label]="'About ' + label()" tabindex="0"
            (click)="$event.stopPropagation()">info</mat-icon>
        </div>
        <div class="count" data-testid="count">{{ count() }}</div>
      </mat-card>
    }
  `,
  styles: `
    :host { display: block; }
    .card { padding: 16px 12px 20px 20px; display: flex; flex-direction: column; gap: 8px; cursor: pointer; height: 100%; box-sizing: border-box; transition: background .15s; }
    .card:hover, .card:focus-visible { background: #f2f7fc; }
    .card:focus-visible { outline: 2px solid #0a254e; outline-offset: 2px; }
    .skeleton { cursor: default; height: 112px; padding: 20px; gap: 14px; }
    .skeleton:hover { background: #fff; }
    .top { display: flex; align-items: center; justify-content: space-between; }
    .title { display: flex; align-items: center; gap: 8px; }
    .label { font: 500 14px Roboto, sans-serif; color: #49454f; }
    .dot { width: 8px; height: 8px; border-radius: 50%; background: #78909c; }
    .dot.green { background: #2e7d32; }
    .ring { width: 6px; height: 6px; border-radius: 50%; border: 2px solid #2f6db5; border-top-color: transparent; }
    .err { font-size: 18px; width: 18px; height: 18px; color: #b3261e; }
    .info { font-size: 20px; width: 20px; height: 20px; padding: 4px; color: #79747e; cursor: help; }
    .count { font: 400 40px/48px Roboto, sans-serif; color: #1d1b20; }
  `,
})
export class StatusCard {
  readonly status = input.required<JobStatus>();
  readonly count = input<number>(0);
  readonly tooltip = input<string>('');
  readonly loading = input(false);
  readonly open = output<JobStatus>();
  protected readonly label = computed(() => STATUS_LABELS[this.status()]);
}
