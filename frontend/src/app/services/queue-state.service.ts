import { computed, Injectable, signal, Signal, WritableSignal } from '@angular/core';
import { AppError } from '../api/app-error';
import { JobDetail, JobSummary, Page, Stats } from '../api/models';

/** A piece of server data plus its first-load error. */
export class Loadable<T> {
  readonly value: WritableSignal<T | null> = signal<T | null>(null);
  readonly error: WritableSignal<AppError | null> = signal<AppError | null>(null);
  /** First load in flight: no data yet and no error. */
  readonly loading: Signal<boolean> = computed(() => this.value() === null && this.error() === null);

  /** Applies a poll result: errors only replace the view when there is no data to keep. */
  apply(data: T | undefined, error: AppError | undefined): void {
    if (error === undefined) {
      this.value.set(data as T);
      this.error.set(null);
    } else if (this.value() === null) {
      this.error.set(error);
    }
  }

  reset(): void {
    this.value.set(null);
    this.error.set(null);
  }
}

/** Shared view state, always straight from the latest backend response. */
@Injectable({ providedIn: 'root' })
export class QueueStateService {
  readonly stats = new Loadable<Stats>();
  readonly recent = new Loadable<JobSummary[]>();
  readonly jobs = new Loadable<Page<JobSummary>>();
  readonly openJob = new Loadable<JobDetail>();
}
