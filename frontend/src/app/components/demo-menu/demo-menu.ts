import { ChangeDetectionStrategy, Component, output } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatDividerModule } from '@angular/material/divider';
import { MatIconModule } from '@angular/material/icon';
import { MatMenuModule } from '@angular/material/menu';

export type DemoAction = 'one-of-each' | 'temporary-failure' | 'permanent-failure' | 'burst';

@Component({
  selector: 'app-demo-menu',
  imports: [MatButtonModule, MatIconModule, MatMenuModule, MatDividerModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <button mat-button class="demo" [matMenuTriggerFor]="menu" #trigger="matMenuTrigger" [class.open]="trigger.menuOpen">
      <mat-icon>play_arrow</mat-icon>Demo
    </button>
    <mat-menu #menu="matMenu" xPosition="before" class="qb-demo-menu">
      <button mat-menu-item (click)="run.emit('one-of-each')"><mat-icon>category</mat-icon>Run one of each type</button>
      <button mat-menu-item (click)="run.emit('temporary-failure')"><mat-icon>replay</mat-icon>Simulate temporary failure</button>
      <button mat-menu-item (click)="run.emit('permanent-failure')"><mat-icon>block</mat-icon>Simulate permanent failure</button>
      <mat-divider></mat-divider>
      <button mat-menu-item (click)="run.emit('burst')"><mat-icon>bolt</mat-icon>Run a burst (15 jobs)</button>
    </mat-menu>
  `,
  styles: `
    .demo.mat-mdc-button { height: 36px; padding: 0 12px 0 8px; border-radius: 18px; color: #fff; --mat-button-text-label-text-color: #fff;
      --mat-button-text-state-layer-color: #fff; }
    .demo.mat-mdc-button.open { background: rgba(155, 199, 238, .24); }
    .demo mat-icon { font-size: 20px; width: 20px; height: 20px; margin-right: 4px; }
  `,
})
export class DemoMenu {
  readonly run = output<DemoAction>();
}
