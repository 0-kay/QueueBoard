# QueueBoard backend

A Spring Boot service where users submit background jobs, watch them move through a lifecycle
(`QUEUED → PROCESSING → COMPLETED | FAILED`), inspect results and errors, and retry failed jobs when
the rules allow. Processing is simulated against seeded, synthetic LMS data (courses, students,
enrollments, terms, accounts, notification templates).

## Prerequisites

- Java 21 (JDK). The Gradle toolchain targets 21.
- No Gradle install needed: use the Gradle wrapper (`./gradlew`) in this folder, which downloads the
  right Gradle version on first run.
- Nothing else. The database is in-memory H2.

## How to run

```bash
./gradlew bootRun
```

The API starts on **http://localhost:8080** with seeded data (13 finished jobs, so the dashboard has
numbers on first load).

| What | URL |
| --- | --- |
| Swagger UI | http://localhost:8080/swagger-ui.html |
| OpenAPI JSON | http://localhost:8080/v3/api-docs |
| API base | http://localhost:8080/api/v1 |

### H2 console (dev profile only)

```bash
./gradlew bootRun --args='--spring.profiles.active=dev'
```

Open http://localhost:8080/h2-console with JDBC URL `jdbc:h2:mem:queueboard`, user `sa`, empty password.

## How to run tests

```bash
./gradlew test
```

| Area | Test | Proves |
| --- | --- | --- |
| Job lifecycle | `JobLifecycleTest` (unit) | Every legal transition works; every illegal one throws |
| Retry rules | `RetryServiceTest` (Mockito) | TEMPORARY under the cap is retryable; PERMANENT, exhausted, and non-FAILED are not |
| Payload validation | `JobPayloadValidatorTest`, `SimulatedJobProcessorTest` (unit) | Missing, wrong and unknown fields per type; unknown type; size cap; `simulateFailure` rejected when demo is off |
| Grade and billing simulation | `GradeReportSimulationTest`, `BillingAssessmentSimulationTest` (H2) | Success writes the expected rows; a failure partway leaves zero domain changes |
| Notification simulation | `NotificationBatchSimulationTest` (H2) | A failure partway keeps SENT rows; a retry sends only the remaining recipients |
| API | `JobControllerTest` (`@WebMvcTest`) | 201, 400 with field errors, 404, 409, 405, 500; every response is an `ApiResult`; `retryable` is correct |
| Claiming | `JobClaimRaceTest` (H2) | 8 threads racing over 25 jobs: each claimed exactly once |
| Worker | `WorkerConcurrencyTest` (`@SpringBootTest`) | 40 jobs through the 5-thread pool: none lost, none run twice, all COMPLETED |
| End to end | `EndToEndTest` (`@SpringBootTest` + MockMvc) | Submit to COMPLETED; temporary failure, retry, COMPLETED; permanent failure, 409, delete; stats match the rows |
| Flyway | `FlywayMigrationTest` | Migrations apply, Hibernate only validates, seed data is present |

Tests run with `queueboard.simulation.delay-scale=0` (or 0.01 for worker tests) and each Spring
context gets its own in-memory database. Async state is awaited with Awaitility.

## API

All routes are under `/api/v1`. Every response, success or error, is wrapped in `ApiResult`:

```json
{ "status": 409, "message": "Retry not allowed",
  "description": "This job failed with a permanent error and can't be retried.",
  "data": null, "errors": [] }
```

`status` always equals the HTTP status, `description` is safe to show users, and `errors` lists
field problems on a 400 (for example `{ "field": "payload.courseCode", "message": "Course not found" }`).

| Method and path | Purpose | Success | Errors |
| --- | --- | --- | --- |
| `POST /jobs` | Submit a job | 201 + `Location` | 400 |
| `GET /jobs?status=&type=&search=&page=0&size=20` | List, newest first | 200 | 400 |
| `GET /jobs/{id}` | Inspect, with attempt history | 200 | 404 |
| `POST /jobs/{id}/retry` | Re-queue a failed job | 202 | 404, 409 |
| `DELETE /jobs/{id}` | Soft delete (COMPLETED or FAILED only) | 200 | 404, 409 |
| `GET /jobs/stats` | Dashboard metrics | 200 | |
| `GET /job-types`, `/courses`, `/terms`, `/notification-templates` | Dropdown data | 200 | |

