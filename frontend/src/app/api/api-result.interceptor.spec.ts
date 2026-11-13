import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { firstValueFrom } from 'rxjs';
import { apiResultInterceptor } from './api-result.interceptor';
import { AppError, GENERIC_ERROR_MESSAGE, isAppError, NETWORK_ERROR_DESCRIPTION } from './app-error';
import { fail, ok } from '../testing/fixtures';

describe('apiResultInterceptor', () => {
  let http: HttpClient;
  let ctrl: HttpTestingController;

  beforeEach(() => {
    vi.spyOn(console, 'error').mockImplementation(() => undefined);
    TestBed.configureTestingModule({
      providers: [provideHttpClient(withInterceptors([apiResultInterceptor])), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpClient);
    ctrl = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    ctrl.verify();
    vi.restoreAllMocks();
  });

  async function error(promise: Promise<unknown>): Promise<AppError> {
    try {
      await promise;
    } catch (e) {
      expect(isAppError(e)).toBe(true);
      return e as AppError;
    }
    throw new Error('expected an error');
  }

  it('unwraps data on success', async () => {
    const result = firstValueFrom(http.get('/api/v1/jobs/stats'));
    ctrl.expectOne('/api/v1/jobs/stats').flush(ok({ a: 1 }));
    expect(await result).toEqual({ a: 1 });
  });

  it('unwraps null data (delete)', async () => {
    const result = firstValueFrom(http.delete('/api/v1/jobs/x'));
    ctrl.expectOne('/api/v1/jobs/x').flush(ok(null));
    expect(await result).toBeNull();
  });

  it('turns an ApiResult error body into a generic AppError, keeping field names and logging the real error', async () => {
    const result = firstValueFrom(http.post('/api/v1/jobs', {}));
    ctrl.expectOne('/api/v1/jobs').flush(
      fail(400, 'Some fields need attention.', [{ field: 'payload.courseCode', message: 'Course not found' }]),
      { status: 400, statusText: 'Bad Request' },
    );
    const err = await error(result);
    expect(err.status).toBe(400);
    expect(err.description).toBe(GENERIC_ERROR_MESSAGE);
    expect(console.error).toHaveBeenCalledWith(
      '[QueueBoard] POST /api/v1/jobs failed',
      expect.objectContaining({ description: 'Some fields need attention.' }),
    );
    expect(err.errors).toEqual([{ field: 'payload.courseCode', message: 'Course not found' }]);
  });

  it('turns a network failure into an AppError with a generic description', async () => {
    const result = firstValueFrom(http.get('/api/v1/jobs'));
    ctrl.expectOne('/api/v1/jobs').error(new ProgressEvent('error'), { status: 0, statusText: 'Unknown Error' });
    const err = await error(result);
    expect(err.status).toBe(0);
    expect(err.description).toBe(NETWORK_ERROR_DESCRIPTION);
    expect(err.errors).toEqual([]);
  });

  it('turns a non-ApiResult error body into an AppError', async () => {
    const result = firstValueFrom(http.get('/api/v1/jobs'));
    ctrl.expectOne('/api/v1/jobs').flush('<html>Bad gateway</html>', { status: 502, statusText: 'Bad Gateway' });
    const err = await error(result);
    expect(err.status).toBe(502);
    expect(err.description).toBe(GENERIC_ERROR_MESSAGE);
    expect(console.error).toHaveBeenCalledWith('[QueueBoard] GET /api/v1/jobs failed', '<html>Bad gateway</html>');
  });

  it('turns a non-ApiResult success body into an AppError instead of crashing', async () => {
    const result = firstValueFrom(http.get('/api/v1/jobs'));
    ctrl.expectOne('/api/v1/jobs').flush({ items: [] });
    const err = await error(result);
    expect(err.description).toBe(GENERIC_ERROR_MESSAGE);
  });
});
