import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting, TestRequest } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { apiResultInterceptor } from '../../api/api-result.interceptor';
import { JobDetail } from '../../api/models';
import { routes } from '../../app.routes';
import { NotificationService } from '../../services/notification.service';
import { fail, job, ok, page, REFERENCE } from '../../testing/fixtures';

describe('JobsPage (detail panel actions)', () => {
  let http: HttpTestingController;
  let harness: RouterTestingHarness;
  const show = vi.fn();

  const root = () => harness.fixture.nativeElement as HTMLElement;
  const badgeText = () => root().querySelector('app-detail-panel app-status-badge .label')?.textContent?.trim();
  const isList = (r: { method: string; url: string }) => r.method === 'GET' && r.url === '/api/v1/jobs';
  const isGet = (r: { method: string; url: string }) => r.method === 'GET' && r.url === '/api/v1/jobs/job-1';

  async function respondPoll(detail: JobDetail) {
    http.match(isList).forEach((r: TestRequest) => r.flush(ok(page([detail]))));
    http.match(isGet).forEach((r: TestRequest) => r.flush(ok(detail)));
    await harness.fixture.whenStable();
  }

  beforeEach(async () => {
    show.mockReset();
    TestBed.configureTestingModule({
      providers: [
        provideRouter(routes),
        provideHttpClient(withInterceptors([apiResultInterceptor])),
        provideHttpClientTesting(),
        { provide: NotificationService, useValue: { show } },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    harness = await RouterTestingHarness.create('/jobs/job-1');
    http.match('/api/v1/job-types').forEach((r) => r.flush(ok(REFERENCE.jobTypes)));
    await respondPoll(job({ status: 'FAILED', retryable: true }));
  });

  afterEach(() => harness.fixture.destroy());

  it('shows the job from the backend with Retry when retryable', () => {
    expect(badgeText()).toBe('Failed');
    expect(root().querySelector('[data-testid=retry]')).not.toBeNull();
  });

  it('after Retry returns 202, shows "Retry queued" and the server status (QUEUED), never success', async () => {
    (root().querySelector('[data-testid=retry]') as HTMLButtonElement).click();
    http.expectOne({ method: 'POST', url: '/api/v1/jobs/job-1/retry' })
      .flush(ok(job({ status: 'QUEUED', retryable: false, attempts: 1 }), 202), { status: 202, statusText: 'Accepted' });
    await harness.fixture.whenStable();

    expect(show).toHaveBeenCalledWith(expect.stringContaining('Retry queued'));
    expect(show).not.toHaveBeenCalledWith(expect.stringMatching(/succeed|completed/i));
    expect(badgeText()).toBe('Queued');
    expect(root().querySelector('[data-status=completed]')).toBeNull();

    // The next poll reports FAILED again: it must render as failed, not completed.
    await respondPoll(job({ status: 'FAILED', attempts: 2, retryable: true }));
    expect(badgeText()).toBe('Failed');
    expect(root().querySelector('app-detail-panel [data-status=completed]')).toBeNull();
  });

  it('on 409 shows a generic message and refetches the job', async () => {
    (root().querySelector('[data-testid=retry]') as HTMLButtonElement).click();
    http.expectOne({ method: 'POST', url: '/api/v1/jobs/job-1/retry' })
      .flush(fail(409, 'This job failed with a permanent error and can\'t be retried.'), { status: 409, statusText: 'Conflict' });
    await harness.fixture.whenStable();

    expect(show).toHaveBeenCalledWith('Something went wrong. Please try again.');
    const refetch = http.match(isGet);
    expect(refetch.length).toBe(1);
    refetch[0].flush(ok(job({ status: 'FAILED', retryable: false })));
    http.match(isList).forEach((r) => r.flush(ok(page([job({ status: 'FAILED', retryable: false })]))));
    await harness.fixture.whenStable();
    expect(root().querySelector('[data-testid=retry]')).toBeNull();
    expect(badgeText()).toBe('Failed');
  });

  it('asks for confirmation before deleting', async () => {
    (root().querySelector('[data-testid=delete]') as HTMLButtonElement).click();
    await harness.fixture.whenStable();
    http.expectNone({ method: 'DELETE', url: '/api/v1/jobs/job-1' });

    const confirm = document.querySelector('.cdk-overlay-container [data-testid=confirm]') as HTMLButtonElement;
    expect(document.querySelector('.cdk-overlay-container')?.textContent).toContain('Delete this job?');
    confirm.click();
    await harness.fixture.whenStable();

    // The dialog reports its result after its exit animation.
    const del = await vi.waitFor(() => http.expectOne({ method: 'DELETE', url: '/api/v1/jobs/job-1' }));
    del.flush(ok(null));
    await harness.fixture.whenStable();
    expect(show).toHaveBeenCalledWith('Job deleted');
  });

  it('does not delete when the confirmation is cancelled', async () => {
    (root().querySelector('[data-testid=delete]') as HTMLButtonElement).click();
    await harness.fixture.whenStable();
    const cancel = [...document.querySelectorAll('.cdk-overlay-container button')].find((b) => b.textContent?.includes('Cancel')) as HTMLButtonElement;
    cancel.click();
    await harness.fixture.whenStable();
    await new Promise((r) => setTimeout(r, 300));
    http.expectNone({ method: 'DELETE', url: '/api/v1/jobs/job-1' });
  });

  it('keeps filters in the URL query string', async () => {
    await harness.navigateByUrl('/jobs?status=FAILED&search=MATH&page=2');
    const list = http.match(isList);
    expect(list.length).toBeGreaterThan(0);
    const params = list.at(-1)!.request.params;
    expect(params.get('status')).toBe('FAILED');
    expect(params.get('search')).toBe('MATH');
    expect(params.get('page')).toBe('2');
    expect(params.get('size')).toBe('20');
  });
});
