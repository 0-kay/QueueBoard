# What I cut, isolated, or chose not to build

Raw notes for the design document. The assessment asks for simulated processing and a clear, tested
core, not breadth, so each item below was weighed against "does this help show sound design for
submitting and tracking jobs?"

## Isolated: realistic processing (kept, but moved out of the core)

The job types do real work against a small simulated LMS. That work now lives in
`simulation/`, behind one interface (`service/processing/JobProcessor`), as scaffolding for realism,
not part of what's being assessed.

| What the simulation does | Where it used to be |
| --- | --- |
| Grade reports: letter grades, completion, badges/certificates, one transaction with full rollback | `service/handler/GradeReportHandler` |
| Notification batches: one delivery row per student, each SENT committed alone, retry sends only the rest | `service/handler/NotificationBatchHandler` |
| Billing: balances, one late fee per past-due account, aid status rules, one transaction | `service/handler/BillingAssessmentHandler` |
| All LMS SQL (8 tables) and the dropdown data | `repository/lms/`, `service/reference/impl/` |
| Simulated delays and a fake email/push gateway | `service/handler/SimulatedWork`, `NotificationSender` |

Why: it makes the demo believable (real rows change, rollbacks and partial failures are observable),
but it is the bulk of the code and none of it is about job management. Keeping it behind
`JobProcessor` means a reviewer can read the core without it, and swapping in real integrations
is one new class.

## Removed while simplifying the structure

| Removed | Replaced by | Why |
| --- | --- | --- |
| `api/` interfaces (`JobApi`, `StatsApi`, `ReferenceDataApi`) holding routes and Swagger docs | Annotations directly on the controllers | A second file per controller with no second implementation |
| Interface + `Impl` for worker internals (claim, execute, outcome services) | Plain classes | Interfaces kept only for the four services the controllers call |
| `JobPoller`, `ClaimedJob`, `JobClaimService`, `JobExecutionService`, `JobOutcomeService` | `JobWorker` (poll + run) and `JobTransitions` (claim / complete / fail) | Same guarantees in 2 classes instead of 7 |
| 6 JDBC repositories, a SQL helper, 8 row records | One `LmsRepository` with nested records (now in `simulation/`) | Small queries, one consumer |
| `JobHandler` interface + `JobHandlerRegistry` | A sealed `JobPayload` and one exhaustive `switch` in `SimulatedJobProcessor` | The compiler checks every type is handled; no registry to explain |
| Batch updates and a manual transaction template in handlers | Simple per-row statements | Readability over micro-performance at demo scale |
| Actuator | Nothing | Its responses don't use the `ApiResult` envelope |

## Not built (out of scope)

| Feature | How I'd do it |
| --- | --- |
| Authentication / authorization | Spring Security with a resource server; jobs owned by a user |
| Scheduled or recurring jobs | A schedule table plus a scheduler that inserts QUEUED rows |
| Automatic retry with backoff | `next_attempt_at` column; the poller only claims due jobs |
| Cancel a queued or running job | A `CANCELLED` status; handlers check a cancel flag between steps |
| Stuck-job reaper | A heartbeat column; a sweeper re-queues PROCESSING jobs whose heartbeat is stale |
| Push updates to the UI (SSE / WebSockets) | The UI polls instead; an SSE endpoint fed by status changes would replace polling |
| Exactly-once notification delivery | Idempotency keys on the gateway; today it is effectively once |
| Multiple app instances | The atomic claim already allows it; needs a shared database and testing |
| Persistent database, Docker | Postgres + Flyway works unchanged apart from a few H2-specific SQL functions in stats |
| Charts | The stats endpoint returns the numbers; charting is a frontend concern |
