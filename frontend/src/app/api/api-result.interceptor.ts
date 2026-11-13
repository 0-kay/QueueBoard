import { HttpErrorResponse, HttpEvent, HttpInterceptorFn, HttpRequest, HttpResponse } from '@angular/common/http';
import { catchError, map, Observable, throwError } from 'rxjs';
import { ApiResult } from './models';
import { appError, AppError, NETWORK_ERROR_DESCRIPTION } from './app-error';

export function isApiResult(body: unknown): body is ApiResult<unknown> {
  if (!body || typeof body !== 'object') return false;
  const b = body as Record<string, unknown>;
  return typeof b['status'] === 'number' && 'data' in b && typeof b['description'] === 'string';
}

/** Logs the real failure; the UI only ever gets the generic text. */
function logError(req: HttpRequest<unknown>, detail: unknown): void {
  console.error(`[QueueBoard] ${req.method} ${req.urlWithParams} failed`, detail);
}

function fromErrorResponse(req: HttpRequest<unknown>, err: HttpErrorResponse): AppError {
  logError(req, err.error ?? err);
  if (err.status === 0) return appError(0, NETWORK_ERROR_DESCRIPTION);
  const body = err.error;
  if (isApiResult(body)) {
    return appError(body.status || err.status, undefined, Array.isArray(body.errors) ? body.errors : []);
  }
  return appError(err.status);
}

/**
 * Unwraps `ApiResult.data` on success and turns every failure (ApiResult error body,
 * network failure, non-ApiResult body) into one AppError.
 */
export const apiResultInterceptor: HttpInterceptorFn = (req, next): Observable<HttpEvent<unknown>> => {
  if (!req.url.startsWith('/api/')) return next(req);

  return next(req).pipe(
    map((event) => {
      if (!(event instanceof HttpResponse)) return event;
      if (!isApiResult(event.body)) {
        logError(req, { status: event.status, body: event.body, reason: 'Response is not an ApiResult' });
        throw appError(event.status);
      }
      return event.clone({ body: event.body.data });
    }),
    catchError((err: unknown) => {
      if (err instanceof HttpErrorResponse) return throwError(() => fromErrorResponse(req, err));
      if (err && typeof err === 'object' && (err as AppError).kind === 'AppError') return throwError(() => err);
      logError(req, err);
      return throwError(() => appError(0));
    }),
  );
};
