import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatTabsModule } from '@angular/material/tabs';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatTooltipModule } from '@angular/material/tooltip';
import { DemoAction, DemoMenu } from '../demo-menu/demo-menu';

@Component({
  selector: 'app-header',
  imports: [MatToolbarModule, MatTabsModule, MatButtonModule, MatIconModule, MatTooltipModule, RouterLink, RouterLinkActive, DemoMenu],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <mat-toolbar class="bar">
      <a class="brand" routerLink="/dashboard">QueueBoard</a>
      <nav mat-tab-nav-bar class="tabs" [tabPanel]="tabPanel" mat-stretch-tabs="false" disableRipple>
        <a mat-tab-link routerLink="/dashboard" routerLinkActive #dash="routerLinkActive" [active]="dash.isActive">Dashboard</a>
        <a mat-tab-link routerLink="/jobs" routerLinkActive #jobs="routerLinkActive" [active]="jobs.isActive">Jobs</a>
      </nav>
      <mat-tab-nav-panel #tabPanel></mat-tab-nav-panel>
      <span class="spacer"></span>
      <div class="right">
        <span class="updated" data-testid="last-updated" aria-live="off">{{ updatedLabel() }}</span>
        <button mat-icon-button class="refresh" aria-label="Refresh" matTooltip="Refresh" (click)="refresh.emit()">
          <mat-icon>refresh</mat-icon>
        </button>
        <app-demo-menu (run)="demo.emit($event)" />
      </div>
    </mat-toolbar>
    @if (stale()) {
      <div class="stale" role="status" data-testid="stale-banner">
        <mat-icon class="stale-icon">cloud_off</mat-icon>
        <span class="stale-text"><strong>Connection lost.</strong> Showing data from {{ staleAge() }}.</span>
        <button mat-button class="reconnect" (click)="reconnect.emit()">Reconnect</button>
      </div>
    }
  `,
  styles: `
    :host { display: block; position: relative; z-index: 5; }
    .bar { height: 64px; background: #0a254e; color: #fff; padding: 0 16px 0 24px; gap: 32px; }
    .brand { font: 500 20px/1 Roboto, sans-serif; color: #fff; letter-spacing: .1px; text-decoration: none; }
    .brand:hover { color: #fff; }
    .tabs { align-self: stretch; height: 64px; border: 0;
      --mat-tab-container-height: 64px;
      --mat-tab-divider-height: 0;
      --mat-tab-active-indicator-color: #b72025;
      --mat-tab-active-focus-indicator-color: #b72025;
      --mat-tab-active-hover-indicator-color: #b72025;
      --mat-tab-active-indicator-height: 3px;
      --mat-tab-active-indicator-shape: 0;
      --mat-tab-active-label-text-color: #fff;
      --mat-tab-active-focus-label-text-color: #fff;
      --mat-tab-active-hover-label-text-color: #fff;
      --mat-tab-inactive-label-text-color: #c3d4e8;
      --mat-tab-inactive-hover-label-text-color: #fff;
      --mat-tab-inactive-focus-label-text-color: #fff;
      --mat-tab-label-text-weight: 500;
      --mat-tab-label-text-size: 14px;
      --mat-tab-background-color: transparent;
    }
    .tabs ::ng-deep .mat-mdc-tab-link-container { border-bottom: 0; }
    .tabs ::ng-deep .mdc-tab { padding: 0 20px; min-width: 0; }
    .spacer { flex: 1; }
    .updated { font: 400 13px Roboto, sans-serif; color: #c3d4e8; margin-right: 4px; white-space: nowrap; }
    .refresh.mat-mdc-icon-button { color: #fff; --mat-icon-button-state-layer-color: #fff; }
    .right { display: flex; align-items: center; gap: 4px; }
    .stale { display: flex; align-items: center; gap: 10px; height: 40px; padding: 0 24px; background: #fff8e1;
      border-bottom: 1px solid #ffe082; color: #5d4000; font: 400 14px Roboto, sans-serif; box-sizing: border-box; }
    .stale strong { font-weight: 500; }
    .stale-icon { font-size: 20px; width: 20px; height: 20px; color: #8a6100; }
    .stale-text { flex: 1; }
    .reconnect.mat-mdc-button { height: 32px; --mat-button-text-label-text-color: #5d4000; }
    @media (max-width: 899px) {
      .bar { padding: 0 8px 0 16px; gap: 16px; }
      .updated { display: none; }
    }
  `,
})
export class AppHeader {
  readonly updatedLabel = input<string>('');
  readonly stale = input(false);
  readonly staleAge = input<string>('');
  readonly refresh = output<void>();
  readonly reconnect = output<void>();
  readonly demo = output<DemoAction>();
}
