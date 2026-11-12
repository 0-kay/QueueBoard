import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { AppHeader } from './components/app-header/app-header';
import { ClockService } from './services/clock.service';
import { DemoService } from './services/demo.service';
import { JobPollingService } from './services/job-polling.service';
import { formatAgo } from './shared/format';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, AppHeader],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <app-header [updatedLabel]="updatedLabel()" [stale]="polling.stale()" [staleAge]="age()"
      (refresh)="polling.refreshNow()" (reconnect)="polling.refreshNow()" (demo)="demo.run($event)" />
    <div class="outlet"><router-outlet /></div>
  `,
  styles: `
    :host { display: flex; flex-direction: column; height: 100vh; }
    app-header { flex: none; }
    .outlet { flex: 1; min-height: 0; overflow: auto; }
  `,
})
export class App {
  protected readonly polling = inject(JobPollingService);
  protected readonly demo = inject(DemoService);
  private readonly clock = inject(ClockService);

  protected readonly age = computed(() => formatAgo(this.polling.lastUpdated(), this.clock.now()));
  protected readonly updatedLabel = computed(() =>
    this.polling.lastUpdated() === null ? 'Connecting…' : `Last updated ${this.age()}`,
  );
}
