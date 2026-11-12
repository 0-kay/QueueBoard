import { DOCUMENT } from '@angular/common';
import { inject, Injectable, signal } from '@angular/core';
import {
  catchError, defer, filter, fromEvent, map, merge, Observable, of, repeat, share, Subject, switchMap, tap, timer,
} from 'rxjs';
import { AppError, toAppError } from '../api/app-error';

export interface PollEvent<T> {
  data?: T;
  error?: AppError;
}

export const ACTIVE_POLL_MS = 2_000;
export const IDLE_POLL_MS = 10_000;

/**
 * Polls the current view's data: every 2 s while anything visible is QUEUED or PROCESSING,
 * every 10 s otherwise. Pauses while the tab is hidden, refetches as soon as it is visible
 * again, and `refreshNow()` forces an immediate fetch. Owns the connection signals.
 */
@Injectable({ providedIn: 'root' })
export class JobPollingService {
  private readonly document = inject(DOCUMENT);
  private readonly refresh$ = new Subject<void>();
  private readonly visible$: Observable<void> = fromEvent(this.document, 'visibilitychange').pipe(
    filter(() => !this.document.hidden),
    map(() => undefined),
    share(),
  );

  /** Epoch ms of the last successful poll. */
  readonly lastUpdated = signal<number | null>(null);
  /** True when a poll failed after data had loaded; cleared by the next good poll. */
  readonly stale = signal(false);

  refreshNow(): void {
    this.refresh$.next();
  }

  poll<T>(fetch: () => Observable<T>, isActive: (data: T) => boolean): Observable<PollEvent<T>> {
    return defer(() => {
      let hasData = false;
      let nextDelay = ACTIVE_POLL_MS;

      const cycle$ = defer(fetch).pipe(
        map((data): PollEvent<T> => ({ data })),
        catchError((err: unknown) => of<PollEvent<T>>({ error: toAppError(err) })),
        tap((event) => {
          if (event.error === undefined) {
            hasData = true;
            nextDelay = isActive(event.data as T) ? ACTIVE_POLL_MS : IDLE_POLL_MS;
            this.lastUpdated.set(Date.now());
            this.stale.set(false);
          } else {
            if (hasData) this.stale.set(true);
            else nextDelay = IDLE_POLL_MS;
          }
        }),
        // Wait, then poll again; while the tab is hidden the loop ends and a
        // visibilitychange restarts it.
        repeat({ delay: () => timer(nextDelay).pipe(filter(() => !this.document.hidden)) }),
      );

      return merge(of(undefined), this.refresh$, this.visible$).pipe(switchMap(() => cycle$));
    });
  }
}
