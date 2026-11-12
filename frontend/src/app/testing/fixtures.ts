import { ApiResult, JobDetail, Page, ReferenceData, Stats } from '../api/models';

export function ok<T>(data: T, status = 200): ApiResult<T> {
  return { status, message: 'OK', description: 'Request completed.', data, errors: [] };
}

export function fail(status: number, description: string, errors: { field: string; message: string }[] = []): ApiResult<null> {
  return { status, message: 'Error', description, data: null, errors };
}

export function job(overrides: Partial<JobDetail> = {}): JobDetail {
  return {
    id: 'job-1',
    name: 'Final grades MATH-201',
    type: 'GRADE_REPORT',
    status: 'FAILED',
    attempts: 1,
    maxAttempts: 3,
    lastErrorCode: 'SIMULATED_TEMPORARY',
    retryable: true,
    createdAt: '2026-11-11T18:30:00Z',
    updatedAt: '2026-11-11T18:30:02Z',
    payload: { courseCode: 'MATH-201' },
    resultSummary: null,
    attemptHistory: [
      {
        attemptNumber: 1, startedAt: '2026-11-11T18:30:01Z', finishedAt: '2026-11-11T18:30:02Z', outcome: 'FAILED',
        errorCode: 'SIMULATED_TEMPORARY', errorKind: 'TEMPORARY', errorMessage: 'Simulated temporary failure',
      },
    ],
    ...overrides,
  };
}

export function page(items: JobDetail[]): Page<JobDetail> {
  return { items, page: 0, size: 20, totalItems: items.length };
}

export const STATS: Stats = {
  byStatus: { QUEUED: 2, PROCESSING: 5, COMPLETED: 41, FAILED: 6 },
  avgQueueWaitMs: 1840,
  avgRunTimeMs: 4210,
  failureRate: { total: 0.1277, temporary: 0.0851, permanent: 0.0426 },
  retryRecoveryRate: 0.6667,
};

export const REFERENCE: ReferenceData = {
  jobTypes: [
    { type: 'GRADE_REPORT', label: 'Grade report', description: 'Recalculates letter grades.' },
    { type: 'NOTIFICATION_BATCH', label: 'Notification batch', description: 'Sends a template.' },
    { type: 'BILLING_ASSESSMENT', label: 'Billing assessment', description: 'Recalculates balances.' },
  ],
  courses: [
    { code: 'CSCI-110', title: 'Introduction to Programming', status: 'OPEN' },
    { code: 'PHYS-099', title: 'Legacy Physics Lab (retired)', status: 'CLOSED' },
  ],
  terms: [
    { code: 'FA26', name: 'Fall 2026', status: 'OPEN' },
    { code: 'SP26', name: 'Spring 2026', status: 'CLOSED' },
  ],
  templates: [
    { code: 'ANNOUNCE_MIDTERM', kind: 'ANNOUNCEMENT', subject: 'Midterm exam schedule', active: true },
    { code: 'LEGACY_NEWSLETTER', kind: 'ANNOUNCEMENT', subject: 'Department newsletter (retired)', active: false },
  ],
};
