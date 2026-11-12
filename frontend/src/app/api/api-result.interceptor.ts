import { HttpErrorResponse, HttpEvent, HttpInterceptorFn, HttpResponse } from '@angular/common/http';
import { catchError, map, Observable, throwError } from 'rxjs';
import { ApiResult } from './models';
import { appError, AppError, NETWORK_ERROR_DESCRIPTION } from './app-error';

export function isApiResult(body: unknown): body is ApiResult<unknown> {
  if (!body || typeof body !== 'object') return false;
  const b = body as Record<string, unknown>;
  return typeof b['status'] === 'number' && 'data' in b && typeof b['description'] === 'string';
}

function fromErrorResponse(err: HttpErrorResponse): AppError {
  if (err.status === 0) return appError(0, NETWORK_ERROR_DESCRIPTION);
  const body = err.error;
  if (isApiResult(body)) {
    return appError(body.status || err.status, body.description, Array.isArray(body.errors) ? body.errors : []);
  }
  const statusText = err.statusText && err.statusText !== 'OK' ? ` ${err.statusText}` : '';
  return appError(err.status, `The server returned ${err.status}${statusText}. Please try again.`);
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
        throw appError(event.status, 'The server sent a response QueueBoard doesn’t understand.');
      }
      return event.clone({ body: event.body.data });
    }),
    catchError((err: unknown) => {
      if (err instanceof HttpErrorResponse) return throwError(() => fromErrorResponse(err));
      if (err && typeof err === 'object' && (err as AppError).kind === 'AppError') return throwError(() => err);
      return throwError(() => appError(0, 'Something went wrong. Please try again.'));
    }),
  );
};