Swagger UI has request and response examples for every endpoint.

### Job types

| Type | Payload | Work |
| --- | --- | --- |
| `GRADE_REPORT` | `courseCode` | Letter grades from scores, completion flags, awards (90+ badge, 95+ certificate). One transaction. |
| `NOTIFICATION_BATCH` | `templateCode`, `courseCode`, `channel` (`EMAIL`/`PUSH`) | One delivery row per enrolled student, each marked SENT in its own transaction. |
| `BILLING_ASSESSMENT` | `termCode`, `applyLateFees` | Recalculates balances, one LATE_FEE per past-due account, aid status updates. One transaction. |

Every payload also accepts `simulateFailure`: `"TEMPORARY"`, `"PERMANENT"` or null (demo mode only).
PERMANENT fails every attempt. **TEMPORARY fails the first attempt only**, so a manual retry shows a
temporary error going away.

Closed courses (`PHYS-099`), closed terms (`SP26`) and inactive templates (`LEGACY_NEWSLETTER`) are
valid at submit and fail during processing with a permanent error.

### Retry rule

A job is retryable when it is FAILED, its latest attempt's error is TEMPORARY, and
`attempts < maxAttempts` (3 total: one run plus two retries). The server computes `retryable` on
every job response. Retry keeps the job id.

**Retries stop after 3 attempts.** Once a job has used all 3, it stays FAILED and `retryable` is
false, even if its error was TEMPORARY. So a demo job that keeps failing ends up FAILED with no more
retries, and `POST /jobs/{id}/retry` returns 409.

## Configuration

| Property | Default | Purpose |
| --- | --- | --- |
| `queueboard.worker.threads` | 5 | Worker pool size |
| `queueboard.worker.poll-interval-ms` | 500 | How often the poller claims jobs |
| `queueboard.worker.enabled` | true | Run the poller (tests that drive the simulation directly turn it off) |
| `queueboard.jobs.max-attempts` | 3 | Total attempts per job |
| `queueboard.demo.enabled` | true | Allows `simulateFailure` in payloads |
| `queueboard.simulation.delay-scale` | 1.0 | Multiplier for simulated work time; 0 makes jobs instant |

There is no CORS configuration. The frontend always reaches the API through its nginx proxy (or the
Angular dev server proxy), so browser requests are same-origin.

## Logging

| Level | What you see |
| --- | --- |
| INFO (default) | Worker start, job submitted, started, completed or failed (with duration and error code), retried, retry rejected, deleted, 409s |
| DEBUG (`dev` profile) | Polls that found work, claims, handler steps (rows graded, accounts assessed, recipients pending), 400/404s |
| TRACE | Each simulated notification send |
| WARN / ERROR | Unexpected handler errors (with stack trace), unhandled API exceptions, failures to record an outcome |

Every line written while a job runs, including handler lines, is tagged with the job id:

```
INFO [job 4c89d3ab-...] [job-worker-4] c.l.q.service.worker.JobWorker : Completed in 1622 ms: 40 accounts assessed, 6 late fees, 4 aid changes
```

Change the level with `logging.level.com.liberty.queueboardbe=DEBUG`.

## How processing works

The `job` table is the queue; there is no in-memory queue, so status and queue can never disagree.

What a job actually *does* sits behind one interface, `JobProcessor` (`validate` at submit,
`process` in the worker). The core never looks past it. The `simulation` package implements it with a
small simulated LMS; see [its README](src/main/java/com/liberty/queueboardbe/simulation/README.md).
That part is realism only and not the focus of this assessment.

Two classes do all of it:

- **`JobWorker`** polls every `poll-interval-ms`. It checks free threads (a semaphore sized to the
  pool), selects that many QUEUED jobs oldest first, claims each, and runs it on the pool. Running a
  job catches every exception and maps it to a `JobErrorCode` (`UNEXPECTED_ERROR` if unknown), so no
  job stays PROCESSING because of an exception.
