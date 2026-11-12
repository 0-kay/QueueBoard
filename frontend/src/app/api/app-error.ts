import { FieldError } from './models';

/** The single error type every API call fails with. Show `description` to the user. */
export interface AppError {
  readonly kind: 'AppError';
  status: number;
  description: string;
  errors: FieldError[];
}

export function appError(status: number, description: string, errors: FieldError[] = []): AppError {
  return { kind: 'AppError', status, description, errors };
}

export function isAppError(value: unknown): value is AppError {
  return !!value && typeof value === 'object' && (value as AppError).kind === 'AppError';
}

/** Coerces anything thrown into an AppError (never crashes the caller). */
export function toAppError(value: unknown): AppError {
  if (isAppError(value)) return value;
  return appError(0, 'Something went wrong. Please try again.');
}

export const NETWORK_ERROR_DESCRIPTION = 'Can’t reach the QueueBoard server. Check your connection and try again.';
