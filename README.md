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

## Progress

- [x] Phase 1: project setup, health endpoints, Docker, routing skeleton
- [x] Phase 2: database schema, entities, repositories, role seed
