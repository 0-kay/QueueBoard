# QueueBoard frontend

Angular 21 + Angular Material (M3) admin UI for QueueBoard: submit background jobs, watch their
status change live, inspect details and errors, and retry, duplicate or delete jobs. It talks only
to the QueueBoard backend API (`/api/v1`).

**Rule zero:** the UI never shows success unless the backend reported it. Unknown statuses render as
a grey "Unknown" badge, Retry always says "queued", and status always comes from the latest response.

## Prerequisites

- Node.js `^20.19`, `^22.12` or `>=24` (the Angular 21 requirement). I test with Node 22.17, and the
  Docker image uses Node 22.
- npm 10 or later
- The QueueBoard backend running on `http://localhost:8080`

## Run

```bash
npm ci
npm start
```

Open http://localhost:4200. `npm start` runs `ng serve` on port 4200 with `proxy.conf.json`.

> If `npm install` (not `npm ci`) fails with `Cannot read properties of null (reading 'edgesOut')`,
> that's a known npm 10.9 bug when building a fresh dependency tree. Use `npm ci` (it installs from
> the lockfile) or `npx npm@11 install`.

Fonts (Roboto, Roboto Mono) and icons (Material Symbols) are bundled from npm, so the app makes no
requests to Google Fonts and works offline.

## Proxy setup

`proxy.conf.json` forwards every `/api` request to `http://localhost:8080`, so the browser only
ever talks to `localhost:4200`. To point at a different backend, change `target` in
`proxy.conf.json`.

## Build

```bash
npm run build
```

Runs a production `ng build`. The output goes to `dist/QueueBoardFE/browser`.

## Docker

The `Dockerfile` builds the app with Node 22 (`npm ci` + production build), then serves it from
nginx on port 80. It's used by the root `docker-compose.yml` (published on `localhost:8080`).
`nginx.conf`:

- serves the built files, with `index.html` as the fallback for app routes like `/jobs/:id`
- proxies `/api/` to `http://backend:8080` (the Compose service name)
- proxies `/swagger-ui` and `/v3/api-docs` to the backend, so Swagger UI works on the same address
- resolves `backend` per request through Docker's DNS, so nginx starts even if the backend isn't
  up yet and answers `/api` with 502 until it is (the UI shows its connection-lost state)

Build the image on its own with:

```bash
docker build -t queueboard-frontend .
```

## Test

```bash
npm test
```

Runs the default Angular 21 unit-test builder (Vitest + jsdom) once. Use `npx ng test` for watch
mode. HTTP is mocked with `HttpTestingController`. Covered:

| Area | Spec |
| --- | --- |
| Status badge (each status, unknown values → "Unknown") | `components/status-badge/status-badge.spec.ts` |
| Interceptor (unwrap, generic error text, real error logged, network failure, non-ApiResult bodies) | `api/api-result.interceptor.spec.ts` |
| Polling (2 s active, 10 s idle, stale keeps data, refresh, hidden tab) | `services/job-polling.service.spec.ts` |
| Retry visibility, Delete only for COMPLETED/FAILED, FAILED never completed | `components/detail-panel/detail-panel.spec.ts` |
| Retry 202 → "Retry queued" + QUEUED, 409 → generic message + refetch, delete confirmation, URL filters | `pages/jobs/jobs-page.spec.ts` |
| Submit modal (fields per type, 400 fields marked with generic text, disabled while sending, duplicate prefill) | `components/submit-modal/submit-modal.spec.ts` |
| Dashboard cards and metrics from `Stats`, first-load error | `pages/dashboard/dashboard-page.spec.ts` |

## Structure

```
src/app/
├── api/          JobApiService, ReferenceApiService (the only HttpClient users), models.ts,
│                 app-error.ts, api-result.interceptor.ts
├── services/     JobPollingService, QueueStateService (signals), NotificationService (toasts),
│                 SessionJobsService, JobSubmitService, DemoService, ReferenceDataService, ClockService
├── pages/        DashboardPage (/dashboard), JobsPage (/jobs and /jobs/:id)
├── components/   app-header, demo-menu, status-card, metric-card, status-badge, job-table,
│                 job-filters, recent-jobs, detail-panel, submit-modal, confirm-dialog
└── shared/       format.ts (labels, durations, percentages, dates)
```

- Pages fetch data and hold state; components only take `input()`s and emit `output()`s.
- The submit modal is a presentational component; `JobSubmitService` opens it, feeds it reference
  data, the in-flight flag and server errors, and sends the request.
- Every response is `ApiResult<T>`. The interceptor unwraps `data` on success and turns any failure
  (ApiResult error, network error, unexpected body) into one `AppError { status, description, errors }`.
- The UI never shows server or exception text. `description` is always "Something went wrong.
  Please try again." (or a "can't reach the server" message for network failures), and fields the
  server rejected show "Check this value." The real error goes to `console.error`.

## Behavior notes

- **Polling:** every 2 s while anything visible is QUEUED or PROCESSING, otherwise every 10 s.
  Paused while the tab is hidden, refetched as soon as it is visible. The header refresh button and
  the stale banner's Reconnect force an immediate fetch.
- **States:** skeletons on first load, empty and filtered-empty states, inline errors with Try
  again, and a "Connection lost. Showing data from Ns ago." banner when a poll fails after data
  loaded (cleared by the next good poll).
- **Jobs filters, search, page, page size and sort** live in the URL query string.
- **Sorting:** the API has no sort parameter and returns newest first, so sorting by Created or
  Updated reorders the rows of the current page.
- **Toasts:** "Job submitted", "Retry queued · name", "Job deleted", and "name completed / failed"
  when a poll shows a job submitted or retried in this session finished.
- **Demo menu:** each item is real `POST /api/v1/jobs` calls (one of each type, a temporary
  failure via `simulateFailure`, a grade report on a closed course, a burst of 15).
