# QueueBoard

A small application for submitting and monitoring background jobs: submit a job, watch it move
through QUEUED, PROCESSING, and COMPLETED or FAILED, inspect its attempts and errors, and retry
failed jobs when the rules allow.

| Part | Stack | Folder |
| --- | --- | --- |
| Backend | Java 21, Spring Boot 4.1, Gradle, H2 (in memory), Flyway | `backend/` |
| Frontend | Angular 21, Angular Material | `frontend/` |

## Run with Docker (recommended)

Prerequisite: Docker with Compose.

```bash
docker compose up --build
```

| What | URL |
| --- | --- |
| App | http://localhost:8080 |
| Swagger UI | http://localhost:8080/swagger-ui.html |

The first build downloads Gradle and npm dependencies and takes a few minutes. The UI may show a
"connection lost" banner for a few seconds while the backend starts; it clears on its own.

Stop with `Ctrl+C`, or `docker compose down`. Data is in memory and resets on every restart.

## Run without Docker

Prerequisites: Java 21 and Node 22 (or any version Angular 21 supports, see `frontend/README.md`).

```bash
# Terminal 1: backend on http://localhost:8080
cd backend
./gradlew bootRun

# Terminal 2: frontend on http://localhost:4200 (proxies /api to the backend)
cd frontend
npm ci
npm start
```

## Tests

```bash
cd backend && ./gradlew test
cd frontend && npm test
```

## Known limitations

[backend/docs/descoped.md](backend/docs/descoped.md) lists what I cut, isolated, or chose not to
build. In short:

- **Processing is simulated.** Job types run against a small simulated LMS, kept behind an
  interface so real integrations could replace it.
- **Not built:** authentication, scheduled or recurring jobs, automatic retry with backoff, cancel,
  a stuck-job reaper, and push updates (the UI polls). Manual retry stops after 3 attempts.
- **H2 only, in memory.** Data resets on every restart, and a few SQL functions would need
  rewriting for Postgres.

## More detail

- `backend/README.md`: API, configuration, and runtime behaviour
- `frontend/README.md`: frontend structure and scripts
- `backend/docs/descoped.md`: what I cut and how I would build it
