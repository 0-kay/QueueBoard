# simulation

**Not the focus of the assessment.** The assessment asks for simulated processing; this package makes
that simulation behave like a small LMS so jobs have something realistic to do. It is kept apart so
the core stays easy to read.

The core (API, job lifecycle, worker, retry rules, error handling) knows only one interface,
`service/processing/JobProcessor`. This package implements it:

| Class | Role |
| --- | --- |
| `SimulatedJobProcessor` | The dispatcher: one `switch` from payload type to simulation |
| `GradeReportSimulation` | Letter grades and awards for a course, in one transaction (rolls back on failure) |
| `NotificationBatchSimulation` | One delivery per enrolled student, each SENT flag committed on its own, so a retry sends only the rest |
| `BillingAssessmentSimulation` | Balances, one late fee per past-due account, aid status, in one transaction |
| `LmsRepository` | All the SQL for the LMS tables (`V2__simulation_schema.sql`), via JdbcTemplate |
| `SimulatedReferenceData` | Dropdown data (courses, terms, templates) for the submit form |
| `NotificationSender` | Pretend email/push gateway; tests spy on it to inject failures |
| `SimulatedWork` | Sleeps to make jobs take a few seconds (`queueboard.simulation.delay-scale`) |

It also produces the permanent failures the UI demonstrates: a closed course (`PHYS-099`), a closed
term (`SP26`) or an inactive template (`LEGACY_NEWSLETTER`).

Replacing the simulation with real integrations means writing another `JobProcessor`; nothing in the
core changes.
