import { DestroyRef, inject, Injectable, signal } from '@angular/core';

/** A 1-second "now" signal for relative times ("3s ago"). */
@Injectable({ providedIn: 'root' })
export class ClockService {
  private readonly _now = signal(Date.now());
  readonly now = this._now.asReadonly();

  constructor() {
    const id = setInterval(() => this._now.set(Date.now()), 1000);
    inject(DestroyRef).onDestroy(() => clearInterval(id));
  }
}
