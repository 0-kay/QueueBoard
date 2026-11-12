import { JOB_STATUSES, JobStatus, JobType, JobTypeInfo } from '../api/models';

export type BadgeKind = 'queued' | 'processing' | 'completed' | 'failed' | 'unknown';

/** Any status not in the known list maps to Unknown, never to a success style. */
export function badgeKind(status: string | null | undefined): BadgeKind {
  const s = String(status ?? '').toUpperCase();
  return (JOB_STATUSES as readonly string[]).includes(s) ? (s.toLowerCase() as BadgeKind) : 'unknown';
}

export function isKnownStatus(status: string | null | undefined): status is JobStatus {
  return (JOB_STATUSES as readonly string[]).includes(String(status ?? ''));
}

export function isActiveStatus(status: string | null | undefined): boolean {
  return status === 'QUEUED' || status === 'PROCESSING';
}

export const STATUS_LABELS: Record<JobStatus, string> = {
  QUEUED: 'Queued',
  PROCESSING: 'Processing',
  COMPLETED: 'Completed',
  FAILED: 'Failed',
};

const TYPE_LABELS: Record<JobType, string> = {
  GRADE_REPORT: 'Grade report',
  NOTIFICATION_BATCH: 'Notification batch',
  BILLING_ASSESSMENT: 'Billing assessment',
};

export function typeLabel(type: string, types?: readonly JobTypeInfo[] | null): string {
  const fromApi = types?.find((t) => t.type === type)?.label;
  return fromApi ?? TYPE_LABELS[type as JobType] ?? type;
}

/** "1.8 s" */
export function formatSeconds(ms: number | null | undefined): string {
  if (ms == null || !Number.isFinite(ms)) return '—';
  return `${(ms / 1000).toFixed(1)} s`;
}

/** "13%" */
export function formatPercent(rate: number | null | undefined): string {
  if (rate == null || !Number.isFinite(rate)) return '—';
  return `${Math.round(rate * 100)}%`;
}

/** "3s ago", "4 min ago", "2 h ago", "3 d ago" */
export function formatAgo(iso: string | number | null | undefined, now: number): string {
  if (iso == null) return '—';
  const t = typeof iso === 'number' ? iso : Date.parse(iso);
  if (!Number.isFinite(t)) return '—';
  const s = Math.max(0, Math.round((now - t) / 1000));
  if (s < 60) return `${s}s ago`;
  const m = Math.floor(s / 60);
  if (m < 60) return `${m} min ago`;
  const h = Math.floor(m / 60);
  if (h < 24) return `${h} h ago`;
  return `${Math.floor(h / 24)} d ago`;
}

/** "Nov 11, 10:42:08" */
export function formatShort(iso: string | null | undefined): string {
  if (!iso) return '—';
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return '—';
  const date = d.toLocaleDateString('en-US', { month: 'short', day: 'numeric' });
  return `${date}, ${time(d)}`;
}

/** "Nov 11, 2026, 10:12:40" */
export function formatLong(iso: string | null | undefined): string {
  if (!iso) return '—';
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return '—';
  const date = d.toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' });
  return `${date}, ${time(d)}`;
}

/** "10:12:40" */
export function formatTime(iso: string | null | undefined): string {
  if (!iso) return '—';
  const d = new Date(iso);
  return Number.isNaN(d.getTime()) ? '—' : time(d);
}

function time(d: Date): string {
  return d.toLocaleTimeString('en-GB', { hour: '2-digit', minute: '2-digit', second: '2-digit', hour12: false });
}
