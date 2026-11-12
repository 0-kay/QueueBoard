import { TestBed } from '@angular/core/testing';
import { Observable, of, Subscription, throwError } from 'rxjs';
import { appError } from '../api/app-error';
import { ACTIVE_POLL_MS, IDLE_POLL_MS, JobPollingService, PollEvent } from './job-polling.service';
import { Loadable } from './queue-state.service';

describe('JobPollingService', () => {
  let service: JobPollingService;
  let sub: Subscription | undefined;

  beforeEach(() => {
    vi.useFakeTimers();
    service = TestBed.inject(JobPollingService);
  });

  afterEach(() => {
    sub?.unsubscribe();
    vi.useRealTimers();
  });

  function start<T>(fetch: () => Observable<T>, isActive: (d: T) => boolean) {
    const events: PollEvent<T>[] = [];
    sub = service.poll(fetch, isActive).subscribe((e) => events.push(e));
    return events;
  }

  it('polls every 2 s while jobs are active', () => {
    const fetch = vi.fn(() => of({ active: true }));
    start(fetch, (d) => d.active);
    expect(fetch).toHaveBeenCalledTimes(1);
    vi.advanceTimersByTime(ACTIVE_POLL_MS - 1);
    expect(fetch).toHaveBeenCalledTimes(1);
    vi.advanceTimersByTime(1);
    expect(fetch).toHaveBeenCalledTimes(2);
    vi.advanceTimersByTime(ACTIVE_POLL_MS);
    expect(fetch).toHaveBeenCalledTimes(3);
  });

  it('polls every 10 s when everything is finished', () => {
    const fetch = vi.fn(() => of({ active: false }));
    start(fetch, (d) => d.active);
    vi.advanceTimersByTime(ACTIVE_POLL_MS);
    expect(fetch).toHaveBeenCalledTimes(1);
    vi.advanceTimersByTime(IDLE_POLL_MS - ACTIVE_POLL_MS);
    expect(fetch).toHaveBeenCalledTimes(2);
  });

  it('switches from 2 s to 10 s when the last active job finishes', () => {
    let active = true;
    const fetch = vi.fn(() => of({ active }));
    start(fetch, (d) => d.active);
    active = false;
    vi.advanceTimersByTime(ACTIVE_POLL_MS); // 2nd fetch sees idle
    expect(fetch).toHaveBeenCalledTimes(2);
    vi.advanceTimersByTime(ACTIVE_POLL_MS);
    expect(fetch).toHaveBeenCalledTimes(2);
    vi.advanceTimersByTime(IDLE_POLL_MS - ACTIVE_POLL_MS);
    expect(fetch).toHaveBeenCalledTimes(3);
  });

  it('keeps old data and flags stale when a poll fails, then clears on the next good poll', () => {
    const results: Observable<{ n: number }>[] = [
      of({ n: 1 }),
      throwError(() => appError(0, 'offline')),
      of({ n: 2 }),
    ];
    let i = 0;
    const events = start(() => results[i++], () => true);
    const loadable = new Loadable<{ n: number }>();
    const apply = () => { const e = events.at(-1)!; loadable.apply(e.data, e.error); };

    apply();
    expect(loadable.value()).toEqual({ n: 1 });
    expect(service.stale()).toBe(false);
    const updatedAt = service.lastUpdated();

    vi.advanceTimersByTime(ACTIVE_POLL_MS);
    apply();
    expect(events.at(-1)!.error?.description).toBe('offline');
    expect(service.stale()).toBe(true);
    expect(loadable.value()).toEqual({ n: 1 });
    expect(loadable.error()).toBeNull();
    expect(service.lastUpdated()).toBe(updatedAt);

    vi.advanceTimersByTime(ACTIVE_POLL_MS);
    apply();
    expect(service.stale()).toBe(false);
    expect(loadable.value()).toEqual({ n: 2 });
  });

  it('shows a first-load error (not stale) when there is no data yet', () => {
    const events = start(() => throwError(() => appError(503, 'Service unavailable')), () => true);
    const loadable = new Loadable<unknown>();
    loadable.apply(events[0].data, events[0].error);
    expect(loadable.error()?.description).toBe('Service unavailable');
    expect(service.stale()).toBe(false);
  });

  it('refreshNow forces an immediate fetch', () => {
    const fetch = vi.fn(() => of({ active: false }));
    start(fetch, (d) => d.active);
    service.refreshNow();
    expect(fetch).toHaveBeenCalledTimes(2);
  });

  it('pauses while the tab is hidden and refetches when it becomes visible', () => {
    let hidden = false;
    vi.spyOn(document, 'hidden', 'get').mockImplementation(() => hidden);
    const fetch = vi.fn(() => of({ active: true }));
    start(fetch, (d) => d.active);
    hidden = true;
    vi.advanceTimersByTime(ACTIVE_POLL_MS * 5);
    expect(fetch).toHaveBeenCalledTimes(1);
    hidden = false;
    document.dispatchEvent(new Event('visibilitychange'));
    expect(fetch).toHaveBeenCalledTimes(2);
  });
});
