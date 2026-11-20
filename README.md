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

## AI word suggestions (optional)

Without configuration, weekly sets are filled from the built-in dictionary. To let an LLM propose words,
set in `.env`:

```bash
AI_PROVIDER=gemini            # or openai-compatible (Groq by default)
AI_API_KEY=your-key
# AI_MODEL=..., AI_BASE_URL=... to override the defaults
```

Generated words are validated, stored in the shared dictionary and reused. If the provider fails or
its quota runs out, the app falls back to the dictionary and retries the provider after 5 minutes.

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
