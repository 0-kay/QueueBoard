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

Prerequisites: Java 21, Node 22.

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

## More detail

- `backend/README.md`: API, configuration, and backend known limitations
- `frontend/README.md`: frontend structure and scripts
- `backend/docs/`: build notes and what was descoped
