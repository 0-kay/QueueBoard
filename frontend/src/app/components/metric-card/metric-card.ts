import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatTooltipModule } from '@angular/material/tooltip';

@Component({
  selector: 'app-metric-card',
  imports: [MatCardModule, MatIconModule, MatTooltipModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <mat-card appearance="outlined" class="card">
      <div class="top">
        <span class="label">{{ label() }}</span>
        <mat-icon class="info" [matTooltip]="tooltip()" [attr.aria-label]="'About ' + label()" tabindex="0">info</mat-icon>
      </div>
      @if (loading()) {
        <span class="qb-skel" style="height:28px;width:72px;margin:4px 0"></span>
      } @else {
        <div class="value" data-testid="value">{{ value() }}</div>
        @if (sub()) { <div class="sub">{{ sub() }}</div> }
      }
    </mat-card>
  `,
  styles: `
    :host { display: block; }
    .card { padding: 14px 12px 16px 20px; display: flex; flex-direction: column; gap: 4px; }
    .top { display: flex; align-items: center; justify-content: space-between; }
    .label { font: 500 14px Roboto, sans-serif; color: #49454f; }
    .info { font-size: 20px; width: 20px; height: 20px; padding: 4px; color: #79747e; cursor: help; }
    .value { font: 400 28px/36px Roboto, sans-serif; color: #1d1b20; }
    .sub { font: 400 13px/18px Roboto, sans-serif; color: #49454f; }
  `,
})
export class MetricCard {
  readonly label = input.required<string>();
  readonly value = input<string>('—');
  readonly sub = input<string | null>(null);
  readonly tooltip = input<string>('');
  readonly loading = input(false);
}
