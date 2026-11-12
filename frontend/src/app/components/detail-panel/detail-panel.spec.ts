import { ComponentFixture, TestBed } from '@angular/core/testing';
import { JobDetail } from '../../api/models';
import { job } from '../../testing/fixtures';
import { DetailPanel } from './detail-panel';

describe('DetailPanel', () => {
  let fixture: ComponentFixture<DetailPanel>;
  const el = () => fixture.nativeElement as HTMLElement;

  async function render(j: JobDetail) {
    fixture = TestBed.createComponent(DetailPanel);
    fixture.componentRef.setInput('job', j);
    await fixture.whenStable();
  }

  it('shows Retry only when the server says retryable', async () => {
    await render(job({ retryable: true }));
    expect(el().querySelector('[data-testid=retry]')).not.toBeNull();
    await render(job({ retryable: false }));
    expect(el().querySelector('[data-testid=retry]')).toBeNull();
  });

  it('never computes retry rules itself (FAILED + attempts left but retryable=false → no Retry)', async () => {
    await render(job({ status: 'FAILED', attempts: 1, maxAttempts: 3, retryable: false }));
    expect(el().querySelector('[data-testid=retry]')).toBeNull();
  });

  it.each([
    ['COMPLETED', true], ['FAILED', true], ['QUEUED', false], ['PROCESSING', false], ['mystery', false],
  ])('offers Delete for %s: %s', async (status, offered) => {
    await render(job({ status, retryable: false }));
    expect(!!el().querySelector('[data-testid=delete]')).toBe(offered);
  });

  it('renders a FAILED job as failed, never completed', async () => {
    await render(job({ status: 'FAILED', resultSummary: 'should not show' }));
    expect(el().querySelector('app-status-badge .label')?.textContent?.trim()).toBe('Failed');
    expect(el().querySelector('[data-status=completed]')).toBeNull();
    expect(el().querySelector('[data-testid=result]')).toBeNull();
  });

  it('shows attempts, payload JSON and the attempt history with error kind', async () => {
    await render(job());
    expect(el().querySelector('[data-testid=attempts]')?.textContent?.trim()).toBe('1 of 3');
    expect(el().querySelector('[data-testid=payload]')?.textContent).toContain('"courseCode": "MATH-201"');
    const attempt = el().querySelector('[data-testid=attempt]')!;
    expect(attempt.textContent).toContain('SIMULATED_TEMPORARY');
    expect(attempt.textContent).toContain('TEMPORARY');
    expect(attempt.textContent).toContain('Simulated temporary failure');
  });

  it('emits retry, duplicate and delete with the job', async () => {
    const j = job({ status: 'FAILED' });
    await render(j);
    const retry = vi.fn(); const dup = vi.fn(); const del = vi.fn();
    fixture.componentInstance.retry.subscribe(retry);
    fixture.componentInstance.duplicate.subscribe(dup);
    fixture.componentInstance.delete.subscribe(del);
    (el().querySelector('[data-testid=retry]') as HTMLButtonElement).click();
    (el().querySelector('[data-testid=duplicate]') as HTMLButtonElement).click();
    (el().querySelector('[data-testid=delete]') as HTMLButtonElement).click();
    expect(retry).toHaveBeenCalledWith(j);
    expect(dup).toHaveBeenCalledWith(j);
    expect(del).toHaveBeenCalledWith(j);
  });
});
