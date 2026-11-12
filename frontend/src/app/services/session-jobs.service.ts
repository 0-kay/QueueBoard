import { inject, Injectable } from '@angular/core';
import { Router } from '@angular/router';
import { NotificationService } from './notification.service';

interface Watched { id: string; name: string; status: string }

/**
 * Remembers jobs submitted (or retried) in this browser session and toasts when a
 * poll shows one reached COMPLETED or FAILED. Only backend-reported status is used.
 */
@Injectable({ providedIn: 'root' })
export class SessionJobsService {
  private readonly notify = inject(NotificationService);
  private readonly router = inject(Router);
  private readonly tracked = new Map<string, string>();

  track(id: string, name: string): void {
    this.tracked.set(id, name);
  }

  isTracked(id: string): boolean {
    return this.tracked.has(id);
  }

  check(jobs: readonly Watched[] | null | undefined): void {
    if (!jobs || this.tracked.size === 0) return;
    for (const job of jobs) {
      if (!this.tracked.has(job.id)) continue;
      if (job.status !== 'COMPLETED' && job.status !== 'FAILED') continue;
      this.tracked.delete(job.id);
      const word = job.status === 'COMPLETED' ? 'completed' : 'failed';
      this.notify.show(`${job.name} ${word}`, {
        label: 'View',
        run: () => this.router.navigate(['/jobs', job.id]),
      });
    }
  }
}
