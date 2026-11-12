export interface ApiResult<T> {
  status: number;
  message: string;
  description: string;
  data: T | null;
  errors: FieldError[];
}

export interface FieldError {
  field: string;
  message: string;
}

export type JobStatus = 'QUEUED' | 'PROCESSING' | 'COMPLETED' | 'FAILED';
export type JobType = 'GRADE_REPORT' | 'NOTIFICATION_BATCH' | 'BILLING_ASSESSMENT';
export type ErrorKind = 'TEMPORARY' | 'PERMANENT';

export const JOB_STATUSES: readonly JobStatus[] = ['QUEUED', 'PROCESSING', 'COMPLETED', 'FAILED'];
export const JOB_TYPES: readonly JobType[] = ['GRADE_REPORT', 'NOTIFICATION_BATCH', 'BILLING_ASSESSMENT'];

export interface JobSummary {
  id: string;
  name: string;
  type: JobType;
  /** Unknown values must render as "Unknown". */
  status: JobStatus | string;
  attempts: number;
  maxAttempts: number;
  lastErrorCode: string | null;
  retryable: boolean;
  /** ISO-8601 UTC, displayed in local time. */
  createdAt: string;
  updatedAt: string;
}

export interface JobAttempt {
  attemptNumber: number;
  startedAt: string;
  finishedAt: string | null;
  outcome: 'COMPLETED' | 'FAILED' | null;
  errorCode: string | null;
  errorKind: ErrorKind | null;
  errorMessage: string | null;
}

export interface JobDetail extends JobSummary {
  payload: Record<string, unknown>;
  resultSummary: string | null;
  attemptHistory: JobAttempt[];
}

export interface Page<T> {
  items: T[];
  page: number;
  size: number;
  totalItems: number;
}

export interface Stats {
  byStatus: Record<JobStatus, number>;
  avgQueueWaitMs: number;
  avgRunTimeMs: number;
  failureRate: { total: number; temporary: number; permanent: number };
  retryRecoveryRate: number;
}

export interface SubmitJobRequest {
  name: string;
  type: JobType;
  /** A JSON object, not a string. */
  payload: Record<string, unknown>;
}

export interface JobQuery {
  status?: JobStatus | null;
  type?: JobType | null;
  search?: string | null;
  page?: number;
  size?: number;
}

export interface CourseRef { code: string; title: string; status: 'OPEN' | 'CLOSED'; }
export interface TermRef { code: string; name: string; status: 'OPEN' | 'CLOSED'; }
export interface TemplateRef { code: string; kind: string; subject: string; active: boolean; }
export interface JobTypeInfo { type: JobType; label: string; description: string; }

export interface ReferenceData {
  jobTypes: JobTypeInfo[];
  courses: CourseRef[];
  terms: TermRef[];
  templates: TemplateRef[];
}
