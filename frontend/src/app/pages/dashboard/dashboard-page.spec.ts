import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { apiResultInterceptor } from '../../api/api-result.interceptor';
import { NotificationService } from '../../services/notification.service';
import { job, ok, page, STATS } from '../../testing/fixtures';
import { DashboardPage } from './dashboard-page';

describe('DashboardPage', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(withInterceptors([apiResultInterceptor])),
        provideHttpClientTesting(),
        { provide: NotificationService, useValue: { show: vi.fn() } },
      ],
    });
  });

  it('shows skeletons first, then status cards and health metrics from Stats', async () => {
    const fixture = TestBed.createComponent(DashboardPage);
    await fixture.whenStable();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelectorAll('[aria-busy=true]').length).toBeGreaterThan(0);

    const http = TestBed.inject(HttpTestingController);
    http.expectOne('/api/v1/jobs/stats').flush(ok(STATS));
    http.expectOne((r) => r.url === '/api/v1/jobs' && r.params.get('size') === '5')
      .flush(ok(page([job({ name: 'Midterm grades CSCI-110', status: 'COMPLETED' })])));
    await fixture.whenStable();

    const counts = [...el.querySelectorAll('app-status-card [data-testid=count]')].map((c) => c.textContent?.trim());
    expect(counts).toEqual(['2', '5', '41', '6']);

    const text = el.textContent!;
    expect(text).toContain('1.8 s');
    expect(text).toContain('4.2 s');
    expect(text).toContain('13%');
    expect(text).toContain('9% temporary, 4% permanent');
    expect(text).toContain('67%');
    expect(text).toContain('Midterm grades CSCI-110');
    fixture.destroy();
  });

  it('shows an inline generic error, not the server text, when the first load fails', async () => {
    const fixture = TestBed.createComponent(DashboardPage);
    await fixture.whenStable();
    const http = TestBed.inject(HttpTestingController);
    http.expectOne('/api/v1/jobs/stats').flush({ status: 503, message: 'x', description: 'QueueBoard is restarting.', data: null, errors: [] }, { status: 503, statusText: 'Service Unavailable' });
    // forkJoin cancels the sibling recent-jobs request when stats fails.
    await fixture.whenStable();
    expect((fixture.nativeElement as HTMLElement).querySelector('[role=alert]')?.textContent).toContain('Something went wrong. Please try again.');
    expect((fixture.nativeElement as HTMLElement).textContent).not.toContain('QueueBoard is restarting.');
    fixture.destroy();
  });
});
