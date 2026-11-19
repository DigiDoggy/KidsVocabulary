# KidsVocabulary

A web application that helps children learn foreign-language words, with a focus on spelling.
Architecture and domain decisions are described in [docs/DESIGN.md](docs/DESIGN.md).

## Run with Docker

```bash
cp .env.example .env
docker compose up --build
```

- App: http://localhost:3000
- Backend API: http://localhost:8080/api/v1/info
- Health: http://localhost:8080/actuator/health

Stop: `docker compose down`. Stop and delete database data: `docker compose down -v`.

## Local development (database in Docker)

Requires Java 21 and Node 22.

```bash
docker compose up -d postgres
cd backend && ./mvnw spring-boot:run
cd frontend && npm install && npm run dev     # http://localhost:5173, /api is proxied to :8080
```

## Tests

```bash
cd backend && ./mvnw test           # all tests, including integration tests (requires Docker / Testcontainers)
cd backend && ./mvnw test -Pfast    # without integration tests
cd frontend && npm test
```

## Structure

```
backend/    Spring Boot 3, Java 21, Flyway, PostgreSQL
frontend/   React + TypeScript (Vite); served by nginx in Docker, /api proxied to the backend
docs/       design documentation
```
