import { FieldError } from './models';

/** The only error text the UI shows. Real error details go to console.error. */
export const GENERIC_ERROR_MESSAGE = 'Something went wrong. Please try again.';

/**
 * The single error type every API call fails with. `description` is always safe to show:
 * it is GENERIC_ERROR_MESSAGE (or the network message), never server or exception text.
 * `errors` keeps the field names so forms can highlight the right inputs.
 */
export interface AppError {
  readonly kind: 'AppError';
  status: number;
  description: string;
  errors: FieldError[];
}

export function appError(status: number, description: string = GENERIC_ERROR_MESSAGE, errors: FieldError[] = []): AppError {
  return { kind: 'AppError', status, description, errors };
}

export function isAppError(value: unknown): value is AppError {
  return !!value && typeof value === 'object' && (value as AppError).kind === 'AppError';
}

/** Coerces anything thrown into an AppError (never crashes the caller). */
export function toAppError(value: unknown): AppError {
  if (isAppError(value)) return value;
  console.error('[QueueBoard] Unexpected error', value);
  return appError(0);
}

export const NETWORK_ERROR_DESCRIPTION = 'Can’t reach the QueueBoard server. Check your connection and try again.';
