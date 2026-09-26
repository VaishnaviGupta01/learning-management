# Learning Management System

| Service      | Stack                    | Port | Health              |
|--------------|--------------------------|------|---------------------|
| backend      | Spring Boot 3.3, Java 21 | 8080 | `GET /api/health`   |
| ml-service   | FastAPI                  | 8001 | `GET /api/health`   |
| rag-service  | FastAPI                  | 8002 | `GET /api/health`   |
| frontend     | React + Vite             | 5173 | —                   |
| postgres     | PostgreSQL 16 + pgvector | 5432 | —                   |

## Run with Docker

```bash
cp .env.example .env
docker compose up --build
```

Open http://localhost:5173. The home page shows a live status panel for all three services.

## Run locally

```bash
# backend (needs a running PostgreSQL matching .env)
cd backend && mvn spring-boot:run

# ml-service / rag-service
cd ml-service && pip install -r requirements.txt && uvicorn app.main:app --reload --port 8001
cd rag-service && pip install -r requirements.txt && uvicorn app.main:app --reload --port 8002

# frontend
cd frontend && npm install && npm run dev
```

Backend tests use an in-memory H2 database: `cd backend && mvn test`.

## Layout

```
backend/src/main/java/com/lms/
  entity/          21 JPA entities (+ BaseEntity with id/created_at/updated_at)
  entity/enums/    9 enums
  repository/      21 Spring Data repositories
  seed/RoleSeeder  idempotent STUDENT/INSTRUCTOR/ADMIN seed
  controller/      HealthController
docs/SCHEMA.sql    reference DDL matching the entities
```

## API (backend)

All endpoints except `/api/auth/**` and `/api/health` need `Authorization: Bearer <token>`.
An initial admin is created from `ADMIN_EMAIL` / `ADMIN_PASSWORD` on startup (admins cannot self-register).

| Area | Endpoints | Who |
|---|---|---|
| Auth | `POST /api/auth/register`, `POST /api/auth/login`, `GET /api/users/me` | public / any |
| Courses | `GET/POST /api/courses`, `GET/PUT/DELETE /api/courses/{id}`, `PATCH /api/courses/{id}/publish?published=` | write: owning instructor or admin |
| Modules | `GET/POST /api/courses/{id}/modules`, `PUT/DELETE /api/modules/{id}` | write: owning instructor or admin |
| Topics | `POST /api/modules/{id}/topics`, `GET/PUT/DELETE /api/topics/{id}`, `GET /api/courses/{id}/topics` | write: owning instructor or admin |
| Prerequisites | `GET/POST /api/topics/{id}/prerequisites`, `DELETE /api/topics/{id}/prerequisites/{prereqId}`, `GET /api/courses/{id}/learning-path` | write: owning instructor or admin |
| Questions | `GET/POST /api/topics/{id}/questions`, `GET /api/questions/pending`, `GET/PUT/DELETE /api/questions/{id}`, `PATCH /api/questions/{id}/approve` / `reject` | instructor / admin |
| Quizzes | `GET/POST /api/courses/{id}/quizzes`, `GET/DELETE /api/quizzes/{id}`, `PATCH /api/quizzes/{id}/publish` | write: instructor / admin |
| Attempts | `POST /api/quizzes/{id}/attempts`, `POST /api/attempts/{id}/submit`, `GET /api/attempts/{id}`, `GET /api/attempts/me` | student |
| Diagnostic | `POST /api/courses/{id}/diagnostic` (optional body `{"topicIds": [...]}`) | student |
| Progress | `GET /api/students/me/progress` | student |
| Knowledge | `GET /api/students/me/topics/knowledge?courseId=` | student |
| Recommendations | `GET /api/students/me/recommendations?courseId=` (recomputes and stores) | student |

Submitting a quiz updates course progress, logs a study session and recalculates knowledge for every topic in the quiz.

## ML service

| Endpoint | Formula |
|---|---|
| `POST /api/knowledge/score` | `0.4·diagnostic + 0.3·recent_quiz + 0.2·practice + 0.1·revision` (missing inputs are skipped and weights renormalised); bands STRONG ≥ 0.80, MODERATE ≥ 0.60, NEEDS_PRACTICE ≥ 0.40, else WEAK |
| `POST /api/recommend` | `priority = 0.5·(1 − knowledge) + 0.3·importance + 0.2·urgency` |

Weights and thresholds live in [ml-service/app/config.py](ml-service/app/config.py) and can be overridden with JSON env vars.
Tests: `cd ml-service && pip install -r requirements-dev.txt && pytest`.

## Database changes

Hibernate adds new columns automatically but does not rewrite CHECK constraints. For a database created
before Phase 6, apply [docs/migrations/V6_8__knowledge_bands_and_topic_importance.sql](docs/migrations/V6_8__knowledge_bands_and_topic_importance.sql) once.

Errors use one JSON shape: `{timestamp, status, error, message, path, fieldErrors?}` with
400 (validation / business rule), 401 (missing token or bad credentials), 403 (role/ownership), 404, 409 (duplicate / in use).

## Progress

- [x] Phase 1: project setup, health endpoints, Docker, routing skeleton
- [x] Phase 2: database schema, entities, repositories, role seed
- [x] Phase 3: JWT authentication, role-based access, global error handling
- [x] Phase 4: course / module / topic management, prerequisite graph with cycle detection
- [x] Phase 5: question bank with AI review workflow, quizzes, server-side grading, diagnostics
- [x] Phase 6: progress tracking (completion, accuracy, study sessions)
- [x] Phase 7: knowledge model (ml-service scoring, per-topic mastery bands)
- [x] Phase 8: recommendation engine with prerequisite gating