- **`JobTransitions`** holds the state changes, each in its own `REQUIRES_NEW` transaction:
  `claim` (one conditional UPDATE, `... WHERE id = ? AND status = 'QUEUED' AND deleted_at IS NULL`,
  where only a row count of 1 wins; it also opens the attempt), `complete` and `fail`. It is a separate
  bean because `@Transactional` doesn't apply to calls within one class.

So each run is three transactions: claim, the processor's work, and the outcome. A failed job's work
rolls back; the record that it failed does not.

Awards, charges and deliveries carry the `job_id`; unique constraints are a second guard against
double awards, double fees and double sends.

## Project structure

Layers at the top, a folder per domain inside each layer. The four services the controllers call
are an interface plus an `impl/` class; everything else is a plain class.

```
src/main/java/com/liberty/queueboardbe/
├── controller/           Routes, validation and Swagger docs (ApiExamples holds the example JSON)
├── model/
│   ├── common/           ApiResult, FieldError, PageResponse
│   ├── job/              Job and JobAttempt entities, job enums, job DTOs
│   ├── payload/          One record per job type, plus SimulateFailure and Channel
│   ├── stats/            StatsResponse
│   └── reference/        Dropdown DTOs
├── service/
│   ├── job/              JobService, RetryService (+ impl/), JobMapper, RetryPolicy, JobPayloadValidator
│   ├── worker/           JobWorker (poll + run), JobTransitions (claim, complete, fail)
│   ├── processing/       JobProcessor: the one interface between the core and the simulation
│   ├── stats/            StatsService (+ impl/)
│   └── reference/        ReferenceDataService (+ impl/)
├── repository/
│   └── job/              Spring Data repositories for job and job_attempt, stats queries
├── config/               Properties, worker pool, OpenAPI
├── exception/            GlobalExceptionHandler and custom exceptions
└── simulation/           NOT the focus: SimulatedJobProcessor (dispatcher), one simulation per type,
                          LmsRepository, SimulatedReferenceData, NotificationSender, SimulatedWork
src/main/resources/db/migration/   V1__job_schema.sql, V2__simulation_schema.sql, V3__seed.sql
```

Adding a job type means a payload record in `model/payload` (added to the sealed `JobPayload`), the
`JobType` value and the `ck_job_type` constraint; the compiler then points at the dispatcher `switch`
that needs a new case. Everything above `simulation/` is the assessment core.

## Known limitations

[docs/descoped.md](docs/descoped.md) has the full list of what I cut, isolated, or chose not to
build. In short:

- **Processing is simulated.** The job types run against a small simulated LMS in `simulation/`,
  kept behind `JobProcessor` so it can be swapped for real integrations.
- **Not built:** authentication, scheduled or recurring jobs, automatic retry with backoff, cancel,
  a stuck-job reaper, push updates to the UI (the UI polls), and exactly-once notifications.
- **H2 only.** Moving to Postgres means rewriting `DATEADD` in the seed migration and `DATEDIFF` in
  the two stats queries; the rest of the SQL is portable.

Behaviour to be aware of when running it:

- **In-memory data resets on restart.** H2 runs in memory; every start re-runs the migrations and seed.
- **Notifications are effectively once, not exactly once.** A crash between sending a message and
  saving its SENT flag would resend that one message on retry.
- **No stuck-job reaper.** If a worker thread dies (or the process stops) mid-job, the job stays
  PROCESSING. Exceptions are always recorded; only a dead thread or JVM leaves a job behind.
- **Single instance tested only.** The atomic claim is safe across instances in principle, but I only
  tested one instance, against an in-memory database.
- Grade or billing jobs touching the same rows at once serialize on row locks (`LOCK_TIMEOUT=10000`);
  a wait longer than that fails the job with a retryable `UNEXPECTED_ERROR`.
- The Docker setup lives at the repo root (`docker-compose.yml`).
