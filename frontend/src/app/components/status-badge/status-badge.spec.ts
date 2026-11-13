import { TestBed } from '@angular/core/testing';
import { StatusBadge } from './status-badge';

async function render(status: string | null) {
  const fixture = TestBed.createComponent(StatusBadge);
  fixture.componentRef.setInput('status', status);
  await fixture.whenStable();
  return fixture.nativeElement as HTMLElement;
}

describe('StatusBadge', () => {
  it.each([
    ['QUEUED', 'Queued', 'queued'],
    ['PROCESSING', 'Processing', 'processing'],
    ['COMPLETED', 'Completed', 'completed'],
    ['FAILED', 'Failed', 'failed'],
  ])('renders %s as "%s" text', async (status, text, kind) => {
    const el = await render(status);
    expect(el.querySelector('.label')?.textContent?.trim()).toBe(text);
    expect(el.querySelector('.badge')?.getAttribute('data-status')).toBe(kind);
  });

  it.each(['reconciling', 'DONE', 'completed ', '', null])('renders unknown value %j as Unknown, never a success style', async (status) => {
    const el = await render(status);
    expect(el.querySelector('.label')?.textContent?.trim()).toBe('Unknown');
    const badge = el.querySelector('.badge')!;
    expect(badge.getAttribute('data-status')).toBe('unknown');
    expect(badge.classList.contains('completed')).toBe(false);
  });

  it('renders a status value the UI does not know (CANCELLED) as a grey Unknown badge with no status icon', async () => {
    const el = await render('CANCELLED');
    const badge = el.querySelector('.badge')!;
    expect(el.querySelector('.label')?.textContent?.trim()).toBe('Unknown');
    expect(badge.getAttribute('data-status')).toBe('unknown');
    expect(badge.querySelector('.hollow')).not.toBeNull();
    expect(el.querySelector('.icon')).toBeNull();
  });

  it('always shows the error icon with the word for Failed', async () => {
    const el = await render('FAILED');
    expect(el.querySelector('.icon')?.textContent?.trim()).toBe('error');
  });
});
